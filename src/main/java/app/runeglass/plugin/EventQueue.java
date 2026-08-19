/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Buffers events between the client thread that produces them and the flush task that ships them.
 * <p>
 * Events are held until the backend acknowledges them, so a failed request is retried rather than
 * losing data. The buffer is bounded: if the backend stays unreachable, the oldest events are
 * dropped rather than growing without limit. Losing the oldest is the right trade — the newest
 * events are the ones the app is showing.
 */
@Slf4j
public class EventQueue
{
	/**
	 * At the cadence the collectors run, this is many minutes of buffer — comfortably longer than
	 * any transient outage, and small enough that a stuck client cannot eat meaningful memory.
	 */
	static final int MAX_EVENTS = 500;

	/** Keeps a single request small enough to stay well inside any sane body limit. */
	static final int MAX_BATCH = 100;

	private final Deque<RuneGlassApi.Event> pending = new ArrayDeque<>();

	private long nextSeq;
	private long droppedSinceLastWarning;

	/**
	 * Adds an event, assigning it the next sequence number. Called from the client thread.
	 */
	public synchronized void offer(String kind, Map<String, Object> data)
	{
		if (pending.size() >= MAX_EVENTS)
		{
			pending.removeFirst();
			droppedSinceLastWarning++;

			// One line per 100 drops: enough to diagnose, not enough to flood a user's log.
			if (droppedSinceLastWarning % 100 == 1)
			{
				log.warn("RuneGlass event buffer full, dropping oldest events ({} dropped so far)",
					droppedSinceLastWarning);
			}
		}

		pending.addLast(new RuneGlassApi.Event(nextSeq++, System.currentTimeMillis(), kind, data));
	}

	/**
	 * Returns the oldest events without removing them, so a failed send can be retried.
	 */
	public synchronized List<RuneGlassApi.Event> peekBatch()
	{
		if (pending.isEmpty())
		{
			return Collections.emptyList();
		}

		final List<RuneGlassApi.Event> batch = new ArrayList<>(Math.min(pending.size(), MAX_BATCH));
		for (RuneGlassApi.Event event : pending)
		{
			if (batch.size() >= MAX_BATCH)
			{
				break;
			}
			batch.add(event);
		}
		return batch;
	}

	/**
	 * Discards everything the backend has confirmed it stored.
	 */
	public synchronized void ackThrough(long seq)
	{
		while (!pending.isEmpty() && pending.peekFirst().seq <= seq)
		{
			pending.removeFirst();
		}
	}

	/**
	 * Starts a new session: clears the buffer and restarts numbering, because {@code seq} is only
	 * meaningful within a session.
	 */
	public synchronized void reset()
	{
		pending.clear();
		nextSeq = 0;
		droppedSinceLastWarning = 0;
	}

	public synchronized int size()
	{
		return pending.size();
	}

	public synchronized boolean isEmpty()
	{
		return pending.isEmpty();
	}

	/** Sequence number the next offered event will receive. Exposed for tests and diagnostics. */
	synchronized long nextSeq()
	{
		return nextSeq;
	}
}
