package com.runeglass.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginBaselineGateTest
{
	@Test
	public void capturesOnceAfterTwoGameTicks()
	{
		LoginBaselineGate gate = new LoginBaselineGate();
		gate.arm();

		assertTrue(gate.isWaiting());
		assertFalse(gate.onGameTick());
		assertTrue(gate.isWaiting());
		assertTrue(gate.onGameTick());
		assertFalse(gate.isWaiting());
		assertFalse(gate.onGameTick());
	}

	@Test
	public void cancellationPreventsCapture()
	{
		LoginBaselineGate gate = new LoginBaselineGate();
		gate.arm();
		gate.cancel();

		assertFalse(gate.isWaiting());
		assertFalse(gate.onGameTick());
	}
}
