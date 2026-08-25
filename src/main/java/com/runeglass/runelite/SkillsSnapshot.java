package com.runeglass.runelite;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class SkillsSnapshot
{
	public static final String TYPE = "skills.snapshot.v1";

	private final String type;
	private final String observedAt;
	private final String reason;
	private final int catalogVersion;
	private final long[] experience;
	private final int[] levels;
	private final int[] boostedLevels;

	SkillsSnapshot(
		Instant observedAt,
		SnapshotReason reason,
		long[] experience,
		int[] levels,
		int[] boostedLevels)
	{
		this.type = TYPE;
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt").toString();
		this.reason = Objects.requireNonNull(reason, "reason").wireValue();
		this.catalogVersion = SkillCatalog.VERSION;
		this.experience = copyExperienceVector(experience);
		this.levels = copyLevelVector(levels, "levels");
		this.boostedLevels = copyLevelVector(boostedLevels, "boostedLevels");
	}

	public String getType()
	{
		return type;
	}

	public String getObservedAt()
	{
		return observedAt;
	}

	public String getReason()
	{
		return reason;
	}

	public int getCatalogVersion()
	{
		return catalogVersion;
	}

	public long[] getExperience()
	{
		return Arrays.copyOf(experience, experience.length);
	}

	public int[] getLevels()
	{
		return Arrays.copyOf(levels, levels.length);
	}

	public int[] getBoostedLevels()
	{
		return Arrays.copyOf(boostedLevels, boostedLevels.length);
	}

	private static long[] copyExperienceVector(long[] vector)
	{
		Objects.requireNonNull(vector, "experience");
		if (vector.length != SkillCatalog.VECTOR_SIZE)
		{
			throw new IllegalArgumentException("Experience vector must match the V1 catalog");
		}
		return Arrays.copyOf(vector, vector.length);
	}

	private static int[] copyLevelVector(int[] vector, String name)
	{
		Objects.requireNonNull(vector, name);
		if (vector.length != SkillCatalog.VECTOR_SIZE)
		{
			throw new IllegalArgumentException(name + " vector must match the V1 catalog");
		}
		return Arrays.copyOf(vector, vector.length);
	}
}
