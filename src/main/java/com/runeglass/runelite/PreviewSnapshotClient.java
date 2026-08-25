package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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

final class PreviewSnapshotClient
{
	private static final String BATCH_PATH = "runelite/v1/batches";
	private static final int MAX_PENDING_SNAPSHOTS = 8;
	private static final int MAX_RESPONSE_CHARACTERS = 8 * 1_024;
	private static final int MAX_RETRY_AFTER_SECONDS = 300;
	private static final int MAX_NEXT_UPLOAD_SECONDS = 3_600;
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	interface Listener
	{
		void onUploading(int recordCount);

		void onAccepted(Instant serverTime);

		void onRetryScheduled();

		void onFailure(Failure failure);

		default void onSessionDrained()
		{
		}
	}

	enum Failure
	{
		CONFIGURATION_MISSING,
		INVALID_CONNECTION,
		BINDING_MISMATCH,
		UNSUPPORTED_PROFILE,
		REJECTED_BATCH,
		PROTOCOL_ERROR
	}

	private final Object lock = new Object();
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final HttpUrl baseUrl;
	private final String previewKey;
	private final boolean enabled;
	private final Clock clock;
	private final int baseRetrySeconds;
	private final LongSupplier retryJitterMillis;
	private final Deque<SkillsSnapshot> pending = new ArrayDeque<>();

	private long generation;
	private PreviewPairingClient.Credentials credentials;
	private PreviewSyncContext context;
	private Listener listener;
	private BigInteger nextSequence = BigInteger.ONE;
	private Instant nextUploadAt = Instant.EPOCH;
	private InFlightBatch inFlight;
	private Call activeCall;
	private ScheduledFuture<?> scheduledDispatch;
	private int retryAttempt;
	private boolean finalizingSession;
	private boolean closingAfterFlush;
	private boolean forceNextDispatch;

	static PreviewSnapshotClient fromEnvironment(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor)
	{
		return new PreviewSnapshotClient(
			httpClient,
			gson,
			executor,
			Objects.requireNonNull(
				HttpUrl.parse(PreviewPairingClient.PREVIEW_BASE_URL),
				"preview base URL"),
			System.getenv(PreviewPairingClient.PREVIEW_KEY_ENV),
			"true".equals(System.getenv(PreviewPairingClient.PREVIEW_ENABLED_ENV)),
			Clock.systemUTC(),
			5,
			() -> java.util.concurrent.ThreadLocalRandom.current().nextLong(1_001));
	}

	PreviewSnapshotClient(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor,
		HttpUrl baseUrl,
		String previewKey,
		boolean enabled,
		Clock clock,
		int baseRetrySeconds,
		LongSupplier retryJitterMillis)
	{
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.gson = Objects.requireNonNull(gson, "gson");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.previewKey = previewKey;
		this.enabled = enabled;
		this.clock = Objects.requireNonNull(clock, "clock");
		if (baseRetrySeconds <= 0 || baseRetrySeconds > MAX_RETRY_AFTER_SECONDS)
		{
			throw new IllegalArgumentException("Invalid base retry interval");
		}
		this.baseRetrySeconds = baseRetrySeconds;
		this.retryJitterMillis = Objects.requireNonNull(retryJitterMillis, "retryJitterMillis");
	}

	boolean isAvailable()
	{
		return enabled && previewKey != null && previewKey.length() >= 32;
	}

	boolean connect(
		PreviewPairingClient.Credentials nextCredentials,
		PreviewSyncContext nextContext,
		Listener nextListener)
	{
		Objects.requireNonNull(nextCredentials, "credentials");
		Objects.requireNonNull(nextContext, "context");
		Objects.requireNonNull(nextListener, "listener");
		if (!isAvailable())
		{
			cancel();
			nextListener.onFailure(Failure.CONFIGURATION_MISSING);
			return false;
		}

		synchronized (lock)
		{
			if (finalizingSession || activeCall != null || scheduledDispatch != null)
			{
				return false;
			}
			boolean sameConnection = credentials != null
				&& credentials.sameConnection(nextCredentials);
			generation++;
			resetSessionLocked();
			if (!sameConnection)
			{
				nextSequence = BigInteger.ONE;
			}
			credentials = nextCredentials;
			context = nextContext;
			listener = nextListener;
			nextUploadAt = Instant.EPOCH;
			closingAfterFlush = false;
		}
		return true;
	}

	boolean publish(SkillsSnapshot snapshot)
	{
		return publish(snapshot, false);
	}

