/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "RuneGlass",
	description = "Sync your account to the RuneGlass companion app",
	tags = {"companion", "tracker", "stats", "sync"}
)
public class RuneGlassPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RuneGlassConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PairingService pairingService;

	@Inject
	private SyncService syncService;

	@Inject
	private EventBus eventBus;

	@Inject
	private SkillsCollector skillsCollector;

	@Inject
	private ItemsCollector itemsCollector;

	@Inject
	private LootCollector lootCollector;

	@Inject
	private ProgressCollector progressCollector;

	@Inject
	private CollectionLogCollector collectionLogCollector;

	@Inject
	private SnapshotBuilder snapshotBuilder;

	private RuneGlassPanel panel;
	private NavigationButton navButton;

	/** Game ticks are 600ms, so this is a snapshot roughly every 60 seconds. */
	private static final int SNAPSHOT_TICKS = 100;
	/** First snapshot of a session lands sooner, once containers have populated. */
	private static final int FIRST_SNAPSHOT_TICKS = 10;

	private int ticksUntilSnapshot = SNAPSHOT_TICKS;

	/**
	 * Last identity we observed. Written on the client thread, read from the panel's Swing thread,
	 * so the reference is volatile and the object it points at is immutable.
	 */
	private volatile AccountIdentity identity = null;

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(RuneGlassPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(RuneGlassPlugin.class, "/app/runeglass/plugin/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("RuneGlass")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		pairingService.setListener(state -> {
			panel.onPairingStateChanged(state);
			// A fresh link clears any rejected-token state left over from the previous one.
			syncService.onLinkChanged();
		});
		syncService.setListener(panel::onSyncStatusChanged);
		syncService.startUp();

		// Collectors live in their own classes, so they need registering explicitly.
		eventBus.register(skillsCollector);
		eventBus.register(itemsCollector);
		eventBus.register(lootCollector);
		eventBus.register(progressCollector);
		eventBus.register(collectionLogCollector);

		// Picks up an account that is already logged in when the plugin is toggled on mid-session.
		clientThread.invokeLater(this::refreshIdentity);

		log.info("RuneGlass started");
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(skillsCollector);
		eventBus.unregister(itemsCollector);
		eventBus.unregister(lootCollector);
		eventBus.unregister(progressCollector);
		eventBus.unregister(collectionLogCollector);

		// Cancel in-flight work without blocking; RuneLite owns the executor itself.
		pairingService.shutdown();
		syncService.shutDown();

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		identity = null;

		log.info("RuneGlass stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneGlassConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		final RuneGlassPanel p = panel;
		if (p != null)
		{
			p.refresh();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			refreshIdentity();
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// getAccountHash() goes back to -1 here; stop attributing anything to the old account.
			setIdentity(null);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (identity == null || !config.syncEnabled() || config.deviceToken().isEmpty())
		{
			return;
		}

		// A bank change carries too much to be an event, so it asks for a snapshot instead.
		final boolean requested = itemsCollector.consumeSnapshotRequest();

		if (--ticksUntilSnapshot > 0 && !requested)
		{
			return;
		}

		ticksUntilSnapshot = SNAPSHOT_TICKS;

		// Quests have no change event, so they are polled and diffed here, before the snapshot
		// is assembled from what the collectors last saw.
		progressCollector.refresh();

		// Built here because only the client thread may read the game client.
		syncService.submitSnapshot(snapshotBuilder.build());
	}

	/**
	 * Reads the current account off the client. Must run on the client thread.
	 */
	private void refreshIdentity()
	{
		final long hash = client.getAccountHash();
		if (hash == AccountIdentity.NO_ACCOUNT)
		{
			setIdentity(null);
			return;
		}

		final Player local = client.getLocalPlayer();
		final String name = local != null ? local.getName() : null;

		final List<String> worldTypes = new ArrayList<>();
		final EnumSet<WorldType> types = client.getWorldType();
		if (types != null)
		{
			for (WorldType type : types)
			{
				worldTypes.add(type.name());
			}
		}

		setIdentity(new AccountIdentity(hash, name, readAccountType(), worldTypes));
	}

	/**
	 * {@code Client#getAccountType()} and its enum are both deprecated, so read the varbit directly.
	 */
	private String readAccountType()
	{
		return AccountIdentity.accountTypeOf(client.getVarbitValue(VarbitID.IRONMAN));
	}

	private void setIdentity(@Nullable AccountIdentity next)
	{
		final AccountIdentity previous = identity;
		if (previous == null ? next == null : (next != null && previous.getAccountHash() == next.getAccountHash()))
		{
			return;
		}

		identity = next;
		log.debug("Account identity changed: {}", next);

		if (next != null)
		{
			// Baselines are per character; comparing levels across accounts would invent level ups.
			skillsCollector.reset();
			itemsCollector.reset();
			lootCollector.reset();
			progressCollector.reset();
			collectionLogCollector.reset();
			syncService.startSession(next);
			ticksUntilSnapshot = FIRST_SNAPSHOT_TICKS;
		}
		else if (previous != null)
		{
			syncService.endSession();
		}

		final RuneGlassPanel p = panel;
		if (p != null)
		{
			p.onIdentityChanged(next);
		}
	}

	@Nullable
	public AccountIdentity getIdentity()
	{
		return identity;
	}

	@Provides
	RuneGlassConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneGlassConfig.class);
	}
}
