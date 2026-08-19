/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the flush loop directly with a backend stub that answers on the calling thread, so the
 * retry, drop and backoff decisions are observable without waiting on the scheduler.
 */
public class SyncServiceTest
{
	private static final AccountIdentity ZEZIMA =
		new AccountIdentity(6041938472910385761L, "Zezima", "IRONMAN", Collections.singletonList("MEMBERS"));

	private StubConfig config;
	private StubClient client;
	private ScheduledExecutorService executor;
	private SyncService service;

	@Before
	public void setUp()
	{
		config = new StubConfig();
		client = new StubClient(config);
		executor = Executors.newSingleThreadScheduledExecutor();
		service = new SyncService(client, config, executor, new com.google.gson.Gson());
	}

	@After
	public void tearDown()
	{
		service.shutDown();
		executor.shutdownNow();
	}

	@Test
	public void startingASessionRecordsASessionStartEvent()
	{
		service.startSession(ZEZIMA);

		assertEquals(1, service.getQueue().size());
		assertEquals(RuneGlassApi.Kind.SESSION_START, service.getQueue().peekBatch().get(0).kind);
	}

	@Test
	public void eventsBeforeLoginAreIgnored()
	{
		service.record(RuneGlassApi.Kind.LEVEL_UP, Collections.singletonMap("skill", "SLAYER"));

		assertTrue("nothing can be attributed to an account we do not have", service.getQueue().isEmpty());
	}

	@Test
	public void aSuccessfulFlushSendsTheAccountHashAsAString()
	{
		service.startSession(ZEZIMA);
		service.flushIfDue();

		assertNotNull(client.lastRequest);
		assertEquals("6041938472910385761", client.lastRequest.accountHash);
		assertEquals("IRONMAN", client.lastRequest.accountType);
		assertNotNull("a session id scopes the sequence numbers", client.lastRequest.sessionId);
	}

	@Test
	public void acknowledgedEventsLeaveTheQueue()
	{
		service.startSession(ZEZIMA);
		service.record(RuneGlassApi.Kind.LEVEL_UP, Collections.singletonMap("skill", "SLAYER"));

		service.flushIfDue();

		assertTrue(service.getQueue().isEmpty());
		assertEquals(SyncStatus.Phase.IDLE, service.getStatus().getPhase());
	}

	@Test
	public void aRetryableFailureKeepsTheEventsForAnotherAttempt()
	{
		service.startSession(ZEZIMA);
		client.error = ApiError.network("connection reset");

		service.flushIfDue();

		assertEquals("events must survive a transient failure", 1, service.getQueue().size());
		assertEquals(SyncStatus.Phase.ERROR, service.getStatus().getPhase());
	}

	@Test
	public void backoffSuppressesTheNextAttempt()
	{
		service.startSession(ZEZIMA);
		client.error = ApiError.network("connection reset");
		service.flushIfDue();

		final int callsAfterFirstFailure = client.calls;
		service.flushIfDue();

		assertEquals("a second attempt must wait for the backoff window", callsAfterFirstFailure, client.calls);
	}

	@Test
	public void aBatchTheServerWillNeverAcceptIsDroppedRatherThanBlockingTheQueue()
	{
		service.startSession(ZEZIMA);
		client.error = new ApiError(400, "bad_request", "malformed event");

		service.flushIfDue();

		assertTrue("a poison batch must not block sync forever", service.getQueue().isEmpty());
	}

	@Test
	public void aRejectedTokenStopsSyncingUntilRelinked()
	{
		service.startSession(ZEZIMA);
		client.error = new ApiError(401, "unauthorized", "token revoked");

		service.flushIfDue();
		assertEquals(SyncStatus.Phase.ERROR, service.getStatus().getPhase());

		final int callsAfterRejection = client.calls;
		service.record(RuneGlassApi.Kind.LEVEL_UP, Collections.emptyMap());
		service.flushIfDue();

		assertEquals("a revoked token must not be retried", callsAfterRejection, client.calls);

		// Linking again clears the block.
		client.error = null;
		service.onLinkChanged();
		service.flushIfDue();

		assertTrue("re-linking should resume sync", client.calls > callsAfterRejection);
	}

	@Test
	public void snapshotsAreClearedOnlyWhenTheOneSentSucceeds()
	{
		service.startSession(ZEZIMA);

		final RuneGlassApi.Snapshot snapshot = new RuneGlassApi.Snapshot();
		snapshot.capturedAt = 123L;
		service.submitSnapshot(snapshot);

		service.flushIfDue();

		assertNotNull("the snapshot should have been sent", client.lastRequest.snapshot);
		assertEquals(123L, client.lastRequest.snapshot.capturedAt);

		client.lastRequest = null;
		service.flushIfDue();
		assertNull("a sent snapshot must not be resent", client.lastRequest);
	}

