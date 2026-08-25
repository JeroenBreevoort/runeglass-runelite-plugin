package com.runeglass.runelite;

final class LoginBaselineGate
{
	private static final int STABILIZATION_TICKS = 2;

	private int remainingTicks;

	void arm()
	{
		remainingTicks = STABILIZATION_TICKS;
	}

	boolean onGameTick()
	{
		if (remainingTicks <= 0)
		{
			return false;
		}

		remainingTicks--;
		return remainingTicks == 0;
	}

	boolean isWaiting()
	{
		return remainingTicks > 0;
	}

	void cancel()
	{
		remainingTicks = 0;
	}
}
