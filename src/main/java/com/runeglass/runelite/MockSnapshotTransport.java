package com.runeglass.runelite;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class MockSnapshotTransport
{
	private static final int MAX_SNAPSHOTS = 16;

	private final Function<SkillsSnapshot, String> serializer;
	private final Deque<String> payloads = new ArrayDeque<>();

	@Inject
	public MockSnapshotTransport(Gson gson)
	{
		this(Objects.requireNonNull(gson, "gson")::toJson);
	}

	MockSnapshotTransport(Function<SkillsSnapshot, String> serializer)
	{
		this.serializer = Objects.requireNonNull(serializer, "serializer");
	}

	public synchronized void publish(SkillsSnapshot snapshot)
	{
		Objects.requireNonNull(snapshot, "snapshot");
		String payload = Objects.requireNonNull(serializer.apply(snapshot), "serialized payload");
		while (payloads.size() >= MAX_SNAPSHOTS)
		{
			payloads.removeFirst();
		}
		payloads.addLast(payload);
	}

	public synchronized Optional<String> latestPayload()
	{
		return Optional.ofNullable(payloads.peekLast());
	}

	public synchronized int snapshotCount()
	{
		return payloads.size();
	}

	public synchronized void clear()
	{
		payloads.clear();
	}
}
