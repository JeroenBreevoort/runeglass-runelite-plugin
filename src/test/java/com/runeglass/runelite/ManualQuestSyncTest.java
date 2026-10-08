package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigClient;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.eventbus.EventBus;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ManualQuestSyncTest
{
	private static final Instant BEFORE = Instant.parse("2026-10-07T12:00:00Z");
	private final BlockingQueue<Instant> accepted = new LinkedBlockingQueue<>();
	private final BlockingQueue<Boolean> retries = new LinkedBlockingQueue<>();
	private final BlockingQueue<SemanticSnapshotClient.Failure> failures = new LinkedBlockingQueue<>();
	private MockWebServer server;
	private ScheduledExecutorService executor;
	private SemanticSnapshotClient<QuestSnapshot> transport;
	private SnapshotClient snapshotClient;
	private RuneGlassPlugin plugin;
	private ConfigManager configManager;
	private QuestSnapshotReducer reducer;
	private SkillsSyncSession session;
	private boolean masterEnabled = true;
	private boolean questEnabled = true;
	private GameState gameState = GameState.LOGGED_IN;
	private Player localPlayer;
	private int questPoints = 1;
	private int questStatus = 2;
	private int currentQuestId;
	private int scriptReads;
	private int pointReads;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor();
		OkHttpClient http = new OkHttpClient();
		Gson gson = new Gson();
		Clock clock = Clock.systemUTC();
		transport = new SemanticSnapshotClient<>(http, gson, executor, server.url("/"), clock, 1, () -> 0L,
			"runelite/v1/quests", (context, connectionId, snapshot) -> SemanticSnapshotPayload.create(
				context, connectionId, snapshot.getSnapshotId(), snapshot.getObservedAt(), "progress", snapshot.getProgress()));
		snapshotClient = new SnapshotClient(http, gson, executor, server.url("/"), clock, 1, () -> 0L);
		localPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, args) -> { throw new AssertionError("Unexpected player read " + method.getName()); });
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getGameState": return gameState;
				case "getLocalPlayer": return localPlayer;
				case "getSkillExperience": return 0;
				case "getRealSkillLevel":
				case "getBoostedSkillLevel": return 1;
				case "runScript":
					Object[] script = (Object[]) args[0];
					assertEquals(ScriptID.QUEST_STATUS_GET, script[0]);
					currentQuestId = (Integer) script[1];
					scriptReads++;
					return null;
				case "getIntStack": return new int[]{currentQuestId == 17 ? questStatus : -1};
				case "getVarpValue":
					assertEquals(VarPlayerID.QP, args[0]);
					pointReads++;
					return questPoints;
				default: throw new AssertionError("Unexpected client read " + method.getName());
			}
		});
		RuneGlassConfig config = (RuneGlassConfig) Proxy.newProxyInstance(RuneGlassConfig.class.getClassLoader(), new Class<?>[]{RuneGlassConfig.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "syncEnabled": return masterEnabled;
				case "questSyncEnabled": return questEnabled;
				case "birdHouseSyncEnabled":
				case "farmingPatchSyncEnabled":
				case "appearanceSyncEnabled": return false;
				default: throw new AssertionError("Unexpected config read " + method.getName());
			}
		});
		Constructor<ConfigManager> constructor = ConfigManager.class.getDeclaredConstructor(String.class,
			ScheduledExecutorService.class, EventBus.class, Client.class, Gson.class,
			ConfigClient.class, ProfileManager.class, SessionManager.class);
		constructor.setAccessible(true);
		configManager = constructor.newInstance(null, executor, new EventBus(), client, gson, null, null, null);
		setField(configManager, "rsProfileKey", "fixture-profile");
		PairingClient.Credentials credentials = new PairingClient.Credentials("pcn_" + "c".repeat(43), "r".repeat(43));
		transport.connect(credentials,
			new SyncContext("0b54873e-c169-4de7-9bd0-abc879f84d2f", "Fixture", "regular", "standard", "1.2.0", "1.13.1", 231),
			new SemanticSnapshotClient.Listener()
			{
				@Override public void onAccepted(Instant serverTime) { accepted.add(serverTime); }
				@Override public void onRetryScheduled() { retries.add(true); }
				@Override public void onFailure(SemanticSnapshotClient.Failure failure) { failures.add(failure); }
			});
		plugin = new RuneGlassPlugin();
		setField(plugin, "client", client);
		setField(plugin, "config", config);
		setField(plugin, "configManager", configManager);
		setField(plugin, "activeProfileKey", "fixture-profile");
		setField(plugin, "activeCredentials", credentials);
		setField(plugin, "snapshotClient", snapshotClient);
		setField(plugin, "questClient", transport);
		reducer = (QuestSnapshotReducer) getField(plugin, "questReducer");
		session = (SkillsSyncSession) getField(plugin, "session");
		session.start();
	}

	@After
	public void tearDown() throws Exception
	{
		transport.discard();
		snapshotClient.discard();
		executor.shutdownNow();
		server.shutdown();
	}

	@Test
	public void manualSyncPostsFreshQuestObservationDespiteUnchangedProgress() throws Exception
	{
		QuestSnapshot previous = reducer.observe(BEFORE, questPoints, Map.of(17, "completed")).get();
		server.enqueue(acceptedResponse());
		Instant startedAt = Instant.now();
		manualSync();
		RecordedRequest request = takeRequest();
		assertEquals("POST", request.getMethod());
		assertEquals("/runelite/v1/quests", request.getPath());
		assertEquals("Bearer " + "r".repeat(43), request.getHeader("Authorization"));
		JsonObject body = body(request);
		assertNotEquals(previous.getSnapshotId(), body.get("snapshotId").getAsString());
		assertFalse(Instant.parse(body.get("observedAt").getAsString()).isBefore(startedAt));
		assertProgress(body, 1, "completed");
		assertEquals(QuestCatalog.ids().length, scriptReads);
		assertEquals(1, pointReads);
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
		assertFalse(reducer.observe(Instant.now(), 1, Map.of(17, "completed")).isPresent());
	}

	@Test
	public void manualSyncReadsCurrentQuestPointsAndState() throws Exception
	{
		reducer.observe(BEFORE, 0, Map.of(17, "not_started"));
		questPoints = 7;
		questStatus = 0;
		server.enqueue(acceptedResponse());
		manualSync();
		assertProgress(body(takeRequest()), 7, "in_progress");
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
	}

	@Test
	public void manualSyncHonorsConsentSessionAndLoginGuards() throws Exception
	{
		masterEnabled = false;
		assertNoQuestCapture();
		masterEnabled = true;
		questEnabled = false;
		assertNoQuestCapture();
		assertEquals("manual_sync", ((SkillsSnapshot) getField(plugin, "latestSnapshot")).getReason());
		questEnabled = true;
		session.cancel();
		assertNoQuestCapture();
		session.start();
		gameState = GameState.LOGIN_SCREEN;
		assertNoQuestCapture();
		gameState = GameState.LOGGED_IN;
		localPlayer = null;
		assertNoQuestCapture();
	}

	@Test
	public void manualSyncHonorsConnectionAndFinishingGuards() throws Exception
	{
		Object credentials = getField(plugin, "activeCredentials");
		setField(plugin, "activeCredentials", null);
		assertNoQuestCapture();
		setField(plugin, "activeCredentials", credentials);
		setField(plugin, "snapshotClient", null);
		assertNoQuestCapture();
		setField(plugin, "snapshotClient", snapshotClient);
		setField(snapshotClient, "finalizingSession", true);
		assertNoQuestCapture();
		setField(snapshotClient, "finalizingSession", false);
		setField(plugin, "questClient", null);
		assertNoQuestCapture();
	}

	@Test
	public void manualSyncWaitsForBaselineAndTheCurrentProfile() throws Exception
	{
		LoginBaselineGate gate = (LoginBaselineGate) getField(plugin, "baselineGate");
		gate.arm();
		assertNoQuestCapture();
		gate.cancel();
		setField(configManager, "rsProfileKey", "different-profile");
		assertNoQuestCapture();
	}

	@Test
	public void manualSyncDoesNotReadOrReconnectADiscardedTransport() throws Exception
	{
		transport.discard();
		assertFalse(transport.isConnected());
		assertNoQuestCapture();
		assertFalse(transport.isConnected());
	}

	@Test
	public void manualSyncDoesNotReadOrReconnectAServerPausedTransport() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(422).setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"error\":\"unsupported_profile\"}"));
		assertTrue(transport.publish(new QuestSnapshot(BEFORE, 1, Map.of(17, "completed"))));
		takeRequest();
		assertEquals(SemanticSnapshotClient.Failure.UNSUPPORTED_PROFILE, failures.poll(3, TimeUnit.SECONDS));
		assertFalse(transport.isConnected());
		assertNoQuestCapture();
		assertFalse(transport.isConnected());
	}

	@Test
	public void emptyManualCaptureRetriesOnTheAutomaticSchedule() throws Exception
	{
		captureAutomaticBaseline();
		questStatus = -1;
		manualSync();
		assertEquals(2 * QuestCatalog.ids().length, scriptReads);
		assertEquals(1, pointReads);
		assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
		questStatus = 2;
		questPoints = 2;
		assertAutomaticRetry();
	}

	@Test
	public void invalidManualQuestPointsRetryOnTheAutomaticSchedule() throws Exception
	{
		captureAutomaticBaseline();
		questPoints = -1;
		manualSync();
		assertEquals(2 * QuestCatalog.ids().length, scriptReads);
		assertEquals(2, pointReads);
		assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
		questPoints = 2;
		assertAutomaticRetry();
	}

	@Test
	public void manualCapturePreservesTheAutomaticTickThrottle() throws Exception
	{
		captureAutomaticBaseline();
		manualSync();
		assertEquals(2 * QuestCatalog.ids().length, scriptReads);
		plugin.onVarbitChanged(null);
		questPoints = 2;
		for (int tick = 0; tick < 49; tick++) plugin.onGameTick(null);
		assertEquals(2 * QuestCatalog.ids().length, scriptReads);
		plugin.onGameTick(null);
		assertEquals(3 * QuestCatalog.ids().length, scriptReads);
	}

	@Test
	public void manualCaptureQueuesBehindAnIdenticalRetryAndServerCooldown() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "1"));
		server.enqueue(acceptedResponse());
		server.enqueue(acceptedResponse());
		manualSync();
		String initialBody = takeRequest().getBody().readUtf8();
		assertNotNull(retries.poll(3, TimeUnit.SECONDS));
		questPoints = 7;
		questStatus = 0;
		manualSync();
		assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS));
		assertEquals(initialBody, takeRequest().getBody().readUtf8());
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
		assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS));
		assertProgress(body(takeRequest()), 7, "in_progress");
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
		assertEquals(3, server.getRequestCount());
	}

	private void assertNoQuestCapture() throws Exception
	{
		int readsBefore = scriptReads;
		int pointsBefore = pointReads;
		manualSync();
		assertEquals(readsBefore, scriptReads);
		assertEquals(pointsBefore, pointReads);
		assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
	}

	private void captureAutomaticBaseline() throws Exception
	{
		server.enqueue(acceptedResponse());
		plugin.onGameTick(null);
		plugin.onGameTick(null);
		plugin.onGameTick(null);
		takeRequest();
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
	}

	private void assertAutomaticRetry() throws Exception
	{
		int readsBefore = scriptReads;
		for (int tick = 0; tick < 49; tick++) plugin.onGameTick(null);
		assertEquals(readsBefore, scriptReads);
		server.enqueue(acceptedResponse());
		plugin.onGameTick(null);
		assertProgress(body(takeRequest()), 2, "completed");
		assertNotNull(accepted.poll(3, TimeUnit.SECONDS));
	}

	private void manualSync() throws Exception
	{
		Method method = RuneGlassPlugin.class.getDeclaredMethod("requestManualSync");
		method.setAccessible(true);
		method.invoke(plugin);
	}

	private RecordedRequest takeRequest() throws Exception
	{
		RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
		assertNotNull("Expected a semantic quest request", request);
		return request;
	}

	private static JsonObject body(RecordedRequest request)
	{
		return new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
	}

	private static void assertProgress(JsonObject body, int points, String state)
	{
		JsonObject progress = body.getAsJsonObject("progress");
		assertEquals(points, progress.get("questPoints").getAsInt());
		assertEquals(1, progress.getAsJsonArray("quests").size());
		JsonObject quest = progress.getAsJsonArray("quests").get(0).getAsJsonObject();
		assertEquals(17, quest.get("id").getAsInt());
		assertEquals(state, quest.get("state").getAsString());
	}

	private static MockResponse acceptedResponse()
	{
		return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
			.setBody("{\"protocolVersion\":1,\"status\":\"accepted\",\"serverTime\":\"2026-10-07T12:00:00Z\",\"nextUploadAfterSeconds\":1}");
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object getField(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
