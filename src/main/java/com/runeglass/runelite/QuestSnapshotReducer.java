package com.runeglass.runelite;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

final class QuestSnapshotReducer
{
	private static final long MIN_CAPTURE_TICKS = 50;
	private static final long QUIET_TICKS = 3;
	private static final long MAX_DIRTY_TICKS = 10;
	private long tick;
	private long lastCapture = -MIN_CAPTURE_TICKS;
	private long dirtySince;
	private long lastChange;
	private boolean dirty = true;
	private Map<String, Object> lastProgress;

	void markDirty()
	{
		if (!dirty) dirtySince = tick;
		dirty = true;
		lastChange = tick;
	}

	boolean shouldCapture()
	{
		tick++;
		if (!dirty || tick - lastCapture < MIN_CAPTURE_TICKS
			|| (tick - lastChange < QUIET_TICKS && tick - dirtySince < MAX_DIRTY_TICKS)) return false;
		dirty = false;
		lastCapture = tick;
		return true;
	}

	Optional<QuestSnapshot> observe(Instant observedAt, int questPoints, Map<Integer, String> states)
	{
		QuestSnapshot snapshot = new QuestSnapshot(observedAt, questPoints, states);
		if (snapshot.getProgress().equals(lastProgress)) return Optional.empty();
		lastProgress = snapshot.getProgress();
		return Optional.of(snapshot);
	}

	QuestSnapshot observeManual(Instant observedAt, int questPoints, Map<Integer, String> states)
	{
		QuestSnapshot snapshot = new QuestSnapshot(observedAt, questPoints, states);
		lastProgress = snapshot.getProgress();
		return snapshot;
	}

	void reset()
	{
		tick = 0;
		lastCapture = -MIN_CAPTURE_TICKS;
		dirtySince = 0;
		lastChange = 0;
		dirty = true;
		lastProgress = null;
	}
}
