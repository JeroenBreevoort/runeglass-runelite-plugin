package com.runeglass.runelite;

import com.google.inject.Provides;
import com.google.gson.Gson;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Model;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "RuneGlass",
	description = "Synchronizes your current character's skills, XP, and opted-in timers, quests and drops with RuneGlass",
	tags = {"mobile", "progress", "skills", "xp", "timers", "sync"},
	internalName = "runeglass",
	legacyDataDirectory = "runeglass"
)
public class RuneGlassPlugin extends Plugin
{
	private static final Logger LOG = LoggerFactory.getLogger(RuneGlassPlugin.class);

	interface DropCaptureObserver
	{
		void onLootHandled(DropCaptureResult result);

		void onDropStatus(String status);
	}

	@Inject
	private Client client;

	@Inject
	private RuneGlassConfig config;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PluginManager pluginManager;

	private volatile boolean lootTrackerRunning;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	private final DropChatListener dropChatListener = new DropChatListener();
	private boolean dropChatSubscribed;

	private final SkillsSyncSession session = new SkillsSyncSession();
	private final LoginBaselineGate baselineGate = new LoginBaselineGate();
	private final BirdHouseSnapshotReducer birdHouseReducer = new BirdHouseSnapshotReducer();
	private final QuestSnapshotReducer questReducer = new QuestSnapshotReducer();
	private final FarmingPatchSnapshotReducer farmingPatchReducer = new FarmingPatchSnapshotReducer();
	private PairingClient pairingClient;
	private SnapshotClient snapshotClient;
	private SemanticSnapshotClient<BirdHouseSnapshot> birdHouseClient;
	private SemanticSnapshotClient<QuestSnapshot> questClient;
	private SemanticSnapshotClient<FarmingPatchSnapshot> farmingPatchClient;
	private SemanticSnapshotClient<AppearanceSnapshot> appearanceClient;
	private ConnectionStateStore connectionStateStore;
	private DropClient dropClient;
	private volatile Object storageRequest;
	private volatile boolean storageSettled;
	private volatile Filepath storageDirectory;
	private final DropCapture dropCapture = new DropCapture();
	private final DropCorrelator dropCorrelator = new DropCorrelator();
	private final DropGameContext dropGameContext = new DropGameContext();
	private volatile DropCaptureObserver dropCaptureObserver;
	private RuneGlassPanel panel;
	private NavigationButton navigationButton;
	private UUID syncSessionId;
	private volatile SkillsSnapshot latestSnapshot;
	private PairingClient.Credentials activeCredentials;
	private String activeProfileKey;
	private boolean appearanceDirty = true;
	private int pendingAppearanceCompositionHash = Integer.MIN_VALUE;
	private int stableAppearanceTicks;
	private volatile boolean appearanceEncoding;
	private String lastAppearanceModelHash;
	private long appearanceGeneration;

	@Override
	protected void startUp()
	{
		lootTrackerRunning = pluginManager.getPlugins().stream()
			.anyMatch(plugin -> plugin instanceof LootTrackerPlugin && pluginManager.isPluginActive(plugin));
		pairingClient = PairingClient.create(httpClient, gson, executor);
		snapshotClient = SnapshotClient.create(httpClient, gson, executor);
		questClient = SemanticSnapshotClient.create(httpClient, gson, executor, "runelite/v1/quests",
			(context, connectionId, snapshot) -> SemanticSnapshotPayload.create(context, connectionId,
				snapshot.getSnapshotId(), snapshot.getObservedAt(), "progress", snapshot.getProgress()));
		birdHouseClient = SemanticSnapshotClient.create(
			httpClient,
			gson,
			executor,
			"runelite/v1/bird-houses",
			(context, connectionId, snapshot) -> SemanticSnapshotPayload.create(
				context,
				connectionId,
				snapshot.getSnapshotId(),
				snapshot.getObservedAt(),
				"houses",
				snapshot.getHouses()));
		farmingPatchClient = SemanticSnapshotClient.create(
			httpClient,
			gson,
			executor,
			"runelite/v1/farming-patches",
			(context, connectionId, snapshot) -> SemanticSnapshotPayload.create(
				context,
				connectionId,
				snapshot.getSnapshotId(),
				snapshot.getObservedAt(),
				"patches",
				snapshot.getPatches()));
		appearanceClient = SemanticSnapshotClient.create(
			httpClient,
			gson,
			executor,
			"runelite/v1/appearance",
			AppearanceSnapshotPayload::create);
		panel = new RuneGlassPanel(
			() -> clientThread.invokeLater(this::startPairing),
			() -> clientThread.invokeLater(this::stopPairing),
			() -> clientThread.invokeLater(this::requestManualSync));
		panel.showDropCaptureAvailable(lootTrackerRunning);
		if (config.syncEnabled())
		{
			panel.showDropStatus("Drop sync enabled. Log in and pair this profile; Loot Tracker must be enabled.");
		}
		navigationButton = NavigationButton.builder()
			.tooltip("RuneGlass Sync")
			.icon(createNavigationIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		LOG.debug("RuneGlass started");
		requestStorage();
		refreshPairingPanel();
	}

	void requestStorage()
	{
		Object request = new Object();
		storageRequest = request;
		storageSettled = false;
		storageDirectory = null;
		try
		{
			executor.execute(() ->
			{
				Filepath directory = openStorageDirectory();
				clientThread.invokeLater(() -> storageOpened(request, directory));
			});
		}
		catch (RuntimeException exception)
		{
			LOG.debug("RuneGlass local storage could not be scheduled");
			clientThread.invokeLater(() -> storageOpened(request, null));
		}
	}

	Filepath openStorageDirectory()
	{
		return openStorageDirectory(this::getPluginDirectory);
	}

	static Filepath openStorageDirectory(Callable<Filepath> pluginDirectory)
	{
		for (int attempt = 0; attempt < 2; attempt++)
		{
			try
			{
				return pluginDirectory.call();
			}
			catch (Exception exception)
			{
				LOG.debug("RuneGlass local storage attempt {} failed", attempt + 1);
			}
		}
		return null;
	}

	private void storageOpened(Object request, Filepath directory)
	{
		if (request != storageRequest)
		{
			return;
		}
		storageDirectory = directory;
		storageSettled = true;
		if (directory != null)
		{
			dropClient = DropClient.create(httpClient, gson, executor, directory.joinSegment("drops-v1"), new DropClient.Listener()
			{
				@Override public void onStatus(String status)
				{
					RuneGlassPanel currentPanel = panel;
					if (currentPanel != null) currentPanel.showDropStatus(status);
					DropCaptureObserver observer = dropCaptureObserver;
					if (observer != null) observer.onDropStatus(status);
				}
				@Override public void onInvalidConnection(PairingClient.Credentials credentials, boolean bindingMismatch)
				{
					clientThread.invokeLater(() -> handleSnapshotFailure(credentials, bindingMismatch
						? SnapshotClient.Failure.BINDING_MISMATCH : SnapshotClient.Failure.INVALID_CONNECTION));
				}
			});
		}
		if (!config.syncEnabled())
		{
			if (dropClient != null)
			{
				dropClient.discardAll();
			}
		}
		else if (directory == null)
		{
			showDropStorageUnavailable();
		}
		if (config.syncEnabled() && client.getGameState() == GameState.LOGGED_IN)
		{
			startSession();
		}
		refreshPairingPanel();
	}

	private void showDropStorageUnavailable()
	{
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.showDropStatus("Drops paused: local storage could not be opened. No new drops are saved.");
		}
	}

