package com.runeglass.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneGlassConfig.GROUP)
public interface RuneGlassConfig extends Config
{
	String GROUP = "runeglass-sync";
	String THIRD_PARTY_WARNING = "Shares your character name/type, skills/XP, enabled timers, quests,\n"
		+ "3D appearance, version information and IP address with RuneGlass,\n"
		+ "a third-party service not controlled or verified by RuneLite developers.";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Enable RuneGlass sync",
		description = "Enable sharing the current character's name/type and complete skill and XP snapshots with RuneGlass. Timers, quests and appearance require their separate toggles.",
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
		position = 2
	)
	default boolean farmingPatchSyncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "appearanceSyncEnabled",
		name = "Sync character appearance",
		description = "Opt in to sending the current character's equipped item identifiers, body colours, gender presentation, low-poly model geometry and render attributes (including texture identifiers). No other players, animations, screenshots or texture assets are sent. Turning this off pauses uploads; delete stored data in RuneGlass settings.",
		position = 3
	)
	default boolean appearanceSyncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "questSyncEnabled",
		name = "Sync quests and quest points",
		description = "Opt in to sending current semantic quest states and quest points for this character. No raw varbits, walkthrough actions or completion history are sent. Turning this off pauses uploads; delete stored RuneLite data in RuneGlass settings. Manual planner declarations are stored separately.",
		position = 4
	)
	default boolean questSyncEnabled()
	{
		return false;
	}
}
