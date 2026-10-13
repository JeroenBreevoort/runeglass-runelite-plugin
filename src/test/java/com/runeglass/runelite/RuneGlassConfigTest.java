package com.runeglass.runelite;

import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneGlassConfigTest
{
	@Test
	public void synchronizationIsExplicitlyOptIn()
	{
		RuneGlassConfig config = new RuneGlassConfig()
		{
		};

		assertFalse(config.syncEnabled());
		assertFalse(config.birdHouseSyncEnabled());
		assertFalse(config.farmingPatchSyncEnabled());
		assertFalse(config.appearanceSyncEnabled());
		assertFalse(config.questSyncEnabled());
		assertFalse(config.dropSyncEnabled());
	}

	@Test
	public void masterAndNewDropConsentShowTheirSharingWarnings() throws Exception
	{
		int warningCount = 0;
		for (Method method : RuneGlassConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && !item.warning().isEmpty())
			{
				assertTrue(item.keyName().equals("syncEnabled") || item.keyName().equals("dropSyncEnabled"));
				assertEquals(item.keyName().equals("syncEnabled")
					? RuneGlassConfig.THIRD_PARTY_WARNING : RuneGlassConfig.DROP_SHARING_WARNING, item.warning());
				warningCount++;
			}
		}
		assertEquals(2, warningCount);

		String warning = RuneGlassConfig.THIRD_PARTY_WARNING;
		assertTrue(warning.length() <= 300);
		for (String line : warning.split("\n"))
		{
			assertTrue(line.length() <= 80);
		}
		assertTrue(warning.contains("character name/type"));
		assertTrue(warning.contains("skills/XP"));
		assertTrue(warning.contains("enabled timers, quests"));
		assertTrue(warning.contains("3D appearance"));
		assertTrue(warning.contains("version information and IP address"));
		assertTrue(warning.contains("RuneGlass,"));
		assertTrue(warning.contains("a third-party service"));
		assertTrue(warning.contains("not controlled or verified by RuneLite developers"));

		String description = RuneGlassConfig.class.getMethod("syncEnabled").getAnnotation(ConfigItem.class).description();
		assertTrue(description.contains("Enable sharing"));
		assertTrue(description.contains("Timers, quests, appearance and drops require their separate toggles"));
	}

	@Test
	public void timerDescriptionsRetainTheirDataBoundaries() throws Exception
	{
		String birdHouses = RuneGlassConfig.class.getMethod("birdHouseSyncEnabled").getAnnotation(ConfigItem.class).description();
		assertTrue(birdHouses.contains("semantic bird house states observed on Fossil Island"));
		assertTrue(birdHouses.contains("No raw varps or location history are sent"));

		String farming = RuneGlassConfig.class.getMethod("farmingPatchSyncEnabled").getAnnotation(ConfigItem.class).description();
		assertTrue(farming.contains("semantic crop and tree states observed at supported farming locations"));
		assertTrue(farming.contains("Compost bins are excluded"));
		assertTrue(farming.contains("No raw varbits or location history are sent"));
	}

	@Test
	public void dropConsentDisclosesCapturedStackThresholdAndWholeRewardRetention() throws Exception
	{
		ConfigItem item = RuneGlassConfig.class.getMethod("dropSyncEnabled").getAnnotation(ConfigItem.class);
		assertTrue(item.warning().contains("drop sources, items, quantities, times and value estimates"));
		assertTrue(item.warning().contains("not controlled or verified by"));
		assertTrue(item.warning().contains("Saved history remains until you delete it"));
		assertTrue(item.warning().length() <= 300);
		for (String line : item.warning().split("\n"))
		{
			assertTrue(line.length() <= 80);
		}
		String description = item.description();
		assertTrue(description.contains("item stack worth at least 50,000 GP at capture"));
		assertTrue(description.contains("curated notable reward, including all accompanying items"));
		assertTrue(description.contains("Loot Tracker must be enabled"));
		assertTrue(description.contains("History remains until deletion"));
		assertTrue(description.contains("deletes unsent drops across local profiles"));
	}

	@Test
	public void appearanceConsentDisclosesRenderMetadataAndRetention() throws Exception
	{
		ConfigItem item = RuneGlassConfig.class.getMethod("appearanceSyncEnabled").getAnnotation(ConfigItem.class);
		String description = item.description();
		assertTrue(description.contains("equipped item identifiers"));
		assertTrue(description.contains("body colours"));
		assertTrue(description.contains("gender presentation"));
		assertTrue(description.contains("model geometry"));
		assertTrue(description.contains("render attributes (including texture identifiers)"));
		assertTrue(description.contains("No other players, animations, screenshots or texture assets are sent"));
		assertTrue(description.contains("pauses uploads; delete stored data in RuneGlass settings"));
	}
}
