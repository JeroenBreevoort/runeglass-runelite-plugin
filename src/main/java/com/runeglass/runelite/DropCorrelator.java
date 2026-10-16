package com.runeglass.runelite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class DropCorrelator
{
	static final int WINDOW = 5;
	static final int LIMIT = 64;
	static final long ORDINARY_NANOS = 12_000_000_000L;
	static final long RAID_NANOS = 30 * 60_000_000_000L;
	private final List<Pending> drops = new ArrayList<>();
	private final List<CounterMessage> counts = new ArrayList<>();
	private final List<LogMessage> logs = new ArrayList<>();
	private String setting = "unknown";
	private long settingFrom;
	private String encounter;
	private long uncertainUntil = Long.MIN_VALUE;

	void context(long tick, String nextSetting, String nextEncounter)
	{
		if (!Objects.equals(setting, nextSetting))
		{
			setting = nextSetting;
			settingFrom = tick;
			for (Pending drop : drops) drop.setting = "unknown";
		}
		if (!Objects.equals(encounter, nextEncounter))
		{
			counts.removeIf(message -> message.count.counter.raid());
			for (Pending drop : drops) if (drop.encounter != null) drop.countAmbiguous = true;
			encounter = nextEncounter;
		}
	}

	void count(DropCountParser.Count count, long tick, long nanos, String raidEncounter)
	{
		if (count == null || (count.counter.raid() && (raidEncounter == null || !raidEncounter.startsWith(count.counter.raidFamily() + ":")))) return;
		if (counts.stream().anyMatch(m -> m.tick == tick && m.count.value == count.value && m.count.counter.key.equals(count.counter.key))) return;
		if (counts.size() == LIMIT)
		{
			counts.clear();
			uncertainUntil = tick + 2 * WINDOW;
			for (Pending drop : drops) drop.countAmbiguous = true;
		}
		counts.add(new CounterMessage(count, tick, nanos, raidEncounter));
		markAmbiguities();
	}

	void collection(String name, long tick, long nanos)
	{
		if (name == null) return;
		if (logs.stream().anyMatch(m -> m.tick == tick && m.name.equals(name))) return;
		if (logs.size() == LIMIT)
		{
			logs.clear();
			uncertainUntil = tick + 2 * WINDOW;
			for (Pending drop : drops) { drop.logAmbiguous = true; drop.setting = "unknown"; }
		}
		logs.add(new LogMessage(name, tick, nanos));
		markAmbiguities();
	}

	void loot(DropCaptureResult result, long tick, long nanos, String raidEncounter, Consumer<Completed> publish)
	{
		if (!result.observation().isPresent()) return;
		if (drops.size() == LIMIT)
		{
			flush(publish);
			uncertainUntil = tick + 2 * WINDOW;
		}
		Pending drop = new Pending(result, tick, nanos, raidEncounter,
			tick - settingFrom >= WINDOW ? setting : "unknown");
		if (tick <= uncertainUntil) { drop.countAmbiguous = true; drop.logAmbiguous = true; drop.setting = "unknown"; }
		drops.add(drop);
		markAmbiguities();
	}

	void advance(long tick, long nanos, Consumer<Completed> publish)
	{
		markAmbiguities();
		for (Pending drop : new ArrayList<>(drops))
		{
			if (tick - drop.tick > 2 * WINDOW || nanos - drop.nanos > ORDINARY_NANOS)
			{
				publish.accept(tick - drop.tick > 2 * WINDOW ? finish(drop) : new Completed(drop.result, null, "unknown", Set.of()));
				drops.remove(drop);
			}
		}
		counts.removeIf(m -> m.count.counter.raid()
			? nanos - m.nanos > RAID_NANOS
			: tick - m.tick > 3 * WINDOW + 1 || nanos - m.nanos > 2 * ORDINARY_NANOS);
		logs.removeIf(m -> tick - m.tick > 3 * WINDOW + 1 || nanos - m.nanos > 2 * ORDINARY_NANOS);
	}

	void flush(Consumer<Completed> publish)
	{
		for (Pending drop : drops) publish.accept(new Completed(drop.result, null, "unknown", Set.of()));
		clear();
	}

	void clear()
	{
		drops.clear(); counts.clear(); logs.clear(); encounter = null; setting = "unknown"; uncertainUntil = Long.MIN_VALUE;
	}

	private Completed finish(Pending drop)
	{
		List<CounterMessage> candidates = candidates(drop);
		CounterMessage candidate = candidates.size() == 1 ? candidates.get(0) : null;
		DropCountParser.Count count = null;
		if (!drop.countAmbiguous && candidate != null && !candidate.ambiguous && !candidate.consumed)
		{
			candidate.consumed = true;
			count = candidate.count;
		}
		Set<Integer> newSlots = new HashSet<>();
		boolean uncertainLog = drop.logAmbiguous;
		for (LogMessage log : logs)
		{
			if (!matches(drop, log)) continue;
			List<Integer> ids = drop.event().itemIdsNamed(log.name);
			if (!log.ambiguous && !log.consumed && !drop.logAmbiguous && ids.size() == 1)
			{
				newSlots.add(ids.get(0)); log.consumed = true;
			}
			else uncertainLog = true;
		}
		return new Completed(drop.result, count, uncertainLog ? "unknown" : drop.setting, newSlots);
	}

	private void markAmbiguities()
	{
		for (Pending drop : drops)
		{
			List<CounterMessage> matches = candidates(drop);
			if (matches.size() > 1)
			{
				drop.countAmbiguous = true;
				for (CounterMessage message : matches) message.ambiguous = true;
			}
		}
		for (CounterMessage message : counts)
		{
			List<Pending> matches = new ArrayList<>();
			for (Pending drop : drops) if (matches(drop, message)) matches.add(drop);
			if (matches.size() > 1)
			{
				message.ambiguous = true;
				for (Pending drop : matches) drop.countAmbiguous = true;
			}
		}
		for (LogMessage log : logs)
		{
			List<Pending> matches = new ArrayList<>();
			for (Pending drop : drops) if (matches(drop, log)) matches.add(drop);
			if (matches.size() > 1 || logs.stream().anyMatch(other -> other != log && other.name.equals(log.name) && Math.abs(other.tick - log.tick) <= 2 * WINDOW))
			{
				log.ambiguous = true;
				for (Pending drop : matches) { drop.logAmbiguous = true; drop.setting = "unknown"; }
			}
		}
	}

	private List<CounterMessage> candidates(Pending drop)
	{
		List<CounterMessage> matches = new ArrayList<>();
		for (CounterMessage message : counts) if (matches(drop, message)) matches.add(message);
		return matches;
	}

	private boolean matches(Pending drop, CounterMessage message)
	{
		DropEvent event = drop.event();
		if (event.rollCount() != 1 || !message.count.counter.matches(event.sourceType(), event.sourceName())) return false;
		if (message.count.counter.raid()) return drop.encounter != null && drop.encounter.equals(message.encounter)
			&& drop.nanos - message.nanos <= RAID_NANOS && message.tick - drop.tick <= WINDOW
			&& message.nanos - drop.nanos <= ORDINARY_NANOS;
		return Math.abs(drop.tick - message.tick) <= WINDOW && Math.abs(drop.nanos - message.nanos) <= ORDINARY_NANOS;
	}

	private boolean matches(Pending drop, LogMessage log)
	{
		return Math.abs(drop.tick - log.tick) <= WINDOW && Math.abs(drop.nanos - log.nanos) <= ORDINARY_NANOS
			&& !drop.event().itemIdsNamed(log.name).isEmpty();
	}

	static final class Completed
	{
		final DropCaptureResult result;
		final DropCountParser.Count count;
		final String collectionLog;
		final Set<Integer> newSlots;
		Completed(DropCaptureResult result, DropCountParser.Count count, String collectionLog, Set<Integer> newSlots)
		{
			this.result = result; this.count = count; this.collectionLog = collectionLog; this.newSlots = Set.copyOf(newSlots);
		}
	}

	private static final class Pending
	{
		final DropCaptureResult result;
		final long tick;
		final long nanos;
		final String encounter;
		String setting;
		boolean countAmbiguous;
		boolean logAmbiguous;
		Pending(DropCaptureResult result, long tick, long nanos, String encounter, String setting)
		{ this.result = result; this.tick = tick; this.nanos = nanos; this.encounter = encounter; this.setting = setting; }
		DropEvent event() { return result.observation().get(); }
	}
	private static final class CounterMessage
	{
		final DropCountParser.Count count;
		final long tick;
		final long nanos;
		final String encounter;
		boolean ambiguous;
		boolean consumed;
		CounterMessage(DropCountParser.Count count, long tick, long nanos, String encounter)
		{ this.count = count; this.tick = tick; this.nanos = nanos; this.encounter = encounter; }
	}
	private static final class LogMessage
	{
		final String name;
		final long tick;
		final long nanos;
		boolean ambiguous;
		boolean consumed;
		LogMessage(String name, long tick, long nanos) { this.name = name; this.tick = tick; this.nanos = nanos; }
	}
}
