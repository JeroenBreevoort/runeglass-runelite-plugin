package com.runeglass.runelite;

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
		assertEquals(
			"This plugin sends your RuneScape character name, account/profile type, skill levels, experience values, opted-in timer states and estimated ready times, opted-in equipment identifiers, body colours, gender presentation, character model geometry and render attributes (including texture identifiers), plugin/client versions, and IP address to RuneGlass, a third-party service not controlled or verified by RuneLite developers.",
			RuneGlassConfig.THIRD_PARTY_WARNING);
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
		assertEquals(RuneGlassConfig.THIRD_PARTY_WARNING, item.warning());
	}
}