	boolean publishImmediately(SkillsSnapshot snapshot)
	{
		return publish(snapshot, true);
	}

	private boolean publish(SkillsSnapshot snapshot, boolean immediate)
	{
		Objects.requireNonNull(snapshot, "snapshot");
		synchronized (lock)
		{
			if (credentials == null || context == null || listener == null)
			{
				return false;
			}
			if (finalizingSession || closingAfterFlush)
			{
				return false;
			}
			enqueueSnapshotLocked(snapshot);
			if (immediate)
			{
				forceNextDispatch = true;
				nextUploadAt = Instant.EPOCH;
				cancelScheduledDispatchLocked();
			}
		}
		dispatch();
		return true;
	}

	boolean finishSession(SkillsSnapshot finalSnapshot)
	{
		Objects.requireNonNull(finalSnapshot, "finalSnapshot");
		return finishSessionInternal(finalSnapshot);
	}

	boolean finishSession()
	{
		return finishSessionInternal(null);
	}

	private boolean finishSessionInternal(SkillsSnapshot finalSnapshot)
	{
		final Listener drainedListener;
		synchronized (lock)
		{
			if (credentials == null || context == null || listener == null)
			{
				return false;
			}
			if (!finalizingSession && finalSnapshot != null)
			{
				enqueueSnapshotLocked(finalSnapshot);
			}
			finalizingSession = true;
			nextUploadAt = Instant.EPOCH;
			cancelScheduledDispatchLocked();
			drainedListener = drainSessionIfIdleLocked();
		}
		if (drainedListener != null)
		{
			drainedListener.onSessionDrained();
		}
		else
		{
			dispatch();
		}
		return true;
	}

	void closeAfterFlush()
	{
		boolean alreadyDrained = false;
		synchronized (lock)
		{
			closingAfterFlush = true;
			finalizingSession = true;
			nextUploadAt = Instant.EPOCH;
			cancelScheduledDispatchLocked();
			if (activeCall == null && inFlight == null && pending.isEmpty())
			{
				generation++;
				resetLocked();
				alreadyDrained = true;
			}
		}
		if (!alreadyDrained)
		{
			dispatch();
		}
	}

	boolean isFinishingSession()
	{
		synchronized (lock)
		{
			return finalizingSession || closingAfterFlush;
		}
	}

	void cancel()
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
		final Listener currentListener;
		final int recordCount;
		Failure preparationFailure = null;

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

			if (forceNextDispatch && inFlight == null && !pending.isEmpty())
			{
				nextUploadAt = Instant.EPOCH;
			}
			long cooldownMillis = nextUploadAt.toEpochMilli() - clock.millis();
			if (inFlight == null && cooldownMillis > 0 && !pending.isEmpty())
			{
				if (scheduleLocked(generation, cooldownMillis))
				{
					return;
				}
				preparationFailure = Failure.PROTOCOL_ERROR;
			}

			if (preparationFailure == null && inFlight == null)
			{
				if (pending.isEmpty())
				{
					return;
				}
				List<SkillsSnapshot> records = new ArrayList<>(MAX_PENDING_SNAPSHOTS);
				while (!pending.isEmpty() && records.size() < MAX_PENDING_SNAPSHOTS)
				{
					records.add(pending.removeFirst());
				}
				forceNextDispatch = false;
				try
				{
					PreviewSyncBatch batch = new PreviewSyncBatch(
						java.util.UUID.randomUUID().toString(),
						credentials.getConnectionId(),
						nextSequence.toString(),
						clock.instant(),
						context,
						records);
					inFlight = new InFlightBatch(
						nextSequence.toString(),
						gson.toJson(batch),
						records.size());
				}
				catch (RuntimeException exception)
				{
					preparationFailure = Failure.PROTOCOL_ERROR;
				}
			}

