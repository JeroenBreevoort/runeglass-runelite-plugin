/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks the wire format against Convex's hard limits before the backend exists.
 * <p>
 * The snapshot uses dynamic keys — skill names, quest names, diary labels — as object fields.
 * Convex restricts what a field name may contain, and caps document size, array length and
 * nesting depth. Discovering a violation after the backend is built would mean redesigning the
 * contract and migrating whatever had already been stored, so it is pinned down here instead.
 *
 * @see <a href="https://docs.convex.dev/production/state/limits">Convex limits</a>
 */
public class ConvexCompatibilityTest
{
	private static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
	private static final int MAX_ARRAY_LENGTH = 8192;
	private static final int MAX_NESTING_DEPTH = 16;

	/** Non-control alphanumeric ASCII and underscores, starting with a letter or underscore. */
	private static final Pattern VALID_FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	/** Generous: OSRS bank capacity is well under this even fully unlocked. */
	private static final int WORST_CASE_BANK_SLOTS = 2000;

	private final Gson gson = new Gson();

	@Test
	public void everySkillNameIsAValidConvexFieldName()
	{
		for (Skill skill : Skill.values())
		{
			assertValidFieldName(skill.name());
		}
	}

	@Test
	public void everyQuestNameIsAValidConvexFieldName()
	{
		// Quest names come from an enum, but they are the riskiest keys we emit — OSRS quest
		// titles contain punctuation and digits that could have survived into the constants.
		for (Quest quest : Quest.values())
		{
			assertValidFieldName(quest.name());
		}
	}

	@Test
	public void everyTrackedVarbitLabelIsAValidConvexFieldName()
	{
		for (String label : buildVarbitLabels().keySet())
		{
			assertValidFieldName(label);
		}
	}

	@Test
	public void aWorstCaseSnapshotFitsInsideOneConvexDocument()
	{
		final int bytes = serializedSize(worstCaseSnapshot());

		assertTrue(
			"worst-case snapshot is " + bytes + " bytes, over the 1 MiB document limit",
			bytes < MAX_DOCUMENT_BYTES);

		// Fitting is not enough; leave room for whatever the backend wraps around it.
		assertTrue(
			"snapshot uses " + (bytes * 100 / MAX_DOCUMENT_BYTES) + "% of a document, leaving little headroom",
			bytes < MAX_DOCUMENT_BYTES / 2);
	}

	@Test
	public void aFullIngestBatchFitsInsideOneConvexDocument()
	{
		final RuneGlassApi.IngestRequest request = new RuneGlassApi.IngestRequest();
		request.accountHash = "6041938472910385761";
		request.displayName = "Zezima";
		request.accountType = "IRONMAN";
		request.worldTypes = Collections.singletonList("MEMBERS");
		request.sessionId = "00000000-0000-0000-0000-000000000000";
		request.sentAt = System.currentTimeMillis();
		request.snapshot = worstCaseSnapshot();

		// A maximum batch of the largest event we actually emit: a full equipment change.
		// The bank is deliberately not an event — see ItemsCollector.
		final List<RuneGlassApi.Event> events = new ArrayList<>();
		for (int i = 0; i < EventQueue.MAX_BATCH; i++)
		{
			final Map<String, Object> data = new LinkedHashMap<>();
			data.put("container", "WORN");
			data.put("items", items(14));
			events.add(new RuneGlassApi.Event(i, System.currentTimeMillis(), RuneGlassApi.Kind.CONTAINER, data));
		}
		request.events = events;

		final int bytes = serializedSize(request);

		assertTrue(
			"a full batch plus snapshot is " + (bytes / 1024) + " KiB, over the Convex document limit",
			bytes < MAX_DOCUMENT_BYTES);
	}

	@Test
	public void noArrayExceedsTheConvexLimit()
	{
		final RuneGlassApi.Snapshot snapshot = worstCaseSnapshot();

		assertTrue(snapshot.bank.size() < MAX_ARRAY_LENGTH);
		assertTrue(snapshot.inventory.size() < MAX_ARRAY_LENGTH);
		assertTrue(snapshot.equipment.size() < MAX_ARRAY_LENGTH);
	}

	@Test
	public void nestingStaysWellInsideTheConvexLimit()
	{
		// ingest -> snapshot -> bank -> stack -> field is five levels; events add one more.
		assertTrue(depthOf(gson.toJsonTree(worstCaseSnapshot())) < MAX_NESTING_DEPTH);
	}

