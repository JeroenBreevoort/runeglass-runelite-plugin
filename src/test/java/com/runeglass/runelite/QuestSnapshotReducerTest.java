package com.runeglass.runelite;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestSnapshotReducerTest
{
	@Test
	public void explicitCatalogMatchesPinnedRuneLiteApiIncludingNewQuests()
	{
		Set<Integer> ids = Arrays.stream(Quest.values()).map(Quest::getId).collect(Collectors.toSet());
		assertEquals(213, QuestCatalog.ids().length);
		for (int id : QuestCatalog.ids()) assertTrue("Missing Quest ID " + id, ids.contains(id));
		assertTrue(ids.contains(16971));
		assertTrue(ids.contains(16972));
	}

	@Test
	public void capturesStableBaselineThenOnlyDebouncedDirtyChangesAtBoundedFrequency()
	{
		QuestSnapshotReducer reducer = new QuestSnapshotReducer();
		assertFalse(reducer.shouldCapture()); assertFalse(reducer.shouldCapture());
		assertTrue(reducer.shouldCapture());
		for (int i = 0; i < 100; i++) assertFalse(reducer.shouldCapture());
		reducer.markDirty();
		assertFalse(reducer.shouldCapture()); assertFalse(reducer.shouldCapture());
		assertTrue(reducer.shouldCapture());
		int captures = 0;
		for (int i = 0; i < 200; i++) { reducer.markDirty(); if (reducer.shouldCapture()) captures++; }
		assertEquals(4, captures);
		reducer.reset();
		assertFalse(reducer.shouldCapture()); assertFalse(reducer.shouldCapture()); assertTrue(reducer.shouldCapture());
	}

	@Test
	public void unchangedSemanticBodyIsNotPublishedAndPartialMissingEntriesStayAbsent()
	{
		QuestSnapshotReducer reducer = new QuestSnapshotReducer();
		Map<Integer, String> states = new LinkedHashMap<>();
		states.put(17, "not_started");
		Instant now = Instant.parse("2026-10-07T12:00:00Z");
		QuestSnapshot initial = reducer.observe(now, 0, states).get();
		assertEquals("partial", initial.getProgress().get("coverage"));
		assertFalse(reducer.observe(now.plusSeconds(1), 0, states).isPresent());
		states.put(17, "completed");
		assertTrue(reducer.observe(now.plusSeconds(2), 1, states).isPresent());
		reducer.reset();
		assertTrue(reducer.observe(now.plusSeconds(3), 1, states).isPresent());
	}

	@Test
	public void manualRefreshEmitsUnchangedProgressWithANewIdentityAndObservationTime()
	{
		QuestSnapshotReducer reducer = new QuestSnapshotReducer();
		Instant now = Instant.parse("2026-10-07T12:00:00Z");
		Map<Integer, String> states = Map.of(17, "completed");
		QuestSnapshot initial = reducer.observe(now, 1, states).get();
		QuestSnapshot manual = reducer.observeManual(now.plusSeconds(30), 1, states);
		assertEquals(initial.getProgress(), manual.getProgress());
		assertNotEquals(initial.getSnapshotId(), manual.getSnapshotId());
		assertEquals(now.plusSeconds(30).toString(), manual.getObservedAt());
		assertFalse(reducer.observe(now.plusSeconds(31), 1, states).isPresent());
	}

	@Test
	public void manualChangesUpdateAutomaticDedupeAndChangedQuestPointsStillEmit()
	{
		QuestSnapshotReducer reducer = new QuestSnapshotReducer();
		Instant now = Instant.parse("2026-10-07T12:00:00Z");
		assertTrue(reducer.observe(now, 0, Map.of(17, "not_started")).isPresent());
		reducer.observeManual(now.plusSeconds(1), 1, Map.of(17, "completed"));
		assertFalse(reducer.observe(now.plusSeconds(2), 1, Map.of(17, "completed")).isPresent());
		QuestSnapshot changed = reducer.observe(now.plusSeconds(3), 2, Map.of(17, "completed")).get();
		assertEquals(2, changed.getProgress().get("questPoints"));
	}

	@Test
	public void rejectsUnknownIdsAndMalformedState()
	{
		assertThrows(IllegalArgumentException.class, () -> new QuestSnapshot(Instant.now(), 0, Map.of(999999, "completed")));
		assertThrows(IllegalArgumentException.class, () -> new QuestSnapshot(Instant.now(), 0, Map.of(17, "unknown")));
		assertThrows(IllegalArgumentException.class, () -> new QuestSnapshot(Instant.now(), -1, Map.of(17, "not_started")));
	}
}
