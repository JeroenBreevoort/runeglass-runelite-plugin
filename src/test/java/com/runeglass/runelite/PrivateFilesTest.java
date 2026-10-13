package com.runeglass.runelite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class PrivateFilesTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void writesReplacementsAtomicallyWithOwnerOnlyFiles() throws Exception
	{
		assumeTrue(TestStorage.posix());
		Path rootPath = temporaryFolder.newFolder("plugin").toPath();
		Filepath target = TestStorage.rooted(rootPath).joinSegment("state.json");
		byte[] first = "{\"value\":1}".getBytes(StandardCharsets.UTF_8);
		byte[] second = "{\"value\":22}".getBytes(StandardCharsets.UTF_8);

		PrivateFiles.write(target, "state.", first);
		assertArrayEquals(first, Files.readAllBytes(TestStorage.path(target)));
		assertEquals("rw-------", TestStorage.permissions(TestStorage.path(target)));

		PrivateFiles.write(target, "state.", second);
		assertArrayEquals(second, Files.readAllBytes(TestStorage.path(target)));
		assertEquals("rw-------", TestStorage.permissions(TestStorage.path(target)));
		assertEquals(List.of("state.json"), names(rootPath));
	}

	@Test
	public void createsPrivateChildDirectoriesIdempotentlyAndConcurrently() throws Exception
	{
		ExecutorService threads = Executors.newFixedThreadPool(4);
		try
		{
			for (int round = 0; round < 50; round++)
			{
				Path rootPath = temporaryFolder.getRoot().toPath().resolve("absent-plugin-" + round);
				Filepath nested = TestStorage.rooted(rootPath).joinSegment("profiles").joinSegment("hash");
				CountDownLatch start = new CountDownLatch(1);
				List<Future<Boolean>> results = new ArrayList<>();
				for (int index = 0; index < 4; index++)
				{
					String name = "state-" + index + ".json";
					results.add(threads.submit((Callable<Boolean>) () ->
					{
						assertTrue(start.await(5, TimeUnit.SECONDS));
						PrivateFiles.createDirectory(nested);
						PrivateFiles.write(nested.joinSegment(name), "state.", new byte[]{1});
						return true;
					}));
				}
				start.countDown();
				for (Future<Boolean> result : results)
				{
					assertTrue(result.get(5, TimeUnit.SECONDS));
				}
				PrivateFiles.createDirectory(nested);

				assertEquals(List.of("profiles"), names(rootPath));
				assertEquals(List.of("hash"), names(rootPath.resolve("profiles")));
				assertEquals(List.of("state-0.json", "state-1.json", "state-2.json", "state-3.json"),
					names(rootPath.resolve("profiles").resolve("hash")));
				if (TestStorage.posix())
				{
					assertEquals("rwx------", TestStorage.permissions(rootPath.resolve("profiles")));
					assertEquals("rwx------", TestStorage.permissions(rootPath.resolve("profiles").resolve("hash")));
				}
			}
		}
		finally
		{
			threads.shutdownNow();
		}
	}

	@Test
	public void failedReplacementKeepsTheTargetAndRemovesItsTemporaryFile() throws Exception
	{
		Path rootPath = temporaryFolder.newFolder("plugin").toPath();
		Files.createDirectories(rootPath.resolve("state.json").resolve("occupied"));
		Filepath target = TestStorage.rooted(rootPath).joinSegment("state.json");

		assertThrows(IOException.class, () -> PrivateFiles.write(
			target,
			"state.",
			"{}".getBytes(StandardCharsets.UTF_8)));

		assertEquals(List.of("state.json"), names(rootPath));
		assertEquals(List.of("occupied"), names(rootPath.resolve("state.json")));
	}

	@Test
	public void deletesOnlyMatchingLegacyAndRandomTemporaryFiles() throws Exception
	{
		Path rootPath = temporaryFolder.newFolder("queue").toPath();
		for (String name : List.of(
			"snapshot-queue-v1.tmp",
			"snapshot-queue-v1.123456.tmp",
			"snapshot-queue-v1.json",
			"connection-state-v1.tmp",
			"other.tmp"))
		{
			Files.write(rootPath.resolve(name), new byte[]{1});
		}
		Files.createDirectory(rootPath.resolve("snapshot-queue-v1.dir.tmp"));

		PrivateFiles.deleteTemporaryFiles(TestStorage.rooted(rootPath), "snapshot-queue-v1.");
		PrivateFiles.deleteTemporaryFiles(TestStorage.rooted(rootPath).joinSegment("absent"), "snapshot-queue-v1.");

		assertEquals(
			List.of("connection-state-v1.tmp", "other.tmp", "snapshot-queue-v1.dir.tmp", "snapshot-queue-v1.json"),
			names(rootPath));
	}

	@Test
	public void readsStrictUtf8() throws Exception
	{
		Path rootPath = temporaryFolder.newFolder("plugin").toPath();
		Files.write(rootPath.resolve("valid.json"), "{\"name\":\"界\"}".getBytes(StandardCharsets.UTF_8));
		Files.write(rootPath.resolve("invalid.json"), new byte[]{'"', (byte) 0xC3, (byte) 0x28, '"'});
		Filepath root = TestStorage.rooted(rootPath);

		assertEquals("{\"name\":\"界\"}", PrivateFiles.readUtf8(root.joinSegment("valid.json")));
		assertThrows(IOException.class, () -> PrivateFiles.readUtf8(root.joinSegment("invalid.json")));
	}

	private static List<String> names(Path directory) throws IOException
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}
}
