package com.runeglass.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DropContinuityStoreTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private final Gson gson = new Gson();

	@Test public void restartPreservesConsecutiveRunAndJumpsOnlyChangeItsCompleteness() throws Exception
	{
		Filepath directory = TestStorage.rooted(temporary.newFolder().toPath());
		DropContinuityStore store = new DropContinuityStore(directory, gson, "profile-a");
		JsonObject first = store.observe(count(1200)); store.save();
		store = new DropContinuityStore(directory, gson, "profile-a");
		JsonObject afterRestart = store.observe(count(1201));
		assertEquals(first, afterRestart);
		JsonObject gap = store.observe(count(1250)); store.save();
		assertEquals(first.get("id"), gap.get("id")); assertEquals(1250, gap.get("from").getAsInt());
		assertEquals(gap, new DropContinuityStore(directory, gson, "profile-a").observe(count(1251)));
		if (TestStorage.posix()) assertEquals("rw-------", TestStorage.permissions(TestStorage.path(directory.joinSegment(DropContinuityStore.FILE))));
	}

	@Test public void counterRollbackAndProfileChangeStartNewStreams() throws Exception
	{
		Filepath directory = TestStorage.rooted(temporary.newFolder().toPath());
		DropContinuityStore store = new DropContinuityStore(directory, gson, "profile-a");
		JsonObject original = store.observe(count(100)); store.save();
		JsonObject different = new DropContinuityStore(directory, gson, "profile-b").observe(count(101));
		assertNotEquals(original.get("id"), different.get("id"));
		assertEquals(101, different.get("from").getAsInt());
		assertNotEquals(original.get("id"), store.observe(count(99)).get("id"));
	}

	@Test public void invalidationCorruptionAndPurgeCannotClaimContinuity() throws Exception
	{
		Path path = temporary.newFolder().toPath(); Filepath directory = TestStorage.rooted(path);
		DropContinuityStore store = new DropContinuityStore(directory, gson, "profile-a");
		JsonObject first = store.observe(count(100)); store.save();
		store.invalidate("npc", "Vorkath"); store.save();
		assertNotEquals(first.get("id"), new DropContinuityStore(directory, gson, "profile-a").observe(count(101)).get("id"));
		Files.writeString(path.resolve(DropContinuityStore.FILE), "{\"corrupt\":true}", StandardCharsets.UTF_8);
		store = new DropContinuityStore(directory, gson, "profile-a");
		assertTrue(store.damaged()); assertEquals(102, store.observe(count(102)).get("from").getAsInt());
		store.save(); DurableDropQueue.purge(directory);
		assertFalse(Files.exists(path.resolve(DropContinuityStore.FILE)));
	}

	private static DropCountParser.Count count(int value) { return new DropCountParser.Count(DropCounterRegistry.byKey("vorkath"), value); }
}
