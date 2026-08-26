package com.runeglass.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneGlassConfig.GROUP)
public interface RuneGlassConfig extends Config
{
	String GROUP = "runeglass-sync";
	String THIRD_PARTY_WARNING = "This plugin sends your RuneScape character name, account/profile type, skill levels, experience values, plugin/client versions, and IP address to RuneGlass, a third-party service not controlled or verified by RuneLite developers.";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Enable RuneGlass sync",
		description = "Opt in to sending complete skill and XP snapshots for the current character to RuneGlass.",
		warning = THIRD_PARTY_WARNING
	)
	default boolean syncEnabled()
	{
		return false;
	}
}
