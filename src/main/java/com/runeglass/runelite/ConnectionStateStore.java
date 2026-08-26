package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ConnectionStateStore
{
	static final String FILE_NAME = "connection-state-v1.json";
	static final String TEMP_FILE_NAME = "connection-state-v1.tmp";
	static final String LEGACY_CONFIG_KEY = "connectionStateV1";

	private static final int FORMAT_VERSION = 1;
	private static final int MAX_FILE_BYTES = 1_024;
	private static final Pattern CONNECTION_ID = Pattern.compile("^pcn_[A-Za-z0-9_-]{43}$");
	private static final Pattern CREDENTIAL = Pattern.compile("^[A-Za-z0-9_-]{43}$");
	private static final Pattern SEQUENCE = Pattern.compile("^[1-9][0-9]{0,19}$");
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(
		PosixFilePermission.OWNER_READ,
		PosixFilePermission.OWNER_WRITE,
		PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = EnumSet.of(
		PosixFilePermission.OWNER_READ,
		PosixFilePermission.OWNER_WRITE);

	static final class State
	{
		private final PairingClient.Credentials credentials;
		private final BigInteger nextSequence;

		private State(
			PairingClient.Credentials credentials,
			BigInteger nextSequence)
		{
			this.credentials = credentials;
			this.nextSequence = nextSequence;
		}

		PairingClient.Credentials getCredentials()
		{
			return credentials;
		}

		BigInteger getNextSequence()
		{
			return nextSequence;
		}
	}

	private static final class StoredState
	{
		private final int version = FORMAT_VERSION;
		private final String connectionId;
		private final String credential;
		private final String nextSequence;

		private StoredState(
			PairingClient.Credentials credentials,
			BigInteger nextSequence)
		{
			this.connectionId = credentials.getConnectionId();
			this.credential = credentials.getRawCredential();
			this.nextSequence = nextSequence.toString();
		}
	}

	private final Path directory;
	private final Path stateFile;
	private final Path temporaryFile;
	private final Gson gson;

	ConnectionStateStore(Path profilesDirectory, String profileKey, Gson gson)
	{
		Objects.requireNonNull(profilesDirectory, "profilesDirectory");
		Objects.requireNonNull(profileKey, "profileKey");
		if (profileKey.isEmpty())
		{
			throw new IllegalArgumentException("RuneLite profile key is required");
		}
		this.directory = profilesDirectory.resolve(hashProfileKey(profileKey));
		this.stateFile = directory.resolve(FILE_NAME);
		this.temporaryFile = directory.resolve(TEMP_FILE_NAME);
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	Optional<State> load()
	{
		try
		{
			Files.deleteIfExists(temporaryFile);
			if (!Files.exists(stateFile) || Files.size(stateFile) > MAX_FILE_BYTES)
			{
				return Optional.empty();
			}
			JsonObject body;
			try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8))
			{
				body = new JsonParser().parse(reader).getAsJsonObject();
			}
			if (!ProtocolJson.hasExactKeys(
				body,
				"version",
				"connectionId",
				"credential",
				"nextSequence")
				|| ProtocolJson.intValue(body, "version") != FORMAT_VERSION)
			{
				throw new IllegalArgumentException("Unsupported RuneGlass connection state");
			}
			String connectionId = ProtocolJson.stringValue(body, "connectionId");
			String credential = ProtocolJson.stringValue(body, "credential");
			String nextSequence = ProtocolJson.stringValue(body, "nextSequence");
			validate(connectionId, credential, nextSequence);
			applyPrivatePermissions(directory, DIRECTORY_PERMISSIONS);
			applyPrivatePermissions(stateFile, FILE_PERMISSIONS);
			return Optional.of(new State(
				new PairingClient.Credentials(connectionId, credential),
				new BigInteger(nextSequence)));
		}
		catch (IOException | RuntimeException exception)
		{
			clear();
			return Optional.empty();
		}
	}

	boolean save(PairingClient.Credentials credentials, BigInteger nextSequence)
	{
		Objects.requireNonNull(credentials, "credentials");
		Objects.requireNonNull(nextSequence, "nextSequence");
		validate(
			credentials.getConnectionId(),
			credentials.getRawCredential(),
			nextSequence.toString());
		byte[] serialized = gson.toJson(new StoredState(credentials, nextSequence))
			.getBytes(StandardCharsets.UTF_8);
		if (serialized.length > MAX_FILE_BYTES)
		{
			return false;
		}
		try
		{
			Files.createDirectories(directory);
			applyPrivatePermissions(directory, DIRECTORY_PERMISSIONS);
			try (FileChannel channel = FileChannel.open(
				temporaryFile,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE))
			{
				applyPrivatePermissions(temporaryFile, FILE_PERMISSIONS);
				ByteBuffer buffer = ByteBuffer.wrap(serialized);
				while (buffer.hasRemaining())
				{
					channel.write(buffer);
				}
				channel.force(true);
			}
			try
			{
				Files.move(
					temporaryFile,
					stateFile,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception)
			{
				Files.deleteIfExists(temporaryFile);
				return false;
			}
			applyPrivatePermissions(stateFile, FILE_PERMISSIONS);
			return true;
		}
		catch (IOException | RuntimeException exception)
		{
			try
			{
				Files.deleteIfExists(temporaryFile);
			}
			catch (IOException ignored)
			{
				return false;
			}
			return false;
		}
	}

	boolean clear()
	{
		try
		{
			Files.deleteIfExists(temporaryFile);
			Files.deleteIfExists(stateFile);
			Files.deleteIfExists(directory);
			return true;
		}
		catch (IOException | RuntimeException exception)
		{
			return false;
		}
	}

	Path getStateFile()
	{
		return stateFile;
	}

	private static void validate(String connectionId, String credential, String nextSequence)
	{
		if (!CONNECTION_ID.matcher(connectionId).matches()
			|| !CREDENTIAL.matcher(credential).matches()
			|| !SEQUENCE.matcher(nextSequence).matches())
		{
			throw new IllegalArgumentException("Invalid RuneGlass connection state");
		}
	}

	private static String hashProfileKey(String profileKey)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(
				("runeglass-profile-state-v1:" + profileKey).getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void applyPrivatePermissions(
		Path path,
		Set<PosixFilePermission> permissions) throws IOException
	{
		try
		{
			Files.setPosixFilePermissions(path, permissions);
		}
		catch (UnsupportedOperationException ignored)
		{
			return;
		}
	}
}
