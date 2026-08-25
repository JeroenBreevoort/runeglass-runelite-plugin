package com.runeglass.runelite;

import java.util.Objects;

final class SkillObservation
{
	private static final int MAX_SKILL_EXPERIENCE = 200_000_000;

	private final int experience;
	private final int level;
	private final int boostedLevel;

	SkillObservation(int experience, int level, int boostedLevel)
	{
		if (experience < 0 || experience > MAX_SKILL_EXPERIENCE)
		{
			throw new IllegalArgumentException("Skill experience is outside the supported range");
		}
		if (level < 0 || boostedLevel < 0)
		{
			throw new IllegalArgumentException("Skill levels cannot be negative");
		}

		this.experience = experience;
		this.level = level;
		this.boostedLevel = boostedLevel;
	}

	int getExperience()
	{
		return experience;
	}

	int getLevel()
	{
		return level;
	}

	int getBoostedLevel()
	{
		return boostedLevel;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof SkillObservation))
		{
			return false;
		}

		SkillObservation that = (SkillObservation) other;
		return experience == that.experience
			&& level == that.level
			&& boostedLevel == that.boostedLevel;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(experience, level, boostedLevel);
	}
}
