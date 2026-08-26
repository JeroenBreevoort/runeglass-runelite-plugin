package com.runeglass.runelite;

import com.google.gson.Gson;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionStateStoreTest
{
	private static final String CONNECTION_ID = "pcn_ccccccccccccccccccccccccccccccccccccccccccc";
	private static final String CREDENTIAL = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void savesAndRestoresOneProfileScopedState() throws Exception
	{
		Path profiles = temporaryFolder.newFolder("profiles").toPath();
		ConnectionStateStore store = new ConnectionStateStore(profiles, "profile-one", new Gson());
		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.valueOf(42)));

		ConnectionStateStore.State state = store.load().orElseThrow(AssertionError::new);
		assertEquals(CONNECTION_ID, state.getCredentials().getConnectionId());
		assertEquals(CREDENTIAL, state.getCredentials().getRawCredential());
		assertEquals(BigInteger.valueOf(42), state.getNextSequence());
		assertTrue(Files.exists(store.getStateFile()));
		assertFalse(Files.exists(store.getStateFile().resolveSibling(ConnectionStateStore.TEMP_FILE_NAME)));
	}

	@Test
	public void separatesProfilesWithoutUsingRawProfileKeys() throws Exception
	{
		Path profiles = temporaryFolder.newFolder("profiles").toPath();
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
		Path profiles = temporaryFolder.newFolder("profiles").toPath();
		ConnectionStateStore store = new ConnectionStateStore(profiles, "profile-one", new Gson());
		Files.createDirectories(store.getStateFile().getParent());
		Files.write(
			store.getStateFile(),
			"{\"version\":1,\"connectionId\":\"wrong\",\"credential\":\"wrong\",\"nextSequence\":\"1\"}"
				.getBytes(StandardCharsets.UTF_8));

		assertFalse(store.load().isPresent());
		assertFalse(Files.exists(store.getStateFile()));
	}

	@Test
	public void appliesOwnerOnlyPermissionsWhenSupported() throws Exception
	{
		Path profiles = temporaryFolder.newFolder("profiles").toPath();
		ConnectionStateStore store = new ConnectionStateStore(profiles, "profile-one", new Gson());
		assertTrue(store.save(
			new PairingClient.Credentials(CONNECTION_ID, CREDENTIAL),
			BigInteger.ONE));

		try
		{
			Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(store.getStateFile());
			assertEquals(EnumSet.of(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE), filePermissions);
			Set<PosixFilePermission> directoryPermissions = Files.getPosixFilePermissions(
				store.getStateFile().getParent());
			assertEquals(EnumSet.of(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE), directoryPermissions);
		}
		catch (UnsupportedOperationException ignored)
		{
			assertTrue(Files.exists(store.getStateFile()));
		}
	}
}
