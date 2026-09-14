package com.runeglass.runelite;

import com.google.inject.Provides;
import com.google.gson.Gson;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "RuneGlass",
	description = "Synchronizes your current character's skills, XP, and opted-in timers with RuneGlass",
	tags = {"mobile", "progress", "skills", "xp", "timers", "sync"}
)
public class RuneGlassPlugin extends Plugin
{
	private static final Logger LOG = LoggerFactory.getLogger(RuneGlassPlugin.class);

	@Inject
	private Client client;

	@Inject
	private RuneGlassConfig config;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	private final SkillsSyncSession session = new SkillsSyncSession();
	private final LoginBaselineGate baselineGate = new LoginBaselineGate();
	private final BirdHouseSnapshotReducer birdHouseReducer = new BirdHouseSnapshotReducer();
	private final FarmingPatchSnapshotReducer farmingPatchReducer = new FarmingPatchSnapshotReducer();
	private PairingClient pairingClient;
	private SnapshotClient snapshotClient;
	private SemanticSnapshotClient<BirdHouseSnapshot> birdHouseClient;
	private SemanticSnapshotClient<FarmingPatchSnapshot> farmingPatchClient;
	private ConnectionStateStore connectionStateStore;
	private RuneGlassPanel panel;
	private NavigationButton navigationButton;
	private UUID syncSessionId;
	private volatile SkillsSnapshot latestSnapshot;
	private PairingClient.Credentials activeCredentials;
	private String activeProfileKey;

	@Override
	protected void startUp()
	{
		pairingClient = PairingClient.create(httpClient, gson, executor);
		snapshotClient = SnapshotClient.create(httpClient, gson, executor);
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
		panel = new RuneGlassPanel(
			() -> clientThread.invokeLater(this::startPairing),
			() -> clientThread.invokeLater(this::stopPairing),
			() -> clientThread.invokeLater(this::requestManualSync));
		navigationButton = NavigationButton.builder()
			.tooltip("RuneGlass Sync")
			.icon(createNavigationIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		LOG.debug("RuneGlass started");
		if (config.syncEnabled() && client.getGameState() == GameState.LOGGED_IN)
		{
			startSession();
		}
		refreshPairingPanel();
	}

	@Override
	protected void shutDown()
	{
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
		farmingPatchClient = null;
		connectionStateStore = null;
		syncSessionId = null;
		latestSnapshot = null;
		activeCredentials = null;
		activeProfileKey = null;
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
		if (!config.syncEnabled())
		{
			return;
		}

		if (baselineGate.onGameTick())
		{
			connectRestoredSnapshot();
			captureCurrentClientState();
		}

		session.poll(Instant.now()).ifPresent(this::publishLocally);
		captureBirdHouses();
		captureFarmingPatch();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneGlassConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		clientThread.invokeLater(() -> handleConfigChanged(key));
	}

	private void handleConfigChanged(String key)
	{
		if ("birdHouseSyncEnabled".equals(key))
		{
			birdHouseReducer.reset();
			if (birdHouseClient != null)
			{
				birdHouseClient.discard();
			}
			if (config.syncEnabled()
				&& config.birdHouseSyncEnabled()
				&& client.getGameState() == GameState.LOGGED_IN)
			{
				connectBirdHouseTransport();
				captureBirdHouses();
			}
			return;
		}
		if ("farmingPatchSyncEnabled".equals(key))
		{
			farmingPatchReducer.reset();
			if (farmingPatchClient != null)
			{
				farmingPatchClient.discard();
			}
			if (config.syncEnabled()
				&& config.farmingPatchSyncEnabled()
				&& client.getGameState() == GameState.LOGGED_IN)
			{
				connectFarmingPatchTransport();
				captureFarmingPatch();
			}
			return;
		}
		if (!"syncEnabled".equals(key))
		{
			return;
		}

		if (!config.syncEnabled())
		{
			session.cancel();
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
			birdHouseReducer.reset();
			farmingPatchReducer.reset();
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
		session.start();
		baselineGate.arm();
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
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
		activeProfileKey = configManager.getRSProfileKey();
		connectionStateStore = null;
		if (activeProfileKey != null)
		{
			configManager.unsetRSProfileConfiguration(
				RuneGlassConfig.GROUP,
				ConnectionStateStore.LEGACY_CONFIG_KEY);
			connectionStateStore = new ConnectionStateStore(
				RuneLite.RUNELITE_DIR.toPath().resolve("runeglass").resolve("profiles"),
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
			|| !config.birdHouseSyncEnabled()
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

	private void captureFarmingPatch()
	{
		SemanticSnapshotClient<FarmingPatchSnapshot> currentClient = farmingPatchClient;
		Player localPlayer = client.getLocalPlayer();
		if (!config.syncEnabled()
			|| !config.farmingPatchSyncEnabled()
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

	private void connectBirdHouseTransport()
	{
		PairingClient.Credentials credentials = activeCredentials;
		UUID sessionId = syncSessionId;
		if (credentials == null || sessionId == null)
		{
			return;
		}
		try
		{
			connectBirdHouseTransport(credentials, SyncContext.capture(client, sessionId));
		}
		catch (RuntimeException exception)
		{
			handleBirdHouseFailure(credentials, SemanticSnapshotClient.Failure.REJECTED_SNAPSHOT);
		}
	}

	private void connectBirdHouseTransport(
		PairingClient.Credentials credentials,
		SyncContext context)
	{
		SemanticSnapshotClient<BirdHouseSnapshot> currentClient = birdHouseClient;
		if (!config.birdHouseSyncEnabled()
			|| currentClient == null
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

	private void connectFarmingPatchTransport()
	{
		PairingClient.Credentials credentials = activeCredentials;
		UUID sessionId = syncSessionId;
		if (credentials == null || sessionId == null)
		{
			return;
		}
		try
		{
			connectFarmingPatchTransport(credentials, SyncContext.capture(client, sessionId));
		}
		catch (RuntimeException exception)
		{
			handleFarmingPatchFailure(
				credentials,
				SemanticSnapshotClient.Failure.REJECTED_SNAPSHOT);
		}
	}

	private void connectFarmingPatchTransport(
		PairingClient.Credentials credentials,
		SyncContext context)
	{
		SemanticSnapshotClient<FarmingPatchSnapshot> currentClient = farmingPatchClient;
		if (!config.farmingPatchSyncEnabled()
			|| currentClient == null
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

	private void finishSession(SnapshotReason reason)
	{
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
			|| client.getGameState() != GameState.LOGGED_IN
			|| activeCredentials == null
			|| snapshotClient == null
			|| snapshotClient.isFinishingSession())
		{
			refreshPairingPanel();
			return;
		}

		captureCurrentClientState();
		captureBirdHouses();
		captureFarmingPatch();
		session.manualSync(Instant.now()).ifPresent(this::publishManualLocally);
	}

	private void startPairing()
	{
		PairingClient currentClient = pairingClient;
		RuneGlassPanel currentPanel = panel;
		if (currentClient == null
			|| currentPanel == null
			|| !config.syncEnabled()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			refreshPairingPanel();
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
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
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
		if (currentSnapshotClient == null
			|| currentPanel == null
			|| sessionId == null
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

		Path queueDirectory = RuneLite.RUNELITE_DIR.toPath()
			.resolve("runeglass")
			.resolve(credentials.getConnectionId());
		connectBirdHouseTransport(credentials, context);
		connectFarmingPatchTransport(credentials, context);
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
		birdHouseReducer.reset();
		farmingPatchReducer.reset();
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
