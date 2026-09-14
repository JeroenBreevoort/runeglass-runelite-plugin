package com.runeglass.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneGlassConfig.GROUP)
public interface RuneGlassConfig extends Config
{
	String GROUP = "runeglass-sync";
	String THIRD_PARTY_WARNING = "This plugin sends your RuneScape character name, account/profile type, skill levels, experience values, opted-in timer states and estimated ready times, plugin/client versions, and IP address to RuneGlass, a third-party service not controlled or verified by RuneLite developers.";

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

	@ConfigItem(
		keyName = "birdHouseSyncEnabled",
		name = "Sync bird house timers",
		description = "Opt in to sending semantic bird house states observed on Fossil Island. No raw varps or location history are sent.",
		warning = THIRD_PARTY_WARNING,
		position = 1
	)
	default boolean birdHouseSyncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "farmingPatchSyncEnabled",
		name = "Sync farming timers",
		description = "Opt in to sending semantic crop and tree states observed at supported farming locations. Compost bins are excluded. No raw varbits or location history are sent.",
		warning = THIRD_PARTY_WARNING,
		position = 2
	)
	default boolean farmingPatchSyncEnabled()
	{
		return false;
	}
}
