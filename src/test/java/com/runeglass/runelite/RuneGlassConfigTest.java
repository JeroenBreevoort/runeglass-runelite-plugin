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
	}

	@Test
	public void oneConsentToggleCoversEverySharedCategory() throws Exception
	{
		int itemCount = 0;
		for (Method method : RuneGlassConfig.class.getDeclaredMethods())
		{
			if (method.getAnnotation(ConfigItem.class) != null)
			{
				itemCount++;
			}
		}
		assertEquals(1, itemCount);

		ConfigItem item = RuneGlassConfig.class.getMethod("syncEnabled").getAnnotation(ConfigItem.class);
		assertEquals(RuneGlassConfig.THIRD_PARTY_WARNING, item.warning());

		String warning = RuneGlassConfig.THIRD_PARTY_WARNING;
		assertTrue(warning.length() <= 300);
		for (String line : warning.split("\n"))
		{
			assertTrue(line.length() <= 80);
		}
		for (String disclosure : new String[]{"character name/type", "skills/XP", "bird house and farming timers", "quests",
			"3D appearance", "valuable drops with kill counts and collection-log", "version information and IP address",
			"RuneGlass, a third-party", "not controlled or verified by RuneLite developers"})
		{
			assertTrue(disclosure, warning.contains(disclosure));
		}

		String description = item.description();
		for (String disclosure : new String[]{"quest states and quest points", "item stack worth at least 50,000 GP",
			"curated notable reward", "kill count, raid mode, collection-log evidence and notification settings",
			"Loot Tracker must be enabled", "No raw varbits, game messages, location history, other players or screenshots",
			"stops all uploads and discards unsent data", "delete stored data in RuneGlass settings"})
		{
			assertTrue(disclosure, description.contains(disclosure));
		}
	}
}