			if (preparationFailure != null)
			{
				requestGeneration = generation;
				call = null;
				currentListener = listener;
				recordCount = 0;
			}
			else
			{
				requestGeneration = generation;
				Request request = request(inFlight.body, credentials.getRawCredential());
				call = httpClient.newCall(request);
				activeCall = call;
				currentListener = listener;
				recordCount = inFlight.recordCount;
			}
		}

		if (preparationFailure != null)
		{
			fail(requestGeneration, preparationFailure);
			return;
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
			return;
		}
		currentListener.onUploading(recordCount);
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

		JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!PreviewProtocolJson.hasExactKeys(body, "protocolVersion", "error")
			|| PreviewProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		String error = PreviewProtocolJson.stringValue(body, "error");
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
		else if (response.code() == 400
			|| response.code() == 409
			|| response.code() == 413
			|| response.code() == 422)
		{
			fail(requestGeneration, Failure.REJECTED_BATCH);
		}
		else
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
		}
	}

	private void handleAccepted(long requestGeneration, Response response) throws IOException
	{
		JsonObject body = PreviewProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!PreviewProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"status",
			"acceptedSequence",
			"serverTime",
			"nextUploadAfterSeconds")
			|| PreviewProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		String status = PreviewProtocolJson.stringValue(body, "status");
		String acceptedSequence = PreviewProtocolJson.stringValue(body, "acceptedSequence");
		int nextUploadAfterSeconds = PreviewProtocolJson.intValue(
			body,
			"nextUploadAfterSeconds");
		final Instant serverTime;
		try
		{
			serverTime = Instant.parse(PreviewProtocolJson.stringValue(body, "serverTime"));
		}
		catch (DateTimeParseException exception)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}

		final Listener currentListener;
		final Listener drainedListener;
		synchronized (lock)
		{
			if (requestGeneration != generation || inFlight == null)
			{
				return;
			}
			if (!("accepted".equals(status) || "duplicate".equals(status))
				|| !inFlight.sequence.equals(acceptedSequence)
				|| nextUploadAfterSeconds <= 0
				|| nextUploadAfterSeconds > MAX_NEXT_UPLOAD_SECONDS)
			{
				currentListener = null;
				drainedListener = null;
			}
			else
			{
				activeCall = null;
				inFlight = null;
				nextSequence = nextSequence.add(BigInteger.ONE);
				nextUploadAt = clock.instant().plusSeconds(nextUploadAfterSeconds);
				if (finalizingSession)
				{
					nextUploadAt = Instant.EPOCH;
				}
				retryAttempt = 0;
				currentListener = listener;
				drainedListener = drainSessionIfIdleLocked();
			}
		}
		if (currentListener == null)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		currentListener.onAccepted(serverTime);
		if (drainedListener != null)
		{
			drainedListener.onSessionDrained();
		}
		else
		{
			dispatch();
		}
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
			if (closingAfterFlush)
			{
				generation++;
				resetLocked();
				return;
			}
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
		currentListener.onRetryScheduled();
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

	private void resetLocked()
	{
		if (activeCall != null)
		{
			activeCall.cancel();
			activeCall = null;
		}
		cancelScheduledDispatchLocked();
		pending.clear();
		credentials = null;
		context = null;
		listener = null;
		inFlight = null;
		nextSequence = BigInteger.ONE;
		nextUploadAt = Instant.EPOCH;
		retryAttempt = 0;
		finalizingSession = false;
		closingAfterFlush = false;
		forceNextDispatch = false;
	}

	private void resetSessionLocked()
	{
		cancelScheduledDispatchLocked();
		pending.clear();
		context = null;
		listener = null;
		inFlight = null;
		nextUploadAt = Instant.EPOCH;
		retryAttempt = 0;
		finalizingSession = false;
		closingAfterFlush = false;
		forceNextDispatch = false;
	}

	private void enqueueSnapshotLocked(SkillsSnapshot snapshot)
	{
		while (pending.size() >= MAX_PENDING_SNAPSHOTS)
		{
			pending.removeFirst();
		}
		pending.addLast(snapshot);
	}

	private void cancelScheduledDispatchLocked()
	{
		if (scheduledDispatch != null)
		{
			scheduledDispatch.cancel(false);
			scheduledDispatch = null;
		}
	}

	private Listener drainSessionIfIdleLocked()
	{
		if (!finalizingSession || activeCall != null || inFlight != null || !pending.isEmpty())
		{
			return null;
		}
		Listener drainedListener = listener;
		generation++;
		if (closingAfterFlush)
		{
			resetLocked();
		}
		else
		{
			resetSessionLocked();
		}
		return drainedListener;
	}

	private Request request(String body, String rawCredential)
	{
		HttpUrl url = Objects.requireNonNull(baseUrl.resolve(BATCH_PATH), "preview batch route");
		return new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.header("X-Runeglass-Preview-Key", previewKey)
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

	private static final class InFlightBatch
	{
		private final String sequence;
		private final String body;
		private final int recordCount;

		private InFlightBatch(String sequence, String body, int recordCount)
		{
			this.sequence = sequence;
			this.body = body;
			this.recordCount = recordCount;
		}
	}
}
