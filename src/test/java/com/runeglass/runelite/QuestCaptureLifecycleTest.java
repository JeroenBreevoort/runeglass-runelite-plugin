package com.runeglass.runelite;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuestCaptureLifecycleTest
{
	private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
	private MockWebServer server;
	private ScheduledExecutorService executor;
	private SemanticSnapshotClient<QuestSnapshot> transport;
	private RuneGlassPlugin plugin;
	private boolean masterEnabled;
	private boolean questEnabled;
	private CountDownLatch retryScheduled;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor();
		OkHttpClient http = new OkHttpClient();
		Gson gson = new Gson();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		transport = new SemanticSnapshotClient<>(http, gson, executor, server.url("/"), clock, 1, () -> 0L,
			"runelite/v1/quests", (context, connectionId, snapshot) -> SemanticSnapshotPayload.create(
				context, connectionId, snapshot.getSnapshotId(), snapshot.getObservedAt(), "progress", snapshot.getProgress()));
		plugin = new RuneGlassPlugin();
		masterEnabled = true;
		questEnabled = true;
		RuneGlassConfig config = (RuneGlassConfig) Proxy.newProxyInstance(RuneGlassConfig.class.getClassLoader(), new Class<?>[]{RuneGlassConfig.class}, (proxy, method, args) ->
		{
			if ("syncEnabled".equals(method.getName())) return masterEnabled;
			if ("questSyncEnabled".equals(method.getName())) return questEnabled;
			throw new AssertionError("Unexpected config read " + method.getName());
		});
		setField("config", config);
		setField("questClient", transport);
		setField("pairingClient", new PairingClient(http, gson, executor, server.url("/"), clock));
		setField("snapshotClient", new SnapshotClient(http, gson, executor, server.url("/"), clock, 1, () -> 0L));
		retryScheduled = new CountDownLatch(1);
		transport.connect(new PairingClient.Credentials("pcn_" + "c".repeat(43), "r".repeat(43)),
			new SyncContext("0b54873e-c169-4de7-9bd0-abc879f84d2f", "Fixture", "regular", "standard", "1.2.0", "1.13.1", 231),
			new SemanticSnapshotClient.Listener()
			{
				@Override public void onAccepted(Instant serverTime) {}
				@Override public void onRetryScheduled() { retryScheduled.countDown(); }
				@Override public void onFailure(SemanticSnapshotClient.Failure failure) {}
			});
	}

	@After
	public void tearDown() throws Exception
	{
		transport.discard();
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void questOptOutDiscardsAnAlreadyQueuedRetry() throws Exception
	{
		queueRetry();
		questEnabled = false;
		invoke("handleConfigChanged", String.class, "questSyncEnabled");
		assertDiscarded();
	}

	@Test
	public void masterOffDiscardsAnAlreadyQueuedRetry() throws Exception
	{
		queueRetry();
		masterEnabled = false;
		invoke("handleConfigChanged", String.class, "syncEnabled");
		assertDiscarded();
	}

	@Test
	public void profileSwitchDiscardsAnAlreadyQueuedRetry() throws Exception
	{
		queueRetry();
		invoke("finishSession", SnapshotReason.class, SnapshotReason.PROFILE_SWITCH);
		assertDiscarded();
	}

	@Test
	public void profileBecomingReadyRestartsThePreviouslyUnboundSession() throws Exception
	{
		assertFalse(plugin.needsProfileRestart(null));
		assertTrue(plugin.needsProfileRestart("ready-profile"));
		setField("activeProfileKey", "ready-profile");
		assertFalse(plugin.needsProfileRestart("ready-profile"));
		assertTrue(plugin.needsProfileRestart("other-profile"));
		assertTrue(plugin.needsProfileRestart(null));
	}

	private void queueRetry() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "1"));
		assertTrue(transport.publish(snapshot()));
		assertTrue(server.takeRequest(3, TimeUnit.SECONDS) != null);
		assertTrue(retryScheduled.await(3, TimeUnit.SECONDS));
		QuestSnapshotReducer reducer = (QuestSnapshotReducer) getField("questReducer");
		assertTrue(reducer.observe(NOW, 1, Map.of(17, "completed")).isPresent());
		assertFalse(reducer.observe(NOW, 1, Map.of(17, "completed")).isPresent());
	}

	private void assertDiscarded() throws Exception
	{
		assertFalse(transport.publish(snapshot()));
		assertNull(server.takeRequest(1200, TimeUnit.MILLISECONDS));
		QuestSnapshotReducer reducer = (QuestSnapshotReducer) getField("questReducer");
		assertTrue(reducer.observe(NOW, 1, Map.of(17, "completed")).isPresent());
	}

	private QuestSnapshot snapshot()
	{
		return new QuestSnapshot(NOW, 1, Map.of(17, "completed"));
	}

	private void invoke(String name, Class<?> type, Object argument) throws Exception
	{
		Method method = RuneGlassPlugin.class.getDeclaredMethod(name, type);
		method.setAccessible(true);
		method.invoke(plugin, argument);
	}

	private void setField(String name, Object value) throws Exception
	{
		Field field = RuneGlassPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private Object getField(String name) throws Exception
	{
		Field field = RuneGlassPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}
}
