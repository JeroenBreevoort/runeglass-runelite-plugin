package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class DurableDropQueueTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private final Gson gson = new Gson();

	@Test public void restartRetainsImmutableIdsContentAndCaptureSession() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		JsonObject first = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		JsonObject second = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		queue.append(first);
		queue.append(second);
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertEquals(2, restored.size());
		assertEquals(first, restored.batch(false).get(0).body);
		assertEquals(second, restored.batch(false).get(1).body);
		List<DurableDropQueue.Entry> batch = restored.batch(false);
		restored.acknowledge(batch, Set.of(batch.get(1).id()));
		assertEquals(first, new DurableDropQueue(root, "connection", gson).batch(false).get(0).body);
	}

	@Test public void countEvidenceRoundTripsAlongsideLegacyEventsAndRejectsInvalidExtensions() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		JsonObject legacy = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		DropEvent captured = DropCorrelatorTest.drop("npc", "Vorkath", true).event().get();
		JsonObject continuity = new JsonObject(); continuity.addProperty("id", java.util.UUID.randomUUID().toString()); continuity.addProperty("from", 100);
		JsonObject current = captured.freeze(new DropCountParser.Count(DropCounterRegistry.byKey("vorkath"), 1200),
			continuity, "enabled", Set.of(22006)).envelope(DropCaptureTest.CONTEXT, "connection");
		queue.append(legacy); queue.append(current);
		DropContinuityStore state = new DropContinuityStore(root.joinSegment("connection"), gson, "profile");
		state.observe(new DropCountParser.Count(DropCounterRegistry.byKey("vorkath"), 1200)); state.save();
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertFalse(restored.hasDamagedRecords()); assertEquals(2, restored.size());
		assertEquals(legacy, restored.batch(false).get(0).body); assertEquals(current, restored.batch(false).get(1).body);
		for (String field : List.of("sourceCount", "continuity", "collectionLog"))
		{
			JsonObject bad = current.deepCopy(); DropCaptureTest.event(bad).add(field, com.google.gson.JsonNull.INSTANCE);
			assertThrows(Exception.class, () -> queue.append(bad));
		}
		for (String field : List.of("computedLuck", "waitingInterval", "firstCopy", "rawChat"))
		{
			JsonObject bad = current.deepCopy(); DropCaptureTest.event(bad).addProperty(field, true);
			assertThrows(java.io.IOException.class, () -> queue.append(bad));
		}
		JsonObject mismatch = current.deepCopy(); DropCaptureTest.event(mismatch).getAsJsonObject("sourceCount").addProperty("counterKey", "zulrah");
		assertThrows(java.io.IOException.class, () -> queue.append(mismatch));
		JsonObject invalidFrom = current.deepCopy(); DropCaptureTest.event(invalidFrom).getAsJsonObject("continuity").addProperty("from", 1201);
		assertThrows(java.io.IOException.class, () -> queue.append(invalidFrom));
		JsonObject invalidFlag = current.deepCopy(); DropCaptureTest.event(invalidFlag).getAsJsonArray("items").get(0).getAsJsonObject().addProperty("newCollectionLogSlot", "true");
		assertThrows(java.io.IOException.class, () -> queue.append(invalidFlag));
		assertEquals(2, queue.size());
	}

	@Test public void countRevocationLeavesLegacyPayloadAndDiskBytesIdentical() throws Exception
	{
		Filepath root = root().joinSegment("drops-v1");
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		DropEvent event = DropCaptureTest.notedDrop(123);
		JsonObject original = event.envelope(DropCaptureTest.CONTEXT, "connection");
		assertEquals(gson.toJson(original), gson.toJson(event.withoutCountEvidence().envelope(DropCaptureTest.CONTEXT, "connection")));
		queue.append(original);
		Path path = TestStorage.path(queue.batch(false).get(0).path);
		String unusualWhitespace = "\n " + Files.readString(path) + "\n";
		Files.writeString(path, unusualWhitespace);
		DurableDropQueue.stripCountEvidence(root, gson);
		assertEquals(unusualWhitespace, Files.readString(path));
		assertEquals(original, new DurableDropQueue(root, "connection", gson).batch(false).get(0).body);
		assertFalse(root.joinSegment("connection").joinSegment(DropContinuityStore.FILE).exists());
	}

	@Test public void damagedRecordDoesNotDestroyOrBlockHealthyRecords() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		Path corrupted = TestStorage.path(root).resolve("connection").resolve("bad.json");
		Files.writeString(corrupted, "broken");
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertTrue(restored.hasDamagedRecords());
		assertEquals(1, restored.size());
		assertTrue(Files.exists(corrupted));
		restored.block(restored.batch(false).get(0));
		assertEquals(0, new DurableDropQueue(root, "connection", gson).size());
		assertEquals(2, names(TestStorage.path(root).resolve("connection")).size());
	}

	@Test public void marketIdSurvivesCapturePersistenceRestartBatchAndAcknowledgementWithLegacyItems() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		JsonObject coins = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		JsonObject legacy = DropCaptureTest.notedDrop(123).envelope(DropCaptureTest.CONTEXT, "connection");
		DropCaptureTest.event(legacy).getAsJsonArray("items").get(0).getAsJsonObject().remove("marketItemId");
		JsonObject current = DropCaptureTest.notedDrop(0).envelope(DropCaptureTest.CONTEXT, "connection");
		List<JsonObject> bodies = List.of(coins, legacy, current);
		for (JsonObject body : bodies) queue.append(body);
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertFalse(restored.hasDamagedRecords());
		assertEquals(3, restored.size());
		List<DurableDropQueue.Entry> batch = restored.batch(false);
		JsonObject payload = gson.fromJson(gson.toJson(DurableDropQueue.payload(batch)), JsonObject.class);
		for (int index = 0; index < bodies.size(); index++)
		{
			assertEquals(bodies.get(index).toString(), batch.get(index).body.toString());
			assertEquals(DropCaptureTest.event(bodies.get(index)), payload.getAsJsonArray("events").get(index));
		}
		restored.acknowledge(batch, Set.of(batch.get(2).id()));
		DurableDropQueue remaining = new DurableDropQueue(root, "connection", gson);
		assertEquals(2, remaining.size());
		assertEquals(legacy, remaining.batch(false).get(1).body);
	}

	@Test public void rejectsInvalidMarketIdsAndUnexpectedFieldsBeforePersistence() throws Exception
	{
		DurableDropQueue queue = new DurableDropQueue(root(), "connection", gson);
		for (String raw : List.of("0", "-1", "995", "2147483648", "1.5", "null", "true", "\"22124\"", "{}", "[]"))
		{
			JsonObject body = DropCaptureTest.notedDrop(0).envelope(DropCaptureTest.CONTEXT, "connection");
			DropCaptureTest.event(body).getAsJsonArray("items").get(0).getAsJsonObject()
				.add("marketItemId", gson.fromJson(raw, com.google.gson.JsonElement.class));
			assertThrows(raw, java.io.IOException.class, () -> queue.append(body));
		}
		for (String kind : List.of("coins", "untradeable"))
		{
			JsonObject body = DropCaptureTest.notedDrop(0).envelope(DropCaptureTest.CONTEXT, "connection");
			JsonObject item = DropCaptureTest.event(body).getAsJsonArray("items").get(0).getAsJsonObject();
			item.addProperty("itemId", "coins".equals(kind) ? 995 : 22125);
			item.getAsJsonObject("value").addProperty("kind", kind);
			assertThrows(java.io.IOException.class, () -> queue.append(body));
		}
		for (boolean withMarketId : List.of(false, true))
		{
			JsonObject body = DropCaptureTest.notedDrop(0).envelope(DropCaptureTest.CONTEXT, "connection");
			JsonObject item = DropCaptureTest.event(body).getAsJsonArray("items").get(0).getAsJsonObject();
			if (!withMarketId) item.remove("marketItemId");
			item.addProperty("unexpected", 1);
			assertThrows(java.io.IOException.class, () -> queue.append(body));
		}
		assertEquals(0, queue.size());
		JsonObject maximum = DropCaptureTest.notedDrop(0).envelope(DropCaptureTest.CONTEXT, "connection");
		DropCaptureTest.event(maximum).getAsJsonArray("items").get(0).getAsJsonObject().addProperty("marketItemId", Integer.MAX_VALUE);
		queue.append(maximum);
		assertEquals(maximum, queue.batch(false).get(0).body);
	}

	@Test public void boundedBatchSplitsByCountAndUtf8BytesWithoutDroppingEvents() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		for (int i = 0; i < 23; i++) queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		assertEquals(20, queue.batch(false).size());
		Set<String> ids = new HashSet<>();
		queue.batch(false).forEach(entry -> ids.add(entry.id()));
		queue.acknowledge(queue.batch(false), ids);
		assertEquals(3, queue.size());
		JsonObject large = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		JsonArray items = DropCaptureTest.event(large).getAsJsonArray("items");
		items.remove(0);
		for (int i = 1; i <= 64; i++)
		{
			JsonObject item = new JsonObject();
			item.addProperty("itemId", i);
			item.addProperty("itemName", "界".repeat(120));
			item.addProperty("quantity", 1);
			JsonObject value = new JsonObject(); value.addProperty("kind", "unknown"); item.add("value", value);
			items.add(item);
		}
		for (int i = 0; i < 4; i++)
		{
			DropCaptureTest.event(large).addProperty("eventId", java.util.UUID.randomUUID().toString());
			queue.append(large);
		}
		List<DurableDropQueue.Entry> batch = queue.batch(false);
		assertTrue(batch.size() < queue.size());
		assertTrue(gson.toJson(DurableDropQueue.payload(batch)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64 * 1024);
		assertEquals(7, queue.size());
	}

	@Test public void queueCapacityKeepsPreviouslySavedEvents() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		Files.write(TestStorage.path(root).resolve("connection").resolve("blocked.bin"), new byte[(int) DurableDropQueue.MAX_BYTES]);
		DurableDropQueue full = new DurableDropQueue(root, "connection", gson);
		try { full.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection")); fail(); }
		catch (DurableDropQueue.CapacityException expected) { assertEquals(1, full.size()); }
		assertEquals(1, new DurableDropQueue(root, "connection", gson).size());
	}
	@Test public void failedAtomicWritePreservesPreviouslyCommittedDrop() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		queue.append(DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection"));
		JsonObject next = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		String nextId = DropCaptureTest.event(next).get("eventId").getAsString();
		Path directory = TestStorage.path(root).resolve("connection");
		Path blocker = directory.resolve("00000000000000000002-" + nextId + ".json");
		Files.createDirectories(blocker.resolve("occupied"));
		try { queue.append(next); fail(); }
		catch (java.io.IOException expected) { assertEquals(1, queue.size()); }
		assertTrue(names(directory).stream().noneMatch(name -> name.endsWith(".tmp")));
		TestStorage.rooted(blocker).deleteRecursively();
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertEquals(1, restored.size());
		assertFalse(restored.hasDamagedRecords());
	}

	@Test public void committedRecordsAreOwnerOnlyAndInterruptedWritesStayVisibleAsDamage() throws Exception
	{
		Filepath root = root();
		DurableDropQueue queue = new DurableDropQueue(root, "connection", gson);
		JsonObject body = DropCaptureTest.drop().envelope(DropCaptureTest.CONTEXT, "connection");
		queue.append(body);
		Path directory = TestStorage.path(root).resolve("connection");
		List<String> committed = names(directory);
		assertEquals(1, committed.size());
		assertTrue(committed.get(0).matches("[0-9]{20}-[a-f0-9-]{36}\\.json"));
		assertEquals(List.of("connection"), names(TestStorage.path(root)));
		Files.writeString(directory.resolve(committed.get(0) + ".4812.tmp"), "partial");
		DurableDropQueue restored = new DurableDropQueue(root, "connection", gson);
		assertTrue(restored.hasDamagedRecords());
		assertEquals(body, restored.batch(false).get(0).body);
		assumeTrue(TestStorage.posix());
		assertEquals("rw-------", TestStorage.permissions(directory.resolve(committed.get(0))));
		assertEquals("rwx------", TestStorage.permissions(directory));
	}

	private Filepath root() throws Exception
	{
		return TestStorage.rooted(temporary.newFolder().toPath());
	}

	private static List<String> names(Path directory) throws Exception
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}

}
