package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigClient;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemClient;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

final class DropHandlerFixture implements AutoCloseable
{
	static final PairingClient.Credentials CREDENTIALS = new PairingClient.Credentials("connection", "r".repeat(43));
	static final UUID SESSION = DropCaptureTest.SESSION;

	final RuneGlassPlugin plugin = new RuneGlassPlugin();
	final MockWebServer server = new MockWebServer();
	final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	final BlockingQueue<JsonObject> uploads = new LinkedBlockingQueue<>();
	final List<String> statuses = new CopyOnWriteArrayList<>();
	final AtomicInteger definitionReads = new AtomicInteger();
	final Map<Integer, ItemComposition> definitions = new ConcurrentHashMap<>();
	final DropClient dropClient;
	final ConfigManager configManager;
	volatile int tick = 100;
	volatile int collectionLogSetting = 1;
	volatile boolean syncEnabled = true;
	volatile boolean dropSyncEnabled = true;
	volatile boolean dropCountSyncEnabled;
	final EventBus eventBus = new EventBus();
	final AtomicInteger evidenceReads = new AtomicInteger();
	volatile GameState gameState = GameState.LOGGED_IN;
	volatile EnumSet<WorldType> worldTypes = EnumSet.noneOf(WorldType.class);

	DropHandlerFixture(Path root) throws Exception
	{
		Gson gson = new Gson();
		server.setDispatcher(new Dispatcher()
		{
			@Override public MockResponse dispatch(RecordedRequest request)
			{
				JsonObject body = gson.fromJson(request.getBody().readUtf8(), JsonObject.class);
				JsonArray ids = new JsonArray();
				for (JsonElement event : body.getAsJsonArray("events"))
				{
					uploads.add(event.getAsJsonObject());
					ids.add(event.getAsJsonObject().get("eventId"));
				}
				JsonObject result = new JsonObject();
				result.addProperty("protocolVersion", 1);
				result.addProperty("status", "accepted");
				result.add("acceptedEventIds", ids);
				result.addProperty("serverTime", "2026-10-13T12:00:00Z");
				result.addProperty("nextUploadAfterSeconds", 1);
				return new MockResponse().setBody(gson.toJson(result));
			}
		});
		server.start();
		definitions.put(995, DropCaptureTest.definition(995, true, -1, -1));
		definitions.put(4151, DropCaptureTest.definition(4151, true, -1, -1));
		definitions.put(1127, DropCaptureTest.definition(1127, true, -1, -1));
		definitions.put(21907, DropCaptureTest.definition(21907, false, -1, -1));
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getGameState": return gameState;
				case "getWorldType": return worldTypes;
				case "isClientThread": return true;
				case "getTopLevelWorldView": return null;
				case "getTickCount": return tick;
				case "getVarbitValue":
					if ((Integer) args[0] == net.runelite.api.gameval.VarbitID.IRONMAN) return 0;
					evidenceReads.incrementAndGet(); return collectionLogSetting;
				case "getRevision": return 231;
				case "getLocalPlayer":
					return Proxy.newProxyInstance(net.runelite.api.Player.class.getClassLoader(),
						new Class<?>[]{net.runelite.api.Player.class}, (player, getter, arguments) ->
						{
							if (getter.getName().equals("getName")) return "Fixture";
							throw new AssertionError("Unexpected player read " + getter.getName());
						});
				case "getItemDefinition":
					definitionReads.incrementAndGet();
					return definitions.get((Integer) args[0]);
				default: throw new AssertionError("Unexpected client read " + method.getName());
			}
		});
		RuneGlassConfig config = new RuneGlassConfig()
		{
			@Override public boolean syncEnabled() { return syncEnabled; }
			@Override public boolean dropSyncEnabled() { return dropSyncEnabled; }
			@Override public boolean dropCountSyncEnabled() { return dropCountSyncEnabled; }
		};
		Constructor<ConfigManager> configConstructor = ConfigManager.class.getDeclaredConstructor(String.class,
			ScheduledExecutorService.class, EventBus.class, Client.class, Gson.class,
			ConfigClient.class, ProfileManager.class, SessionManager.class);
		configConstructor.setAccessible(true);
		configManager = configConstructor.newInstance(null, executor, new EventBus(), client, gson, null, null, null);
		setField(configManager, "rsProfileKey", "fixture-profile");
		ScheduledExecutorService inert = (ScheduledExecutorService) Proxy.newProxyInstance(
			ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> null);
		Constructor<ItemManager> itemConstructor = ItemManager.class.getDeclaredConstructor(Client.class,
			ScheduledExecutorService.class, ClientThread.class, EventBus.class, ItemClient.class, RuneLiteConfig.class);
		itemConstructor.setAccessible(true);
		ItemManager itemManager = itemConstructor.newInstance(client, inert, null, new EventBus(), null, null);
		dropClient = new DropClient(new OkHttpClient(), gson, executor, server.url("/runelite/v1/drops"),
			TestStorage.rooted(root).joinSegment("drops-v1"), new DropClient.Listener()
		{
			@Override public void onStatus(String status) { statuses.add(status); }
			@Override public void onInvalidConnection(PairingClient.Credentials credentials, boolean bindingMismatch) { }
		});
		setField(plugin, "client", client);
		setField(plugin, "eventBus", eventBus);
		setField(plugin, "clientThread", new ClientThread()
		{
			@Override public void invokeLater(Runnable task) { task.run(); }
		});
		setField(plugin, "config", config);
		setField(plugin, "configManager", configManager);
		setField(plugin, "itemManager", itemManager);
		setField(plugin, "dropClient", dropClient);
		setField(plugin, "activeCredentials", CREDENTIALS);
		setField(plugin, "activeProfileKey", "fixture-profile");
		setField(plugin, "syncSessionId", SESSION);
		setField(plugin, "lootTrackerRunning", true);
		((SkillsSyncSession) getField(plugin, "session")).start();
		dropClient.connect(CREDENTIALS, DropCaptureTest.CONTEXT);
		drainTransport();
		plugin.advanceDropEvidence();
		tick += 6;
		plugin.advanceDropEvidence();
	}

	void changeCountConsent(boolean enabled) throws Exception
	{
		dropCountSyncEnabled = enabled;
		net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
		event.setGroup(RuneGlassConfig.GROUP);
		event.setKey("dropCountSyncEnabled");
		event.setNewValue(Boolean.toString(enabled));
		plugin.onConfigChanged(event);
		drainTransport();
	}

	void enableCounts() throws Exception
	{
		dropCountSyncEnabled = true;
		dropClient.connect(CREDENTIALS, DropCaptureTest.CONTEXT, "fixture-profile", true);
		drainTransport();
		plugin.advanceDropEvidence();
		tick += 6;
		plugin.advanceDropEvidence();
	}

	void drainTransport() throws Exception
	{
		executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
	}

	JsonObject takeUpload() throws InterruptedException
	{
		tick += 11;
		plugin.advanceDropEvidence();
		return uploads.poll(3, TimeUnit.SECONDS);
	}

	@Override
	public void close() throws java.io.IOException
	{
		dropClient.pause();
		executor.shutdownNow();
		server.shutdown();
	}

	static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	static Object getField(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
