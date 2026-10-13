package com.runeglass.runelite;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.RuneLite;
import net.runelite.client.util.Filepath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

final class TestStorage
{
	private TestStorage()
	{
	}

	static Filepath rooted(Path directory)
	{
		return Filepath.Unchecked.getRooted(directory);
	}

	static Path path(Filepath filepath)
	{
		return Filepath.Unchecked.getPath(filepath);
	}

	static boolean posix()
	{
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	static String permissions(Path path) throws IOException
	{
		return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
	}

	static Path isolatedRuneLiteDirectory()
	{
		String expected = System.getProperty("runeglass.testHome");
		assertNotNull("Run RuneLite storage tests through Gradle so user.home is isolated", expected);
		Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
		assertEquals(Paths.get(expected).toAbsolutePath().normalize(), home);
		assertEquals("test-home", home.getParent().getFileName().toString());
		assertEquals("build", home.getParent().getParent().getFileName().toString());
		Path runelite = RuneLite.RUNELITE_DIR.toPath().toAbsolutePath().normalize();
		assertEquals(home.resolve(".runelite"), runelite);
		assertEquals(runelite.resolve("plugin-data"), RuneLite.PLUGIN_DATA.toAbsolutePath().normalize());
		return runelite;
	}

	static Path resetRuneLiteDirectory() throws IOException
	{
		Path runelite = isolatedRuneLiteDirectory();
		for (String name : new String[]{"plugin-data", "runeglass", "runeglass-development"})
		{
			Path directory = runelite.resolve(name);
			if (Files.exists(directory))
			{
				rooted(directory).deleteRecursively();
			}
		}
		return runelite;
	}

	static Map<String, String> tree(Path root) throws IOException, NoSuchAlgorithmException
	{
		Map<String, String> tree = new TreeMap<>();
		try (Stream<Path> files = Files.walk(root))
		{
			for (Path file : files.collect(Collectors.toList()))
			{
				String digest = Files.isDirectory(file) ? "directory" : Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
				String mode = posix() ? permissions(file) : "";
				tree.put(root.relativize(file).toString().replace('\\', '/'), digest + " " + mode);
			}
		}
		return tree;
	}
}
