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
	}

	@Test
	public void onlyMasterSyncShowsTheSharingWarning() throws Exception
	{
		int warningCount = 0;
		for (Method method : RuneGlassConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && !item.warning().isEmpty())
			{
				assertEquals("syncEnabled", item.keyName());
				assertEquals(RuneGlassConfig.THIRD_PARTY_WARNING, item.warning());
				warningCount++;
			}
		}
		assertEquals(1, warningCount);

		String warning = RuneGlassConfig.THIRD_PARTY_WARNING;
		assertTrue(warning.length() <= 300);
		for (String line : warning.split("\n"))
		{
			assertTrue(line.length() <= 80);
		}
		assertTrue(warning.contains("character name/type"));
		assertTrue(warning.contains("skills/XP"));
		assertTrue(warning.contains("enabled timers and 3D appearance"));
		assertTrue(warning.contains("version information and IP address"));
		assertTrue(warning.contains("RuneGlass, a third-party service"));
		assertTrue(warning.contains("not controlled or verified by RuneLite developers"));

		String description = RuneGlassConfig.class.getMethod("syncEnabled").getAnnotation(ConfigItem.class).description();
		assertTrue(description.contains("Enable sharing"));
		assertTrue(description.contains("Timers and appearance require their separate toggles"));
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