	@Test
	public void theQuestCountIsWhatWeThinkItIs()
	{
		// A sanity anchor: if RuneLite adds a pile of quests, the size assertions above should
		// be re-examined rather than silently drifting toward the limit.
		assertTrue("unexpectedly few quests: " + Quest.values().length, Quest.values().length > 100);
		assertTrue("unexpectedly many quests: " + Quest.values().length, Quest.values().length < 400);
	}

	// ------------------------------------------------------------------

	private RuneGlassApi.Snapshot worstCaseSnapshot()
	{
		final RuneGlassApi.Snapshot snapshot = new RuneGlassApi.Snapshot();
		snapshot.capturedAt = System.currentTimeMillis();

		final Map<String, Integer> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			skills.put(skill.name(), 200_000_000);
		}
		snapshot.skills = skills;

		final Map<String, String> quests = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			quests.put(quest.name(), "FINISHED");
		}
		snapshot.quests = quests;

		snapshot.varbits = buildVarbitLabels();
		snapshot.bank = bankItems();
		snapshot.inventory = items(28);
		snapshot.equipment = items(14);

		return snapshot;
	}

	private static List<RuneGlassApi.ItemStack> bankItems()
	{
		return items(WORST_CASE_BANK_SLOTS);
	}

	/**
	 * The regression guard for the bug this test found: a batch of bank-sized events is many times
	 * the document limit, which is why the bank travels in snapshots instead.
	 */
	@Test
	public void aBatchOfBankSizedEventsWouldNotFitWhichIsWhyBankIsSnapshotOnly()
	{
		final List<RuneGlassApi.Event> oversized = new ArrayList<>();
		for (int i = 0; i < EventQueue.MAX_BATCH; i++)
		{
			final Map<String, Object> data = new LinkedHashMap<>();
			data.put("container", "BANK");
			data.put("items", bankItems());
			oversized.add(new RuneGlassApi.Event(i, 0L, RuneGlassApi.Kind.CONTAINER, data));
		}

		assertTrue(
			"if this ever fits, the reasoning in ItemsCollector should be revisited",
			serializedSize(oversized) > MAX_DOCUMENT_BYTES);
	}

	private static List<RuneGlassApi.ItemStack> items(int count)
	{
		final List<RuneGlassApi.ItemStack> items = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			// Large ids and quantities, so the size estimate is pessimistic rather than rosy.
			items.add(new RuneGlassApi.ItemStack(29_000 + i, 2_000_000_000, i));
		}
		return items;
	}

	/** Mirrors ProgressCollector's table without needing a game client. */
	private static Map<String, Integer> buildVarbitLabels()
	{
		final Map<String, Integer> labels = new LinkedHashMap<>();
		final String[] regions = {
			"ARDOUGNE", "DESERT", "FALADOR", "FREMENNIK", "KANDARIN", "KARAMJA",
			"KOUREND", "LUMBRIDGE", "MORYTANIA", "VARROCK", "WESTERN", "WILDERNESS",
		};
		for (String region : regions)
		{
			for (String tier : new String[]{"EASY", "MEDIUM", "HARD", "ELITE"})
			{
				labels.put(region + "_" + tier, 1);
			}
		}
		for (String tier : new String[]{"EASY", "MEDIUM", "HARD", "ELITE", "MASTER", "GRANDMASTER"})
		{
			labels.put("CA_" + tier, 2);
		}

		assertEquals("must mirror ProgressCollector", ProgressCollector.trackedVarbitCount(), labels.size());
		return labels;
	}

	private int serializedSize(Object value)
	{
		return gson.toJson(value).getBytes(StandardCharsets.UTF_8).length;
	}

	private static void assertValidFieldName(String name)
	{
		assertTrue(
			"'" + name + "' is not a valid Convex field name",
			VALID_FIELD_NAME.matcher(name).matches());
		assertTrue(
			"'" + name + "' would collide with a Convex system field",
			!name.startsWith("_"));
	}

	private static int depthOf(com.google.gson.JsonElement element)
	{
		if (element.isJsonObject())
		{
			int deepest = 0;
			for (Map.Entry<String, com.google.gson.JsonElement> entry : element.getAsJsonObject().entrySet())
			{
				deepest = Math.max(deepest, depthOf(entry.getValue()));
			}
			return deepest + 1;
		}

		if (element.isJsonArray())
		{
			int deepest = 0;
			for (com.google.gson.JsonElement child : element.getAsJsonArray())
			{
				deepest = Math.max(deepest, depthOf(child));
			}
			return deepest + 1;
		}

		return 1;
	}
}
