package com.runeglass.runelite;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropGameContextTest
{
	@Test public void raidTokensRequireBothKnownRegionAndGameState()
	{
		assertEquals("Chambers of Xeric", DropGameContext.raidFamily(12889, 1, 1, 0, 0));
		assertEquals("Theatre of Blood", DropGameContext.raidFamily(12867, 0, 0, 2, 0));
		assertEquals("Tombs of Amascut", DropGameContext.raidFamily(14160, 0, 0, 0, 2));
		assertNull(DropGameContext.raidFamily(12889, 1, 0, 0, 0));
		assertNull(DropGameContext.raidFamily(12867, 0, 0, 1, 0));
		assertNull(DropGameContext.raidFamily(9999, 1, 1, 2, 2));
		assertNull(DropGameContext.raidFamily(14642, 0, 0, 2, 0));
	}

	@Test public void changingRegionFamilyOrLeavingRaidInvalidatesEncounterIdentity()
	{
		DropGameContext context = new DropGameContext();
		String first = context.update("Chambers of Xeric");
		assertEquals(first, context.update("Chambers of Xeric"));
		assertNull(context.update(null));
		assertNotEquals(first, context.update("Chambers of Xeric"));
		context.clear();
		assertNotEquals(first, context.update("Chambers of Xeric"));
		assertTrue(context.update("Tombs of Amascut").startsWith("Tombs of Amascut:"));
	}
}
