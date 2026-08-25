package com.runeglass.runelite;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PreviewSyncContextTest
{
	@Test
	public void mapsRuneLiteAccountModesToTheV1CharacterTypes()
	{
		assertEquals("regular", PreviewSyncContext.accountType(0));
		assertEquals("ironman", PreviewSyncContext.accountType(1));
		assertEquals("ultimate", PreviewSyncContext.accountType(2));
		assertEquals("hardcore", PreviewSyncContext.accountType(3));
		assertEquals("ironman", PreviewSyncContext.accountType(4));
		assertEquals("hardcore", PreviewSyncContext.accountType(5));
	}

	@Test(expected = IllegalStateException.class)
	public void rejectsUnknownAccountModes()
	{
		PreviewSyncContext.accountType(6);
	}

	@Test
	public void keepsStandardLeagueAndDeadmanProfilesSeparate()
	{
		assertEquals("standard", PreviewSyncContext.profileType(EnumSet.of(WorldType.MEMBERS)));
		assertEquals("league", PreviewSyncContext.profileType(EnumSet.of(WorldType.SEASONAL)));
		assertEquals("deadman", PreviewSyncContext.profileType(EnumSet.of(WorldType.DEADMAN)));
	}
}
