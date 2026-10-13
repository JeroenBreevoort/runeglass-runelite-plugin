package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import net.runelite.client.util.Filepath;

final class ConnectionStateStore
{
	static final String FILE_NAME = "connection-state-v1.json";
	static final String TEMP_PREFIX = "connection-state-v1.";
	static final String LEGACY_CONFIG_KEY = "connectionStateV1";

	private static final int FORMAT_VERSION = 1;
	private static final int MAX_FILE_BYTES = 1_024;
	private static final Pattern CONNECTION_ID = Pattern.compile("^pcn_[A-Za-z0-9_-]{43}$");
	private static final Pattern CREDENTIAL = Pattern.compile("^[A-Za-z0-9_-]{43}$");
	private static final Pattern SEQUENCE = Pattern.compile("^[1-9][0-9]{0,19}$");

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

	private final Filepath directory;
	private final Filepath stateFile;
	private final Gson gson;

	ConnectionStateStore(Filepath profilesDirectory, String profileKey, Gson gson)
	{
		Objects.requireNonNull(profilesDirectory, "profilesDirectory");
		Objects.requireNonNull(profileKey, "profileKey");
		if (profileKey.isEmpty())
		{
			throw new IllegalArgumentException("RuneLite profile key is required");
		}
		this.directory = profilesDirectory.joinSegment(hashProfileKey(profileKey));
		this.stateFile = directory.joinSegment(FILE_NAME);
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	Optional<State> load()
	{
		try
		{
			PrivateFiles.deleteTemporaryFiles(directory, TEMP_PREFIX);
			if (!stateFile.exists() || stateFile.size() > MAX_FILE_BYTES)
			{
				return Optional.empty();
			}
			JsonObject body = new JsonParser().parse(PrivateFiles.readUtf8(stateFile)).getAsJsonObject();
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
			PrivateFiles.createDirectory(directory);
			PrivateFiles.write(stateFile, TEMP_PREFIX, serialized);
			return true;
		}
		catch (IOException | RuntimeException exception)
		{
			return false;
		}
	}

	boolean clear()
	{
		try
		{
			PrivateFiles.deleteTemporaryFiles(directory, TEMP_PREFIX);
			stateFile.deleteIfExists();
			directory.deleteIfExists();
			return true;
		}
		catch (IOException | RuntimeException exception)
		{
			return false;
		}
	}

	Filepath getStateFile()
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
}
