package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class DropClient
{
	interface Listener
	{
		void onStatus(String status);
		void onInvalidConnection(PairingClient.Credentials credentials, boolean bindingMismatch);
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final HttpUrl endpoint;
	private final Filepath root;
	private final Listener listener;
	private final ArrayDeque<Runnable> work = new ArrayDeque<>();
	private final AtomicInteger capturesWaiting = new AtomicInteger();
	private boolean running;
	private volatile boolean purgeFailed;
	private final Set<State> pendingStates = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
	private volatile State active;

	static DropClient create(OkHttpClient http, Gson gson, ScheduledExecutorService executor, Filepath root, Listener listener)
	{
		return new DropClient(http, gson, executor,
			Objects.requireNonNull(HttpUrl.parse(PairingClient.BASE_URL)).resolve("runelite/v1/drops"), root, listener);
	}

	DropClient(OkHttpClient http, Gson gson, ScheduledExecutorService executor, HttpUrl endpoint, Filepath root, Listener listener)
	{
		this.http = http;
		this.gson = gson;
		this.executor = executor;
		this.endpoint = endpoint;
		this.root = root;
		this.listener = listener;
	}

	void connect(PairingClient.Credentials credentials, SyncContext context)
	{
		pause();
		State state = new State(credentials, context);
		pendingStates.add(state);
		active = state;
		submit(() ->
		{
			if (state.revoked) return;
			if (DurableDropQueue.discardMarker(root).exists()) purge(root);
			if (DurableDropQueue.discardMarker(root.joinSegment(credentials.getConnectionId())).exists())
			{
				purge(root.joinSegment(credentials.getConnectionId()));
			}
			if (purgeFailed)
			{
				status(state, "Drops paused: local deletion failed. Turn drop sync off to retry deletion.");
				return;
			}
			if (!"standard".equals(context.getProfileType()))
			{
				status(state, "Drops paused: only standard-profile worlds are supported.");
				return;
			}
			try
			{
				state.queue = new DurableDropQueue(root, credentials.getConnectionId(), gson);
				status(state, queueStatus(state));
				dispatch(state);
			}
			catch (IOException | RuntimeException exception)
			{
				state.paused = true;
				status(state, "Drops paused: local storage could not be opened. No new drops are saved.");
			}
		});
	}

	boolean isConnected()
	{
		State state = active;
		return state != null && !state.revoked && !state.paused && !purgeFailed && "standard".equals(state.context.getProfileType());
	}

	void captureFailed()
	{
		State state = active;
		if (state == null) return;
		state.missed = true;
		status(state, "A drop could not be captured safely. Previously saved drops remain queued.");
	}

	boolean publish(DropEvent event)
	{
		State state = active;
		if (state == null || state.revoked || state.paused || !"standard".equals(state.context.getProfileType())) return false;
		if (capturesWaiting.incrementAndGet() > 256)
		{
			capturesWaiting.decrementAndGet();
			state.missed = true;
			status(state, "Drop not saved: local storage is busy. Previously saved drops remain queued.");
			return false;
		}
		JsonObject body = event.envelope(state.context, state.credentials.getConnectionId());
		if (submit(() ->
		{
			try
			{
				if (state.revoked || purgeFailed || state.paused) return;
				if (state.queue == null) throw new IOException("Queue unavailable");
				state.queue.append(body);
				status(state, queueStatus(state));
				dispatch(state);
			}
			catch (DurableDropQueue.CapacityException exception)
			{
				state.missed = true;
				status(state, "Drop not saved: local queue is full (5 MiB / 10,000 events). Existing drops remain queued.");
				dispatch(state);
			}
			catch (IOException | RuntimeException exception)
			{
				state.missed = true;
				state.paused = true;
				status(state, "Drops paused: local persistence failed. New drops cannot be saved; previously saved drops remain queued.");
			}
			finally { capturesWaiting.decrementAndGet(); }
		})) return true;
		capturesWaiting.decrementAndGet();
		return false;
	}

	void pause()
	{
		State previous = active;
		active = null;
		if (previous != null)
		{
			cancel(previous);
			listener.onStatus("Drop sync paused. Saved pending drops remain on disk for this connection.");
			submit(() -> pendingStates.remove(previous));
		}
	}

	void discardAll()
	{
		pendingStates.forEach(state -> state.revoked = true);
		pause();
		submit(() -> purge(root));
	}

	void discardConnection(String connectionId)
	{
		pendingStates.stream().filter(state -> state.credentials.getConnectionId().equals(connectionId))
			.forEach(state -> state.revoked = true);
		State previous = active;
		if (previous != null && previous.credentials.getConnectionId().equals(connectionId))
		{
			previous.revoked = true;
			pause();
		}
		if (connectionId.matches("[A-Za-z0-9_-]{1,128}")) submit(() -> purge(root.joinSegment(connectionId)));
	}

	private void purge(Filepath path)
	{
		try
		{
			if (path.exists() || DurableDropQueue.discardMarker(path).exists())
			{
				DurableDropQueue.markDiscard(path);
				DurableDropQueue.purge(path);
				DurableDropQueue.discardMarker(path).deleteIfExists();
			}
			purgeFailed = DurableDropQueue.discardMarker(root).exists();
			if (active == null) listener.onStatus("Drop sync is off. Pending drops have been removed; synced history remains until deletion.");
		}
		catch (IOException | RuntimeException exception)
		{
			purgeFailed = true;
			listener.onStatus("Drops stopped: local deletion failed. Pending files remain on disk; uploads are blocked.");
		}
	}

	private void dispatch(State state)
	{
		if (!current(state) || purgeFailed || state.queue == null || state.paused || state.call != null || state.retry != null) return;
		List<DurableDropQueue.Entry> batch = state.queue.batch(state.individual);
		if (batch.isEmpty()) return;
		Call call = http.newCall(new Request.Builder().url(endpoint)
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + state.credentials.getRawCredential())
			.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), gson.toJson(DurableDropQueue.payload(batch))))
			.build());
		state.call = call;
		if (!current(state)) { call.cancel(); return; }
		try
		{
			call.enqueue(new Callback()
			{
				@Override public void onFailure(Call ignored, IOException exception)
				{
					submit(() -> retry(state, null));
				}
				@Override public void onResponse(Call ignored, Response response)
				{
					try (Response closed = response)
					{
						int code = closed.code();
						String retryAfter = closed.header("Retry-After");
						JsonObject body = code == 429 || code >= 500 ? null : ProtocolJson.readObject(closed, 8 * 1_024);
						submit(() -> response(state, batch, code, body, retryAfter));
					}
					catch (IOException | RuntimeException exception)
					{
						submit(() -> retry(state, null));
					}
				}
			});
		}
		catch (RuntimeException exception) { retry(state, null); }
	}

	private void response(State state, List<DurableDropQueue.Entry> batch, int code, JsonObject body, String retryAfter)
	{
		if (!current(state)) return;
		state.call = null;
		if (code == 429 || code >= 500) { retry(state, retryAfter); return; }
		try
		{
			if (code == 200)
			{
				if (!ProtocolJson.hasExactKeys(body, "protocolVersion", "status", "acceptedEventIds", "serverTime", "nextUploadAfterSeconds")
					|| ProtocolJson.intValue(body, "protocolVersion") != 1
					|| !Set.of("accepted", "duplicate").contains(ProtocolJson.stringValue(body, "status"))) throw new IOException("Invalid acknowledgement");
				Instant.parse(ProtocolJson.stringValue(body, "serverTime"));
				int delay = ProtocolJson.intValue(body, "nextUploadAfterSeconds");
				if (delay < 1 || delay > 3_600) throw new IOException("Invalid interval");
				Set<String> expected = new HashSet<>();
				batch.forEach(entry -> expected.add(entry.id()));
				Set<String> accepted = new HashSet<>();
				for (JsonElement id : body.getAsJsonArray("acceptedEventIds"))
				{
					if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()
						|| !expected.contains(id.getAsString()) || !accepted.add(id.getAsString())) throw new IOException("Unmatched acknowledgement");
				}
				if (accepted.isEmpty()) throw new IOException("Empty acknowledgement");
				state.queue.acknowledge(batch, accepted);
				state.attempt = 0;
				status(state, queueStatus(state));
				schedule(state, delay * 1_000L);
				return;
			}
			if (!ProtocolJson.hasExactKeys(body, "protocolVersion", "error")
				|| ProtocolJson.intValue(body, "protocolVersion") != 1) throw new IOException("Invalid error");
			String error = ProtocolJson.stringValue(body, "error");
			if ((code == 401 && "invalid_connection_credential".equals(error))
				|| (code == 403 && Set.of("binding_mismatch", "scope_denied").contains(error)))
			{
				if (code == 401)
				{
					discardConnection(state.credentials.getConnectionId());
					listener.onInvalidConnection(state.credentials, false);
				}
				else
				{
					state.paused = true;
					status(state, "Drops paused: character binding or consent does not match. Saved pending drops are retained.");
				}
				return;
			}
			if (code == 422 && "unsupported_profile".equals(error))
			{
				state.paused = true;
				status(state, "Drops paused: unsupported profile. Saved pending drops are retained.");
				return;
			}
			if ((code == 400 && "invalid_payload".equals(error)) || (code == 409 && "event_conflict".equals(error)))
			{
				if (batch.size() == 1) state.queue.block(batch.get(0));
				state.individual = true;
				status(state, "Drops need attention: rejected records remain locally; other saved drops continue syncing.");
				schedule(state, 5_000);
				return;
			}
			throw new IOException("Unexpected response");
		}
		catch (IOException | RuntimeException exception)
		{
			retry(state, null);
		}
	}

	private void retry(State state, String retryAfter)
	{
		if (!current(state)) return;
		state.call = null;
		long seconds = Math.min(300, 5L << Math.min(state.attempt++, 6));
		if (retryAfter != null)
		{
			try { seconds = Math.max(seconds, Math.min(86_400L, Long.parseLong(retryAfter))); }
			catch (NumberFormatException ignored)
			{
				try { seconds = Math.max(seconds, Math.min(86_400L, java.time.Duration.between(Instant.now(),
					java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
				catch (RuntimeException invalidDate) { seconds = Math.max(5, seconds); }
			}
		}
		status(state, "Drops waiting to retry. " + queueStatus(state));
		schedule(state, seconds * 1_000L);
	}

	private void schedule(State state, long millis)
	{
		if (!current(state)) return;
		state.retry = executor.schedule(() ->
		{
			submit(() ->
			{
				state.retry = null;
				dispatch(state);
			});
		}, millis, TimeUnit.MILLISECONDS);
	}

	private boolean current(State state) { return active == state && !state.revoked; }
	private void status(State state, String value) { if (current(state)) listener.onStatus(value); }
	private String queueStatus(State state)
	{
		return (state.queue.size() == 0 ? "Drop tracking ready. No saved events waiting to sync."
			: state.queue.size() + " drop events saved locally, waiting to sync.")
			+ (state.queue.hasDamagedRecords() ? " Some local records need attention and have not been uploaded." : "")
			+ (state.missed ? " Some drops in this session could not be saved." : "");
	}

	private static void cancel(State state)
	{
		Call call = state.call;
		if (call != null) call.cancel();
		ScheduledFuture<?> retry = state.retry;
		if (retry != null) retry.cancel(false);
	}

	private boolean submit(Runnable task)
	{
		synchronized (work)
		{
			work.add(task);
			if (running) return true;
			running = true;
			try { executor.execute(this::drain); return true; }
			catch (RuntimeException exception)
			{
				work.clear();
				running = false;
				listener.onStatus("Drops stopped: background storage is unavailable. New drops cannot be saved.");
				return false;
			}
		}
	}

	private void drain()
	{
		while (true)
		{
			Runnable task;
			synchronized (work)
			{
				task = work.poll();
				if (task == null) { running = false; return; }
			}
			try { task.run(); }
			catch (RuntimeException exception)
			{
				listener.onStatus("Drops paused: background processing failed. Existing saved records are retained.");
			}
		}
	}

	private static final class State
	{
		final PairingClient.Credentials credentials;
		final SyncContext context;
		volatile boolean revoked;
		volatile boolean missed;
		volatile Call call;
		volatile ScheduledFuture<?> retry;
		DurableDropQueue queue;
		boolean individual;
		volatile boolean paused;
		int attempt;
		State(PairingClient.Credentials credentials, SyncContext context) { this.credentials = credentials; this.context = context; }
	}
}
