package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class SemanticSnapshotClient<T>
{
	private static final int MAX_RESPONSE_CHARACTERS = 8 * 1_024;
	private static final int MAX_RETRY_AFTER_SECONDS = 300;
	private static final int MAX_NEXT_UPLOAD_SECONDS = 3_600;
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	interface PayloadFactory<T>
	{
		Object create(SyncContext context, String connectionId, T snapshot);
	}

	interface Listener
	{
		void onAccepted(Instant serverTime);

		void onRetryScheduled();

		void onFailure(Failure failure);
	}

	enum Failure
	{
		INVALID_CONNECTION,
		BINDING_MISMATCH,
		UNSUPPORTED_PROFILE,
		REJECTED_SNAPSHOT,
		PROTOCOL_ERROR
	}

	private final Object lock = new Object();
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final HttpUrl baseUrl;
	private final Clock clock;
	private final int baseRetrySeconds;
	private final LongSupplier retryJitterMillis;
	private final String path;
	private final PayloadFactory<T> payloadFactory;

	private long generation;
	private PairingClient.Credentials credentials;
	private SyncContext context;
	private Listener listener;
	private T pending;
	private InFlight inFlight;
	private Call activeCall;
	private ScheduledFuture<?> scheduledDispatch;
	private Instant nextUploadAt = Instant.EPOCH;
	private int retryAttempt;

	static <T> SemanticSnapshotClient<T> create(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor,
		String path,
		PayloadFactory<T> payloadFactory)
	{
		return new SemanticSnapshotClient<>(
			httpClient,
			gson,
			executor,
			Objects.requireNonNull(HttpUrl.parse(PairingClient.BASE_URL), "RuneGlass base URL"),
			Clock.systemUTC(),
			5,
			() -> java.util.concurrent.ThreadLocalRandom.current().nextLong(1_001),
			path,
			payloadFactory);
	}

	SemanticSnapshotClient(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor,
		HttpUrl baseUrl,
		Clock clock,
		int baseRetrySeconds,
		LongSupplier retryJitterMillis,
		String path,
		PayloadFactory<T> payloadFactory)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.gson = Objects.requireNonNull(gson, "gson");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.clock = Objects.requireNonNull(clock, "clock");
		if (baseRetrySeconds <= 0 || baseRetrySeconds > MAX_RETRY_AFTER_SECONDS)
		{
			throw new IllegalArgumentException("Invalid base retry interval");
		}
		if (path == null || !path.matches("^runelite/v1/[a-z-]+$"))
		{
			throw new IllegalArgumentException("Invalid semantic snapshot path");
		}
		this.baseRetrySeconds = baseRetrySeconds;
		this.retryJitterMillis = Objects.requireNonNull(retryJitterMillis, "retryJitterMillis");
		this.path = path;
		this.payloadFactory = Objects.requireNonNull(payloadFactory, "payloadFactory");
	}

	void connect(
		PairingClient.Credentials nextCredentials,
		SyncContext nextContext,
		Listener nextListener)
	{
		synchronized (lock)
		{
			generation++;
			resetLocked();
			credentials = Objects.requireNonNull(nextCredentials, "credentials");
			context = Objects.requireNonNull(nextContext, "context");
			listener = Objects.requireNonNull(nextListener, "listener");
		}
	}

	boolean publish(T snapshot)
	{
		synchronized (lock)
		{
			if (credentials == null || context == null || listener == null)
			{
				return false;
			}
			pending = Objects.requireNonNull(snapshot, "snapshot");
		}
		dispatchAsync();
		return true;
	}

	void discard()
	{
		synchronized (lock)
		{
			generation++;
			resetLocked();
		}
	}

	private void dispatch()
	{
		final long requestGeneration;
		final Call call;
		synchronized (lock)
		{
			if (credentials == null
				|| context == null
				|| listener == null
				|| activeCall != null
				|| scheduledDispatch != null)
			{
				return;
			}

			long cooldownMillis = nextUploadAt.toEpochMilli() - clock.millis();
			if (inFlight == null && pending != null && cooldownMillis > 0)
			{
				if (!scheduleLocked(generation, cooldownMillis))
				{
					failLocked(Failure.PROTOCOL_ERROR);
				}
				return;
			}
			if (inFlight == null && pending != null)
			{
				T snapshot = pending;
				pending = null;
				inFlight = new InFlight(gson.toJson(payloadFactory.create(
					context,
					credentials.getConnectionId(),
					snapshot)));
			}
			if (inFlight == null)
			{
				return;
			}

			requestGeneration = generation;
			call = httpClient.newCall(request(inFlight.body, credentials.getRawCredential()));
			activeCall = call;
		}

		try
		{
			call.enqueue(new Callback()
			{
				@Override
				public void onFailure(Call ignored, IOException exception)
				{
					retry(requestGeneration, null);
				}

				@Override
				public void onResponse(Call ignored, Response response)
				{
					try (Response closedResponse = response)
					{
						handleResponse(requestGeneration, closedResponse);
					}
					catch (IOException | RuntimeException exception)
					{
						fail(requestGeneration, Failure.PROTOCOL_ERROR);
					}
				}
			});
		}
		catch (RuntimeException exception)
		{
			retry(requestGeneration, null);
		}
	}

	private void handleResponse(long requestGeneration, Response response) throws IOException
	{
		if (response.code() == 200)
		{
			handleAccepted(requestGeneration, response);
			return;
		}
		if (response.code() == 429 || response.code() == 500 || response.code() == 503)
		{
			retry(requestGeneration, retryAfterSeconds(response));
			return;
		}

		JsonObject body = ProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!ProtocolJson.hasExactKeys(body, "protocolVersion", "error")
			|| ProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		String error = ProtocolJson.stringValue(body, "error");
		if (response.code() == 401 && "invalid_connection_credential".equals(error))
		{
			fail(requestGeneration, Failure.INVALID_CONNECTION);
		}
		else if (response.code() == 403
			&& ("binding_mismatch".equals(error) || "scope_denied".equals(error)))
		{
			fail(requestGeneration, Failure.BINDING_MISMATCH);
		}
		else if (response.code() == 422 && "unsupported_profile".equals(error))
		{
			fail(requestGeneration, Failure.UNSUPPORTED_PROFILE);
		}
		else if (response.code() == 409 && "stale_observation".equals(error))
		{
			acceptLocally(requestGeneration, clock.instant(), 5);
		}
		else if (response.code() == 400 || response.code() == 413 || response.code() == 422)
		{
			fail(requestGeneration, Failure.REJECTED_SNAPSHOT);
		}
		else
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
		}
	}

	private void handleAccepted(long requestGeneration, Response response) throws IOException
	{
		JsonObject body = ProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!ProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"status",
			"serverTime",
			"nextUploadAfterSeconds")
			|| ProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		String status = ProtocolJson.stringValue(body, "status");
		int nextUploadAfterSeconds = ProtocolJson.intValue(body, "nextUploadAfterSeconds");
		final Instant serverTime;
		try
		{
			serverTime = Instant.parse(ProtocolJson.stringValue(body, "serverTime"));
		}
		catch (DateTimeParseException exception)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		if (!("accepted".equals(status) || "duplicate".equals(status))
			|| nextUploadAfterSeconds <= 0
			|| nextUploadAfterSeconds > MAX_NEXT_UPLOAD_SECONDS)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		acceptLocally(requestGeneration, serverTime, nextUploadAfterSeconds);
	}

	private void acceptLocally(
		long requestGeneration,
		Instant serverTime,
		int nextUploadAfterSeconds)
	{
		final Listener currentListener;
		synchronized (lock)
		{
			if (requestGeneration != generation || inFlight == null)
			{
				return;
			}
			activeCall = null;
			inFlight = null;
			nextUploadAt = clock.instant().plusSeconds(nextUploadAfterSeconds);
			retryAttempt = 0;
			currentListener = listener;
		}
		if (currentListener != null)
		{
			currentListener.onAccepted(serverTime);
		}
		dispatchAsync();
	}

	private void retry(long requestGeneration, Integer serverDelaySeconds)
	{
		final Listener currentListener;
		final boolean scheduled;
		synchronized (lock)
		{
			if (requestGeneration != generation || inFlight == null)
			{
				return;
			}
			activeCall = null;
			retryAttempt++;
			int exponent = Math.min(retryAttempt - 1, 6);
			int backoffSeconds = Math.min(
				MAX_RETRY_AFTER_SECONDS,
				baseRetrySeconds * (1 << exponent));
			int delaySeconds = serverDelaySeconds == null
				? backoffSeconds
				: Math.max(backoffSeconds, serverDelaySeconds);
			long jitterMillis = Math.max(0L, Math.min(1_000L, retryJitterMillis.getAsLong()));
			scheduled = scheduleLocked(
				requestGeneration,
				TimeUnit.SECONDS.toMillis(delaySeconds) + jitterMillis);
			currentListener = listener;
		}
		if (!scheduled)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		if (currentListener != null)
		{
			currentListener.onRetryScheduled();
		}
	}

	private boolean scheduleLocked(long requestGeneration, long delayMillis)
	{
		try
		{
			scheduledDispatch = executor.schedule(() ->
			{
				synchronized (lock)
				{
					if (requestGeneration != generation)
					{
						return;
					}
					scheduledDispatch = null;
				}
				dispatch();
			}, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
			return true;
		}
		catch (RuntimeException exception)
		{
			return false;
		}
	}

	private void fail(long requestGeneration, Failure failure)
	{
		final Listener currentListener;
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			currentListener = listener;
			generation++;
			resetLocked();
		}
		if (currentListener != null)
		{
			currentListener.onFailure(failure);
		}
	}

	private void failLocked(Failure failure)
	{
		Listener currentListener = listener;
		generation++;
		resetLocked();
		if (currentListener != null)
		{
			executor.execute(() -> currentListener.onFailure(failure));
		}
	}

	private Request request(String body, String rawCredential)
	{
		HttpUrl url = Objects.requireNonNull(baseUrl.resolve(path), "RuneGlass semantic snapshot route");
		return new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + rawCredential)
			.post(RequestBody.create(JSON, body))
			.build();
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

	private void dispatchAsync()
	{
		final long requestGeneration;
		synchronized (lock)
		{
			requestGeneration = generation;
		}
		try
		{
			executor.execute(() ->
			{
				synchronized (lock)
				{
					if (requestGeneration != generation)
					{
						return;
					}
				}
				dispatch();
			});
		}
		catch (RuntimeException exception)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
		}
	}

	private void resetLocked()
	{
		if (activeCall != null)
		{
			activeCall.cancel();
		}
		if (scheduledDispatch != null)
		{
			scheduledDispatch.cancel(false);
		}
		credentials = null;
		context = null;
		listener = null;
		pending = null;
		inFlight = null;
		activeCall = null;
		scheduledDispatch = null;
		nextUploadAt = Instant.EPOCH;
		retryAttempt = 0;
	}

	private static final class InFlight
	{
		private final String body;

		private InFlight(String body)
		{
			this.body = body;
		}
	}
}
