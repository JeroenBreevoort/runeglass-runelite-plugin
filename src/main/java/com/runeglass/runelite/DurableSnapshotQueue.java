package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class DurableSnapshotQueue
{
	static final String FILE_NAME = "snapshot-queue-v1.json";
	static final String TEMP_FILE_NAME = "snapshot-queue-v1.tmp";
	static final long MAX_FILE_BYTES = 5L * 1_024L * 1_024L;
	static final int MAX_ENTRY_BYTES = 256 * 1_024;
	static final Duration MAX_AGE = Duration.ofDays(7);

	private static final int FORMAT_VERSION = 1;
	private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofMinutes(10);
	private static final Pattern RECORD_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

	enum LoadStatus
	{
		READY,
		RECOVERED_CORRUPTION,
		DISCARDED_EXPIRED
	}

	static final class Entry
	{
		private final String recordId;
		private final long createdAtEpochMillis;
		private final String payload;

		private Entry(String recordId, long createdAtEpochMillis, String payload)
		{
			this.recordId = recordId;
			this.createdAtEpochMillis = createdAtEpochMillis;
			this.payload = payload;
		}

		String getRecordId()
		{
			return recordId;
		}

		long getCreatedAtEpochMillis()
		{
			return createdAtEpochMillis;
		}

		String getPayload()
		{
			return payload;
		}
	}

	static final class EnqueueResult
	{
		private final boolean added;
		private final int expiredDropped;
		private final int capacityDropped;

		private EnqueueResult(boolean added, int expiredDropped, int capacityDropped)
		{
			this.added = added;
			this.expiredDropped = expiredDropped;
			this.capacityDropped = capacityDropped;
		}

		boolean wasAdded()
		{
			return added;
		}

		int getExpiredDropped()
		{
			return expiredDropped;
		}

		int getCapacityDropped()
		{
			return capacityDropped;
		}
	}

	private static final class StoredEntry
	{
		private final String recordId;
		private final long createdAtEpochMillis;
		private final String payload;

		private StoredEntry(String recordId, long createdAtEpochMillis, String payload)
		{
			this.recordId = recordId;
			this.createdAtEpochMillis = createdAtEpochMillis;
			this.payload = payload;
		}
	}

	private static final class QueueFile
	{
		private final int version = FORMAT_VERSION;
		private final List<StoredEntry> entries;

		private QueueFile(List<StoredEntry> entries)
		{
			this.entries = entries;
		}
	}

	private final Path directory;
	private final Path queueFile;
	private final Path temporaryFile;
	private final Gson gson;
	private final Clock clock;
	private final List<Entry> entries = new ArrayList<>();
	private LoadStatus loadStatus = LoadStatus.READY;

	DurableSnapshotQueue(Path directory, Gson gson, Clock clock) throws IOException
	{
		this.directory = Objects.requireNonNull(directory, "directory");
		this.queueFile = directory.resolve(FILE_NAME);
		this.temporaryFile = directory.resolve(TEMP_FILE_NAME);
		this.gson = Objects.requireNonNull(gson, "gson");
		this.clock = Objects.requireNonNull(clock, "clock");
		load();
	}

	synchronized EnqueueResult enqueue(String recordId, String payload) throws IOException
	{
		validateRecord(recordId, payload);
		long cutoff = clock.millis() - MAX_AGE.toMillis();
		List<Entry> candidate = new ArrayList<>(entries.size() + 1);
		for (Entry entry : entries)
		{
			if (entry.createdAtEpochMillis >= cutoff)
			{
				candidate.add(entry);
			}
		}
		int expiredDropped = entries.size() - candidate.size();
		if (expiredDropped > 0)
		{
			Files.deleteIfExists(temporaryFile);
			Files.deleteIfExists(queueFile);
			entries.clear();
			loadStatus = LoadStatus.DISCARDED_EXPIRED;
			throw new IOException("RuneGlass queue retention expired");
		}
		if (candidate.stream().anyMatch(entry -> entry.recordId.equals(recordId)))
		{
			return new EnqueueResult(false, expiredDropped, 0);
		}

		long createdAt = clock.millis();
		if (!candidate.isEmpty())
		{
			createdAt = Math.max(
				createdAt,
				candidate.get(candidate.size() - 1).createdAtEpochMillis);
		}
		candidate.add(new Entry(recordId, createdAt, payload));

		byte[] serialized = serialize(candidate);
		if (serialized.length > MAX_FILE_BYTES)
		{
			throw new IOException("RuneGlass queue capacity reached");
		}
		persist(serialized);
		replaceEntries(candidate);
		return new EnqueueResult(true, expiredDropped, 0);
	}

	synchronized Optional<Entry> peek()
	{
		if (entries.isEmpty())
		{
			return Optional.empty();
		}
		return Optional.of(copy(entries.get(0)));
	}

	synchronized List<Entry> pendingEntries()
	{
		List<Entry> pending = new ArrayList<>(entries.size());
		for (Entry entry : entries)
		{
			pending.add(copy(entry));
		}
		return Collections.unmodifiableList(pending);
	}

	synchronized void acknowledge(String recordId) throws IOException
	{
		Objects.requireNonNull(recordId, "recordId");
		if (entries.isEmpty() || !entries.get(0).recordId.equals(recordId))
		{
			throw new IllegalStateException("RuneGlass queue acknowledgements must remain ordered");
		}
		List<Entry> candidate = new ArrayList<>(entries);
		candidate.remove(0);
		persistOrDelete(candidate);
		replaceEntries(candidate);
	}

	synchronized void clear() throws IOException
	{
		Files.deleteIfExists(temporaryFile);
		Files.deleteIfExists(queueFile);
		entries.clear();
		loadStatus = LoadStatus.READY;
	}

	synchronized int size()
	{
		return entries.size();
	}

	synchronized LoadStatus getLoadStatus()
	{
		return loadStatus;
	}

	private void load() throws IOException
	{
		Files.deleteIfExists(temporaryFile);
		if (!Files.exists(queueFile))
		{
			return;
		}
		final List<Entry> loaded;
		try
		{
			if (Files.size(queueFile) > MAX_FILE_BYTES)
			{
				throw new IOException("RuneGlass queue exceeds its size limit");
			}
			loaded = readEntries();
		}
		catch (IOException | RuntimeException exception)
		{
			entries.clear();
			Files.deleteIfExists(temporaryFile);
			Files.deleteIfExists(queueFile);
			loadStatus = LoadStatus.RECOVERED_CORRUPTION;
			return;
		}
		entries.addAll(loaded);
		int expiredDropped = pruneExpired(clock.millis());
		if (expiredDropped > 0)
		{
			entries.clear();
			Files.deleteIfExists(queueFile);
			loadStatus = LoadStatus.DISCARDED_EXPIRED;
		}
	}

	private List<Entry> readEntries() throws IOException
	{
		JsonElement parsed;
		try (Reader reader = Files.newBufferedReader(queueFile, StandardCharsets.UTF_8))
		{
			parsed = new JsonParser().parse(reader);
		}
		if (!parsed.isJsonObject())
		{
			throw new IOException("RuneGlass queue must be an object");
		}
		JsonObject body = parsed.getAsJsonObject();
		if (!ProtocolJson.hasExactKeys(body, "version", "entries")
			|| !isExactInteger(body.get("version"), FORMAT_VERSION)
			|| body.get("entries") == null
			|| !body.get("entries").isJsonArray())
		{
			throw new IOException("Unsupported RuneGlass queue format");
		}

		JsonArray serializedEntries = body.getAsJsonArray("entries");
		List<Entry> loaded = new ArrayList<>(serializedEntries.size());
		Set<String> recordIds = new HashSet<>();
		long previousCreatedAt = -1L;
		long maximumCreatedAt = clock.millis() + MAX_FUTURE_CLOCK_SKEW.toMillis();
		for (JsonElement serializedEntry : serializedEntries)
		{
			if (!serializedEntry.isJsonObject())
			{
				throw new IOException("Invalid RuneGlass queue entry");
			}
			JsonObject entry = serializedEntry.getAsJsonObject();
			if (!ProtocolJson.hasExactKeys(
				entry,
				"recordId",
				"createdAtEpochMillis",
				"payload"))
			{
				throw new IOException("Invalid RuneGlass queue entry keys");
			}
			String recordId = ProtocolJson.stringValue(entry, "recordId");
			String payload = ProtocolJson.stringValue(entry, "payload");
			long createdAt = exactLong(entry.get("createdAtEpochMillis"));
			validateRecord(recordId, payload);
			if (createdAt <= 0L
				|| createdAt < previousCreatedAt
				|| createdAt > maximumCreatedAt
				|| !recordIds.add(recordId))
			{
				throw new IOException("Invalid RuneGlass queue ordering");
			}
			loaded.add(new Entry(recordId, createdAt, payload));
			previousCreatedAt = createdAt;
		}
		return loaded;
	}

	private int pruneExpired(long now)
	{
		long cutoff = now - MAX_AGE.toMillis();
		int originalSize = entries.size();
		entries.removeIf(entry -> entry.createdAtEpochMillis < cutoff);
		return originalSize - entries.size();
	}

	private void persistOrDelete(List<Entry> candidate) throws IOException
	{
		if (candidate.isEmpty())
		{
			Files.deleteIfExists(temporaryFile);
			Files.deleteIfExists(queueFile);
			return;
		}
		persist(serialize(candidate));
	}

	private byte[] serialize(List<Entry> candidate)
	{
		List<StoredEntry> stored = new ArrayList<>(candidate.size());
		for (Entry entry : candidate)
		{
			stored.add(new StoredEntry(
				entry.recordId,
				entry.createdAtEpochMillis,
				entry.payload));
		}
		return gson.toJson(new QueueFile(stored)).getBytes(StandardCharsets.UTF_8);
	}

	private void replaceEntries(List<Entry> candidate)
	{
		entries.clear();
		entries.addAll(candidate);
	}

	private void persist(byte[] serialized) throws IOException
	{
		if (serialized.length > MAX_FILE_BYTES)
		{
			throw new IOException("RuneGlass queue exceeds its size limit");
		}
		Files.createDirectories(directory);
		try (FileChannel channel = FileChannel.open(
			temporaryFile,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
			StandardOpenOption.WRITE))
		{
			ByteBuffer buffer = ByteBuffer.wrap(serialized);
			while (buffer.hasRemaining())
			{
				channel.write(buffer);
			}
			channel.force(true);
		}
		try
		{
			Files.move(
				temporaryFile,
				queueFile,
				StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException exception)
		{
			Files.deleteIfExists(temporaryFile);
			throw new IOException("Atomic RuneGlass queue writes are not supported", exception);
		}
	}

	private static Entry copy(Entry entry)
	{
		return new Entry(entry.recordId, entry.createdAtEpochMillis, entry.payload);
	}

	private static void validateRecord(String recordId, String payload)
	{
		Objects.requireNonNull(recordId, "recordId");
		Objects.requireNonNull(payload, "payload");
		if (!RECORD_ID_PATTERN.matcher(recordId).matches())
		{
			throw new IllegalArgumentException("Invalid RuneGlass queue record ID");
		}
		if (payload.isEmpty()
			|| payload.getBytes(StandardCharsets.UTF_8).length > MAX_ENTRY_BYTES)
		{
			throw new IllegalArgumentException("Invalid RuneGlass queue payload size");
		}
	}

	private static boolean isExactInteger(JsonElement value, int expected)
	{
		return value != null
			&& value.isJsonPrimitive()
			&& value.getAsJsonPrimitive().isNumber()
			&& Integer.toString(expected).equals(value.toString());
	}

	private static long exactLong(JsonElement value) throws IOException
	{
		if (value == null
			|| !value.isJsonPrimitive()
			|| !value.getAsJsonPrimitive().isNumber()
			|| !value.toString().matches("^[1-9][0-9]{0,18}$"))
		{
			throw new IOException("Invalid RuneGlass queue timestamp");
		}
		try
		{
			return Long.parseLong(value.toString());
		}
		catch (NumberFormatException exception)
		{
			throw new IOException("Invalid RuneGlass queue timestamp", exception);
		}
	}
}
