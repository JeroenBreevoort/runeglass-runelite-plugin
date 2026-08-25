package com.runeglass.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

public final class SkillCatalog
{
	public static final int VERSION = 1;
	public static final int VECTOR_SIZE = 25;

	private static final List<Skill> TRAINABLE_SKILLS = Collections.unmodifiableList(Arrays.asList(
		Skill.ATTACK,
		Skill.DEFENCE,
		Skill.STRENGTH,
		Skill.HITPOINTS,
		Skill.RANGED,
		Skill.PRAYER,
		Skill.MAGIC,
		Skill.COOKING,
		Skill.WOODCUTTING,
		Skill.FLETCHING,
		Skill.FISHING,
		Skill.FIREMAKING,
		Skill.CRAFTING,
		Skill.SMITHING,
		Skill.MINING,
		Skill.HERBLORE,
		Skill.AGILITY,
		Skill.THIEVING,
		Skill.SLAYER,
		Skill.FARMING,
		Skill.RUNECRAFT,
		Skill.HUNTER,
		Skill.CONSTRUCTION,
		Skill.SAILING
	));

	private SkillCatalog()
	{
	}

	public static List<Skill> trainableSkills()
	{
		return TRAINABLE_SKILLS;
	}

	public static boolean contains(Skill skill)
	{
		return TRAINABLE_SKILLS.contains(skill);
	}
}