	@Override
	protected void shutDown()
	{
		storageRequest = null;
		storageSettled = false;
		finishSession(SnapshotReason.LOGOUT_FLUSH);
		if (pairingClient != null)
		{
			pairingClient.cancel();
		}
		if (snapshotClient != null)
		{
			snapshotClient.closeAfterFlush();
		}
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		panel = null;
		navigationButton = null;
		pairingClient = null;
		snapshotClient = null;
		birdHouseClient = null;
		questClient = null;
		farmingPatchClient = null;
		appearanceClient = null;
		connectionStateStore = null;
		dropClient = null;
		storageDirectory = null;
		syncSessionId = null;
		latestSnapshot = null;
		activeCredentials = null;
		activeProfileKey = null;
		resetAppearanceCapture();
		LOG.debug("RuneGlass stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!config.syncEnabled())
		{
			return;
		}

		GameState gameState = event.getGameState();
		if (gameState == GameState.LOGGED_IN && !session.isActive())
		{
			startSession();
		}
		else if (gameState == GameState.HOPPING)
		{
			finishSession(SnapshotReason.PROFILE_SWITCH);
			pairingClient.cancelPending();
			snapshotClient.closeAfterFlush();
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			finishSession(SnapshotReason.LOGOUT_FLUSH);
			pairingClient.cancelPending();
			snapshotClient.closeAfterFlush();
		}
		refreshPairingPanel();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.syncEnabled() || !session.isActive() || baselineGate.isWaiting())
		{
			return;
		}

		session.accept(
			event.getSkill(),
			event.getXp(),
			event.getLevel(),
			event.getBoostedLevel(),
			Instant.now())
			.ifPresent(this::publishLocally);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.syncEnabled() || !storageSettled)
		{
			return;
		}
		if (needsProfileRestart(configManager.getRSProfileKey()))
		{
			finishSession(SnapshotReason.PROFILE_SWITCH);
			startSession();
		}

		if (baselineGate.onGameTick())
		{
			connectRestoredSnapshot();
			captureCurrentClientState();
		}