	@Test
	public void aNewerSnapshotSupersedesAnUnsentOne()
	{
		service.startSession(ZEZIMA);

		final RuneGlassApi.Snapshot older = new RuneGlassApi.Snapshot();
		older.capturedAt = 1L;
		final RuneGlassApi.Snapshot newer = new RuneGlassApi.Snapshot();
		newer.capturedAt = 2L;

		service.submitSnapshot(older);
		service.submitSnapshot(newer);
		service.flushIfDue();

		assertEquals("stale state is worth nothing", 2L, client.lastRequest.snapshot.capturedAt);
	}

	@Test
	public void nothingIsSentWhileSyncIsDisabled()
	{
		config.syncEnabled = false;
		service.startSession(ZEZIMA);

		service.flushIfDue();

		assertEquals(0, client.calls);
		assertEquals(SyncStatus.Phase.OFF, service.getStatus().getPhase());
	}

	@Test
	public void nothingIsSentWhileUnlinked()
	{
		config.token = "";
		service.startSession(ZEZIMA);

		service.flushIfDue();

		assertEquals(0, client.calls);
		assertEquals(SyncStatus.Phase.NOT_LINKED, service.getStatus().getPhase());
	}

	@Test
	public void anEmptyQueueSendsNothing()
	{
		service.startSession(ZEZIMA);
		service.flushIfDue();

		final int callsAfterSessionStart = client.calls;
		service.flushIfDue();

		assertEquals("no request when there is nothing to say", callsAfterSessionStart, client.calls);
	}

	/**
	 * Defence in depth for the bug the Convex compatibility check caught: no event type, present
	 * or future, should be able to push a request past the backend's document limit.
	 */
	@Test
	public void anOversizedBatchIsTrimmedToFitTheByteBudget()
	{
		service.startSession(ZEZIMA);

		final List<RuneGlassApi.ItemStack> bulky = new ArrayList<>();
		for (int i = 0; i < 2000; i++)
		{
			bulky.add(new RuneGlassApi.ItemStack(29_000 + i, 2_000_000_000, i));
		}

		for (int i = 0; i < 20; i++)
		{
			service.record(RuneGlassApi.Kind.CONTAINER, Collections.singletonMap("items", bulky));
		}

		final List<RuneGlassApi.Event> batch = service.trimToBudget(service.getQueue().peekBatch());

		assertTrue("batch should have been trimmed", batch.size() < 21);
		assertTrue("a trimmed batch must still carry something", batch.size() >= 1);

		final int bytes = new com.google.gson.Gson().toJson(batch).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		assertTrue("trimmed batch is still " + (bytes / 1024) + " KiB", bytes <= SyncService.MAX_EVENT_BYTES * 2);
	}

	@Test
	public void trimmedEventsAreNotLostJustDeferred()
	{
		service.startSession(ZEZIMA);
		service.record(RuneGlassApi.Kind.LEVEL_UP, Collections.singletonMap("skill", "SLAYER"));

		final int queued = service.getQueue().size();
		service.trimToBudget(service.getQueue().peekBatch());

		assertEquals("trimming must not consume the queue", queued, service.getQueue().size());
	}

	@Test
	public void switchingCharacterDiscardsTheOldSessionsUnsentEvents()
	{
		service.startSession(ZEZIMA);
		service.record(RuneGlassApi.Kind.LEVEL_UP, Collections.singletonMap("skill", "SLAYER"));

		final AccountIdentity alt =
			new AccountIdentity(123L, "Alt", "NORMAL", Collections.emptyList());
		service.startSession(alt);

		// Only the new session's own start event should remain.
		assertEquals(1, service.getQueue().size());
		assertEquals(0L, service.getQueue().peekBatch().get(0).seq);
	}

	// ------------------------------------------------------------------

	private static final class StubClient extends RuneGlassClient
	{
		RuneGlassApi.IngestRequest lastRequest;
		ApiError error;
		int calls;

		StubClient(RuneGlassConfig config)
		{
			super(null, null, config);
		}

		@Override
		public void ingest(RuneGlassApi.IngestRequest request, ApiCallback<RuneGlassApi.IngestResponse> callback)
		{
			calls++;
			lastRequest = request;

			if (error != null)
			{
				callback.onFailure(error);
				return;
			}

			final RuneGlassApi.IngestResponse response = new RuneGlassApi.IngestResponse();
			response.ok = true;
			response.ackSeq = request.events.isEmpty()
				? -1L
				: request.events.get(request.events.size() - 1).seq;
			callback.onSuccess(response);
		}
	}

	private static final class StubConfig implements RuneGlassConfig
	{
		private String token = "rg_test";
		private boolean syncEnabled = true;

		@Override
		public boolean syncEnabled()
		{
			return syncEnabled;
		}

		@Override
		public String deviceToken()
		{
			return token;
		}

		@Override
		public void deviceToken(String value)
		{
			token = value;
		}

		@Override
		public void linkedAccountName(String value)
		{
		}
	}
}
