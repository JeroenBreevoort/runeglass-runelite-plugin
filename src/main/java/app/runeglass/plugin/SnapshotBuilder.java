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

	@Inject
	SnapshotBuilder(RuneGlassConfig config, SkillsCollector skills, ItemsCollector items)
	{
		this.config = config;
		this.skills = skills;
		this.items = items;
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

		return snapshot;
	}
}
