package com.runeglass.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneGlassConfig.GROUP)
public interface RuneGlassConfig extends Config
{
	String GROUP = "runeglass-sync";
	String THIRD_PARTY_WARNING = "Shares your character name/type, skills/XP, bird house and farming timers,\n"
		+ "quests, 3D appearance, valuable drops with kill counts and collection-log\n"
		+ "evidence, version information and IP address with RuneGlass, a third-party\n"
		+ "service not controlled or verified by RuneLite developers.";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Enable RuneGlass sync",
		description = "Share this character's name/type, skills and XP, bird house and farming timers, quest states and quest points, "
			+ "3D appearance, and valuable drops with RuneGlass. Drops are Loot Tracker rewards with an item stack worth at least "
			+ "50,000 GP or a curated notable reward, with their kill count, raid mode, collection-log evidence and notification "
			+ "settings; Loot Tracker must be enabled. No raw varbits, game messages, location history, other players or screenshots are sent. "
			+ "Turning this off stops all uploads and discards unsent data; delete stored data in RuneGlass settings.",
		warning = THIRD_PARTY_WARNING
	)
	default boolean syncEnabled()
	{
		return false;
	}
}
