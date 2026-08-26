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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
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
	description = "Synchronizes your current character's skills and XP with RuneGlass",
	tags = {"mobile", "progress", "skills", "xp", "sync"}
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
	private PairingClient pairingClient;
	private SnapshotClient snapshotClient;
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
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		panel = null;
		navigationButton = null;
		pairingClient = null;
		snapshotClient = null;
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
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneGlassConfig.GROUP.equals(event.getGroup()) || !"syncEnabled".equals(event.getKey()))
		{
			return;
		}

		if (!config.syncEnabled())
		{
			session.cancel();
			baselineGate.cancel();
			pairingClient.cancel();
			snapshotClient.discard();
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
		syncSessionId = UUID.randomUUID();
		latestSnapshot = null;
		activeCredentials = null;
		if (snapshotClient != null)
		{
			snapshotClient.cancel();
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
