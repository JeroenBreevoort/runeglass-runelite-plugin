/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Settings for the RuneGlass plugin.
 * <p>
 * The group name is part of the on-disk config format. Renaming it silently resets every
 * user's saved settings, so it is fixed permanently as {@code runeglass}.
 */
@ConfigGroup(RuneGlassConfig.GROUP)
public interface RuneGlassConfig extends Config
{
	String GROUP = "runeglass";

	/**
	 * Required verbatim by the Plugin Hub on any option that enables third-party network traffic.
	 */
	String THIRD_PARTY_WARNING =
		"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	@ConfigSection(
		name = "Sync",
		description = "What gets sent to the RuneGlass app",
		position = 10
	)
	String syncSection = "sync";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Enable sync",
		description = "Send this account's stats, items, loot and progression to the RuneGlass app.",
		warning = THIRD_PARTY_WARNING,
		position = 1
	)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncSkills",
		name = "Skills and experience",
		description = "Level ups as they happen, plus periodic experience totals.",
		section = syncSection,
		position = 11
	)
	default boolean syncSkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncItems",
		name = "Inventory, equipment and bank",
		description = "Item contents and quantities. Bank contents update only while the bank is open.",
		section = syncSection,
		position = 12
	)
	default boolean syncItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncLoot",
		name = "Loot and kill counts",
		description = "Drops received from NPCs and other PvM sources. PvP loot is never sent.",
		section = syncSection,
		position = 13
	)
	default boolean syncLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncProgress",
		name = "Quests, diaries and collection log",
		description = "Quest states, achievement diaries, combat achievements and collection log slots.",
		section = syncSection,
		position = 14
	)
	default boolean syncProgress()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Link state. Written by the plugin, never shown in the settings panel.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "deviceToken",
		name = "Device token",
		description = "Credential issued when this installation was linked to a RuneGlass account.",
		secret = true,
		hidden = true
	)
	default String deviceToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "deviceToken",
		name = "Device token",
		description = "Credential issued when this installation was linked to a RuneGlass account.",
		secret = true,
		hidden = true
	)
	void deviceToken(String token);

	@ConfigItem(
		keyName = "linkedAccountName",
		name = "Linked account",
		description = "Display name of the linked RuneGlass account.",
		hidden = true
	)
	default String linkedAccountName()
	{
		return "";
	}

	@ConfigItem(
		keyName = "linkedAccountName",
		name = "Linked account",
		description = "Display name of the linked RuneGlass account.",
		hidden = true
	)
	void linkedAccountName(String name);

	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API base URL",
		description = "RuneGlass backend. Change only when testing against a local mock server.",
		position = 90
	)
	default String apiBaseUrl()
	{
		return RuneGlassApi.DEFAULT_BASE_URL;
	}
}
