package com.runeglass.runelite;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DurableSnapshotQueueTest
{
	private static final Instant STARTED_AT = Instant.parse("2026-08-25T08:00:00Z");

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void persistsSyntheticRecordsInOrderAndDeduplicatesAcrossRestart() throws Exception
	{
		Path directory = temporaryFolder.newFolder("ordered").toPath();
		MutableClock clock = new MutableClock(STARTED_AT);
		DurableSnapshotQueue queue = queue(directory, clock);

		assertTrue(queue.enqueue("record-1", "{\"synthetic\":1}").wasAdded());
		clock.advance(Duration.ofSeconds(1));
		assertTrue(queue.enqueue("record-2", "{\"synthetic\":2}").wasAdded());
		assertFalse(queue.enqueue("record-1", "{\"synthetic\":1}").wasAdded());

		DurableSnapshotQueue restored = queue(directory, clock);
		assertEquals(2, restored.size());
		assertEquals("record-1", restored.peek().orElseThrow(AssertionError::new).getRecordId());
		assertThrows(IllegalStateException.class, () -> restored.acknowledge("record-2"));
		restored.acknowledge("record-1");
		assertEquals("record-2", restored.peek().orElseThrow(AssertionError::new).getRecordId());
	}

	@Test
	public void discardsTheWholeQueueWhenRetentionWouldCreateASequenceGap() throws Exception
	{
		Path directory = temporaryFolder.newFolder("expiry").toPath();
		MutableClock clock = new MutableClock(STARTED_AT);
		DurableSnapshotQueue queue = queue(directory, clock);
		queue.enqueue("expired", "{\"synthetic\":true}");

		clock.advance(DurableSnapshotQueue.MAX_AGE.plusSeconds(1));
		assertThrows(
			IOException.class,
			() -> queue.enqueue("fresh", "{\"synthetic\":true}"));
		assertEquals(0, queue.size());
		assertFalse(Files.exists(directory.resolve(DurableSnapshotQueue.FILE_NAME)));
	}

	@Test
	public void rejectsCapacityOverflowWithoutEvictingQueuedEntries() throws Exception
	{
		Path directory = temporaryFolder.newFolder("capacity").toPath();
		MutableClock clock = new MutableClock(STARTED_AT);
		DurableSnapshotQueue queue = queue(directory, clock);
		String payload = "x".repeat(220_000);
		int accepted = 0;
		while (true)
		{
			try
			{
				queue.enqueue("record-" + accepted, payload);
				accepted++;
			}
			catch (IOException exception)
			{
				break;
			}
			clock.advance(Duration.ofMillis(1));
		}

		assertTrue(accepted > 0);
		assertEquals(accepted, queue.size());
		assertEquals("record-0", queue.peek().orElseThrow(AssertionError::new).getRecordId());
		assertTrue(Files.size(directory.resolve(DurableSnapshotQueue.FILE_NAME))
			<= DurableSnapshotQueue.MAX_FILE_BYTES);
	}

	@Test
	public void deletesCorruptStateWithoutLoggingOrRetainingItsPayload() throws Exception
	{
		Path directory = temporaryFolder.newFolder("corrupt").toPath();
		Path queueFile = directory.resolve(DurableSnapshotQueue.FILE_NAME);
		Files.write(queueFile, "not-json-private-data".getBytes(StandardCharsets.UTF_8));

		DurableSnapshotQueue queue = queue(directory, new MutableClock(STARTED_AT));

		assertEquals(DurableSnapshotQueue.LoadStatus.RECOVERED_CORRUPTION, queue.getLoadStatus());
		assertEquals(0, queue.size());
		assertFalse(Files.exists(queueFile));
	}

	@Test
	public void consentRevocationDeletesEveryUnsentQueueFileImmediately() throws Exception
	{
		Path directory = temporaryFolder.newFolder("clear").toPath();
		DurableSnapshotQueue queue = queue(directory, new MutableClock(STARTED_AT));
		queue.enqueue("record-1", "{\"synthetic\":true}");
		Files.write(
			directory.resolve(DurableSnapshotQueue.TEMP_FILE_NAME),
			"partial".getBytes(StandardCharsets.UTF_8));

		queue.clear();

		assertEquals(0, queue.size());
		assertFalse(Files.exists(directory.resolve(DurableSnapshotQueue.FILE_NAME)));
		assertFalse(Files.exists(directory.resolve(DurableSnapshotQueue.TEMP_FILE_NAME)));
	}

	@Test
	public void failedEnqueueDoesNotBecomeAnInMemoryDuplicate() throws Exception
	{
		Path directory = temporaryFolder.getRoot().toPath().resolve("failed-enqueue");
		DurableSnapshotQueue queue = queue(directory, new MutableClock(STARTED_AT));
		Files.write(directory, "blocks-directory-creation".getBytes(StandardCharsets.UTF_8));

		assertThrows(
			IOException.class,
			() -> queue.enqueue("record-1", "{\"synthetic\":true}"));
		assertEquals(0, queue.size());

		Files.delete(directory);
		assertTrue(queue.enqueue("record-1", "{\"synthetic\":true}").wasAdded());
		assertEquals(1, queue.size());
	}

	@Test
	public void failedAcknowledgementKeepsMemoryAndDiskInFifoAgreement() throws Exception
	{
		Path directory = temporaryFolder.newFolder("failed-acknowledge").toPath();
		MutableClock clock = new MutableClock(STARTED_AT);
		DurableSnapshotQueue queue = queue(directory, clock);
		queue.enqueue("record-1", "{\"synthetic\":1}");
		queue.enqueue("record-2", "{\"synthetic\":2}");

		Path preservedDirectory = directory.resolveSibling("failed-acknowledge-preserved");
		Files.move(directory, preservedDirectory);
		Files.write(directory, "blocks-directory-creation".getBytes(StandardCharsets.UTF_8));

		assertThrows(IOException.class, () -> queue.acknowledge("record-1"));
		assertEquals(2, queue.size());
		assertEquals("record-1", queue.peek().orElseThrow(AssertionError::new).getRecordId());

		Files.delete(directory);
		Files.move(preservedDirectory, directory);
		DurableSnapshotQueue restored = queue(directory, clock);
		assertEquals(2, restored.size());
		assertEquals("record-1", restored.peek().orElseThrow(AssertionError::new).getRecordId());
		queue.acknowledge("record-1");
		assertEquals("record-2", queue.peek().orElseThrow(AssertionError::new).getRecordId());
	}

	private static DurableSnapshotQueue queue(Path directory, Clock clock) throws IOException
	{
		return new DurableSnapshotQueue(directory, new Gson(), clock);
	}

	private static final class MutableClock extends Clock
	{
		private Instant now;

		private MutableClock(Instant now)
		{
			this.now = now;
		}

		private void advance(Duration duration)
		{
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return this;
		}

		@Override
		public Instant instant()
		{
			return now;
		}
	}
}
