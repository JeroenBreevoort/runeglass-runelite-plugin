/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Assembles a full-state snapshot from the collectors.
 * <p>
 * Always runs on the client thread, because reading the game client from anywhere else is unsafe.
 * Everything it produces is immutable, so the result can be handed straight to the sync loop.
 */
@Singleton
public class SnapshotBuilder
{
	private final RuneGlassConfig config;
	private final SkillsCollector skills;
	private final ItemsCollector items;
	private final ProgressCollector progress;

	@Inject
	SnapshotBuilder(RuneGlassConfig config, SkillsCollector skills, ItemsCollector items,
		ProgressCollector progress)
	{
		this.config = config;
		this.skills = skills;
		this.items = items;
		this.progress = progress;
	}

	/**
	 * Builds a snapshot honouring the per-domain toggles. Must be called on the client thread.
	 */
	public RuneGlassApi.Snapshot build()
	{
		final RuneGlassApi.Snapshot snapshot = new RuneGlassApi.Snapshot();
		snapshot.capturedAt = System.currentTimeMillis();

		if (config.syncSkills())
		{
			snapshot.skills = skills.buildSkillXp();
		}

		if (config.syncItems())
		{
			snapshot.inventory = items.getInventory();
			snapshot.equipment = items.getEquipment();
			snapshot.bank = items.getBank();
		}

		if (config.syncProgress())
		{
			snapshot.quests = progress.getQuests();
			snapshot.varbits = progress.getVarbits();
		}

		return snapshot;
	}
}
