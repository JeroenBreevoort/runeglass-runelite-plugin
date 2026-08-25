package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class PreviewPairingClient
{
	static final String PREVIEW_ENABLED_ENV = "RUNELITE_PHASE2_HTTP_PREVIEW_ENABLED";
	static final String PREVIEW_KEY_ENV = "RUNELITE_PHASE2_HTTP_PREVIEW_KEY";
	static final String PREVIEW_BASE_URL = "https://small-cassowary-898.eu-west-1.convex.site/";
	static final String VERIFICATION_URI = "https://runeglass.app/settings/runelite";

	private static final String START_PATH = "runelite/v1/pairing/start";
	private static final String TOKEN_PATH = "runelite/v1/pairing/token";
	private static final String CREDENTIAL_HASH_PREFIX = "runelite-connection-credential-v1:";
	private static final int RAW_CREDENTIAL_BYTES = 32;
	private static final int MAX_RESPONSE_CHARACTERS = 8 * 1_024;
	private static final int MAX_PAIRING_LIFETIME_SECONDS = 15 * 60;
	private static final int MAX_POLL_INTERVAL_SECONDS = 30;
	private static final int MAX_RETRY_AFTER_SECONDS = 300;
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Pattern OPAQUE_VALUE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
	private static final Pattern USER_CODE = Pattern.compile(
		"^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}$");
	private static final Pattern CONNECTION_ID = Pattern.compile("^pcn_[A-Za-z0-9_-]{43}$");

	interface Listener
	{
		void onCode(String userCode, Instant expiresAt);

		void onConnected();

		void onFailure(Failure failure);
	}

	enum Failure
	{
		CONFIGURATION_MISSING,
		AUTHORIZATION_DENIED,
		EXPIRED,
		PROTOCOL_ERROR,
		TEMPORARILY_UNAVAILABLE
	}

	static final class Credentials
	{
		private final String connectionId;
		private final String rawCredential;

		Credentials(String connectionId, String rawCredential)
		{
			this.connectionId = connectionId;
			this.rawCredential = rawCredential;
		}

		String getConnectionId()
		{
			return connectionId;
		}

		String getRawCredential()
		{
			return rawCredential;
		}

		boolean sameConnection(Credentials other)
		{
			return other != null
				&& connectionId.equals(other.connectionId)
				&& rawCredential.equals(other.rawCredential);
		}
	}

	private final Object lock = new Object();
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final HttpUrl baseUrl;
	private final String previewKey;
	private final boolean enabled;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	private long generation;
	private Call activeCall;
	private ScheduledFuture<?> scheduledPoll;
	private String deviceCode;
	private String rawCredential;
	private Instant expiresAt;
	private int pollIntervalSeconds;
	private Credentials credentials;

	static PreviewPairingClient fromEnvironment(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor)
	{
		return new PreviewPairingClient(
			httpClient,
			gson,
			executor,
			Objects.requireNonNull(HttpUrl.parse(PREVIEW_BASE_URL), "preview base URL"),
			System.getenv(PREVIEW_KEY_ENV),
			"true".equals(System.getenv(PREVIEW_ENABLED_ENV)),
			Clock.systemUTC());
	}

	PreviewPairingClient(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor,
		HttpUrl baseUrl,
		String previewKey,
		boolean enabled,
		Clock clock)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.gson = Objects.requireNonNull(gson, "gson");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.previewKey = previewKey;
		this.enabled = enabled;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	boolean isAvailable()
	{
		return enabled && previewKey != null && previewKey.length() >= 32;
	}

	Optional<Credentials> getCredentials()
	{
		synchronized (lock)
		{
			return Optional.ofNullable(credentials);
		}
	}

	void start(Listener listener)
	{
		Objects.requireNonNull(listener, "listener");
		if (!isAvailable())
		{
			cancel();
			listener.onFailure(Failure.CONFIGURATION_MISSING);
			return;
		}

		final long requestGeneration;
		final String nextCredential = generateOpaqueValue();
		synchronized (lock)
		{
			cancelNetworkLocked();
			generation++;
			requestGeneration = generation;
			credentials = null;
			rawCredential = nextCredential;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("connectionCredentialHash", hashConnectionCredential(nextCredential));
		Request request = jsonRequest(START_PATH, payload);
		enqueue(requestGeneration, request, listener, this::handleStartResponse);
	}

	void cancel()
	{
		synchronized (lock)
		{
			generation++;
			cancelNetworkLocked();
			credentials = null;
		}
	}

	void cancelPending()
	{
		synchronized (lock)
		{
			generation++;
			cancelNetworkLocked();
		}
	}

	private void handleStartResponse(long requestGeneration, Response response, Listener listener)
		throws IOException
	{
		if (response.code() != 201)
		{
			fail(requestGeneration, listener, failureForResponse(response));
			return;
		}

		JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!PreviewProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"deviceCode",
			"userCode",
			"verificationUri",
			"expiresInSeconds",
			"pollIntervalSeconds")
			|| PreviewProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
			return;
		}

		String nextDeviceCode = PreviewProtocolJson.stringValue(body, "deviceCode");
		String userCode = PreviewProtocolJson.stringValue(body, "userCode");
		String verificationUri = PreviewProtocolJson.stringValue(body, "verificationUri");
		int expiresInSeconds = PreviewProtocolJson.intValue(body, "expiresInSeconds");
		int nextPollInterval = PreviewProtocolJson.intValue(body, "pollIntervalSeconds");
		if (!OPAQUE_VALUE.matcher(nextDeviceCode).matches()
			|| !USER_CODE.matcher(userCode).matches()
			|| !VERIFICATION_URI.equals(verificationUri)
			|| expiresInSeconds <= 0
			|| expiresInSeconds > MAX_PAIRING_LIFETIME_SECONDS
			|| nextPollInterval <= 0
			|| nextPollInterval > MAX_POLL_INTERVAL_SECONDS)
		{
			fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
			return;
		}

		Instant nextExpiry = clock.instant().plusSeconds(expiresInSeconds);
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			activeCall = null;
			deviceCode = nextDeviceCode;
			expiresAt = nextExpiry;
			pollIntervalSeconds = nextPollInterval;
		}
		listener.onCode(userCode, nextExpiry);
		schedulePoll(requestGeneration, listener, nextPollInterval);
	}

	private void poll(long requestGeneration, Listener listener)
	{
		final String currentDeviceCode;
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			scheduledPoll = null;
			if (expiresAt == null || !clock.instant().isBefore(expiresAt))
			{
				currentDeviceCode = null;
			}
			else
			{
				currentDeviceCode = deviceCode;
			}
		}
		if (currentDeviceCode == null)
		{
			fail(requestGeneration, listener, Failure.EXPIRED);
			return;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("deviceCode", currentDeviceCode);
		enqueue(requestGeneration, jsonRequest(TOKEN_PATH, payload), listener, this::handleTokenResponse);
	}

	private void handleTokenResponse(long requestGeneration, Response response, Listener listener)
		throws IOException
	{
		if (response.code() == 429)
		{
			JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
			Integer retryAfterSeconds = retryAfterSeconds(response);
			if (!isError(body, "rate_limited") || retryAfterSeconds == null)
			{
				fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
				return;
			}
			synchronized (lock)
			{
				if (requestGeneration != generation)
				{
					return;
				}
				activeCall = null;
			}
			schedulePoll(requestGeneration, listener, retryAfterSeconds);
			return;
		}

		if (response.code() == 202)
		{
			JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
			if (!isError(body, "authorization_pending"))
			{
				fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
				return;
			}
			final int nextPollInterval;
			synchronized (lock)
			{
				if (requestGeneration != generation)
				{
					return;
				}
				activeCall = null;
				nextPollInterval = pollIntervalSeconds;
			}
			schedulePoll(requestGeneration, listener, nextPollInterval);
			return;
		}

		if (response.code() != 200)
		{
			fail(requestGeneration, listener, failureForResponse(response));
			return;
		}

		JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!PreviewProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"status",
			"connectionId",
			"credentialType",
			"scope")
			|| PreviewProtocolJson.intValue(body, "protocolVersion") != 1
			|| !"issued".equals(PreviewProtocolJson.stringValue(body, "status"))
			|| !"Bearer".equals(PreviewProtocolJson.stringValue(body, "credentialType"))
			|| !"skills:write".equals(PreviewProtocolJson.stringValue(body, "scope")))
		{
			fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
			return;
		}

		String connectionId = PreviewProtocolJson.stringValue(body, "connectionId");
		if (!CONNECTION_ID.matcher(connectionId).matches())
		{
			fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
			return;
		}

		synchronized (lock)
		{
			if (requestGeneration != generation || rawCredential == null)
			{
				return;
			}
			activeCall = null;
			credentials = new Credentials(connectionId, rawCredential);
			deviceCode = null;
			rawCredential = null;
			expiresAt = null;
			pollIntervalSeconds = 0;
		}
		listener.onConnected();
	}

	private void enqueue(
		long requestGeneration,
		Request request,
		Listener listener,
		ResponseHandler responseHandler)
	{
		Call call = httpClient.newCall(request);
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				call.cancel();
				return;
			}
			activeCall = call;
		}
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call ignored, IOException exception)
			{
				fail(requestGeneration, listener, Failure.TEMPORARILY_UNAVAILABLE);
			}

			@Override
			public void onResponse(Call ignored, Response response)
			{
				try (Response closedResponse = response)
				{
					responseHandler.handle(requestGeneration, closedResponse, listener);
				}
				catch (IOException | RuntimeException exception)
				{
					fail(requestGeneration, listener, Failure.PROTOCOL_ERROR);
				}
			}
		});
	}

	private void schedulePoll(long requestGeneration, Listener listener, int delaySeconds)
	{
		try
		{
			ScheduledFuture<?> next = executor.schedule(
				() -> poll(requestGeneration, listener),
				delaySeconds,
				TimeUnit.SECONDS);
			synchronized (lock)
			{
				if (requestGeneration != generation)
				{
					next.cancel(false);
					return;
				}
				scheduledPoll = next;
			}
		}
		catch (RuntimeException exception)
		{
			fail(requestGeneration, listener, Failure.TEMPORARILY_UNAVAILABLE);
		}
	}

	private void fail(long requestGeneration, Listener listener, Failure failure)
	{
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			generation++;
			cancelNetworkLocked();
			credentials = null;
		}
		listener.onFailure(failure);
	}

	private void cancelNetworkLocked()
	{
		if (activeCall != null)
		{
			activeCall.cancel();
			activeCall = null;
		}
		if (scheduledPoll != null)
		{
			scheduledPoll.cancel(false);
			scheduledPoll = null;
		}
		deviceCode = null;
		rawCredential = null;
		expiresAt = null;
		pollIntervalSeconds = 0;
	}

	private Request jsonRequest(String path, JsonObject payload)
	{
		HttpUrl url = Objects.requireNonNull(baseUrl.resolve(path), "preview route");
		return new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.header("X-Runeglass-Preview-Key", previewKey)
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();
	}

	private static Failure failureForResponse(Response response)
	{
		try
		{
			JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
			if (isError(body, "authorization_denied"))
			{
				return Failure.AUTHORIZATION_DENIED;
			}
			if (isError(body, "expired_device_code"))
			{
				return Failure.EXPIRED;
			}
		}
		catch (IOException | RuntimeException ignored)
		{
			return Failure.TEMPORARILY_UNAVAILABLE;
		}
		return Failure.TEMPORARILY_UNAVAILABLE;
	}

	private static boolean isError(JsonObject body, String error)
	{
		return PreviewProtocolJson.hasExactKeys(body, "protocolVersion", "error")
			&& PreviewProtocolJson.intValue(body, "protocolVersion") == 1
			&& error.equals(PreviewProtocolJson.stringValue(body, "error"));
	}

	private static Integer retryAfterSeconds(Response response)
	{
		String header = response.header("Retry-After");
		if (header == null || !header.matches("^[1-9][0-9]{0,2}$"))
		{
			return null;
		}
		try
		{
			int value = Integer.parseInt(header);
			return value <= MAX_RETRY_AFTER_SECONDS ? value : null;
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	private String generateOpaqueValue()
	{
		byte[] bytes = new byte[RAW_CREDENTIAL_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hashConnectionCredential(String rawCredential)
	{
		Objects.requireNonNull(rawCredential, "rawCredential");
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(
				(CREDENTIAL_HASH_PREFIX + rawCredential).getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	@FunctionalInterface
	private interface ResponseHandler
	{
		void handle(long requestGeneration, Response response, Listener listener) throws IOException;
	}
}
