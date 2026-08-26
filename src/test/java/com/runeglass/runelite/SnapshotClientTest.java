package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SnapshotClientTest
{
	private static final String RAW_CREDENTIAL = "rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr";
	private static final String CONNECTION_ID = "pcn_ccccccccccccccccccccccccccccccccccccccccccc";
	private static final Instant CAPTURED_AT = Instant.parse("2026-08-24T14:00:01Z");
	private static final Instant OBSERVED_AT = Instant.parse("2026-08-24T14:00:00Z");
	private static final String SESSION_ID = "0b54873e-c169-4de7-9bd0-abc879f84d2f";

	private MockWebServer server;
	private ScheduledExecutorService executor;
	private SnapshotClient client;
	private DurableSnapshotQueue queue;
	private Path queueDirectory;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, "runeglass-snapshot-test");
			thread.setDaemon(true);
			return thread;
		});
		client = client(server.url("/"));
		queueDirectory = temporaryFolder.newFolder("queue").toPath();
		queue = new DurableSnapshotQueue(
			queueDirectory,
			new Gson(),
			Clock.fixed(CAPTURED_AT, ZoneOffset.UTC));
	}

	@After
	public void tearDown() throws IOException
	{
		if (client != null)
		{
			client.cancel();
		}
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void sendsAnExactAuthenticatedV1SkillsBatch() throws Exception
	{
		server.enqueue(accepted());
		CountDownLatch accepted = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(context(), listener(accepted, failure)));
		assertTrue(client.publish(snapshot()));

		RecordedRequest request = takeRequest();
		assertEquals("Bearer " + RAW_CREDENTIAL, request.getHeader("Authorization"));
		assertEquals("application/json", request.getHeader("Accept"));
		assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));

		String bodyText = request.getBody().readUtf8();
		assertFalse(bodyText.contains(RAW_CREDENTIAL));
		JsonObject body = new JsonParser().parse(bodyText).getAsJsonObject();
		assertExactKeys(
			body,
			"protocolVersion",
			"batchId",
			"connectionId",
			"sessionId",
			"sequence",
			"capturedAt",
			"client",
			"character",
			"records");
		assertEquals(1, body.get("protocolVersion").getAsInt());
		UUID.fromString(body.get("batchId").getAsString());
		assertEquals(CONNECTION_ID, body.get("connectionId").getAsString());
		assertEquals(SESSION_ID, body.get("sessionId").getAsString());
		assertEquals("1", body.get("sequence").getAsString());
		assertEquals(CAPTURED_AT.toString(), body.get("capturedAt").getAsString());

		JsonObject clientBody = body.getAsJsonObject("client");
		assertExactKeys(clientBody, "pluginVersion", "runeliteVersion", "gameRevision");
		assertEquals("1.0.0", clientBody.get("pluginVersion").getAsString());
		assertEquals("1.12.36", clientBody.get("runeliteVersion").getAsString());
		assertEquals(231, clientBody.get("gameRevision").getAsInt());

		JsonObject character = body.getAsJsonObject("character");
		assertExactKeys(character, "displayName", "accountType", "profileType");
		assertEquals("Iron Jeromey", character.get("displayName").getAsString());
		assertEquals("ironman", character.get("accountType").getAsString());
		assertEquals("standard", character.get("profileType").getAsString());

		JsonArray records = body.getAsJsonArray("records");
		assertEquals(1, records.size());
		JsonObject record = records.get(0).getAsJsonObject();
		assertExactKeys(
			record,
			"type",
			"observedAt",
			"reason",
			"catalogVersion",
			"experience",
			"levels",
			"boostedLevels");
		assertEquals("login_baseline", record.get("reason").getAsString());
		assertEquals(25, record.getAsJsonArray("experience").size());

		assertTrue(accepted.await(2, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void retriesTheIdenticalBatchAfterTemporaryFailure() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(503)
			.setHeader("Content-Type", "application/json")
			.setHeader("Retry-After", "1")
			.setBody("{\"protocolVersion\":1,\"error\":\"temporarily_unavailable\"}"));
		server.enqueue(accepted());
		CountDownLatch accepted = new CountDownLatch(1);
		CountDownLatch retryScheduled = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		connect(context(), new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
				accepted.countDown();
			}

			@Override
			public void onRetryScheduled()
			{
				retryScheduled.countDown();
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}
		});
		client.publish(snapshot());

		RecordedRequest first = takeRequest();
		assertTrue(retryScheduled.await(2, TimeUnit.SECONDS));
		RecordedRequest retry = takeRequest();
		assertEquals(first.getBody().readUtf8(), retry.getBody().readUtf8());
		assertTrue(accepted.await(2, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void stopsOnBindingMismatchWithoutRetrying() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(403)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"error\":\"binding_mismatch\"}"));
		CountDownLatch failed = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		connect(context(), new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
			}

			@Override
			public void onRetryScheduled()
			{
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
				failed.countDown();
			}
		});
		client.publish(snapshot());

		assertNotNull(takeRequest());
		assertTrue(failed.await(2, TimeUnit.SECONDS));
		assertEquals(SnapshotClient.Failure.BINDING_MISMATCH, failure.get());
		assertFalse(client.publish(snapshot()));
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void revokedCredentialStopsAndDiscardsTheConnection() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(401)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"error\":\"invalid_connection_credential\"}"));
		CountDownLatch failed = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(context(), failureListener(failed, failure)));
		assertTrue(client.publish(snapshot()));

		assertNotNull(takeRequest());
		assertTrue(failed.await(2, TimeUnit.SECONDS));
		assertEquals(SnapshotClient.Failure.INVALID_CONNECTION, failure.get());
		assertFalse(client.publish(snapshot()));
		assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS));
	}

	@Test
	public void sequenceConflictStopsWithoutMutatingAndRetryingTheBatch() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(409)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"error\":\"sequence_conflict\"}"));
		CountDownLatch failed = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(context(), failureListener(failed, failure)));
		assertTrue(client.publish(snapshot()));

		RecordedRequest rejected = takeRequest();
		assertSequenceAndReason(rejected, "1", "login_baseline");
		assertTrue(failed.await(2, TimeUnit.SECONDS));
		assertEquals(SnapshotClient.Failure.REJECTED_BATCH, failure.get());
		assertEquals(0, queue.size());
		assertFalse(client.publish(snapshot()));
		assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS));
	}

	@Test
	public void replaysAnExactOfflineBatchAfterRestartThenUsesTheNextSequence() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(503)
			.setHeader("Content-Type", "application/json")
			.setHeader("Retry-After", "300")
			.setBody("{\"protocolVersion\":1,\"error\":\"temporarily_unavailable\"}"));
		CountDownLatch retryScheduled = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(context(), new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
			}

			@Override
			public void onRetryScheduled()
			{
				retryScheduled.countDown();
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}
		}));
		assertTrue(client.publish(snapshot()));
		RecordedRequest firstAttempt = takeRequest();
		String persistedBody = firstAttempt.getBody().clone().readUtf8();
		assertTrue(retryScheduled.await(2, TimeUnit.SECONDS));
		assertEquals(1, queue.size());

		client.cancel();
		client = client(server.url("/"));
		queue = new DurableSnapshotQueue(
			queueDirectory,
			new Gson(),
			Clock.fixed(CAPTURED_AT, ZoneOffset.UTC));
		server.enqueue(accepted("1"));
		server.enqueue(accepted("2"));
		CountDownLatch accepted = new CountDownLatch(2);
		String nextSessionId = "eec7d765-0f2f-4f5d-840a-403835e63a0f";
		assertTrue(client.connect(
			credentials(),
			context(nextSessionId),
			BigInteger.valueOf(2),
			queue,
			listener(accepted, failure)));

		RecordedRequest replay = takeRequest();
		assertEquals(persistedBody, replay.getBody().clone().readUtf8());
		assertTrue(client.publishImmediately(snapshot(SnapshotReason.MANUAL_SYNC)));
		RecordedRequest next = takeRequest();
		JsonObject nextBody = new JsonParser().parse(
			next.getBody().clone().readUtf8()).getAsJsonObject();
		assertEquals("2", nextBody.get("sequence").getAsString());
		assertEquals(nextSessionId, nextBody.get("sessionId").getAsString());
		assertTrue(accepted.await(2, TimeUnit.SECONDS));
		assertEquals(0, queue.size());
		assertNull(failure.get());
	}

	@Test
	public void finalSnapshotBypassesCooldownAndSequenceSurvivesSessionReconnect() throws Exception
	{
		server.enqueue(accepted("1"));
		server.enqueue(accepted("2"));
		server.enqueue(accepted("3"));
		CountDownLatch firstAccepted = new CountDownLatch(1);
		CountDownLatch secondAccepted = new CountDownLatch(1);
		CountDownLatch thirdAccepted = new CountDownLatch(1);
		CountDownLatch drained = new CountDownLatch(1);
		AtomicInteger acceptedCount = new AtomicInteger();
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		SnapshotClient.Listener listener = new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
				int count = acceptedCount.incrementAndGet();
				if (count == 1)
				{
					firstAccepted.countDown();
				}
				else if (count == 2)
				{
					secondAccepted.countDown();
				}
				else if (count == 3)
				{
					thirdAccepted.countDown();
				}
			}

			@Override
			public void onRetryScheduled()
			{
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}

			@Override
			public void onSessionDrained()
			{
				drained.countDown();
			}
		};

		assertTrue(connect(context(SESSION_ID), listener));
		assertTrue(client.publish(snapshot(SnapshotReason.LOGIN_BASELINE)));
		assertSequenceAndReason(takeRequest(), "1", "login_baseline");
		assertTrue(firstAccepted.await(2, TimeUnit.SECONDS));

		assertTrue(client.finishSession(snapshot(SnapshotReason.LOGOUT_FLUSH)));
		assertSequenceAndReason(takeRequest(), "2", "logout_flush");
		assertTrue(secondAccepted.await(2, TimeUnit.SECONDS));
		assertTrue(drained.await(2, TimeUnit.SECONDS));

		assertTrue(connect(
			context("eec7d765-0f2f-4f5d-840a-403835e63a0f"),
			listener));
		assertTrue(client.publish(snapshot(SnapshotReason.LOGIN_BASELINE)));
		assertSequenceAndReason(takeRequest(), "3", "login_baseline");
		assertTrue(thirdAccepted.await(2, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void emptySessionBoundaryDrainsBusyOldContextBeforeRebinding() throws Exception
	{
		CountDownLatch firstAccepted = new CountDownLatch(1);
		CountDownLatch firstDrained = new CountDownLatch(1);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		SnapshotClient.Listener firstListener = new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
				firstAccepted.countDown();
			}

			@Override
			public void onRetryScheduled()
			{
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}

			@Override
			public void onSessionDrained()
			{
				firstDrained.countDown();
			}
		};

		assertTrue(connect(context(SESSION_ID), firstListener));
		assertTrue(client.publish(snapshot(SnapshotReason.LOGIN_BASELINE)));
		RecordedRequest first = takeRequest();
		assertSequenceAndReason(first, "1", "login_baseline");

		assertTrue(client.finishSession());
		assertFalse(connect(
			context("eec7d765-0f2f-4f5d-840a-403835e63a0f"),
			firstListener));

		server.enqueue(accepted("1"));
		assertTrue(firstAccepted.await(2, TimeUnit.SECONDS));
		assertTrue(firstDrained.await(2, TimeUnit.SECONDS));

		CountDownLatch secondAccepted = new CountDownLatch(1);
		String secondSessionId = "eec7d765-0f2f-4f5d-840a-403835e63a0f";
		assertTrue(connect(
			context(secondSessionId),
			listener(secondAccepted, failure)));
		server.enqueue(accepted("2"));
		assertTrue(client.publish(snapshot(SnapshotReason.LOGIN_BASELINE)));
		RecordedRequest second = takeRequest();
		JsonObject secondBody = new JsonParser().parse(
			second.getBody().clone().readUtf8()).getAsJsonObject();
		assertEquals(secondSessionId, secondBody.get("sessionId").getAsString());
		assertEquals("2", secondBody.get("sequence").getAsString());
		assertTrue(secondAccepted.await(2, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void manualSnapshotBypassesTheNormalCooldownOnce() throws Exception
	{
		server.enqueue(accepted("1"));
		server.enqueue(accepted("2"));
		CountDownLatch accepted = new CountDownLatch(2);
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(context(), listener(accepted, failure)));

		assertTrue(client.publish(snapshot(SnapshotReason.LOGIN_BASELINE)));
		assertSequenceAndReason(takeRequest(), "1", "login_baseline");

		assertTrue(client.publishImmediately(snapshot(SnapshotReason.MANUAL_SYNC)));
		assertSequenceAndReason(takeRequest(), "2", "manual_sync");
		assertTrue(accepted.await(2, TimeUnit.SECONDS));
		assertNull(failure.get());
	}

	@Test
	public void shutdownFlushMakesOneBestEffortAttemptWithoutRetrying() throws Exception
	{
		server.enqueue(new MockResponse()
			.setResponseCode(503)
			.setHeader("Content-Type", "application/json")
			.setHeader("Retry-After", "1")
			.setBody("{\"protocolVersion\":1,\"error\":\"temporarily_unavailable\"}"));
		AtomicReference<SnapshotClient.Failure> failure = new AtomicReference<>();
		assertTrue(connect(
			context(),
			listener(new CountDownLatch(1), failure)));

		assertTrue(client.finishSession(snapshot(SnapshotReason.LOGOUT_FLUSH)));
		client.closeAfterFlush();
		assertSequenceAndReason(takeRequest(), "1", "logout_flush");
		assertNull(server.takeRequest(1_500, TimeUnit.MILLISECONDS));
		assertFalse(client.publish(snapshot()));
		assertNull(failure.get());
	}

	private SnapshotClient client(HttpUrl baseUrl)
	{
		return new SnapshotClient(
			new OkHttpClient(),
			new Gson(),
			executor,
			baseUrl,
			Clock.fixed(CAPTURED_AT, ZoneOffset.UTC),
			1,
			() -> 0L);
	}

	private boolean connect(
		SyncContext context,
		SnapshotClient.Listener listener)
	{
		return client.connect(credentials(), context, BigInteger.ONE, queue, listener);
	}

	private static PairingClient.Credentials credentials()
	{
		return new PairingClient.Credentials(CONNECTION_ID, RAW_CREDENTIAL);
	}

	private static SyncContext context()
	{
		return context(SESSION_ID);
	}

	private static SyncContext context(String sessionId)
	{
		return new SyncContext(
			sessionId,
			"Iron Jeromey",
			"ironman",
			"standard",
			"1.0.0",
			"1.12.36",
			231);
	}

	private static SkillsSnapshot snapshot()
	{
		return snapshot(SnapshotReason.LOGIN_BASELINE);
	}

	private static SkillsSnapshot snapshot(SnapshotReason reason)
	{
		long[] experience = new long[SkillCatalog.VECTOR_SIZE];
		int[] levels = new int[SkillCatalog.VECTOR_SIZE];
		int[] boostedLevels = new int[SkillCatalog.VECTOR_SIZE];
		for (int index = 1; index < SkillCatalog.VECTOR_SIZE; index++)
		{
			experience[index] = index * 1_000L;
			levels[index] = 1;
			boostedLevels[index] = 1;
			experience[0] += experience[index];
			levels[0] += levels[index];
			boostedLevels[0] += boostedLevels[index];
		}
		return new SkillsSnapshot(
			OBSERVED_AT,
			reason,
			experience,
			levels,
			boostedLevels);
	}

	private static SnapshotClient.Listener listener(
		CountDownLatch accepted,
		AtomicReference<SnapshotClient.Failure> failure)
	{
		return new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
				accepted.countDown();
			}

			@Override
			public void onRetryScheduled()
			{
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
			}
		};
	}

	private static SnapshotClient.Listener failureListener(
		CountDownLatch failed,
		AtomicReference<SnapshotClient.Failure> failure)
	{
		return new SnapshotClient.Listener()
		{
			@Override
			public void onUploading(int recordCount)
			{
			}

			@Override
			public void onAccepted(Instant serverTime)
			{
				throw new AssertionError("Rejected batch must not be accepted");
			}

			@Override
			public void onRetryScheduled()
			{
				throw new AssertionError("Terminal batch rejection must not retry");
			}

			@Override
			public void onFailure(SnapshotClient.Failure nextFailure)
			{
				failure.set(nextFailure);
				failed.countDown();
			}
		};
	}

	private RecordedRequest takeRequest() throws InterruptedException
	{
		RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
		assertNotNull(request);
		assertEquals("POST", request.getMethod());
		assertEquals("/runelite/v1/batches", request.getPath());
		return request;
	}

	private static MockResponse accepted()
	{
		return accepted("1");
	}

	private static MockResponse accepted(String sequence)
	{
		return new MockResponse()
			.setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody(
				"{\"protocolVersion\":1,\"status\":\"accepted\",\"acceptedSequence\":\""
					+ sequence + "\","
					+ "\"serverTime\":\"2026-08-24T14:00:02Z\",\"nextUploadAfterSeconds\":300}");
	}

	private static void assertSequenceAndReason(
		RecordedRequest request,
		String sequence,
		String reason)
	{
		JsonObject body = new JsonParser().parse(request.getBody().clone().readUtf8())
			.getAsJsonObject();
		assertEquals(sequence, body.get("sequence").getAsString());
		assertEquals(
			reason,
			body.getAsJsonArray("records").get(0).getAsJsonObject()
				.get("reason").getAsString());
	}

	private static void assertExactKeys(JsonObject body, String... expected)
	{
		assertTrue(ProtocolJson.hasExactKeys(body, expected));
	}
}
