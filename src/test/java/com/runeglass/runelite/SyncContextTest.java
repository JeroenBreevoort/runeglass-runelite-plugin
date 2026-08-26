package com.runeglass.runelite;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncContextTest
{
	@Test
	public void mapsRuneLiteAccountModesToTheV1CharacterTypes()
	{
		assertEquals("regular", SyncContext.accountType(0));
		assertEquals("ironman", SyncContext.accountType(1));
		assertEquals("ultimate", SyncContext.accountType(2));
		assertEquals("hardcore", SyncContext.accountType(3));
		assertEquals("ironman", SyncContext.accountType(4));
		assertEquals("hardcore", SyncContext.accountType(5));
	}

	@Test(expected = IllegalStateException.class)
	public void rejectsUnknownAccountModes()
	{
		SyncContext.accountType(6);
	}

	@Test
	public void keepsStandardLeagueAndDeadmanProfilesSeparate()
	{
		assertEquals("standard", SyncContext.profileType(EnumSet.of(WorldType.MEMBERS)));
		assertEquals("league", SyncContext.profileType(EnumSet.of(WorldType.SEASONAL)));
		assertEquals("deadman", SyncContext.profileType(EnumSet.of(WorldType.DEADMAN)));
	}
}
