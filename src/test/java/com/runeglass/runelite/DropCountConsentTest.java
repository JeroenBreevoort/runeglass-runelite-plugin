package com.runeglass.runelite;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.game.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DropCountConsentTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private DropHandlerFixture fixture;
	private Path root;

	@Before public void setUp() throws Exception
	{
		root = temporary.newFolder().toPath();
		fixture = new DropHandlerFixture(root);
	}

	@After public void tearDown() throws Exception { fixture.close(); }

	@Test public void masterAndPairingGatesPreventChatReads() throws Exception
	{
		int reads = fixture.evidenceReads.get();
		fixture.syncEnabled = false;
		fixture.plugin.onChatMessage(null);
		fixture.plugin.advanceDropEvidence();
		fixture.syncEnabled = true;
		DropHandlerFixture.setField(fixture.plugin, "activeCredentials", null);
		fixture.plugin.onChatMessage(null);
		fixture.plugin.advanceDropEvidence();
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		assertEquals(reads, fixture.evidenceReads.get());
		assertFalse(Files.exists(continuity()));
		assertNull(fixture.server.takeRequest(100, TimeUnit.MILLISECONDS));
	}

	@Test public void syncedDropsCarryCountsAndCollectionLogEvidence() throws Exception
	{
		assertTrue((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		fixture.eventBus.post(count(100));
		fixture.plugin.onLootReceived(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		fixture.eventBus.post(newItem());
		JsonObject uploaded = fixture.takeUpload();
		assertEquals(100, uploaded.getAsJsonObject("sourceCount").get("value").getAsInt());
		fixture.drainTransport();
		assertTrue(Files.exists(continuity()));
	}

	@Test public void masterOffStopsCollectionAndDiscardsBufferedDropsBeforeDeferredWork() throws Exception
	{
		fixture.eventBus.post(count(100));
		fixture.plugin.onLootReceived(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		assertTrue(fixture.takeUpload().has("sourceCount"));
		fixture.drainTransport();
		assertTrue(Files.exists(continuity()));
		fixture.eventBus.post(count(101));
		fixture.plugin.handleLoot(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		java.util.List<Runnable> deferred = new java.util.ArrayList<>();
		DropHandlerFixture.setField(fixture.plugin, "clientThread", new net.runelite.client.callback.ClientThread()
		{
			@Override public void invokeLater(Runnable task) { deferred.add(task); }
		});
		int reads = fixture.evidenceReads.get();
		fixture.changeSyncConsent(false);
		assertFalse(deferred.isEmpty());
		assertFalse(fixture.dropClient.isConnected());
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		assertFalse(Files.exists(continuity()));
		fixture.plugin.onChatMessage(null);
		fixture.eventBus.post(newItem());
		fixture.plugin.onVarbitChanged(new VarbitChanged());
		fixture.plugin.advanceDropEvidence();
		fixture.drainTransport();
		assertFalse(fixture.dropClient.isConnected());
		assertEquals(reads, fixture.evidenceReads.get());
		assertNull(fixture.uploads.poll(500, TimeUnit.MILLISECONDS));
	}

	private Path continuity() { return root.resolve("drops-v1/connection/" + DropContinuityStore.FILE); }
	private static ChatMessage count(int value)
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "Your Vorkath kill count is: " + value + ".", null, 0);
	}
	private static ChatMessage newItem()
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "New item added to your collection log: Fixture item", null, 0);
	}
}
