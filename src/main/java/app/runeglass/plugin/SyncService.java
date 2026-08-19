/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Ships buffered events and snapshots to the backend.
 * <p>
 * A single fixed tick does all the work and gates internally on whether there is anything to do.
 * That is simpler to reason about — and to cancel — than rescheduling itself at varying delays.
 * <p>
 * Snapshots are built on the client thread by the collectors and handed here via
 * {@link #submitSnapshot}; this class never reads the game client itself.
 */
@Slf4j
@Singleton
public class SyncService
{
	private static final long TICK_MS = 5_000L;
	private static final long BACKOFF_BASE_MS = 10_000L;
	private static final long BACKOFF_MAX_MS = 5 * 60_000L;

	/**
	 * Ceiling on the serialized events in one request. Convex caps a document at 1 MiB, and the
	 * backend stores more than just what we send, so this leaves generous room. Without it, a
	 * single verbose event type is enough to make every request fail permanently.
	 */
	static final int MAX_EVENT_BYTES = 256 * 1024;

	private final RuneGlassClient client;
	private final RuneGlassConfig config;
	private final ScheduledExecutorService executor;
	private final Gson gson;
	private final EventQueue queue = new EventQueue();

	/** Stops two ticks from having requests in flight at once. */
	private final AtomicBoolean inFlight = new AtomicBoolean(false);

	private ScheduledFuture<?> tickFuture;

	private volatile AccountIdentity identity;
	private volatile String sessionId;
	private volatile RuneGlassApi.Snapshot pendingSnapshot;

	private volatile long backoffUntil;
	private volatile long backoffDelay = BACKOFF_BASE_MS;
	private volatile long lastSuccessAt;
	private volatile String lastError;
	/** Set when the backend rejects our token; only re-linking clears it. */
	private volatile boolean linkRejected;

	private volatile Consumer<SyncStatus> listener;

	@Inject
	SyncService(RuneGlassClient client, RuneGlassConfig config, ScheduledExecutorService executor, Gson gson)
	{
		this.client = client;
		this.config = config;
		this.executor = executor;
		this.gson = gson;
	}

	public void setListener(@Nullable Consumer<SyncStatus> listener)
	{
		this.listener = listener;
	}

	public synchronized void startUp()
	{
		stopTicking();
		tickFuture = executor.scheduleWithFixedDelay(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
	}

	/** Cancels the tick without blocking. The executor belongs to RuneLite and is left alone. */
	public synchronized void shutDown()
	{
		stopTicking();
		listener = null;
		identity = null;
		sessionId = null;
		pendingSnapshot = null;
		queue.reset();
	}

	// ------------------------------------------------------------------
	// Session lifecycle — called from the client thread
	// ------------------------------------------------------------------

	/**
	 * Begins a session for a character. Sequence numbers are only meaningful within a session, so
	 * the buffer is reset; anything not yet acknowledged from a previous character is discarded
	 * rather than misattributed.
	 */
	public void startSession(AccountIdentity account)
	{
		identity = account;
		sessionId = UUID.randomUUID().toString();
		queue.reset();
		pendingSnapshot = null;
		resetBackoff();

		queue.offer(RuneGlassApi.Kind.SESSION_START, Collections.singletonMap(
			"accountType", account.getAccountType()));

		log.debug("Sync session {} started for {}", sessionId, account);
		publish();
	}

	public void endSession()
	{
		if (identity == null)
		{
			return;
		}

		queue.offer(RuneGlassApi.Kind.SESSION_END, Collections.emptyMap());
		identity = null;
		publish();
	}

	/** Records an event for the current session. No-op when nothing is logged in. */
	public void record(String kind, Map<String, Object> data)
	{
		if (identity == null)
		{
			return;
		}

		queue.offer(kind, data);
		publish();
	}

	/**
	 * Hands over a snapshot built on the client thread. A newer snapshot supersedes an unsent one —
	 * there is no value in shipping stale state.
	 */
	public void submitSnapshot(RuneGlassApi.Snapshot snapshot)
	{
		if (identity == null)
		{
			return;
		}

		pendingSnapshot = snapshot;
		publish();
	}

	// ------------------------------------------------------------------
	// Flush loop — executor thread
	// ------------------------------------------------------------------

	private void tick()
	{
		try
		{
			flushIfDue();
		}
		catch (Exception e)
		{
			// A scheduled task that throws is silently cancelled, which would stop sync dead.
			log.warn("RuneGlass sync tick failed", e);
		}
	}

	/** Package-private so tests can drive a flush directly instead of waiting on the tick. */
	void flushIfDue()
	{
		if (!isReady() || System.currentTimeMillis() < backoffUntil)
		{
			return;
		}

		final RuneGlassApi.Snapshot snapshot = pendingSnapshot;
		final List<RuneGlassApi.Event> events = trimToBudget(queue.peekBatch());
		if (events.isEmpty() && snapshot == null)
		{
			return;
		}

		if (!inFlight.compareAndSet(false, true))
		{
			return;
		}

		final AccountIdentity account = identity;
		if (account == null)
		{
			inFlight.set(false);
			return;
		}

		final long highestSeq = events.isEmpty() ? -1L : events.get(events.size() - 1).seq;

		final RuneGlassApi.IngestRequest request = new RuneGlassApi.IngestRequest();
		request.accountHash = account.getAccountHashString();
		request.displayName = account.getDisplayName();
		request.accountType = account.getAccountType();
		request.worldTypes = account.getWorldTypes();
		request.sessionId = sessionId;
		request.sentAt = System.currentTimeMillis();
		request.events = events;
		request.snapshot = snapshot;

		client.ingest(request, new ApiCallback<RuneGlassApi.IngestResponse>()
		{
			@Override
			public void onSuccess(RuneGlassApi.IngestResponse result)
			{
				try
				{
					// Trust our own high-water mark when the backend doesn't report one.
					queue.ackThrough(result.ackSeq > 0 ? result.ackSeq : highestSeq);

					// Only clear the snapshot we actually sent; a newer one may have arrived since.
					if (snapshot != null && pendingSnapshot == snapshot)
					{
						pendingSnapshot = null;
					}

					lastSuccessAt = System.currentTimeMillis();
					lastError = null;
					resetBackoff();
					publish();
				}
				finally
				{
					inFlight.set(false);
				}
			}

			@Override
			public void onFailure(ApiError error)
			{
				try
				{
					handleFailure(error, highestSeq);
				}
				finally
				{
					inFlight.set(false);
				}
			}
		});
	}

	/**
	 * Drops events from the end of a batch until it fits the size budget. The remainder goes out
	 * on the next tick, so nothing is lost — the batch is just split.
	 * <p>
	 * A single event over budget is sent alone rather than silently discarded: the backend will
	 * reject it as non-retryable, which drops it with a log line instead of stalling the queue.
	 */
	List<RuneGlassApi.Event> trimToBudget(List<RuneGlassApi.Event> events)
	{
		if (events.isEmpty() || gson == null)
		{
			return events;
		}

		int total = 0;
		for (int i = 0; i < events.size(); i++)
		{
			total += gson.toJson(events.get(i)).getBytes(StandardCharsets.UTF_8).length;

			if (total > MAX_EVENT_BYTES)
			{
				final int keep = Math.max(1, i);
				log.debug("Trimming batch from {} to {} events to stay within {} bytes",
					events.size(), keep, MAX_EVENT_BYTES);
				return events.subList(0, keep);
			}
		}

		return events;
	}

	private void handleFailure(ApiError error, long highestSeq)
	{
		lastError = error.getMessage();

		if (error.isUnauthorized())
		{
			// Retrying a revoked token forever helps nobody; stop until the user re-links.
			linkRejected = true;
			log.warn("RuneGlass link rejected by the backend: {}", error);
			publish();
			return;
		}

		if (!error.isRetryable())
		{
			// A batch the server will never accept would otherwise block the queue forever.
			log.warn("RuneGlass dropped a rejected batch through seq {}: {}", highestSeq, error);
			queue.ackThrough(highestSeq);
			pendingSnapshot = null;
			publish();
			return;
		}

		backoffUntil = System.currentTimeMillis() + backoffDelay;
		backoffDelay = Math.min(backoffDelay * 2, BACKOFF_MAX_MS);
		log.debug("RuneGlass sync retrying in {}ms: {}", backoffDelay, error);
		publish();
	}

	private boolean isReady()
	{
		return config.syncEnabled()
			&& !config.deviceToken().isEmpty()
			&& !linkRejected
			&& identity != null
			&& sessionId != null;
	}

	private void resetBackoff()
	{
		backoffUntil = 0L;
		backoffDelay = BACKOFF_BASE_MS;
	}

	/** Clears a rejected-link flag after the user links again. */
	public void onLinkChanged()
	{
		linkRejected = false;
		lastError = null;
		resetBackoff();
		publish();
	}

	private synchronized void stopTicking()
	{
		if (tickFuture != null)
		{
			tickFuture.cancel(false);
			tickFuture = null;
		}
	}

	// ------------------------------------------------------------------

	public SyncStatus getStatus()
	{
		final int pending = queue.size();

		if (!config.syncEnabled())
		{
			return new SyncStatus(SyncStatus.Phase.OFF, null, lastSuccessAt, pending);
		}
		if (config.deviceToken().isEmpty())
		{
			return new SyncStatus(SyncStatus.Phase.NOT_LINKED, null, lastSuccessAt, pending);
		}
		if (linkRejected)
		{
			return new SyncStatus(SyncStatus.Phase.ERROR, "Link rejected — please link again", lastSuccessAt, pending);
		}
		if (lastError != null && backoffUntil > System.currentTimeMillis())
		{
			return new SyncStatus(SyncStatus.Phase.ERROR, lastError, lastSuccessAt, pending);
		}
		if (identity == null)
		{
			return new SyncStatus(SyncStatus.Phase.WAITING_FOR_LOGIN, null, lastSuccessAt, pending);
		}
		if (pending > 0 || pendingSnapshot != null)
		{
			return new SyncStatus(SyncStatus.Phase.PENDING, null, lastSuccessAt, pending);
		}
		return new SyncStatus(SyncStatus.Phase.IDLE, null, lastSuccessAt, pending);
	}

	private void publish()
	{
		final Consumer<SyncStatus> current = listener;
		if (current != null)
		{
			current.accept(getStatus());
		}
	}

	/** Exposed for tests. */
	EventQueue getQueue()
	{
		return queue;
	}
}
