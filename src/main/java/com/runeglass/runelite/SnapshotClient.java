package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Optional;
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

final class SnapshotClient
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

		default void onNextSequenceChanged(BigInteger nextSequence)
		{
		}

		default void onSessionDrained()
		{
		}
	}

	enum Failure
	{
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
	private final Clock clock;
	private final int baseRetrySeconds;
	private final LongSupplier retryJitterMillis;
	private final Deque<SkillsSnapshot> pending = new ArrayDeque<>();

	private long generation;
	private PairingClient.Credentials credentials;
	private SyncContext context;
	private Listener listener;
	private BigInteger nextSequence = BigInteger.ONE;
	private DurableSnapshotQueue durableQueue;
	private Instant nextUploadAt = Instant.EPOCH;
	private InFlightBatch inFlight;
	private Call activeCall;
	private ScheduledFuture<?> scheduledDispatch;
	private int retryAttempt;
	private boolean finalizingSession;
	private boolean closingAfterFlush;
	private boolean forceNextDispatch;

	static SnapshotClient create(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor)
	{
		return new SnapshotClient(
			httpClient,
			gson,
			executor,
			Objects.requireNonNull(
				HttpUrl.parse(PairingClient.BASE_URL),
				"RuneGlass base URL"),
			Clock.systemUTC(),
			5,
			() -> java.util.concurrent.ThreadLocalRandom.current().nextLong(1_001));
	}

	SnapshotClient(
		OkHttpClient httpClient,
		Gson gson,
		ScheduledExecutorService executor,
		HttpUrl baseUrl,
		Clock clock,
		int baseRetrySeconds,
		LongSupplier retryJitterMillis)
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
		this.baseRetrySeconds = baseRetrySeconds;
		this.retryJitterMillis = Objects.requireNonNull(retryJitterMillis, "retryJitterMillis");
	}

	boolean connect(
		PairingClient.Credentials nextCredentials,
		SyncContext nextContext,
		BigInteger persistedNextSequence,
		DurableSnapshotQueue nextDurableQueue,
		Listener nextListener)
	{
		Objects.requireNonNull(nextCredentials, "credentials");
		Objects.requireNonNull(nextContext, "context");
		Objects.requireNonNull(persistedNextSequence, "persistedNextSequence");
		Objects.requireNonNull(nextDurableQueue, "durableQueue");
		Objects.requireNonNull(nextListener, "listener");
		if (persistedNextSequence.signum() <= 0
			|| persistedNextSequence.toString().length() > 20
			|| nextDurableQueue.getLoadStatus() != DurableSnapshotQueue.LoadStatus.READY)
		{
			nextListener.onFailure(Failure.REJECTED_BATCH);
			return false;
		}
		synchronized (lock)
		{
			if (finalizingSession || activeCall != null || scheduledDispatch != null)
			{
				return false;
			}
			generation++;
			resetSessionLocked();
			credentials = nextCredentials;
			context = nextContext;
			listener = nextListener;
			durableQueue = nextDurableQueue;
			nextSequence = nextSequence.max(persistedNextSequence);
			nextUploadAt = Instant.EPOCH;
			closingAfterFlush = false;
		}
		if (!validateRestoredQueue())
		{
			failCurrent(Failure.REJECTED_BATCH);
			return false;
		}
		dispatchAsync();
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
		dispatchAsync();
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
			dispatchAsync();
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
			dispatchAsync();
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

	void discard()
	{
		final DurableSnapshotQueue queue;
		synchronized (lock)
		{
			queue = durableQueue;
			generation++;
			resetLocked();
		}
		if (queue != null)
		{
			try
			{
				executor.execute(() ->
				{
					try
					{
						queue.clear();
					}
					catch (IOException ignored)
					{
					}
				});
			}
			catch (RuntimeException ignored)
			{
			}
		}
	}

	private void dispatch()
	{
		final SequenceAdvance sequenceAdvance = persistPending();
		if (sequenceAdvance.listener != null)
		{
			sequenceAdvance.listener.onNextSequenceChanged(sequenceAdvance.nextSequence);
		}
		if (sequenceAdvance.failure != null)
		{
			fail(sequenceAdvance.generation, sequenceAdvance.failure);
			return;
		}

		final long requestGeneration;
		final Call call;
		final Listener currentListener;
		final int recordCount;
		final Listener drainedListener;
		Failure preparationFailure = null;

		synchronized (lock)
		{
			if (credentials == null
				|| context == null
				|| listener == null
				|| durableQueue == null
				|| activeCall != null
				|| scheduledDispatch != null)
			{
				return;
			}

			if (forceNextDispatch && inFlight == null && durableQueue.size() > 0)
			{
				nextUploadAt = Instant.EPOCH;
			}
			long cooldownMillis = nextUploadAt.toEpochMilli() - clock.millis();
			if (inFlight == null && cooldownMillis > 0 && durableQueue.size() > 0)
			{
				if (scheduleLocked(generation, cooldownMillis))
				{
					return;
				}
				preparationFailure = Failure.PROTOCOL_ERROR;
			}

			if (preparationFailure == null && inFlight == null)
			{
				Optional<DurableSnapshotQueue.Entry> queued = durableQueue.peek();
				if (!queued.isPresent())
				{
					drainedListener = drainSessionIfIdleLocked();
					requestGeneration = generation;
					call = null;
					currentListener = listener;
					recordCount = 0;
				}
				else
				{
					try
					{
						inFlight = queuedBatch(queued.get(), credentials.getConnectionId());
					}
					catch (RuntimeException exception)
					{
						preparationFailure = Failure.PROTOCOL_ERROR;
					}
					drainedListener = null;
					requestGeneration = generation;
					if (preparationFailure == null)
					{
						forceNextDispatch = false;
						Request request = request(inFlight.body, credentials.getRawCredential());
						call = httpClient.newCall(request);
						activeCall = call;
						currentListener = listener;
						recordCount = inFlight.recordCount;
					}
					else
					{
						call = null;
						currentListener = listener;
						recordCount = 0;
					}
				}
			}
			else if (preparationFailure != null)
			{
				requestGeneration = generation;
				call = null;
				currentListener = listener;
				recordCount = 0;
				drainedListener = null;
			}
			else if (inFlight != null)
			{
				requestGeneration = generation;
				Request request = request(inFlight.body, credentials.getRawCredential());
				call = httpClient.newCall(request);
				activeCall = call;
				currentListener = listener;
				recordCount = inFlight.recordCount;
				drainedListener = null;
			}
			else
			{
				requestGeneration = generation;
				call = null;
				currentListener = listener;
				recordCount = 0;
				drainedListener = null;
			}
		}

		if (drainedListener != null)
		{
			drainedListener.onSessionDrained();
			return;
		}

		if (preparationFailure != null)
		{
			fail(requestGeneration, preparationFailure);
			return;
		}
		if (call == null)
		{
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

	private SequenceAdvance persistPending()
	{
		synchronized (lock)
		{
			if (credentials == null || context == null || listener == null || durableQueue == null)
			{
				return SequenceAdvance.none(generation);
			}
			BigInteger advancedTo = null;
			try
			{
				while (!pending.isEmpty())
				{
					List<SkillsSnapshot> records = new ArrayList<>(MAX_PENDING_SNAPSHOTS);
					for (SkillsSnapshot snapshot : pending)
					{
						if (records.size() == MAX_PENDING_SNAPSHOTS)
						{
							break;
						}
						records.add(snapshot);
					}
					String batchId = java.util.UUID.randomUUID().toString();
					SyncBatch batch = new SyncBatch(
						batchId,
						credentials.getConnectionId(),
						nextSequence.toString(),
						clock.instant(),
						context,
						records);
					DurableSnapshotQueue.EnqueueResult result = durableQueue.enqueue(
						batchId,
						gson.toJson(batch));
					if (!result.wasAdded()
						|| result.getExpiredDropped() != 0
						|| result.getCapacityDropped() != 0)
					{
						throw new IOException("RuneGlass queue rejected a new batch");
					}
					for (int index = 0; index < records.size(); index++)
					{
						pending.removeFirst();
					}
					nextSequence = nextSequence.add(BigInteger.ONE);
					advancedTo = nextSequence;
				}
			}
			catch (IOException | RuntimeException exception)
			{
				return SequenceAdvance.failure(generation, Failure.PROTOCOL_ERROR);
			}
			return advancedTo == null
				? SequenceAdvance.none(generation)
				: SequenceAdvance.advanced(generation, listener, advancedTo);
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
		JsonObject body = ProtocolJson.readObject(response, MAX_RESPONSE_CHARACTERS);
		if (!ProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"status",
			"acceptedSequence",
			"serverTime",
			"nextUploadAfterSeconds")
			|| ProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			fail(requestGeneration, Failure.PROTOCOL_ERROR);
			return;
		}
		String status = ProtocolJson.stringValue(body, "status");
		String acceptedSequence = ProtocolJson.stringValue(body, "acceptedSequence");
		int nextUploadAfterSeconds = ProtocolJson.intValue(
			body,
			"nextUploadAfterSeconds");
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
				if (durableQueue == null)
				{
					currentListener = null;
					drainedListener = null;
					return;
				}
				durableQueue.acknowledge(inFlight.batchId);
				activeCall = null;
				inFlight = null;
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
		final boolean closed;
		synchronized (lock)
		{
			if (requestGeneration != generation || inFlight == null)
			{
				return;
			}
			activeCall = null;
			if (closingAfterFlush)
			{
				currentListener = listener;
				generation++;
				resetLocked();
				scheduled = true;
				closed = true;
			}
			else
			{
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
				closed = false;
			}
		}
		if (closed)
		{
			if (currentListener != null)
			{
				currentListener.onSessionDrained();
			}
			return;
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
		final DurableSnapshotQueue queue;
		synchronized (lock)
		{
			if (requestGeneration != generation)
			{
				return;
			}
			currentListener = listener;
			queue = durableQueue;
			generation++;
			resetLocked();
		}
		if (queue != null)
		{
			try
			{
				queue.clear();
			}
			catch (IOException ignored)
			{
			}
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
		durableQueue = null;
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
		if (!finalizingSession
			|| activeCall != null
			|| inFlight != null
			|| !pending.isEmpty()
			|| (durableQueue != null && durableQueue.size() > 0))
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
		HttpUrl url = Objects.requireNonNull(baseUrl.resolve(BATCH_PATH), "RuneGlass batch route");
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

	private boolean validateRestoredQueue()
	{
		synchronized (lock)
		{
			if (credentials == null || durableQueue == null)
			{
				return false;
			}
			BigInteger previous = null;
			try
			{
				for (DurableSnapshotQueue.Entry entry : durableQueue.pendingEntries())
				{
					InFlightBatch batch = queuedBatch(entry, credentials.getConnectionId());
					BigInteger sequence = new BigInteger(batch.sequence);
					if (previous != null && !sequence.equals(previous.add(BigInteger.ONE)))
					{
						return false;
					}
					previous = sequence;
				}
				if (previous != null)
				{
					nextSequence = nextSequence.max(previous.add(BigInteger.ONE));
				}
				return true;
			}
			catch (RuntimeException exception)
			{
				return false;
			}
		}
	}

	private void failCurrent(Failure failure)
	{
		final long currentGeneration;
		synchronized (lock)
		{
			currentGeneration = generation;
		}
		fail(currentGeneration, failure);
	}

	private static InFlightBatch queuedBatch(
		DurableSnapshotQueue.Entry entry,
		String expectedConnectionId)
	{
		JsonObject body = new JsonParser().parse(entry.getPayload()).getAsJsonObject();
		if (!ProtocolJson.hasExactKeys(
			body,
			"protocolVersion",
			"batchId",
			"connectionId",
			"sessionId",
			"sequence",
			"capturedAt",
			"client",
			"character",
			"records")
			|| ProtocolJson.intValue(body, "protocolVersion") != 1)
		{
			throw new IllegalArgumentException("Invalid queued RuneGlass batch");
		}
		String batchId = ProtocolJson.stringValue(body, "batchId");
		String connectionId = ProtocolJson.stringValue(body, "connectionId");
		String sequence = ProtocolJson.stringValue(body, "sequence");
		int recordCount = body.getAsJsonArray("records").size();
		if (!entry.getRecordId().equals(batchId)
			|| !expectedConnectionId.equals(connectionId)
			|| !sequence.matches("^[1-9][0-9]{0,19}$")
			|| recordCount < 1
			|| recordCount > MAX_PENDING_SNAPSHOTS)
		{
			throw new IllegalArgumentException("Invalid queued RuneGlass batch binding");
		}
		java.util.UUID.fromString(batchId);
		return new InFlightBatch(batchId, sequence, entry.getPayload(), recordCount);
	}

	private static final class SequenceAdvance
	{
		private final long generation;
		private final Listener listener;
		private final BigInteger nextSequence;
		private final Failure failure;

		private SequenceAdvance(
			long generation,
			Listener listener,
			BigInteger nextSequence,
			Failure failure)
		{
			this.generation = generation;
			this.listener = listener;
			this.nextSequence = nextSequence;
			this.failure = failure;
		}

		private static SequenceAdvance none(long generation)
		{
			return new SequenceAdvance(generation, null, null, null);
		}

		private static SequenceAdvance advanced(
			long generation,
			Listener listener,
			BigInteger nextSequence)
		{
			return new SequenceAdvance(generation, listener, nextSequence, null);
		}

		private static SequenceAdvance failure(
			long generation,
			Failure failure)
		{
			return new SequenceAdvance(generation, null, null, failure);
		}
	}

	private static final class InFlightBatch
	{
		private final String batchId;
		private final String sequence;
		private final String body;
		private final int recordCount;

		private InFlightBatch(String batchId, String sequence, String body, int recordCount)
		{
			this.batchId = batchId;
			this.sequence = sequence;
			this.body = body;
			this.recordCount = recordCount;
		}
	}
}
