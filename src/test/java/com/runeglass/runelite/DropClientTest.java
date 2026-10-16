package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Filepath;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DropClientTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private final Gson gson = new Gson();
	private MockWebServer server;
	private FastScheduler executor;
	private DropClient transport;
	private Filepath storage;
	private Path root;
	private final List<String> statuses = new CopyOnWriteArrayList<>();
	private final BlockingQueue<String> progress = new LinkedBlockingQueue<>();
	private final AtomicInteger invalid = new AtomicInteger();
	private final PairingClient.Credentials credentials = new PairingClient.Credentials("connection", "r".repeat(43));

	@Before public void setUp() throws Exception
	{
		storage = TestStorage.rooted(temporary.newFolder().toPath()).joinSegment("drops-v1");
		root = TestStorage.path(storage);
		server = new MockWebServer(); server.start();
		executor = new FastScheduler();
		transport = transport(storage);
	}

	@After public void tearDown() throws Exception
	{
		transport.pause();
		executor.shutdownNow();
		server.shutdown();
	}

	@Test public void observedLowKillsAndRestartMaintainContinuityAndRetriesNeverAdvanceIt() throws Exception
	{
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT, "rs-profile", true);
		transport.observe(observation(100, true));
		JsonObject first = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		await(() -> queueSize("connection") == 0);
		transport.observe(observation(101, false));
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertEquals(1, server.getRequestCount());
		transport.pause();
		transport = transport(storage);
		transport.connect(credentials, DropCaptureTest.CONTEXT, "rs-profile", true);
		transport.observe(observation(102, true));
		JsonObject next = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		assertEquals(first.get("continuity"), next.get("continuity"));
		await(() -> queueSize("connection") == 0);
		transport.observe(observation(105, true));
		JsonObject gap = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		assertEquals(105, gap.getAsJsonObject("continuity").get("from").getAsInt());
		assertEquals(first.getAsJsonObject("continuity").get("id"), gap.getAsJsonObject("continuity").get("id"));
		transport.discardAll();
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertFalse(Files.exists(root.resolve("connection").resolve(DropContinuityStore.FILE)));
	}

	@Test public void evidenceRetryReusesFrozenBytesWithoutAdvancingContinuity() throws Exception
	{
		server.setDispatcher(new Dispatcher()
		{
			private int requests;
			@Override public MockResponse dispatch(RecordedRequest request)
			{ return ++requests == 1 ? new MockResponse().setResponseCode(503) : accepted(request, false); }
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT, "rs-profile", true);
		transport.observe(observation(100, true));
		String first = body(take());
		assertEquals(first, body(take()));
		await(() -> queueSize("connection") == 0);
		JsonObject persisted = gson.fromJson(Files.readString(root.resolve("connection").resolve(DropContinuityStore.FILE)), JsonObject.class);
		assertEquals(100, persisted.getAsJsonObject("counters").getAsJsonObject("vorkath").get("last").getAsInt());
		JsonObject saved = DropCaptureTest.event(gson.fromJson(first, JsonObject.class));
		assertEquals(persisted.getAsJsonObject("counters").getAsJsonObject("vorkath").get("id"), saved.getAsJsonObject("continuity").get("id"));
	}

	@Test public void rejectedSuccessBreaksContinuityAndNewProfileCannotReuseIt() throws Exception
	{
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT, "rs-profile", true);
		transport.observe(observation(100, true));
		JsonObject first = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		await(() -> queueSize("connection") == 0);
		transport.captureFailed();
		transport.observe(observation(101, true));
		JsonObject afterFailure = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		assertNotEquals(first.getAsJsonObject("continuity").get("id"), afterFailure.getAsJsonObject("continuity").get("id"));
		await(() -> queueSize("connection") == 0);
		transport.connect(credentials, DropCaptureTest.CONTEXT, "different-profile", true);
		transport.observe(observation(102, true));
		JsonObject other = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		assertNotEquals(afterFailure.getAsJsonObject("continuity").get("id"), other.getAsJsonObject("continuity").get("id"));
	}

	@Test public void noCountConsentStripsRestoredEvidenceBeforeFirstUploadAndNeverOpensContinuity() throws Exception
	{
		JsonObject enriched = enrichedDrop("connection");
		new DurableDropQueue(storage, "connection", gson).append(enriched);
		Path continuity = root.resolve("connection").resolve(DropContinuityStore.FILE);
		Files.writeString(continuity, "invalid continuity must be deleted without loading it");
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		JsonObject uploaded = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		DropCountConsentTest.assertLegacy(uploaded);
		assertEquals(DropCaptureTest.event(enriched).get("eventId"), uploaded.get("eventId"));
		await(() -> queueSize("connection") == 0);
		assertFalse(Files.exists(continuity));
		transport.observe(observation(101, true));
		DropCountConsentTest.assertLegacy(DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class)));
		transport.captureFailed();
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertFalse(Files.exists(continuity));
	}

	@Test public void countRevocationPreservesAllProfilesAndBlockedDropsWhileRemovingTheirEvidence() throws Exception
	{
		for (String connection : List.of("connection", "inactive"))
		{
			DurableDropQueue queue = new DurableDropQueue(storage, connection, gson);
			queue.append(enrichedDrop(connection));
			queue.append(enrichedDrop(connection));
			queue.block(queue.batch(false).get(0));
			Files.writeString(root.resolve(connection).resolve(DropContinuityStore.FILE), "private continuity");
		}
		transport.revokeCountSharing();
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		for (String connection : List.of("connection", "inactive"))
		{
			assertEquals(1, queueSize(connection));
			assertFalse(Files.exists(root.resolve(connection).resolve(DropContinuityStore.FILE)));
			try (java.util.stream.Stream<Path> paths = Files.list(root.resolve(connection)))
			{
				List<Path> files = paths.collect(java.util.stream.Collectors.toList());
				assertEquals(2, files.size());
				for (Path file : files) DropCountConsentTest.assertLegacy(DropCaptureTest.event(gson.fromJson(Files.readString(file), JsonObject.class)));
			}
		}
		assertFalse(DurableDropQueue.countRevocationMarker(storage).exists());
		assertEquals(0, server.getRequestCount());
	}

	@Test public void revocationDuringQueuedPersistenceCannotSaveOrUploadOldEvidence() throws Exception
	{
		CountDownLatch release = new CountDownLatch(1);
		Future<Boolean> held = executor.submit((Callable<Boolean>) () -> release.await(WAIT_SECONDS, TimeUnit.SECONDS));
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT, "profile", true);
		transport.observe(observation(100, true));
		transport.revokeCountSharing();
		transport.connect(credentials, DropCaptureTest.CONTEXT, "profile", false);
		release.countDown();
		assertTrue(held.get(WAIT_SECONDS, TimeUnit.SECONDS));
		DropCountConsentTest.assertLegacy(DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class)));
		await(() -> queueSize("connection") == 0);
		assertFalse(Files.exists(root.resolve("connection").resolve(DropContinuityStore.FILE)));
	}

	@Test public void revocationCancelsInFlightAndStripsBeforeResumingLegacyUploads() throws Exception
	{
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
		transport.connect(credentials, DropCaptureTest.CONTEXT, "profile", true);
		transport.observe(observation(100, true));
		JsonObject first = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		assertTrue(first.has("sourceCount"));
		transport.revokeCountSharing();
		assertFalse(transport.isConnected());
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertEquals(1, queueSize("connection"));
		assertFalse(Files.exists(root.resolve("connection").resolve(DropContinuityStore.FILE)));
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		JsonObject next = DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class));
		DropCountConsentTest.assertLegacy(next);
		assertEquals(first.get("eventId"), next.get("eventId"));
	}

	@Test public void unfinishedCountRevocationIsRetriedEvenWhenRestartedWithCountsEnabled() throws Exception
	{
		new DurableDropQueue(storage, "connection", gson).append(enrichedDrop("connection"));
		PrivateFiles.write(DurableDropQueue.countRevocationMarker(storage), "revoke.", new byte[]{1});
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT, "profile", true);
		DropCountConsentTest.assertLegacy(DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class)));
		assertFalse(DurableDropQueue.countRevocationMarker(storage).exists());
	}

	@Test public void failedCountCleanupBlocksUploadsUntilRetriedSuccessfully() throws Exception
	{
		new DurableDropQueue(storage, "connection", gson).append(enrichedDrop("connection"));
		Path damaged = root.resolve("connection/broken.json.blocked");
		Files.writeString(damaged, "not json");
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertFalse(transport.isConnected());
		assertTrue(DurableDropQueue.countRevocationMarker(storage).exists());
		assertTrue(statuses.stream().anyMatch(status -> status.contains("count evidence could not be removed")));
		assertEquals(0, server.getRequestCount());
		Files.delete(damaged);
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		DropCountConsentTest.assertLegacy(DropCaptureTest.event(gson.fromJson(body(take()), JsonObject.class)));
		assertFalse(DurableDropQueue.countRevocationMarker(storage).exists());
	}

	private JsonObject enrichedDrop(String connection)
	{
		DropCorrelator.Completed completed = observation(100, true);
		JsonObject evidence = new JsonObject();
		evidence.addProperty("id", java.util.UUID.randomUUID().toString());
		evidence.addProperty("from", 100);
		DropEvent event = completed.result.event().get();
		JsonObject body = event.freeze(completed.count, evidence, "enabled", Set.of(22006))
			.envelope(DropCaptureTest.CONTEXT, connection);
		DropCaptureTest.event(body).getAsJsonObject("source").addProperty("npcId", 8061);
		assertTrue(DropCaptureTest.event(body).getAsJsonArray("items").get(0).getAsJsonObject().has("newCollectionLogSlot"));
		return body;
	}

	private static DropCorrelator.Completed observation(int count, boolean retained)
	{
		return new DropCorrelator.Completed(DropCorrelatorTest.drop("npc", "Vorkath", retained),
			new DropCountParser.Count(DropCounterRegistry.byKey("vorkath"), count), "enabled", Set.of());
	}

	@Test public void lowOnlyRewardsNeverReachDiskOrHttp() throws Exception
	{
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		DropCapture capture = new DropCapture();
		for (LootReceived loot : List.of(
			DropCaptureTest.loot("Guard", LootRecordType.NPC, new ItemStack(10000, 1)),
			DropCaptureTest.loot("Guard", LootRecordType.NPC, new ItemStack(995, 49_999)),
			DropCaptureTest.loot("Guard", LootRecordType.NPC, new ItemStack(10001, 1), new ItemStack(10002, 1))))
		{
			capture.capture(loot, id -> DropCaptureTest.definition(id, true, -1, -1),
				id -> id == 10000 ? 49_999 : 30_000, DropCaptureTest.SESSION, DropCaptureTest.NOW).event().ifPresent(transport::publish);
		}
		executor.submit(() -> { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
		assertEquals(0, queueSize("connection"));
		assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS));
		assertEquals(0, server.getRequestCount());
		try (java.util.stream.Stream<Path> files = Files.walk(root))
		{
			assertEquals(0, files.filter(Files::isRegularFile).count());
		}
	}

	@Test public void retries429And503WithOriginalUuidAndFrozenPayload() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After", "60"));
		server.enqueue(new MockResponse().setResponseCode(503));
		server.setDispatcher(new Dispatcher()
		{
			private int requests;
			@Override public MockResponse dispatch(RecordedRequest request)
			{
				if (++requests == 1) return new MockResponse().setResponseCode(429).addHeader("Retry-After", "60");
				if (requests == 2) return new MockResponse().setResponseCode(503);
				return accepted(request, false);
			}
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		assertTrue(transport.publish(DropCaptureTest.drop()));
		RecordedRequest first = take();
		RecordedRequest second = take();
		RecordedRequest third = take();
		assertEquals(body(first), body(second));
		assertEquals(body(second), body(third));
		assertEquals("Bearer " + credentials.getRawCredential(), first.getHeader("Authorization"));
		assertTrue(executor.delays.contains(60_000L));
		await(() -> queueSize("connection") == 0);
	}

	@Test public void restartAfterLostAcknowledgementResendsSameEvent() throws Exception
	{
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop());
		RecordedRequest first = take();
		transport.pause();
		await(() -> queueSize("connection") == 1);
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, new SyncContext(java.util.UUID.randomUUID().toString(), "Fixture", "regular", "standard", "1.2.0", "1.13.1", 231));
		RecordedRequest next = take();
		assertEquals(body(first), body(next));
		await(() -> queueSize("connection") == 0);
	}

	@Test public void partialAcknowledgementOnlyRemovesConfirmedBatchIds() throws Exception
	{
		DurableDropQueue queue = new DurableDropQueue(storage, "connection", gson);
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		server.setDispatcher(new Dispatcher()
		{
			@Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, true); }
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		JsonObject first = gson.fromJson(body(take()), JsonObject.class);
		assertEquals(2, first.getAsJsonArray("events").size());
		JsonObject second = gson.fromJson(body(take()), JsonObject.class);
		assertEquals(1, second.getAsJsonArray("events").size());
		assertEquals(first.getAsJsonArray("events").get(1), second.getAsJsonArray("events").get(0));
		await(() -> queueSize("connection") == 0);
	}

	@Test public void unrelatedAcknowledgementCannotDeleteLocalRecord() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{\"protocolVersion\":1,\"status\":\"accepted\",\"acceptedEventIds\":[\"00000000-0000-4000-8000-000000000000\"],\"serverTime\":\"2026-10-11T12:00:00Z\",\"nextUploadAfterSeconds\":5}"));
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop()); take();
		await(() -> statuses.stream().anyMatch(value -> value.contains("retry")));
		transport.pause();
		assertEquals(1, queueSize("connection"));
	}

	@Test public void optOutCancelsInFlightAndPurgesInactiveConnections() throws Exception
	{
		new DurableDropQueue(storage, "inactive", gson).append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "inactive"));
		server.setDispatcher(new Dispatcher()
		{
			@Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false).setBodyDelay(300, TimeUnit.MILLISECONDS); }
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop()); take();
		transport.discardAll();
		await(() -> !Files.exists(root));
		assertFalse(transport.isConnected());
		assertNull(server.takeRequest(350, TimeUnit.MILLISECONDS));
		assertFalse(Files.exists(root));
	}

	@Test public void bindingMismatchRetainsButRevokedCredentialPurges() throws Exception
	{
		server.enqueue(error(403, "binding_mismatch"));
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop()); take();
		await(() -> statuses.stream().anyMatch(value -> value.contains("binding")));
		assertEquals(1, queueSize("connection"));
		assertEquals(0, invalid.get());
		server.enqueue(error(401, "invalid_connection_credential"));
		transport.connect(credentials, DropCaptureTest.CONTEXT); take();
		await(() -> invalid.get() == 1 && !Files.exists(root.resolve("connection")));
	}

	@Test public void unsupportedProfileNeverUploadsOrCaptures() throws Exception
	{
		transport.connect(credentials, new SyncContext(DropCaptureTest.SESSION.toString(), "Fixture", "regular", "league", "1.2.0", "1.13.1", 231));
		assertFalse(transport.isConnected());
		assertFalse(transport.publish(DropCaptureTest.drop()));
		assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS));
		assertEquals(0, queueSize("connection"));
	}

	@Test public void permanentBatchFailureIsolatesOffenderAndSyncsHealthyEvent() throws Exception
	{
		DurableDropQueue queue = new DurableDropQueue(storage, "connection", gson);
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		String rejected = queue.batch(false).get(0).id();
		server.setDispatcher(new Dispatcher()
		{
			@Override public MockResponse dispatch(RecordedRequest request)
			{
				JsonObject body = gson.fromJson(body(request), JsonObject.class);
				for (com.google.gson.JsonElement event : body.getAsJsonArray("events"))
				{
					if (event.getAsJsonObject().get("eventId").getAsString().equals(rejected)) return error(409, "event_conflict");
				}
				return accepted(request, false);
			}
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		take(); take(); take();
		await(() -> queueSize("connection") == 0);
		assertTrue(new DurableDropQueue(storage, "connection", gson).hasDamagedRecords());
		try (java.util.stream.Stream<Path> files = Files.list(root.resolve("connection"))) { assertEquals(1, files.count()); }
	}

	@Test public void profileChangeDuringQueuedPersistenceCannotRebindOldEvents() throws Exception
	{
		CountDownLatch release = new CountDownLatch(1);
		Future<Boolean> persistenceHeld = executor.submit((Callable<Boolean>) () -> release.await(WAIT_SECONDS, TimeUnit.SECONDS));
		server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return accepted(request, false); } });
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop());
		transport.pause();
		PairingClient.Credentials next = new PairingClient.Credentials("next_connection", "s".repeat(43));
		transport.connect(next, new SyncContext(java.util.UUID.randomUUID().toString(), "Second", "regular", "standard", "1.2.0", "1.13.1", 231));
		transport.publish(DropCaptureTest.drop());
		assertFalse(persistenceHeld.isDone());
		release.countDown();
		assertTrue(persistenceHeld.get(WAIT_SECONDS, TimeUnit.SECONDS));
		JsonObject uploaded = gson.fromJson(body(take()), JsonObject.class);
		assertEquals("next_connection", uploaded.get("connectionId").getAsString());
		assertEquals("Second", uploaded.getAsJsonObject("character").get("displayName").getAsString());
		await(() -> queueSize("connection") == 1 && queueSize("next_connection") == 0);
		assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS));
	}

	@Test public void unfinishedOptOutMarkerPreventsOldQueueResumptionAfterRestart() throws Exception
	{
		new DurableDropQueue(storage, "connection", gson).append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		DurableDropQueue.markDiscard(storage);
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		await(() -> !Files.exists(TestStorage.path(DurableDropQueue.discardMarker(storage))) && queueSize("connection") == 0);
		assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS));
	}

	@Test public void lootTrackerToggleStopsNewCaptureWithoutStoppingSavedUploads() throws Exception
	{
		RuneGlassPlugin plugin = new RuneGlassPlugin();
		RuneGlassConfig config = new RuneGlassConfig()
		{
			@Override public boolean syncEnabled() { return true; }
			@Override public boolean dropSyncEnabled() { return true; }
		};
		setField(plugin, "config", config);
		setField(plugin, "dropClient", transport);
		setField(plugin, "activeCredentials", credentials);
		setField(plugin, "syncSessionId", DropCaptureTest.SESSION);
		SkillsSyncSession session = new SkillsSyncSession(); session.start(); setField(plugin, "session", session);
		server.setDispatcher(new Dispatcher()
		{
			int count;
			@Override public MockResponse dispatch(RecordedRequest request)
			{
				return count++ == 0 ? new MockResponse().setResponseCode(503) : accepted(request, false);
			}
		});
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		transport.publish(DropCaptureTest.drop()); take();
		net.runelite.client.plugins.loottracker.LootTrackerPlugin tracker = new net.runelite.client.plugins.loottracker.LootTrackerPlugin();
		plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(tracker, false));
		assertTrue(transport.isConnected());
		plugin.onLootReceived(null);
		take(); await(() -> queueSize("connection") == 0);
		plugin.onPluginChanged(new net.runelite.client.events.PluginChanged(tracker, true));
		java.lang.reflect.Field enabled = RuneGlassPlugin.class.getDeclaredField("lootTrackerRunning"); enabled.setAccessible(true);
		assertTrue(enabled.getBoolean(plugin));
	}

	@Test public void connectionCleanupCannotClearAnUnfinishedGlobalOptOut() throws Exception
	{
		new DurableDropQueue(storage, "connection", gson).append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		Files.createDirectory(TestStorage.path(DurableDropQueue.discardMarker(storage)));
		DurableDropQueue.markDiscard(storage.joinSegment("connection"));
		transport.connect(credentials, DropCaptureTest.CONTEXT);
		await(() -> statuses.stream().anyMatch(status -> status.contains("local deletion failed")));
		assertFalse(transport.isConnected());
		assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS));
		assertTrue(Files.exists(TestStorage.path(DurableDropQueue.discardMarker(storage))));
	}

	@Test public void neverEnabledOptOutCreatesNoPluginFolderOrMarker() throws Exception
	{
		Path plugin = temporary.getRoot().toPath().resolve("plugin-data").resolve("runeglass");
		DropClient fresh = transport(TestStorage.rooted(plugin).joinSegment("drops-v1"));
		fresh.discardAll();
		fresh.discardConnection("connection");
		await(() -> statuses.stream().filter(status -> status.startsWith("Drop sync is off.")).count() == 2);
		assertFalse(Files.exists(plugin));
		assertFalse(Files.exists(plugin.getParent()));
		assertEquals(0, server.getRequestCount());
	}

	@Test public void optOutAfterEarlierUseStillPurgesThroughTheDurableMarker() throws Exception
	{
		new DurableDropQueue(storage, "connection", gson).append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		transport.discardAll();
		await(() -> statuses.stream().anyMatch(status -> status.startsWith("Drop sync is off.")));
		assertFalse(Files.exists(root));
		try (java.util.stream.Stream<Path> files = Files.list(root.getParent())) { assertEquals(0, files.count()); }
	}

	private static void setField(RuneGlassPlugin plugin, String name, Object value) throws Exception
	{
		java.lang.reflect.Field field = RuneGlassPlugin.class.getDeclaredField(name);
		field.setAccessible(true); field.set(plugin, value);
	}

	private MockResponse accepted(RecordedRequest request, boolean partial)
	{
		JsonObject body = gson.fromJson(body(request), JsonObject.class);
		JsonArray ids = new JsonArray();
		for (com.google.gson.JsonElement event : body.getAsJsonArray("events"))
		{
			ids.add(event.getAsJsonObject().get("eventId"));
			if (partial) break;
		}
		JsonObject result = new JsonObject(); result.addProperty("protocolVersion", 1);
		result.addProperty("status", "accepted"); result.add("acceptedEventIds", ids);
		result.addProperty("serverTime", "2026-10-11T12:00:00Z"); result.addProperty("nextUploadAfterSeconds", 5);
		return new MockResponse().setBody(gson.toJson(result));
	}

	private static MockResponse error(int code, String error)
	{
		return new MockResponse().setResponseCode(code).setBody("{\"protocolVersion\":1,\"error\":\"" + error + "\"}");
	}
	private static final long WAIT_SECONDS = 10;
	private RecordedRequest take() throws InterruptedException
	{
		RecordedRequest request = server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS); assertNotNull(statuses.toString() + executor.delays.toString(), request); return request;
	}
	private static String body(RecordedRequest request)
	{
		synchronized (request) { return request.getBody().clone().readUtf8(); }
	}
	private int queueSize(String connectionId)
	{
		try
		{
			if (!Files.exists(root.resolve(connectionId))) return 0;
			return new DurableDropQueue(storage, connectionId, gson).size();
		}
		catch (Exception exception) { return -1; }
	}
	private DropClient transport(Filepath dropRoot)
	{
		return new DropClient(new OkHttpClient(), gson, executor, server.url("/runelite/v1/drops"), dropRoot, new DropClient.Listener()
		{
			@Override public void onStatus(String status) { statuses.add(status); progress.add(status); }
			@Override public void onInvalidConnection(PairingClient.Credentials ignored, boolean mismatch) { invalid.incrementAndGet(); progress.add("invalid"); }
		});
	}
	private void await(BooleanSupplier condition) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (!condition.getAsBoolean())
		{
			long remaining = deadline - System.nanoTime();
			assertTrue("Drop transport made no further progress: " + statuses, remaining > 0);
			progress.poll(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)), TimeUnit.NANOSECONDS);
		}
	}
	private static final class FastScheduler extends ScheduledThreadPoolExecutor
	{
		final List<Long> delays = new CopyOnWriteArrayList<>();
		FastScheduler() { super(1); }
		@Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
		{
			delays.add(unit.toMillis(delay));
			return super.schedule(command, Math.min(150, unit.toMillis(delay)), TimeUnit.MILLISECONDS);
		}
	}
}
