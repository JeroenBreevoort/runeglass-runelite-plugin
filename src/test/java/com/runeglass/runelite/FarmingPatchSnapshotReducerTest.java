package com.runeglass.runelite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FarmingPatchSnapshotReducerTest
{
	private static final Instant OBSERVED_AT = Instant.parse("2026-08-28T12:00:00Z");

	@Test
	public void emitsAStableSemanticGrowingHerbObservation()
	{
		FarmingPatchSnapshotReducer reducer = new FarmingPatchSnapshotReducer();
		assertFalse(reducer.observe(FarmingPatchLocation.ARDOUGNE_HERB, 32, OBSERVED_AT).isPresent());
		FarmingPatchObservation patch = reducer
			.observe(FarmingPatchLocation.ARDOUGNE_HERB, 32, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);

		assertEquals("herb", patch.getPatchType());
		assertEquals("ardougne_herb", patch.getLocation());
		assertEquals("growing", patch.getState());
		assertEquals("ranarr", patch.getCrop());
		assertEquals(Integer.valueOf(0), patch.getStage());
		assertEquals(Integer.valueOf(5), patch.getStages());
		assertEquals(
			OBSERVED_AT.plusSeconds(1 + 80 * 60).toString(),
			patch.getReadyAt());
		assertTrue(patch.getEstimated());
	}

	@Test
	public void decodesNormalFruitAndHardwoodTreesWithTheirOwnTicks()
	{
		FarmingPatchSnapshotReducer reducer = new FarmingPatchSnapshotReducer();

		assertFalse(reducer.observe(FarmingPatchLocation.VARROCK_TREE, 35, OBSERVED_AT).isPresent());
		FarmingPatchObservation yew = reducer
			.observe(FarmingPatchLocation.VARROCK_TREE, 35, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals("tree", yew.getPatchType());
		assertEquals("yew", yew.getCrop());
		assertEquals(Integer.valueOf(0), yew.getStage());
		assertEquals(OBSERVED_AT.plusSeconds(1 + 400 * 60).toString(), yew.getReadyAt());

		assertFalse(reducer.observe(FarmingPatchLocation.BRIMHAVEN_FRUIT_TREE, 200, OBSERVED_AT).isPresent());
		FarmingPatchObservation palm = reducer
			.observe(FarmingPatchLocation.BRIMHAVEN_FRUIT_TREE, 200, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals("fruit_tree", palm.getPatchType());
		assertEquals("palm", palm.getCrop());
		assertEquals(OBSERVED_AT.plusSeconds(1 + 960 * 60).toString(), palm.getReadyAt());

		assertFalse(reducer.observe(FarmingPatchLocation.FOSSIL_HARDWOOD_EAST, 8, OBSERVED_AT).isPresent());
		FarmingPatchObservation teak = reducer
			.observe(FarmingPatchLocation.FOSSIL_HARDWOOD_EAST, 8, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals("hardwood_tree", teak.getPatchType());
		assertEquals("teak", teak.getCrop());
		assertEquals(OBSERVED_AT.plusSeconds(1 + 4480 * 60).toString(), teak.getReadyAt());
	}

	@Test
	public void emitsTerminalStatesWithoutTimersAndHealthChecksReadyNow()
	{
		FarmingPatchSnapshotReducer reducer = new FarmingPatchSnapshotReducer();
		reducer.observe(FarmingPatchLocation.FARMING_GUILD_FRUIT_TREE, 253, OBSERVED_AT);
		FarmingPatchObservation readyNow = reducer
			.observe(FarmingPatchLocation.FARMING_GUILD_FRUIT_TREE, 253, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals("growing", readyNow.getState());
		assertEquals("dragonfruit", readyNow.getCrop());
		assertEquals(Integer.valueOf(6), readyNow.getStage());
		assertEquals(OBSERVED_AT.plusSeconds(1).toString(), readyNow.getReadyAt());

		FarmingPatchObservation diseased = reducer
			.observe(FarmingPatchLocation.FARMING_GUILD_FRUIT_TREE, 240, OBSERVED_AT.plusSeconds(2))
			.orElseThrow(AssertionError::new);
		assertEquals("diseased", diseased.getState());
		assertNull(diseased.getReadyAt());

		FarmingPatchObservation dead = reducer
			.observe(FarmingPatchLocation.FARMING_GUILD_FRUIT_TREE, 246, OBSERVED_AT.plusSeconds(3))
			.orElseThrow(AssertionError::new);
		assertEquals("dead", dead.getState());
		assertEquals("dragonfruit", dead.getCrop());

		FarmingPatchObservation empty = reducer
			.observe(FarmingPatchLocation.FARMING_GUILD_FRUIT_TREE, 254, OBSERVED_AT.plusSeconds(4))
			.orElseThrow(AssertionError::new);
		assertEquals("empty", empty.getState());
		assertNull(empty.getCrop());
	}

	@Test
	public void discoversEveryPatchInSharedRegionsAndHonorsBounds()
	{
		assertLocations(
			FarmingPatchLocation.forWorldPoint(new WorldPoint(1248, 3720, 0)),
			"guild_allotment_n", "guild_allotment_s", "guild_flower", "guild_herb",
			"guild_tree", "guild_fruit_tree", "guild_redwood", "guild_spirit_tree",
			"guild_celastrus", "guild_bush", "guild_cactus", "guild_anima");
		assertLocations(
			FarmingPatchLocation.forWorldPoint(new WorldPoint(2440, 3400, 0)),
			"gnome_tree", "gnome_fruit_tree");
		assertLocations(
			FarmingPatchLocation.forWorldPoint(new WorldPoint(3700, 3800, 0)),
			"fossil_hardwood_e", "fossil_hardwood_m", "fossil_hardwood_w");
		assertLocations(
			FarmingPatchLocation.forWorldPoint(new WorldPoint(2820, 3445, 0)),
			"catherby_allotment_n", "catherby_allotment_s", "catherby_flower", "catherby_herb");
		assertLocations(
			FarmingPatchLocation.forWorldPoint(new WorldPoint(2845, 3445, 0)),
			"catherby_fruit_tree");
		assertTrue(FarmingPatchLocation.forWorldPoint(new WorldPoint(3753, 3869, 0)).isEmpty());
	}

	@Test
	public void decodesEveryAdditionalPlantPatchType()
	{
		assertDecoded(FarmingPatchType.ALLOTMENT, 6, FarmingPatchState.GROWING, FarmingCrop.POTATO, 0);
		assertDecoded(FarmingPatchType.FLOWER, 32, FarmingPatchState.HARVESTABLE, FarmingCrop.LIMPWURT, 0);
		assertDecoded(FarmingPatchType.BUSH, 225, FarmingPatchState.DISEASED, FarmingCrop.POISON_IVY, 8);
		assertDecoded(FarmingPatchType.HOPS, 96, FarmingPatchState.GROWING, FarmingCrop.FLAX, 0);
		assertDecoded(FarmingPatchType.REDWOOD, 37, FarmingPatchState.GROWING, FarmingCrop.REDWOOD, 10);
		assertDecoded(FarmingPatchType.SPIRIT_TREE, 20, FarmingPatchState.GROWING, FarmingCrop.SPIRIT_TREE, 12);
		assertDecoded(FarmingPatchType.CALQUAT, 26, FarmingPatchState.DEAD, FarmingCrop.CALQUAT, 1);
		assertDecoded(FarmingPatchType.CELASTRUS, 13, FarmingPatchState.GROWING, FarmingCrop.CELASTRUS, 5);
		assertDecoded(FarmingPatchType.CRYSTAL_TREE, 15, FarmingPatchState.HARVESTABLE,
			FarmingCrop.CRYSTAL_TREE, 0);
		assertDecoded(FarmingPatchType.GRAPES, 10, FarmingPatchState.GROWING, FarmingCrop.GRAPE, 7);
		assertDecoded(FarmingPatchType.MUSHROOM, 16, FarmingPatchState.DISEASED, FarmingCrop.MUSHROOM, 1);
		assertDecoded(FarmingPatchType.HESPORI, 7, FarmingPatchState.HARVESTABLE, FarmingCrop.HESPORI, 0);
		assertDecoded(FarmingPatchType.BELLADONNA, 4, FarmingPatchState.GROWING, FarmingCrop.BELLADONNA, 0);
		assertDecoded(FarmingPatchType.CACTUS, 32, FarmingPatchState.GROWING, FarmingCrop.POTATO_CACTUS, 0);
		assertDecoded(FarmingPatchType.ANIMA, 17, FarmingPatchState.GROWING, FarmingCrop.IASOR, 0);
		assertDecoded(FarmingPatchType.CORAL, 30, FarmingPatchState.GROWING, FarmingCrop.UMBRAL, 4);
		assertDecoded(FarmingPatchType.SEAWEED, 8, FarmingPatchState.HARVESTABLE, FarmingCrop.SEAWEED, 0);
	}

	@Test
	public void usesCropSpecificTicksWithinSharedPatchTypes()
	{
		FarmingPatchSnapshotReducer reducer = new FarmingPatchSnapshotReducer();
		reducer.observe(FarmingPatchLocation.LUMBRIDGE_HOPS, 96, OBSERVED_AT);
		FarmingPatchObservation flax = reducer
			.observe(FarmingPatchLocation.LUMBRIDGE_HOPS, 96, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals(OBSERVED_AT.plusSeconds(1 + 60 * 60).toString(), flax.getReadyAt());

		reducer.observe(FarmingPatchLocation.GUILD_CACTUS, 32, OBSERVED_AT);
		FarmingPatchObservation potatoCactus = reducer
			.observe(FarmingPatchLocation.GUILD_CACTUS, 32, OBSERVED_AT.plusSeconds(1))
			.orElseThrow(AssertionError::new);
		assertEquals(OBSERVED_AT.plusSeconds(1 + 70 * 60).toString(), potatoCactus.getReadyAt());
	}

	@Test
	public void matchesRuneLiteSemanticDecodingAcrossEveryVarbitValue()
	{
		for (FarmingPatchType patchType : FarmingPatchType.values())
		{
			for (int value = 0; value <= 255; value++)
			{
				assertEquals(
					patchType + " value " + value,
					decodeRuneLite(patchType, value),
					encode(FarmingPatchDecoder.decode(patchType, value)));
			}
		}
	}

	@Test
	public void ignoresUnknownValuesAndUnchangedState()
	{
		FarmingPatchSnapshotReducer reducer = new FarmingPatchSnapshotReducer();
		assertFalse(reducer.observe(FarmingPatchLocation.ARDOUGNE_HERB, 120, OBSERVED_AT).isPresent());
		assertFalse(reducer.observe(FarmingPatchLocation.VARROCK_TREE, 76, OBSERVED_AT).isPresent());
		assertFalse(reducer.observe(FarmingPatchLocation.ARDOUGNE_HERB, 32, OBSERVED_AT).isPresent());
		assertTrue(reducer.observe(FarmingPatchLocation.ARDOUGNE_HERB, 32, OBSERVED_AT.plusSeconds(1)).isPresent());
		assertFalse(reducer.observe(FarmingPatchLocation.ARDOUGNE_HERB, 32, OBSERVED_AT.plusSeconds(2)).isPresent());
	}

	private static void assertLocations(List<FarmingPatchLocation> actual, String... expected)
	{
		assertEquals(
			Arrays.asList(expected),
			actual.stream().map(FarmingPatchLocation::getWireName).collect(Collectors.toList()));
	}

	private static void assertDecoded(
		FarmingPatchType patchType,
		int value,
		FarmingPatchState state,
		FarmingCrop crop,
		int stage)
	{
		FarmingPatchDecoder.DecodedState decoded = FarmingPatchDecoder.decode(patchType, value);
		assertEquals(state, decoded.state);
		assertEquals(crop, decoded.crop);
		assertEquals(Integer.valueOf(stage), decoded.stage);
		assertEquals(Integer.valueOf(crop.getStages()), decoded.stages);
	}

	private static String encode(FarmingPatchDecoder.DecodedState decoded)
	{
		if (decoded == null)
		{
			return null;
		}
		if (decoded.state == FarmingPatchState.EMPTY)
		{
			return "EMPTY||";
		}
		String stage = decoded.state == FarmingPatchState.GROWING
			? decoded.stage.toString()
			: "";
		return decoded.state.name() + "|" + (decoded.crop == null ? "" : decoded.crop.name()) + "|" + stage;
	}

	private static String decodeRuneLite(FarmingPatchType patchType, int value)
	{
		try
		{
			Class<?> implementationClass = Class.forName(
				"net.runelite.client.plugins.timetracking.farming.PatchImplementation");
			@SuppressWarnings({"rawtypes", "unchecked"})
			Object implementation = Enum.valueOf((Class) implementationClass, patchType.name());
			Method decode = implementationClass.getDeclaredMethod("forVarbitValue", int.class);
			decode.setAccessible(true);
			Object state = decode.invoke(implementation, value);
			if (state == null)
			{
				return null;
			}
			Object produce = invoke(state, "getProduce");
			String crop = normalizeRuneLiteCrop(((Enum<?>) produce).name());
			if ("WEEDS".equals(crop) || "SCARECROW".equals(crop))
			{
				return "EMPTY||";
			}
			Object cropState = invoke(state, "getCropState");
			String stateName = ((Enum<?>) cropState).name();
			String stage = "GROWING".equals(stateName)
				? invoke(state, "getStage").toString()
				: "";
			return stateName + "|" + crop + "|" + stage;
		}
		catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException error)
		{
			throw new AssertionError(error);
		}
	}

	private static Object invoke(Object target, String methodName)
		throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
	{
		Method method = target.getClass().getDeclaredMethod(methodName);
		method.setAccessible(true);
		return method.invoke(target);
	}

	private static String normalizeRuneLiteCrop(String crop)
	{
		switch (crop)
		{
			case "ANYHERB":
				return "";
			case "REDBERRIES":
				return "REDBERRY";
			case "CADAVABERRIES":
				return "CADAVABERRY";
			case "DWELLBERRIES":
				return "DWELLBERRY";
			case "JANGERBERRIES":
				return "JANGERBERRY";
			case "WHITEBERRIES":
				return "WHITEBERRY";
			case "ELKHORN_CORAL":
				return "ELKHORN";
			case "PILLAR_CORAL":
				return "PILLAR";
			case "UMBRAL_CORAL":
				return "UMBRAL";
			default:
				return crop;
		}
	}
}
