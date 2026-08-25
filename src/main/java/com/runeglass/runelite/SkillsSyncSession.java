package com.runeglass.runelite;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Skill;

public final class SkillsSyncSession
{
	public static final Duration CHANGE_DEBOUNCE = Duration.ofSeconds(30);
	public static final Duration NORMAL_UPLOAD_WINDOW = Duration.ofMinutes(5);
	public static final Duration COVERAGE_HEARTBEAT_WINDOW = Duration.ofMinutes(10);

	private final SkillsSnapshotReducer reducer = new SkillsSnapshotReducer();

	private boolean active;
	private boolean baselineEmitted;
	private Instant firstDirtyAt;
	private Instant lastEmissionAt;

	public void start()
	{
		reducer.reset();
		active = true;
		baselineEmitted = false;
		firstDirtyAt = null;
		lastEmissionAt = null;
	}

	public Optional<SkillsSnapshot> accept(
		Skill skill,
		int experience,
		int level,
		int boostedLevel,
		Instant observedAt)
	{
		Objects.requireNonNull(observedAt, "observedAt");
		if (!active)
		{
			return Optional.empty();
		}

		boolean changed = reducer.accept(skill, experience, level, boostedLevel);
		if (!changed)
		{
			return Optional.empty();
		}

		if (!baselineEmitted && reducer.isComplete())
		{
			return emit(observedAt, SnapshotReason.LOGIN_BASELINE);
		}

		if (baselineEmitted && firstDirtyAt == null)
		{
			firstDirtyAt = observedAt;
		}

		return Optional.empty();
	}

	public Optional<SkillsSnapshot> poll(Instant observedAt)
	{
		Objects.requireNonNull(observedAt, "observedAt");
		if (!active || !baselineEmitted)
		{
			return Optional.empty();
		}

		if (reducer.hasChanges() && firstDirtyAt != null)
		{
			Instant debouncedAt = firstDirtyAt.plus(CHANGE_DEBOUNCE);
			Instant windowOpensAt = lastEmissionAt.plus(NORMAL_UPLOAD_WINDOW);
			if (observedAt.isBefore(debouncedAt) || observedAt.isBefore(windowOpensAt))
			{
				return Optional.empty();
			}
			return emit(observedAt, SnapshotReason.CHANGE_CHECKPOINT);
		}

		if (observedAt.isBefore(lastEmissionAt.plus(COVERAGE_HEARTBEAT_WINDOW)))
		{
			return Optional.empty();
		}
		return emitComplete(observedAt, SnapshotReason.COVERAGE_HEARTBEAT);
	}

	public Optional<SkillsSnapshot> stop(Instant observedAt, SnapshotReason reason)
	{
		Objects.requireNonNull(observedAt, "observedAt");
		Objects.requireNonNull(reason, "reason");
		if (reason != SnapshotReason.LOGOUT_FLUSH && reason != SnapshotReason.PROFILE_SWITCH)
		{
			throw new IllegalArgumentException("A session can only stop for logout or profile switch");
		}

		Optional<SkillsSnapshot> snapshot = Optional.empty();
		if (active && baselineEmitted)
		{
			snapshot = reducer.takeCompleteSnapshot(observedAt, reason);
		}

		cancel();
		return snapshot;
	}

	public Optional<SkillsSnapshot> manualSync(Instant observedAt)
	{
		Objects.requireNonNull(observedAt, "observedAt");
		if (!active || !baselineEmitted)
		{
			return Optional.empty();
		}
		Optional<SkillsSnapshot> snapshot = reducer.takeCompleteSnapshot(
			observedAt,
			SnapshotReason.MANUAL_SYNC);
		if (snapshot.isPresent())
		{
			firstDirtyAt = null;
			lastEmissionAt = observedAt;
		}
		return snapshot;
	}

	public void cancel()
	{
		reducer.reset();
		active = false;
		baselineEmitted = false;
		firstDirtyAt = null;
		lastEmissionAt = null;
	}

	public boolean isActive()
	{
		return active;
	}

	private Optional<SkillsSnapshot> emit(Instant observedAt, SnapshotReason reason)
	{
		Optional<SkillsSnapshot> snapshot = reducer.takeSnapshot(observedAt, reason);
		if (snapshot.isPresent())
		{
			baselineEmitted = true;
			firstDirtyAt = null;
			lastEmissionAt = observedAt;
		}
		return snapshot;
	}

	private Optional<SkillsSnapshot> emitComplete(Instant observedAt, SnapshotReason reason)
	{
		Optional<SkillsSnapshot> snapshot = reducer.takeCompleteSnapshot(observedAt, reason);
		if (snapshot.isPresent())
		{
			baselineEmitted = true;
			firstDirtyAt = null;
			lastEmissionAt = observedAt;
		}
		return snapshot;
	}
}
