package com.runeglass.runelite;

import com.google.inject.Provides;
import com.google.gson.Gson;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
	private MockSnapshotTransport transport;

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

	private final SkillsSyncSession session = new SkillsSyncSession();
	private final LoginBaselineGate baselineGate = new LoginBaselineGate();
	private PreviewPairingClient previewPairingClient;
	private PreviewSnapshotClient previewSnapshotClient;
	private RuneGlassPanel panel;
	private NavigationButton navigationButton;
	private UUID previewSessionId;
	private SkillsSnapshot latestSnapshot;

	@Override
	protected void startUp()
	{
		previewPairingClient = PreviewPairingClient.fromEnvironment(httpClient, gson, executor);
		previewSnapshotClient = PreviewSnapshotClient.fromEnvironment(httpClient, gson, executor);
		panel = new RuneGlassPanel(
			() -> clientThread.invokeLater(this::startPreviewPairing),
			() -> clientThread.invokeLater(this::stopPreviewPairing),
			() -> clientThread.invokeLater(this::requestManualSync));
		navigationButton = NavigationButton.builder()
			.tooltip("RuneGlass Sync")
			.icon(createNavigationIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		LOG.debug("RuneGlass started with mock transport and gated preview pairing and skills upload");
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
		transport.clear();
		if (previewPairingClient != null)
		{
			previewPairingClient.cancel();
		}
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.closeAfterFlush();
		}
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		panel = null;
		navigationButton = null;
		previewPairingClient = null;
		previewSnapshotClient = null;
		previewSessionId = null;
		latestSnapshot = null;
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
			previewPairingClient.cancelPending();
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			finishSession(SnapshotReason.LOGOUT_FLUSH);
			previewPairingClient.cancelPending();
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
			transport.clear();
			previewPairingClient.cancel();
			previewSnapshotClient.cancel();
			previewSessionId = null;
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
		previewSessionId = UUID.randomUUID();
		latestSnapshot = null;
		if (previewPairingClient != null
			&& previewPairingClient.getCredentials().isPresent()
			&& previewSnapshotClient != null
			&& !previewSnapshotClient.isFinishingSession())
		{
			connectPreviewSnapshot();
		}
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
		else if (previewSnapshotClient != null)
		{
			previewSnapshotClient.finishSession();
		}
		baselineGate.cancel();
		previewSessionId = null;
		latestSnapshot = null;
	}

	private void publishLocally(SkillsSnapshot snapshot)
	{
		transport.publish(snapshot);
		latestSnapshot = snapshot;
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.publish(snapshot);
		}
		LOG.debug(
			"Captured {} catalog {} snapshot in the local mock transport",
			snapshot.getReason(),
			snapshot.getCatalogVersion());
	}

	private void publishFinalLocally(SkillsSnapshot snapshot)
	{
		transport.publish(snapshot);
		latestSnapshot = snapshot;
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.finishSession(snapshot);
		}
		LOG.debug(
			"Captured {} catalog {} final snapshot in the local mock transport",
			snapshot.getReason(),
			snapshot.getCatalogVersion());
	}

	private void publishManualLocally(SkillsSnapshot snapshot)
	{
		transport.publish(snapshot);
		latestSnapshot = snapshot;
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.publishImmediately(snapshot);
		}
		LOG.debug(
			"Captured manual catalog {} snapshot in the local mock transport",
			snapshot.getCatalogVersion());
	}

	private void requestManualSync()
	{
		if (!config.syncEnabled()
			|| !session.isActive()
			|| client.getGameState() != GameState.LOGGED_IN
			|| previewPairingClient == null
			|| !previewPairingClient.getCredentials().isPresent()
			|| previewSnapshotClient == null
			|| previewSnapshotClient.isFinishingSession())
		{
			refreshPairingPanel();
			return;
		}

		captureCurrentClientState();
		session.manualSync(Instant.now()).ifPresent(this::publishManualLocally);
	}

	private void startPreviewPairing()
	{
		PreviewPairingClient currentClient = previewPairingClient;
		RuneGlassPanel currentPanel = panel;
		if (currentClient == null
			|| currentPanel == null
			|| !currentClient.isAvailable()
			|| !config.syncEnabled()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			refreshPairingPanel();
			return;
		}

		currentPanel.showStarting();
		currentClient.start(new PreviewPairingClient.Listener()
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
				clientThread.invokeLater(RuneGlassPlugin.this::connectPreviewSnapshot);
			}

			@Override
			public void onFailure(PreviewPairingClient.Failure failure)
			{
				RuneGlassPanel currentPanel = panel;
				if (currentPanel != null)
				{
					currentPanel.showFailure(failure);
				}
			}
		});
	}

	private void stopPreviewPairing()
	{
		if (previewPairingClient != null)
		{
			previewPairingClient.cancel();
		}
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.cancel();
		}
		refreshPairingPanel();
	}

	private void connectPreviewSnapshot()
	{
		PreviewPairingClient pairingClient = previewPairingClient;
		PreviewSnapshotClient snapshotClient = previewSnapshotClient;
		RuneGlassPanel currentPanel = panel;
		UUID sessionId = previewSessionId;
		if (pairingClient == null
			|| snapshotClient == null
			|| currentPanel == null
			|| sessionId == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			stopPreviewPairing();
			return;
		}
		if (snapshotClient.isFinishingSession())
		{
			return;
		}
		Optional<PreviewPairingClient.Credentials> credentials = pairingClient.getCredentials();
		if (!credentials.isPresent())
		{
			stopPreviewPairing();
			return;
		}

		final PreviewSyncContext context;
		try
		{
			context = PreviewSyncContext.capture(client, sessionId);
		}
		catch (RuntimeException exception)
		{
			pairingClient.cancel();
			snapshotClient.cancel();
			currentPanel.showSnapshotFailure(PreviewSnapshotClient.Failure.REJECTED_BATCH);
			return;
		}

		currentPanel.showConnected();
		if (!snapshotClient.connect(
			credentials.get(),
			context,
			new PreviewSnapshotClient.Listener()
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
				public void onFailure(PreviewSnapshotClient.Failure failure)
				{
					clientThread.invokeLater(() -> handleSnapshotFailure(failure));
				}

				@Override
				public void onSessionDrained()
				{
					clientThread.invokeLater(RuneGlassPlugin.this::reconnectAfterSessionDrain);
				}
			}))
		{
			return;
		}

		SkillsSnapshot snapshot = latestSnapshot;
		if (snapshot != null)
		{
			snapshotClient.publish(snapshot);
		}
	}

	private void reconnectAfterSessionDrain()
	{
		if (config.syncEnabled()
			&& session.isActive()
			&& client.getGameState() == GameState.LOGGED_IN
			&& previewPairingClient != null
			&& previewPairingClient.getCredentials().isPresent())
		{
			connectPreviewSnapshot();
		}
		else
		{
			refreshPairingPanel();
		}
	}

	private void handleSnapshotFailure(PreviewSnapshotClient.Failure failure)
	{
		if (previewPairingClient != null)
		{
			previewPairingClient.cancel();
		}
		if (previewSnapshotClient != null)
		{
			previewSnapshotClient.cancel();
		}
		RuneGlassPanel currentPanel = panel;
		if (currentPanel != null)
		{
			currentPanel.showSnapshotFailure(failure);
		}
	}

	private void refreshPairingPanel()
	{
		RuneGlassPanel currentPanel = panel;
		PreviewPairingClient currentClient = previewPairingClient;
		if (currentPanel == null || currentClient == null)
		{
			return;
		}
		if (!currentClient.isAvailable())
		{
			currentPanel.showUnavailable();
		}
		else if (!config.syncEnabled())
		{
			currentPanel.showSyncDisabled();
		}
		else if (client.getGameState() != GameState.LOGGED_IN)
		{
			currentPanel.showLoggedOut();
		}
		else if (currentClient.getCredentials().isPresent())
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