		advanceDropEvidence();
		session.poll(Instant.now()).ifPresent(this::publishLocally);
		captureQuests(false);
		captureBirdHouses();
		captureFarmingPatch();
		captureAppearanceWhenStable();
	}

	boolean needsProfileRestart(String currentProfileKey)
	{
		return !Objects.equals(activeProfileKey, currentProfileKey);
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		if (event.getPlayer() == client.getLocalPlayer())
		{
			appearanceGeneration++;
			appearanceDirty = true;
			stableAppearanceTicks = 0;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (canCaptureDropCounts()) updateDropContext();
		if (config.syncEnabled() && session.isActive())
		{
			questReducer.markDirty();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneGlassConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if ("syncEnabled".equals(key) && !config.syncEnabled())
		{
			stopDropChat();
			if (dropClient != null)
			{
				dropClient.discardAll();
			}
		}
		clientThread.invokeLater(() -> handleConfigChanged(key));
	}

	private void handleConfigChanged(String key)
	{
		if (!"syncEnabled".equals(key))
		{
			return;
		}

		if (!config.syncEnabled())
		{
			clearDropEvidence();
			if (dropClient != null) dropClient.discardAll();
			session.cancel();
			discardQuestCapture();
			baselineGate.cancel();
			pairingClient.cancel();
			snapshotClient.discard();
			if (birdHouseClient != null)
			{
				birdHouseClient.discard();
			}
			if (farmingPatchClient != null)
			{
				farmingPatchClient.discard();
			}
			if (appearanceClient != null)
			{
				appearanceClient.discard();
			}
			birdHouseReducer.reset();
			farmingPatchReducer.reset();
			resetAppearanceCapture();
			clearStoredConnection();
			syncSessionId = null;
			latestSnapshot = null;
			refreshPairingPanel();
			return;
		}

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			startSession();
		}
		refreshPairingPanel();
	}

	@Provides
	RuneGlassConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneGlassConfig.class);
	}

	private void startSession()
	{
		if (!storageSettled)
		{
			return;
		}
		flushDropEvidence();
		if (dropClient != null) dropClient.pause();
		discardQuestCapture();
		session.start();
		baselineGate.arm();
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
		resetAppearanceCapture();
		syncSessionId = UUID.randomUUID();
		latestSnapshot = null;
		activeCredentials = null;
		if (snapshotClient != null)
		{
			snapshotClient.cancel();
		}
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		activeProfileKey = configManager.getRSProfileKey();
		connectionStateStore = null;
		Filepath directory = storageDirectory;
		if (activeProfileKey != null && directory != null)
		{
			configManager.unsetRSProfileConfiguration(
				RuneGlassConfig.GROUP,
				ConnectionStateStore.LEGACY_CONFIG_KEY);
			connectionStateStore = new ConnectionStateStore(
				directory.joinSegment("profiles"),
				activeProfileKey,
				gson);
			ConnectionStateStore store = connectionStateStore;
			Optional<ConnectionStateStore.State> restored = store.load();
			if (restored.isPresent())
			{
				activeCredentials = restored.get().getCredentials();
			}
		}
	}

	private void connectRestoredSnapshot()
	{
		ConnectionStateStore store = connectionStateStore;
		PairingClient.Credentials credentials = activeCredentials;
		if (store == null || credentials == null)
		{
			return;
		}
		Optional<ConnectionStateStore.State> restored = store.load();
		if (!restored.isPresent()
			|| !credentials.sameConnection(restored.get().getCredentials()))
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.PROTOCOL_ERROR);
			return;
		}
		connectSnapshot(credentials, restored.get().getNextSequence());
	}

	private void captureCurrentClientState()
	{
		Instant observedAt = Instant.now();
		for (Skill skill : SkillCatalog.trainableSkills())
		{
			session.accept(
				skill,
				client.getSkillExperience(skill),
				client.getRealSkillLevel(skill),
				client.getBoostedSkillLevel(skill),
				observedAt)
				.ifPresent(this::publishLocally);
		}
	}

	private void captureBirdHouses()
	{
		SemanticSnapshotClient<BirdHouseSnapshot> currentClient = birdHouseClient;
		Player localPlayer = client.getLocalPlayer();
		if (!config.syncEnabled()
			|| baselineGate.isWaiting()
			|| activeCredentials == null
			|| currentClient == null
			|| localPlayer == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		WorldPoint location = localPlayer.getWorldLocation();
		BirdHouseSpace[] spaces = BirdHouseSpace.values();
		int[] varps = new int[spaces.length];
		for (int index = 0; index < spaces.length; index++)
		{
			varps[index] = client.getVarpValue(spaces[index].getVarpId());
		}
		birdHouseReducer.observe(
			location.getRegionID(),
			location.getPlane(),
			varps,
			Instant.now())
			.ifPresent(snapshot ->
			{
				if (currentClient.publish(snapshot))
				{
					LOG.debug("Captured four semantic bird house observations");
				}
			});
	}

	private void discardQuestCapture()
	{
		questReducer.reset();
		if (questClient != null) questClient.discard();
	}

	private void captureQuests(boolean manual)
	{
		SemanticSnapshotClient<QuestSnapshot> currentClient = questClient;
		if (currentClient == null || !currentClient.isConnected() || !config.syncEnabled()
			|| !session.isActive() || baselineGate.isWaiting() || activeCredentials == null
			|| client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
			|| (!manual && !questReducer.shouldCapture())) return;
		Map<Integer, String> states = QuestSnapshotReader.readStates(client);
		if (states.isEmpty()) { questReducer.markDirty(); return; }
		try
		{
			Instant observedAt = Instant.now();
			int questPoints = QuestSnapshotReader.readQuestPoints(client);
			if (manual)
			{
				currentClient.publish(questReducer.observeManual(observedAt, questPoints, states));
			}
			else
			{
				questReducer.observe(observedAt, questPoints, states).ifPresent(currentClient::publish);
			}
		}
		catch (IllegalArgumentException exception)
		{
			questReducer.markDirty();
		}
	}

	private void connectQuestTransport(PairingClient.Credentials credentials, SyncContext context)
	{
		if (!config.syncEnabled() || questClient == null
			|| activeCredentials == null || !activeCredentials.sameConnection(credentials)) return;
		questReducer.reset();
		questClient.connect(credentials, context, new SemanticSnapshotClient.Listener()
		{
			@Override public void onAccepted(Instant serverTime)
			{
				if (panel != null) panel.showSynced(serverTime);
			}
			@Override public void onRetryScheduled() { LOG.debug("Quest sync retry scheduled"); }
			@Override public void onFailure(SemanticSnapshotClient.Failure failure)
			{
				clientThread.invokeLater(() ->
				{
					if (activeCredentials == null || !activeCredentials.sameConnection(credentials)) return;
					if (failure == SemanticSnapshotClient.Failure.INVALID_CONNECTION)
						handleSnapshotFailure(credentials, SnapshotClient.Failure.INVALID_CONNECTION);
					else if (failure == SemanticSnapshotClient.Failure.BINDING_MISMATCH)
						handleSnapshotFailure(credentials, SnapshotClient.Failure.BINDING_MISMATCH);
					else { discardQuestCapture(); if (panel != null) panel.showQuestPaused(); }
				});
			}
		});
	}

	private void captureFarmingPatch()
	{
		SemanticSnapshotClient<FarmingPatchSnapshot> currentClient = farmingPatchClient;
		Player localPlayer = client.getLocalPlayer();
		if (!config.syncEnabled()
			|| baselineGate.isWaiting()
			|| activeCredentials == null
			|| currentClient == null
			|| localPlayer == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		WorldPoint worldPoint = localPlayer.getWorldLocation();
		Instant observedAt = Instant.now();
		List<FarmingPatchObservation> patches = new ArrayList<>();
		for (FarmingPatchLocation location : FarmingPatchLocation.forWorldPoint(worldPoint))
		{
			farmingPatchReducer.observe(
				location,
				client.getVarbitValue(location.getVarbitId()),
				observedAt)
				.ifPresent(patches::add);
		}
		if (patches.isEmpty())
		{
			return;
		}
		FarmingPatchSnapshot snapshot = new FarmingPatchSnapshot(observedAt, patches);
		if (currentClient.publish(snapshot))
		{
			LOG.debug("Captured {} semantic farming patch observations", patches.size());
		}
	}

	private void connectBirdHouseTransport(
		PairingClient.Credentials credentials,
		SyncContext context)
	{
		SemanticSnapshotClient<BirdHouseSnapshot> currentClient = birdHouseClient;
		if (currentClient == null
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		currentClient.connect(credentials, context, new SemanticSnapshotClient.Listener()
		{
			@Override
			public void onAccepted(Instant serverTime)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showSynced(serverTime);
				}
			}

			@Override
			public void onRetryScheduled()
			{
				LOG.debug("Bird house sync retry scheduled");
			}

			@Override
			public void onFailure(SemanticSnapshotClient.Failure failure)
			{
				clientThread.invokeLater(() -> handleBirdHouseFailure(credentials, failure));
			}
		});
	}

	private void handleBirdHouseFailure(
		PairingClient.Credentials credentials,
		SemanticSnapshotClient.Failure failure)
	{
		if (activeCredentials == null || !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.INVALID_CONNECTION)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.INVALID_CONNECTION);
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.BINDING_MISMATCH)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.BINDING_MISMATCH);
			return;
		}
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null)
		{
			if (failure == SemanticSnapshotClient.Failure.UNSUPPORTED_PROFILE)
			{
				currentPanel.showSnapshotFailure(SnapshotClient.Failure.UNSUPPORTED_PROFILE);
			}
			else
			{
				currentPanel.showBirdHousePaused();
			}
		}
	}

	private void connectFarmingPatchTransport(
		PairingClient.Credentials credentials,
		SyncContext context)
	{
		SemanticSnapshotClient<FarmingPatchSnapshot> currentClient = farmingPatchClient;
		if (currentClient == null
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		currentClient.connect(credentials, context, new SemanticSnapshotClient.Listener()
		{
			@Override
			public void onAccepted(Instant serverTime)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showSynced(serverTime);
				}
			}

			@Override
			public void onRetryScheduled()
			{
				LOG.debug("Farming patch sync retry scheduled");
			}

			@Override
			public void onFailure(SemanticSnapshotClient.Failure failure)
			{
				clientThread.invokeLater(() -> handleFarmingPatchFailure(credentials, failure));
			}
		});
	}

	private void handleFarmingPatchFailure(
		PairingClient.Credentials credentials,
		SemanticSnapshotClient.Failure failure)
	{
		if (activeCredentials == null || !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.INVALID_CONNECTION)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.INVALID_CONNECTION);
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.BINDING_MISMATCH)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.BINDING_MISMATCH);
			return;
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null)
		{
			if (failure == SemanticSnapshotClient.Failure.UNSUPPORTED_PROFILE)
			{
				currentPanel.showSnapshotFailure(SnapshotClient.Failure.UNSUPPORTED_PROFILE);
			}
			else
			{
				currentPanel.showFarmingPatchPaused();
			}
		}
	}

	private void captureAppearanceWhenStable()
	{
		Player localPlayer = client.getLocalPlayer();
		SemanticSnapshotClient<AppearanceSnapshot> currentClient = appearanceClient;
		if (!appearanceDirty
			|| appearanceEncoding
			|| !config.syncEnabled()
			|| baselineGate.isWaiting()
			|| activeCredentials == null
			|| currentClient == null
			|| localPlayer == null
			|| client.getGameState() != GameState.LOGGED_IN
			|| localPlayer.getAnimation() != -1
			|| localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation())
		{
			return;
		}

		PlayerComposition composition = localPlayer.getPlayerComposition();
		Model model = localPlayer.getModel();
		if (composition == null || model == null || composition.getTransformedNpcId() != -1)
		{
			return;
		}
		int compositionHash = 31 * Arrays.hashCode(composition.getEquipmentIds())
			+ Arrays.hashCode(composition.getColors());
		compositionHash = 31 * compositionHash + composition.getGender();
		final int stableCompositionHash = compositionHash;
		if (stableCompositionHash != pendingAppearanceCompositionHash)
		{
			pendingAppearanceCompositionHash = stableCompositionHash;
			stableAppearanceTicks = 1;
			return;
		}
		stableAppearanceTicks++;
		if (stableAppearanceTicks < 2)
		{
			return;
		}

		final AppearanceMeshEncoder.CapturedAppearance captured;
		try
		{
			captured = AppearanceMeshEncoder.capture(model, composition);
		}
		catch (RuntimeException exception)
		{
			LOG.debug("Skipped unsupported character appearance", exception);
			appearanceDirty = false;
			return;
		}
		final Instant observedAt = Instant.now();
		final PairingClient.Credentials credentials = activeCredentials;
		final long generation = appearanceGeneration;
		appearanceEncoding = true;
		try
		{
			executor.execute(() ->
			{
				final AppearanceMeshEncoder.EncodedMesh encoded;
				try
				{
					encoded = captured.encode();
				}
				catch (RuntimeException exception)
				{
					clientThread.invokeLater(() ->
					{
						appearanceEncoding = false;
						if (generation != appearanceGeneration)
						{
							return;
						}
						appearanceDirty = false;
						LOG.debug("Could not encode character appearance", exception);
					});
					return;
				}
				clientThread.invokeLater(() -> publishAppearance(
					credentials,
					generation,
					stableCompositionHash,
					observedAt,
					encoded));
			});
		}
		catch (RuntimeException exception)
		{
			appearanceEncoding = false;
			LOG.debug("Could not schedule character appearance encoding", exception);
		}
	}

	private void publishAppearance(
		PairingClient.Credentials expectedCredentials,
		long expectedGeneration,
		int compositionHash,
		Instant observedAt,
		AppearanceMeshEncoder.EncodedMesh encoded)
	{
		appearanceEncoding = false;
		if (expectedGeneration != appearanceGeneration
			|| !config.syncEnabled()
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(expectedCredentials)
			|| appearanceClient == null)
		{
			return;
		}
		if (encoded.getModelHash().equals(lastAppearanceModelHash))
		{
			appearanceDirty = false;
			return;
		}
		if (appearanceClient.publish(new AppearanceSnapshot(observedAt, encoded)))
		{
			lastAppearanceModelHash = encoded.getModelHash();
			pendingAppearanceCompositionHash = compositionHash;
			appearanceDirty = false;
			LOG.debug(
				"Captured character appearance with {} vertices and {} faces",
				encoded.getVertexCount(),
				encoded.getFaceCount());
		}
	}

	private void connectAppearanceTransport(
		PairingClient.Credentials credentials,
		SyncContext context)
	{
		SemanticSnapshotClient<AppearanceSnapshot> currentClient = appearanceClient;
		if (currentClient == null
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		currentClient.connect(credentials, context, new SemanticSnapshotClient.Listener()
		{
			@Override
			public void onAccepted(Instant serverTime)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showSynced(serverTime);
				}
			}

			@Override
			public void onRetryScheduled()
			{
				LOG.debug("Character appearance retry scheduled");
			}

			@Override
			public void onFailure(SemanticSnapshotClient.Failure failure)
			{
				clientThread.invokeLater(() -> handleAppearanceFailure(credentials, failure));
			}
		});
	}

	private void handleAppearanceFailure(
		PairingClient.Credentials credentials,
		SemanticSnapshotClient.Failure failure)
	{
		if (activeCredentials == null || !activeCredentials.sameConnection(credentials))
		{
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.INVALID_CONNECTION)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.INVALID_CONNECTION);
			return;
		}
		if (failure == SemanticSnapshotClient.Failure.BINDING_MISMATCH)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.BINDING_MISMATCH);
			return;
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		appearanceDirty = true;
		LOG.debug("Character appearance sync paused: {}", failure);
	}

	private void resetAppearanceCapture()
	{
		appearanceGeneration++;
		appearanceDirty = true;
		pendingAppearanceCompositionHash = Integer.MIN_VALUE;
		stableAppearanceTicks = 0;
		appearanceEncoding = false;
		lastAppearanceModelHash = null;
	}

	private void finishSession(SnapshotReason reason)
	{
		flushDropEvidence();
		if (dropClient != null) dropClient.pause();
		discardQuestCapture();
		Optional<SkillsSnapshot> finalSnapshot = session.stop(Instant.now(), reason);
		if (finalSnapshot.isPresent())
		{
			publishFinalLocally(finalSnapshot.get());
		}
		else if (snapshotClient != null)
		{
			snapshotClient.finishSession();
		}
		baselineGate.cancel();
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		resetAppearanceCapture();
		syncSessionId = null;
		latestSnapshot = null;
	}

	private void publishLocally(SkillsSnapshot snapshot)
	{
		latestSnapshot = snapshot;
		if (snapshotClient != null)
		{
			snapshotClient.publish(snapshot);
		}
		LOG.debug("Captured {} catalog {} snapshot", snapshot.getReason(), snapshot.getCatalogVersion());
	}

	private void publishFinalLocally(SkillsSnapshot snapshot)
	{
		latestSnapshot = snapshot;
		if (snapshotClient != null)
		{
			snapshotClient.finishSession(snapshot);
		}
		LOG.debug("Captured {} catalog {} final snapshot", snapshot.getReason(), snapshot.getCatalogVersion());
	}

	private void publishManualLocally(SkillsSnapshot snapshot)
	{
		latestSnapshot = snapshot;
		if (snapshotClient != null)
		{
			snapshotClient.publishImmediately(snapshot);
		}
		LOG.debug("Captured manual catalog {} snapshot", snapshot.getCatalogVersion());
	}

	private void requestManualSync()
	{
		if (!config.syncEnabled()
			|| !session.isActive()
			|| baselineGate.isWaiting()
			|| client.getGameState() != GameState.LOGGED_IN
			|| activeCredentials == null
			|| snapshotClient == null
			|| snapshotClient.isFinishingSession()
			|| needsProfileRestart(configManager.getRSProfileKey()))
		{
			refreshPairingPanel();
			return;
		}

		captureCurrentClientState();
		captureQuests(true);
		captureBirdHouses();
		captureFarmingPatch();
		appearanceDirty = true;
		captureAppearanceWhenStable();
		session.manualSync(Instant.now()).ifPresent(this::publishManualLocally);
	}

	private void startPairing()
	{
		PairingClient currentClient = pairingClient;
		RuneGlassPanel currentPanel = panel;
		if (currentClient == null
			|| currentPanel == null
			|| !config.syncEnabled()
			|| client.getGameState() != GameState.LOGGED_IN
			|| !storageSettled)
		{
			refreshPairingPanel();
			return;
		}
		if (connectionStateStore == null)
		{
			currentPanel.showFailure(PairingClient.Failure.TEMPORARILY_UNAVAILABLE);
			return;
		}

		currentPanel.showStarting();
		currentClient.start(new PairingClient.Listener()
		{
			@Override
			public void onCode(String userCode, Instant expiresAt)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showCode(userCode, expiresAt);
				}
			}

			@Override
			public void onConnected()
			{
				clientThread.invokeLater(() ->
				{
					Optional<PairingClient.Credentials> credentials = currentClient.getCredentials();
					if (!credentials.isPresent() || connectionStateStore == null)
					{
						handleSnapshotFailure(SnapshotClient.Failure.REJECTED_BATCH);
						return;
					}
					activeCredentials = credentials.get();
					activeProfileKey = configManager.getRSProfileKey();
					if (!connectionStateStore.save(activeCredentials, BigInteger.ONE))
					{
						handleSnapshotFailure(SnapshotClient.Failure.PROTOCOL_ERROR);
						return;
					}
					connectSnapshot(activeCredentials, BigInteger.ONE);
				});
			}

			@Override
			public void onFailure(PairingClient.Failure failure)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showFailure(failure);
				}
			}
		});
	}

	private void stopPairing()
	{
		clearDropEvidence();
		if (dropClient != null && activeCredentials != null) dropClient.discardConnection(activeCredentials.getConnectionId());
		discardQuestCapture();
		if (pairingClient != null)
		{
			pairingClient.cancel();
		}
		if (snapshotClient != null)
		{
			snapshotClient.discard();
		}
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
		resetAppearanceCapture();
		clearStoredConnection();
		refreshPairingPanel();
	}

	private void connectSnapshot(
		PairingClient.Credentials credentials,
		BigInteger nextSequence)
	{
		SnapshotClient currentSnapshotClient = snapshotClient;
		RuneGlassPanel currentPanel = panel;
		UUID sessionId = syncSessionId;
		Filepath directory = storageDirectory;
		if (currentSnapshotClient == null
			|| currentPanel == null
			|| sessionId == null
			|| directory == null
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(credentials)
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			refreshPairingPanel();
			return;
		}
		final SyncContext context;
		try
		{
			context = SyncContext.capture(client, sessionId);
		}
		catch (RuntimeException exception)
		{
			currentSnapshotClient.cancel();
			handleSnapshotFailure(credentials, SnapshotClient.Failure.REJECTED_BATCH);
			return;
		}

		Filepath queueDirectory = directory.joinSegment(credentials.getConnectionId());
		connectBirdHouseTransport(credentials, context);
		connectQuestTransport(credentials, context);
		connectFarmingPatchTransport(credentials, context);
		connectAppearanceTransport(credentials, context);
		if (dropClient != null
			&& RuneScapeProfileType.getCurrent(client) == RuneScapeProfileType.STANDARD)
		{
			dropClient.connect(credentials, context, activeProfileKey);
			updateDropChatSubscription();
		}
		try
		{
			executor.execute(() ->
			{
				final DurableSnapshotQueue queue;
				try
				{
					queue = new DurableSnapshotQueue(queueDirectory, gson, java.time.Clock.systemUTC());
				}
				catch (java.io.IOException exception)
				{
					clientThread.invokeLater(() ->
						handleSnapshotFailure(credentials, SnapshotClient.Failure.PROTOCOL_ERROR));
					return;
				}
				boolean connected = currentSnapshotClient.connect(
					credentials,
					context,
					nextSequence,
					queue,
					new SnapshotClient.Listener()
					{
						@Override
						public void onUploading(int recordCount)
						{
							RuneGlassPanel activePanel = panel;
							if (activePanel != null)
							{
								activePanel.showUploading(recordCount);
							}
						}

						@Override
						public void onAccepted(Instant serverTime)
						{
							RuneGlassPanel activePanel = panel;
							if (activePanel != null)
							{
								activePanel.showSynced(serverTime);
							}
						}

						@Override
						public void onRetryScheduled()
						{
							RuneGlassPanel activePanel = panel;
							if (activePanel != null)
							{
								activePanel.showRetrying();
							}
						}

						@Override
						public void onFailure(SnapshotClient.Failure failure)
						{
							clientThread.invokeLater(() -> handleSnapshotFailure(credentials, failure));
						}

						@Override
						public void onNextSequenceChanged(BigInteger changedNextSequence)
						{
							clientThread.invokeLater(() ->
								saveNextSequence(credentials, changedNextSequence));
						}

						@Override
						public void onSessionDrained()
						{
							clientThread.invokeLater(RuneGlassPlugin.this::reconnectAfterSessionDrain);
						}
					}
				);
				if (!connected)
				{
					clientThread.invokeLater(() ->
						handleSnapshotFailure(credentials, SnapshotClient.Failure.PROTOCOL_ERROR));
					return;
				}
				clientThread.invokeLater(() ->
				{
					RuneGlassPanel activePanel = panel;
					if (activePanel != null
						&& activeCredentials != null
						&& activeCredentials.sameConnection(credentials))
					{
						activePanel.showConnected();
					}
				});

				SkillsSnapshot snapshot = latestSnapshot;
				if (snapshot != null)
				{
					currentSnapshotClient.publish(snapshot);
				}
			});
		}
		catch (RuntimeException exception)
		{
			handleSnapshotFailure(credentials, SnapshotClient.Failure.PROTOCOL_ERROR);
		}
	}

	private void reconnectAfterSessionDrain()
	{
		if (config.syncEnabled()
			&& session.isActive()
			&& client.getGameState() == GameState.LOGGED_IN
			&& connectionStateStore != null)
		{
			Optional<ConnectionStateStore.State> restored = connectionStateStore.load();
			if (restored.isPresent())
			{
				activeCredentials = restored.get().getCredentials();
				activeProfileKey = configManager.getRSProfileKey();
				connectSnapshot(activeCredentials, restored.get().getNextSequence());
				return;
			}
		}
		refreshPairingPanel();
	}

	private void handleSnapshotFailure(SnapshotClient.Failure failure)
	{
		PairingClient.Credentials credentials = activeCredentials;
		if (credentials != null)
		{
			handleSnapshotFailure(credentials, failure);
		}
	}

	private void handleSnapshotFailure(
		PairingClient.Credentials expectedCredentials,
		SnapshotClient.Failure failure)
	{
		if (activeCredentials == null || !activeCredentials.sameConnection(expectedCredentials))
		{
			return;
		}
		if (dropClient != null)
		{
			clearDropEvidence();
			if (failure == SnapshotClient.Failure.INVALID_CONNECTION) dropClient.discardConnection(expectedCredentials.getConnectionId());
			else dropClient.pause();
		}
		discardQuestCapture();
		if (pairingClient != null)
		{
			pairingClient.cancel();
		}
		if (snapshotClient != null)
		{
			snapshotClient.discard();
		}
		if (birdHouseClient != null)
		{
			birdHouseClient.discard();
		}
		if (farmingPatchClient != null)
		{
			farmingPatchClient.discard();
		}
		if (appearanceClient != null)
		{
			appearanceClient.discard();
		}
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
		resetAppearanceCapture();
		clearStoredConnection();
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.showSnapshotFailure(failure);
		}
	}

	private void saveNextSequence(
		PairingClient.Credentials expectedCredentials,
		BigInteger nextSequence)
	{
		ConnectionStateStore store = connectionStateStore;
		if (store == null
			|| activeCredentials == null
			|| !activeCredentials.sameConnection(expectedCredentials)
			|| activeProfileKey == null
			|| !activeProfileKey.equals(configManager.getRSProfileKey()))
		{
			return;
		}
		if (!store.save(expectedCredentials, nextSequence))
		{
			handleSnapshotFailure(expectedCredentials, SnapshotClient.Failure.PROTOCOL_ERROR);
		}
	}

	private void clearStoredConnection()
	{
		ConnectionStateStore store = connectionStateStore;
		if (store != null
			&& activeProfileKey != null
			&& activeProfileKey.equals(configManager.getRSProfileKey()))
		{
			store.clear();
		}
		activeCredentials = null;
		activeProfileKey = null;
	}

	private void refreshPairingPanel()
	{
		RuneGlassPanel currentPanel = panel;
		PairingClient currentClient = pairingClient;
		if (currentPanel == null || currentClient == null)
		{
			return;
		}
		if (!config.syncEnabled())
		{
			currentPanel.showSyncDisabled();
		}
		else if (client.getGameState() != GameState.LOGGED_IN)
		{
			currentPanel.showLoggedOut();
		}
		else if (activeCredentials != null)
		{
			currentPanel.showConnected();
		}
		else
		{
			currentPanel.showReady();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (!(event.getPlugin() instanceof LootTrackerPlugin)) return;
		if (!event.isLoaded()) flushDropEvidence();
		lootTrackerRunning = event.isLoaded();
		updateDropChatSubscription();
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null) currentPanel.showDropCaptureAvailable(lootTrackerRunning);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		DropCaptureResult result = handleLoot(event);
		DropCaptureObserver observer = dropCaptureObserver;
		if (observer != null) observer.onLootHandled(result);
	}

	DropCaptureResult handleLoot(LootReceived event)
	{
		DropClient currentClient = dropClient;
		DropCaptureResult.Reason blocked = dropCaptureBlocker(currentClient);
		if (blocked != null) return DropCaptureResult.blocked(blocked);
		try
		{
			DropCaptureResult result = dropCapture.capture(event, itemManager, syncSessionId, Instant.now());
			String encounter = updateDropContext();
			dropCorrelator.loot(result, client.getTickCount(), System.nanoTime(), encounter, this::publishCorrelatedDrop);
			return result;
		}
		catch (RuntimeException exception)
		{
			flushDropEvidence();
			currentClient.captureFailed();
			return DropCaptureResult.invalid();
		}
	}

	void onChatMessage(ChatMessage event)
	{
		if (!canCaptureDropCounts()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.MESBOX) return;
		String encounter = updateDropContext();
		long tick = client.getTickCount();
		long nanos = System.nanoTime();
		dropCorrelator.count(DropCountParser.parse(event.getMessage()), tick, nanos, encounter);
		dropCorrelator.collection(DropCountParser.collectionItem(event.getMessage()), tick, nanos);
	}

	private final class DropChatListener
	{
		@Subscribe
		public void onChatMessage(ChatMessage event)
		{
			RuneGlassPlugin.this.onChatMessage(event);
		}
	}

	private boolean canCaptureDropCounts()
	{
		return dropClient != null && dropCaptureBlocker(dropClient) == null;
	}

	private synchronized void updateDropChatSubscription()
	{
		if (!canCaptureDropCounts())
		{
			stopDropChat();
			return;
		}
		if (!dropChatSubscribed)
		{
			eventBus.register(dropChatListener);
			dropChatSubscribed = true;
		}
	}

	private synchronized void stopDropChat()
	{
		if (dropChatSubscribed)
		{
			eventBus.unregister(dropChatListener);
			dropChatSubscribed = false;
		}
	}

	private String updateDropContext()
	{
		String encounter = dropGameContext.raidEncounter(client);
		dropCorrelator.context(client.getTickCount(), DropGameContext.collectionLogSetting(client), encounter);
		return encounter;
	}

	void advanceDropEvidence()
	{
		updateDropChatSubscription();
		if (!canCaptureDropCounts()) return;
		updateDropContext();
		dropCorrelator.advance(client.getTickCount(), System.nanoTime(), this::publishCorrelatedDrop);
	}

	private void publishCorrelatedDrop(DropCorrelator.Completed completed)
	{
		if (dropClient == null || !config.syncEnabled()) return;
		dropClient.observe(completed);
	}

	private void flushDropEvidence()
	{
		stopDropChat();
		dropCorrelator.flush(this::publishCorrelatedDrop);
		dropGameContext.clear();
	}

	private void clearDropEvidence()
	{
		stopDropChat();
		dropCorrelator.clear();
		dropGameContext.clear();
	}

	private DropCaptureResult.Reason dropCaptureBlocker(DropClient currentClient)
	{
		if (currentClient == null) return DropCaptureResult.Reason.PLUGIN_STOPPED;
		if (!config.syncEnabled()) return DropCaptureResult.Reason.SYNC_OFF;
		if (!lootTrackerRunning) return DropCaptureResult.Reason.LOOT_TRACKER_OFF;
		if (client.getGameState() != GameState.LOGGED_IN) return DropCaptureResult.Reason.LOGGED_OUT;
		if (!session.isActive() || syncSessionId == null) return DropCaptureResult.Reason.SESSION_INACTIVE;
		if (baselineGate.isWaiting()) return DropCaptureResult.Reason.BASELINE_PENDING;
		if (needsProfileRestart(configManager.getRSProfileKey())) return DropCaptureResult.Reason.PROFILE_CHANGED;
		if (activeCredentials == null) return DropCaptureResult.Reason.NOT_PAIRED;
		if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
		{
			return DropCaptureResult.Reason.UNSUPPORTED_PROFILE;
		}
		if (!currentClient.isConnected()) return DropCaptureResult.Reason.TRANSPORT_NOT_READY;
		return null;
	}

	void setDropCaptureObserver(DropCaptureObserver observer)
	{
		dropCaptureObserver = observer;
	}

	private void connectDropTransport()
	{
		if (dropClient == null || !config.syncEnabled()
			|| activeCredentials == null || syncSessionId == null || baselineGate.isWaiting()
			|| RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD
			|| client.getGameState() != GameState.LOGGED_IN
			|| needsProfileRestart(configManager.getRSProfileKey())) return;
		try
		{
			dropClient.connect(activeCredentials, SyncContext.capture(client, syncSessionId), activeProfileKey);
			updateDropChatSubscription();
		}
		catch (RuntimeException exception)
		{
			dropClient.pause();
			if (panel != null) panel.showDropStatus("Drops paused: this profile could not be validated.");
		}
	}

	private static BufferedImage createNavigationIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(212, 175, 55));
			graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawOval(2, 2, 9, 9);
			graphics.drawLine(10, 10, 14, 14);
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}
}
