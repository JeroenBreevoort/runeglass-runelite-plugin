/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class EventQueueTest
{
	private EventQueue queue;

	@Before
	public void setUp()
	{
		queue = new EventQueue();
	}

	@Test
	public void assignsMonotonicSequenceNumbersFromZero()
	{
		offer(3);

		final List<RuneGlassApi.Event> batch = queue.peekBatch();
		assertEquals(0L, batch.get(0).seq);
		assertEquals(1L, batch.get(1).seq);
		assertEquals(2L, batch.get(2).seq);
	}

	@Test
	public void peekingDoesNotConsume()
	{
		offer(2);

		assertEquals(2, queue.peekBatch().size());
		assertEquals("a failed send must be able to retry the same events", 2, queue.peekBatch().size());
		assertEquals(2, queue.size());
	}

	@Test
	public void ackRemovesOnlyConfirmedEvents()
	{
		offer(5);

		queue.ackThrough(2L);

		final List<RuneGlassApi.Event> remaining = queue.peekBatch();
		assertEquals(2, remaining.size());
		assertEquals(3L, remaining.get(0).seq);
		assertEquals(4L, remaining.get(1).seq);
	}

	@Test
	public void ackingBeyondTheBufferEmptiesIt()
	{
		offer(3);
		queue.ackThrough(99L);
		assertTrue(queue.isEmpty());
	}

	@Test
	public void ackingAlreadyAckedSequenceIsHarmless()
	{
		offer(3);
		queue.ackThrough(1L);
		queue.ackThrough(1L);

		assertEquals(1, queue.size());
	}

	@Test
	public void batchesAreCappedSoRequestsStaySmall()
	{
		offer(EventQueue.MAX_BATCH + 50);

		assertEquals(EventQueue.MAX_BATCH, queue.peekBatch().size());
	}

	@Test
	public void overflowDropsOldestAndKeepsNewest()
	{
		offer(EventQueue.MAX_EVENTS + 10);

		assertEquals("buffer must stay bounded", EventQueue.MAX_EVENTS, queue.size());

		// The ten oldest were dropped, so the front of the queue has moved forward by ten.
		assertEquals(10L, queue.peekBatch().get(0).seq);
	}

	@Test
	public void overflowDoesNotDisturbSequenceNumbering()
	{
		offer(EventQueue.MAX_EVENTS + 10);

		// Sequence numbers must keep climbing across a drop, so the backend still sees the gap.
		assertEquals(EventQueue.MAX_EVENTS + 10L, queue.nextSeq());
	}

	@Test
	public void resetClearsTheBufferAndRestartsNumbering()
	{
		offer(5);

		queue.reset();

		assertTrue(queue.isEmpty());
		assertEquals("seq is only meaningful within a session", 0L, queue.nextSeq());
	}

	@Test
	public void emptyQueueYieldsAnEmptyBatch()
	{
		assertTrue(queue.peekBatch().isEmpty());
		assertTrue(queue.isEmpty());
	}

	private void offer(int count)
	{
		for (int i = 0; i < count; i++)
		{
			queue.offer(RuneGlassApi.Kind.LEVEL_UP, Collections.singletonMap("n", i));
		}
	}
}
