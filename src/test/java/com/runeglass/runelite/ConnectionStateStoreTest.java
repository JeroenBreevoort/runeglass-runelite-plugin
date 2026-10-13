package com.runeglass.runelite;

import com.google.gson.Gson;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class ConnectionStateStoreTest
{
	private static final String CONNECTION_ID = "pcn_ccccccccccccccccccccccccccccccccccccccccccc";
	private static final String CREDENTIAL = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void savesAndRestoresOneProfileScopedState() throws Exception
	{
		Filepath profiles = profiles();
		ConnectionStateStore store = new ConnectionStateStore(profiles, "profile-one", new Gson());
		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.valueOf(42)));

		ConnectionStateStore.State state = store.load().orElseThrow(AssertionError::new);
		assertEquals(CONNECTION_ID, state.getCredentials().getConnectionId());
		assertEquals(CREDENTIAL, state.getCredentials().getRawCredential());
		assertEquals(BigInteger.valueOf(42), state.getNextSequence());
		Path stateFile = TestStorage.path(store.getStateFile());
		assertTrue(Files.exists(stateFile));
		assertEquals(List.of(ConnectionStateStore.FILE_NAME), names(stateFile.getParent()));
		assertEquals(List.of(stateFile.getParent().getFileName().toString()), names(TestStorage.path(profiles)));
	}

	@Test
	public void separatesProfilesWithoutUsingRawProfileKeys() throws Exception
	{
		Filepath profiles = profiles();
		ConnectionStateStore first = new ConnectionStateStore(profiles, "profile-one", new Gson());
		ConnectionStateStore second = new ConnectionStateStore(profiles, "profile-two", new Gson());

		assertTrue(first.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.ONE));

		assertNotEquals(first.getStateFile(), second.getStateFile());
		assertFalse(first.getStateFile().toString().contains("profile-one"));
		assertFalse(second.load().isPresent());
	}

	@Test
	public void malformedStateIsDiscarded() throws Exception
	{
		ConnectionStateStore store = new ConnectionStateStore(profiles(), "profile-one", new Gson());
		Path stateFile = TestStorage.path(store.getStateFile());
		Files.createDirectories(stateFile.getParent());
		Files.write(
			stateFile,
			"{\"version\":1,\"connectionId\":\"wrong\",\"credential\":\"wrong\",\"nextSequence\":\"1\"}"
				.getBytes(StandardCharsets.UTF_8));

		assertFalse(store.load().isPresent());
		assertFalse(Files.exists(stateFile));
	}

	@Test
	public void appliesOwnerOnlyPermissionsWhenSupported() throws Exception
	{
		assumeTrue(TestStorage.posix());
		ConnectionStateStore store = new ConnectionStateStore(profiles(), "profile-one", new Gson());
		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.ONE));
		Path stateFile = TestStorage.path(store.getStateFile());
		assertEquals("rw-------", TestStorage.permissions(stateFile));
		assertEquals("rwx------", TestStorage.permissions(stateFile.getParent()));

		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.TEN));
		assertEquals("rw-------", TestStorage.permissions(stateFile));
		assertEquals(BigInteger.TEN, store.load().orElseThrow(AssertionError::new).getNextSequence());
	}

	@Test
	public void loadAndClearRemoveOrphanedCredentialTemporaryFiles() throws Exception
	{
		ConnectionStateStore store = new ConnectionStateStore(profiles(), "profile-one", new Gson());
		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.valueOf(5)));
		Path directory = TestStorage.path(store.getStateFile()).getParent();
		writeOrphans(directory);

		assertEquals(BigInteger.valueOf(5), store.load().orElseThrow(AssertionError::new).getNextSequence());
		assertEquals(List.of(ConnectionStateStore.FILE_NAME), names(directory));

		writeOrphans(directory);
		assertTrue(store.clear());
		assertFalse(Files.exists(directory));
	}

	private Filepath profiles() throws Exception
	{
		return TestStorage.rooted(temporaryFolder.newFolder("profiles").toPath());
	}

	private static void writeOrphans(Path directory) throws Exception
	{
		byte[] partial = ("{\"credential\":\"" + CREDENTIAL + "\"").getBytes(StandardCharsets.UTF_8);
		Files.write(directory.resolve("connection-state-v1.tmp"), partial);
		Files.write(directory.resolve("connection-state-v1.8231475.tmp"), partial);
	}

	private static List<String> names(Path directory) throws Exception
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}
}
