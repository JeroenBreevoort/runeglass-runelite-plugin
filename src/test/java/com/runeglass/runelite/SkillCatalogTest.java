package com.runeglass.runelite;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SkillCatalogTest
{
	@Test
	public void matchesTheVersionOneWireOrder()
	{
		List<Skill> expected = Arrays.asList(
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
			Skill.SAILING);

		assertEquals(1, SkillCatalog.VERSION);
		assertEquals(24, SkillCatalog.trainableSkills().size());
		assertEquals(25, SkillCatalog.VECTOR_SIZE);
		assertEquals(expected, SkillCatalog.trainableSkills());
	}
}
