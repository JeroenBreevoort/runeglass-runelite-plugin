package com.runeglass.runelite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class QuestSnapshot
{
	private final String snapshotId = UUID.randomUUID().toString();
	private final String observedAt;
	private final Map<String, Object> progress;

	QuestSnapshot(Instant observedAt, int questPoints, Map<Integer, String> states)
	{
		if (questPoints < 0 || questPoints > 10_000 || states.isEmpty())
		{
			throw new IllegalArgumentException("Invalid semantic quest snapshot");
		}
		this.observedAt = observedAt.toString();
		List<Map<String, Object>> quests = new ArrayList<>();
		for (int id : QuestCatalog.ids())
		{
			String state = states.get(id);
			if (state == null) continue;
			if (!("not_started".equals(state) || "in_progress".equals(state) || "completed".equals(state)))
			{
				throw new IllegalArgumentException("Invalid quest state");
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", id);
			entry.put("state", state);
			quests.add(Collections.unmodifiableMap(entry));
		}
		if (quests.size() != states.size()) throw new IllegalArgumentException("Unknown quest ID");
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("catalogVersion", QuestCatalog.VERSION);
		value.put("coverage", states.size() == QuestCatalog.ids().length ? "complete" : "partial");
		value.put("questPoints", questPoints);
		value.put("quests", Collections.unmodifiableList(quests));
		progress = Collections.unmodifiableMap(value);
	}

	String getSnapshotId() { return snapshotId; }
	String getObservedAt() { return observedAt; }
	Map<String, Object> getProgress() { return progress; }
}
