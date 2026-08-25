package com.runeglass.runelite;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.runelite.api.Skill;

public final class SkillsSnapshotReducer
{
	private final Map<Skill, SkillObservation> observations = new EnumMap<>(Skill.class);
	private boolean dirty;

	public boolean accept(Skill skill, int experience, int level, int boostedLevel)
	{
		Objects.requireNonNull(skill, "skill");
		if (!SkillCatalog.contains(skill))
		{
			throw new IllegalArgumentException("Skill is not part of the V1 catalog");
		}

		SkillObservation next = new SkillObservation(experience, level, boostedLevel);
		SkillObservation previous = observations.put(skill, next);
		boolean changed = !next.equals(previous);
		dirty = dirty || changed;
		return changed;
	}

	public boolean isComplete()
	{
		return observations.size() == SkillCatalog.trainableSkills().size();
	}

	public boolean hasChanges()
	{
		return dirty;
	}

	public Optional<SkillsSnapshot> takeSnapshot(Instant observedAt, SnapshotReason reason)
	{
		if (!isComplete() || !dirty)
		{
			return Optional.empty();
		}
		return buildSnapshot(observedAt, reason);
	}

	public Optional<SkillsSnapshot> takeCompleteSnapshot(Instant observedAt, SnapshotReason reason)
	{
		if (!isComplete())
		{
			return Optional.empty();
		}
		return buildSnapshot(observedAt, reason);
	}

	private Optional<SkillsSnapshot> buildSnapshot(Instant observedAt, SnapshotReason reason)
	{
		Objects.requireNonNull(observedAt, "observedAt");
		Objects.requireNonNull(reason, "reason");

		long[] experience = new long[SkillCatalog.VECTOR_SIZE];
		int[] levels = new int[SkillCatalog.VECTOR_SIZE];
		int[] boostedLevels = new int[SkillCatalog.VECTOR_SIZE];

		int index = 1;
		for (Skill skill : SkillCatalog.trainableSkills())
		{
			SkillObservation observation = observations.get(skill);
			experience[index] = observation.getExperience();
			levels[index] = observation.getLevel();
			boostedLevels[index] = observation.getBoostedLevel();

			experience[0] += observation.getExperience();
			levels[0] += observation.getLevel();
			boostedLevels[0] += observation.getBoostedLevel();
			index++;
		}

		dirty = false;
		return Optional.of(new SkillsSnapshot(
			observedAt,
			reason,
			experience,
			levels,
			boostedLevels));
	}

	public void reset()
	{
		observations.clear();
		dirty = false;
	}
}
