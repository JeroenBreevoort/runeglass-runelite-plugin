package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PluginStorageMigrationTest
{
	private static final String PROFILE = "fixture-profile";
	private static final String CONNECTION_ID = "pcn_" + "c".repeat(43);
	private static final String RAW_CREDENTIAL = "r".repeat(43);
	private static final PairingClient.Credentials CREDENTIALS = new PairingClient.Credentials(CONNECTION_ID, RAW_CREDENTIAL);
	private static final Instant CAPTURED_AT = Instant.parse("2026-10-13T12:00:00Z");
	private static final String OUTGOING_SESSION = "0b54873e-c169-4de7-9bd0-abc879f84d2f";
	private static final String INCOMING_SESSION = "eec7d765-0f2f-4f5d-840a-403835e63a0f";

	private final Gson gson = new Gson();
	private final BlockingQueue<Runnable> clientThreadTasks = new LinkedBlockingQueue<>();
	private final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.LOGIN_SCREEN);
	private ScheduledExecutorService executor;
	private MockWebServer server;
	private Path runelite;
	private Path legacy;
	private Path pluginDirectory;

	@Before
	public void setUp() throws Exception
	{
		runelite = TestStorage.resetRuneLiteDirectory();
		legacy = runelite.resolve("runeglass");
		pluginDirectory = runelite.resolve("plugin-data").resolve("runeglass");
		executor = Executors.newSingleThreadScheduledExecutor();
		server = new MockWebServer();
		server.start();
	}

	@After
	public void tearDown() throws Exception
	{
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void publishedLayoutMovesWithIdenticalBytesModesAndRecoverableState() throws Exception
	{
		Path profile = legacy.resolve("profiles").resolve(profileHash(PROFILE));
		Path queueDirectory = legacy.resolve(CONNECTION_ID);
		Files.createDirectories(profile);
		Files.createDirectories(queueDirectory);
		write(profile.resolve("connection-state-v1.json"), "{\"version\":1,\"connectionId\":\"" + CONNECTION_ID
			+ "\",\"credential\":\"" + RAW_CREDENTIAL + "\",\"nextSequence\":\"7\"}");
		write(profile.resolve("connection-state-v1.tmp"), "{\"version\":1,\"connectionId\":\"" + CONNECTION_ID);
		String batchId = "2f5e8a37-3c1f-4a55-9b84-1c7b5e1f0d6a";
		String payload = gson.toJson(new SyncBatch(batchId, CONNECTION_ID, "6", CAPTURED_AT,
			context(OUTGOING_SESSION), List.of(snapshot(SnapshotReason.LOGOUT_FLUSH))));
		JsonObject entry = new JsonObject();
		entry.addProperty("recordId", batchId);
		entry.addProperty("createdAtEpochMillis", CAPTURED_AT.toEpochMilli());
		entry.addProperty("payload", payload);
		JsonArray entries = new JsonArray();
		entries.add(entry);
		JsonObject queueFile = new JsonObject();
		queueFile.addProperty("version", 1);
		queueFile.add("entries", entries);
		write(queueDirectory.resolve("snapshot-queue-v1.json"), gson.toJson(queueFile));
		write(queueDirectory.resolve("snapshot-queue-v1.tmp"), "partial");
		if (TestStorage.posix())
		{
			Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rwx------"));
			Files.setPosixFilePermissions(profile.resolve("connection-state-v1.json"), PosixFilePermissions.fromString("rw-------"));
			Files.setPosixFilePermissions(queueDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
			Files.setPosixFilePermissions(queueDirectory.resolve("snapshot-queue-v1.json"), PosixFilePermissions.fromString("rw-r--r--"));
		}
		Map<String, String> published = TestStorage.tree(legacy);

		Filepath directory = new RuneGlassPlugin().openStorageDirectory();

		assertEquals(pluginDirectory, TestStorage.path(directory));
		assertFalse(Files.exists(legacy));
		assertEquals(published, TestStorage.tree(pluginDirectory));
		ConnectionStateStore.State state = new ConnectionStateStore(directory.joinSegment("profiles"), PROFILE, gson)
			.load().orElseThrow(AssertionError::new);
		assertEquals(CONNECTION_ID, state.getCredentials().getConnectionId());
		assertEquals(RAW_CREDENTIAL, state.getCredentials().getRawCredential());
		assertEquals(BigInteger.valueOf(7), state.getNextSequence());
		DurableSnapshotQueue queue = new DurableSnapshotQueue(directory.joinSegment(CONNECTION_ID), gson,
			Clock.fixed(CAPTURED_AT.plusSeconds(60), ZoneOffset.UTC));
		assertEquals(DurableSnapshotQueue.LoadStatus.READY, queue.getLoadStatus());
		assertEquals(batchId, queue.peek().orElseThrow(AssertionError::new).getRecordId());
		assertEquals(payload, queue.peek().orElseThrow(AssertionError::new).getPayload());
		assertEquals(List.of("connection-state-v1.json"),
			names(pluginDirectory.resolve("profiles").resolve(profileHash(PROFILE))));
		assertEquals(List.of("snapshot-queue-v1.json"), names(pluginDirectory.resolve(CONNECTION_ID)));
	}

	@Test
	public void migrationIsIdempotentAndNeverMergesARecreatedLegacyFolder() throws Exception
	{
		seedLegacyState();
		Filepath first = new RuneGlassPlugin().openStorageDirectory();
		Map<String, String> migrated = TestStorage.tree(pluginDirectory);

		assertEquals(first, new RuneGlassPlugin().openStorageDirectory());
		assertEquals(migrated, TestStorage.tree(pluginDirectory));

		Files.createDirectories(legacy.resolve("profiles"));
		write(legacy.resolve("profiles").resolve("stale.json"), "{}");
		assertEquals(first, new RuneGlassPlugin().openStorageDirectory());
		assertEquals(migrated, TestStorage.tree(pluginDirectory));
		assertTrue(Files.exists(legacy.resolve("profiles").resolve("stale.json")));
	}

	@Test
	public void concurrentFirstStartsAllOpenTheMigratedFolder() throws Exception
	{
		ExecutorService threads = Executors.newFixedThreadPool(4);
		try
		{
			for (int round = 0; round < 25; round++)
			{
				TestStorage.resetRuneLiteDirectory();
				byte[] state = seedLegacyState();
				CountDownLatch start = new CountDownLatch(1);
				List<Future<Filepath>> results = new ArrayList<>();
				for (int index = 0; index < 4; index++)
				{
					results.add(threads.submit((Callable<Filepath>) () ->
					{
						assertTrue(start.await(5, TimeUnit.SECONDS));
						return new RuneGlassPlugin().openStorageDirectory();
					}));
				}
				start.countDown();
				for (Future<Filepath> result : results)
				{
					Filepath directory = result.get(5, TimeUnit.SECONDS);
					assertNotNull("Round " + round + " could not open storage", directory);
					assertEquals(pluginDirectory, TestStorage.path(directory));
				}
				assertFalse(Files.exists(legacy));
				assertArrayEquals(state, Files.readAllBytes(pluginDirectory.resolve("profiles")
					.resolve(profileHash(PROFILE)).resolve(ConnectionStateStore.FILE_NAME)));
			}
		}
		finally
		{
			threads.shutdownNow();
		}
	}

	@Test
	public void openingRetriesOnceThenReportsUnavailableStorage()
	{
		Filepath expected = TestStorage.rooted(pluginDirectory);
		AtomicInteger calls = new AtomicInteger();
		assertSame(expected, RuneGlassPlugin.openStorageDirectory(() ->
		{
			if (calls.incrementAndGet() == 1)
			{
				throw new IOException("Another client moved the folder first");
			}
			return expected;
		}));
		assertEquals(2, calls.get());

		calls.set(0);
		assertNull(RuneGlassPlugin.openStorageDirectory(() ->
		{
			calls.incrementAndGet();
			throw new IllegalArgumentException("Unusable plugin directory");
		}));
		assertEquals(2, calls.get());
	}

	@Test
	public void freshOptedOutStartCreatesNoPluginFolder() throws Exception
	{
		RuneGlassPlugin plugin = plugin(false);

		plugin.requestStorage();
		runClientThreadTask();
		drainExecutor();

		assertTrue((Boolean) DropHandlerFixture.getField(plugin, "storageSettled"));
		assertNotNull(DropHandlerFixture.getField(plugin, "dropClient"));
		assertEquals(pluginDirectory, TestStorage.path((Filepath) DropHandlerFixture.getField(plugin, "storageDirectory")));
		assertTrue(Files.isDirectory(runelite.resolve("plugin-data")));
		assertFalse(Files.exists(pluginDirectory));
		assertFalse(Files.exists(legacy));
	}

	@Test
	public void storageOpensOnTheExecutorAndIgnoresCallbacksAfterShutdown() throws Exception
	{
		seedLegacyState();
		RuneGlassPlugin plugin = plugin(true);
		CountDownLatch release = new CountDownLatch(1);
		Future<Boolean> busy = executor.submit((Callable<Boolean>) () -> release.await(5, TimeUnit.SECONDS));

		plugin.requestStorage();
		assertTrue(Files.isDirectory(legacy));
		assertFalse(Files.exists(pluginDirectory));
		assertFalse((Boolean) DropHandlerFixture.getField(plugin, "storageSettled"));
		plugin.shutDown();
		release.countDown();
		assertTrue(busy.get(5, TimeUnit.SECONDS));
		runClientThreadTask();

		assertFalse((Boolean) DropHandlerFixture.getField(plugin, "storageSettled"));
		assertNull(DropHandlerFixture.getField(plugin, "storageDirectory"));
		assertNull(DropHandlerFixture.getField(plugin, "dropClient"));

		plugin.requestStorage();
		runClientThreadTask();
		assertTrue((Boolean) DropHandlerFixture.getField(plugin, "storageSettled"));
		assertEquals(pluginDirectory, TestStorage.path((Filepath) DropHandlerFixture.getField(plugin, "storageDirectory")));
		assertNotNull(DropHandlerFixture.getField(plugin, "dropClient"));
		assertFalse(Files.exists(legacy));
	}

	@Test
	public void unavailableStorageKeepsLegacyDataAndFailsPairingVisibly() throws Exception
	{
		byte[] state = seedLegacyState();
		Files.createDirectories(runelite);
		Files.write(runelite.resolve("plugin-data"), new byte[0]);
		RuneGlassPlugin plugin = plugin(true);
		AtomicReference<RuneGlassPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> panel.set(new RuneGlassPanel(() -> { }, () -> { }, () -> { })));
		DropHandlerFixture.setField(plugin, "panel", panel.get());
		DropHandlerFixture.setField(plugin, "pairingClient", PairingClient.create(new OkHttpClient(), gson, executor));

		plugin.requestStorage();
		runClientThreadTask();
		assertTrue((Boolean) DropHandlerFixture.getField(plugin, "storageSettled"));
		assertNull(DropHandlerFixture.getField(plugin, "storageDirectory"));
		assertNull(DropHandlerFixture.getField(plugin, "dropClient"));
		assertArrayEquals(state, Files.readAllBytes(legacy.resolve("profiles")
			.resolve(profileHash(PROFILE)).resolve(ConnectionStateStore.FILE_NAME)));

		gameState.set(GameState.LOGGED_IN);
		Method startPairing = RuneGlassPlugin.class.getDeclaredMethod("startPairing");
		startPairing.setAccessible(true);
		startPairing.invoke(plugin);
		SwingUtilities.invokeAndWait(() -> { });
		String text = text(panel.get());
		assertTrue(text, text.contains("Drops paused: local storage could not be opened."));
		assertTrue(text, text.contains("RuneGlass could not complete pairing."));
		assertEquals(0, server.getRequestCount());
	}

	@Test
	public void outgoingFlushIsPersistedBeforeMigrationAndResentWithItsOriginalBatch() throws Exception
	{
		ConnectionStateStore legacyStore = new ConnectionStateStore(
			TestStorage.rooted(legacy).joinSegment("profiles"), PROFILE, gson);
		assertTrue(legacyStore.save(CREDENTIALS, BigInteger.valueOf(7)));
		CountDownLatch migrated = new CountDownLatch(1);
		AtomicInteger requests = new AtomicInteger();
		server.setDispatcher(new Dispatcher()
		{
			@Override
			public MockResponse dispatch(RecordedRequest request) throws InterruptedException
			{
				int number = requests.incrementAndGet();
				if (number == 1 && !migrated.await(5, TimeUnit.SECONDS))
				{
					return new MockResponse().setResponseCode(500);
				}
				return accepted(number == 2 ? "duplicate" : "accepted", number == 3 ? "8" : "7");
			}
		});
		Clock clock = Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
		SnapshotClient outgoing = snapshotClient(clock);
		CountDownLatch outgoingAccepted = new CountDownLatch(1);
		assertTrue(outgoing.connect(CREDENTIALS, context(OUTGOING_SESSION), BigInteger.valueOf(7),
			new DurableSnapshotQueue(TestStorage.rooted(legacy).joinSegment(CONNECTION_ID), gson, clock),
			listener(outgoingAccepted, sequence -> { })));
		RuneGlassPlugin plugin = plugin(false);
		CountDownLatch release = new CountDownLatch(1);
		Future<Boolean> refreshing = executor.submit((Callable<Boolean>) () -> release.await(5, TimeUnit.SECONDS));

		assertTrue(outgoing.finishSession(snapshot(SnapshotReason.LOGOUT_FLUSH)));
		outgoing.closeAfterFlush();
		plugin.requestStorage();
		assertTrue(Files.isDirectory(legacy));
		release.countDown();
		assertTrue(refreshing.get(5, TimeUnit.SECONDS));
		Runnable opened = clientThreadTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(opened);

		assertFalse(Files.exists(legacy));
		assertTrue(Files.exists(pluginDirectory.resolve(CONNECTION_ID).resolve(DurableSnapshotQueue.FILE_NAME)));
		migrated.countDown();
		String outgoingBody = takeBatch().getBody().clone().readUtf8();
		JsonObject outgoingBatch = body(outgoingBody);
		assertEquals("7", outgoingBatch.get("sequence").getAsString());
		assertTrue(outgoingAccepted.await(5, TimeUnit.SECONDS));
		drainExecutor();
		assertFalse(Files.exists(legacy));

		opened.run();
		Filepath directory = (Filepath) DropHandlerFixture.getField(plugin, "storageDirectory");
		assertEquals(pluginDirectory, TestStorage.path(directory));
		ConnectionStateStore store = new ConnectionStateStore(directory.joinSegment("profiles"), PROFILE, gson);
		ConnectionStateStore.State state = store.load().orElseThrow(AssertionError::new);
		assertEquals(BigInteger.valueOf(7), state.getNextSequence());
		DurableSnapshotQueue queue = new DurableSnapshotQueue(directory.joinSegment(CONNECTION_ID), gson, clock);
		assertEquals(1, queue.size());
		BlockingQueue<BigInteger> savedSequences = new LinkedBlockingQueue<>();
		CountDownLatch incomingAccepted = new CountDownLatch(2);
		SnapshotClient incoming = snapshotClient(clock);
		assertTrue(incoming.connect(state.getCredentials(), context(INCOMING_SESSION), state.getNextSequence(), queue,
			listener(incomingAccepted, sequence ->
			{
				assertTrue(store.save(CREDENTIALS, sequence));
				savedSequences.add(sequence);
			})));

		assertEquals(outgoingBody, takeBatch().getBody().clone().readUtf8());
		assertTrue(incoming.publishImmediately(snapshot(SnapshotReason.MANUAL_SYNC)));
		JsonObject nextBatch = body(takeBatch().getBody().clone().readUtf8());
		assertEquals("8", nextBatch.get("sequence").getAsString());
		assertEquals(INCOMING_SESSION, nextBatch.get("sessionId").getAsString());
		assertNotEquals(outgoingBatch.get("batchId"), nextBatch.get("batchId"));
		assertTrue(incomingAccepted.await(5, TimeUnit.SECONDS));
		assertEquals(BigInteger.valueOf(9), savedSequences.poll(5, TimeUnit.SECONDS));
		assertEquals(BigInteger.valueOf(9), store.load().orElseThrow(AssertionError::new).getNextSequence());
		assertEquals(3, server.getRequestCount());
		incoming.cancel();
	}

	private RuneGlassPlugin plugin(boolean dropSyncEnabled) throws Exception
	{
		RuneGlassPlugin plugin = new RuneGlassPlugin();
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			if ("getGameState".equals(method.getName()))
			{
				return gameState.get();
			}
			throw new AssertionError("Unexpected client read " + method.getName());
		});
		RuneGlassConfig config = new RuneGlassConfig()
		{
			@Override public boolean syncEnabled() { return true; }
			@Override public boolean dropSyncEnabled() { return dropSyncEnabled; }
		};
		ClientThread clientThread = new ClientThread()
		{
			@Override public void invokeLater(Runnable runnable) { clientThreadTasks.add(runnable); }
		};
		DropHandlerFixture.setField(plugin, "client", client);
		DropHandlerFixture.setField(plugin, "config", config);
		DropHandlerFixture.setField(plugin, "executor", executor);
		DropHandlerFixture.setField(plugin, "clientThread", clientThread);
		DropHandlerFixture.setField(plugin, "httpClient", new OkHttpClient());
		DropHandlerFixture.setField(plugin, "gson", gson);
		return plugin;
	}

	private SnapshotClient snapshotClient(Clock clock)
	{
		return new SnapshotClient(new OkHttpClient(), gson, executor, server.url("/"), clock, 1, () -> 0L);
	}

	private void runClientThreadTask() throws Exception
	{
		Runnable task = clientThreadTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull("Storage did not report back to the client thread", task);
		task.run();
	}

	private void drainExecutor() throws Exception
	{
		executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
	}

	private RecordedRequest takeBatch() throws Exception
	{
		RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
		assertNotNull(request);
		assertEquals("/runelite/v1/batches", request.getPath());
		return request;
	}

	private byte[] seedLegacyState() throws Exception
	{
		ConnectionStateStore store = new ConnectionStateStore(TestStorage.rooted(legacy).joinSegment("profiles"), PROFILE, gson);
		assertTrue(store.save(CREDENTIALS, BigInteger.valueOf(3)));
		return Files.readAllBytes(TestStorage.path(store.getStateFile()));
	}

	private static SnapshotClient.Listener listener(CountDownLatch accepted, Consumer<BigInteger> sequences)
	{
		return new SnapshotClient.Listener()
		{
			@Override public void onUploading(int recordCount) { }
			@Override public void onAccepted(Instant serverTime) { accepted.countDown(); }
			@Override public void onRetryScheduled() { throw new AssertionError("Unexpected retry"); }
			@Override public void onFailure(SnapshotClient.Failure failure) { throw new AssertionError("Unexpected failure " + failure); }
			@Override public void onNextSequenceChanged(BigInteger nextSequence) { sequences.accept(nextSequence); }
		};
	}

	private static MockResponse accepted(String status, String sequence)
	{
		return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"status\":\"" + status + "\",\"acceptedSequence\":\"" + sequence
				+ "\",\"serverTime\":\"2026-10-13T12:00:01Z\",\"nextUploadAfterSeconds\":1}");
	}

	private static JsonObject body(String text)
	{
		return new JsonParser().parse(text).getAsJsonObject();
	}

	private static SyncContext context(String sessionId)
	{
		return new SyncContext(sessionId, "Iron Jeromey", "ironman", "standard", "1.2.0", "1.13.1", 231);
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
		return new SkillsSnapshot(CAPTURED_AT, reason, experience, levels, boostedLevels);
	}

	private static String profileHash(String profileKey) throws Exception
	{
		byte[] hash = MessageDigest.getInstance("SHA-256")
			.digest(("runeglass-profile-state-v1:" + profileKey).getBytes(StandardCharsets.UTF_8));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
	}

	private static void write(Path file, String contents) throws IOException
	{
		Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
	}

	private static List<String> names(Path directory) throws IOException
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}

	private static String text(Container container)
	{
		StringBuilder text = new StringBuilder();
		for (Component component : container.getComponents())
		{
			if (component instanceof JTextArea)
			{
				text.append(((JTextArea) component).getText()).append('\n');
			}
			if (component instanceof Container)
			{
				text.append(text((Container) component));
			}
		}
		return text.toString();
	}
}
