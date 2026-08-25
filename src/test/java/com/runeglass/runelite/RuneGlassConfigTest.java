package com.runeglass.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuneGlassConfigTest
{
	@Test
	public void synchronizationIsExplicitlyOptIn()
	{
		RuneGlassConfig config = new RuneGlassConfig()
		{
		};

		assertFalse(config.syncEnabled());
		assertEquals(
			"This plugin sends your RuneScape character name, account/profile type, skill levels, experience values, plugin/client versions, and IP address to RuneGlass, a third-party service not controlled or verified by RuneLite developers.",
			RuneGlassConfig.THIRD_PARTY_WARNING);
	}
}
