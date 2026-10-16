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

	@Test public void existingDropConsentDoesNotSubscribeReadSettingsOrAddEvidence() throws Exception
	{
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		fixture.plugin.onChatMessage(null);
		fixture.eventBus.post(count(100));
		fixture.eventBus.post(newItem());
		fixture.plugin.onVarbitChanged(new VarbitChanged());
		fixture.plugin.onLootReceived(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		assertLegacy(fixture.takeUpload());
		assertEquals(0, fixture.evidenceReads.get());
		assertFalse(Files.exists(continuity()));
	}

	@Test public void dropMasterAndPairingGatesPreventChatReadsEvenWithCountConsent() throws Exception
	{
		fixture.dropCountSyncEnabled = true;
		fixture.dropSyncEnabled = false;
		fixture.plugin.onChatMessage(null);
		fixture.plugin.advanceDropEvidence();
		fixture.dropSyncEnabled = true;
		fixture.syncEnabled = false;
		fixture.plugin.onChatMessage(null);
		fixture.plugin.advanceDropEvidence();
		fixture.syncEnabled = true;
		DropHandlerFixture.setField(fixture.plugin, "activeCredentials", null);
		fixture.plugin.onChatMessage(null);
		fixture.plugin.advanceDropEvidence();
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		assertEquals(0, fixture.evidenceReads.get());
		assertFalse(Files.exists(continuity()));
		assertNull(fixture.server.takeRequest(100, TimeUnit.MILLISECONDS));
	}

	@Test public void revocationUnsubscribesDeletesContinuityAndKeepsBufferedDropWithoutEvidence() throws Exception
	{
		fixture.enableCounts();
		assertTrue((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		fixture.eventBus.post(count(100));
		fixture.plugin.onLootReceived(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		fixture.eventBus.post(newItem());
		assertTrue(fixture.takeUpload().has("sourceCount"));
		fixture.drainTransport();
		assertTrue(Files.exists(continuity()));
		fixture.eventBus.post(count(101));
		DropCaptureResult buffered = fixture.plugin.handleLoot(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		fixture.eventBus.post(newItem());
		int reads = fixture.evidenceReads.get();
		fixture.changeCountConsent(false);
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		assertFalse(Files.exists(continuity()));
		fixture.plugin.onChatMessage(null);
		fixture.eventBus.post(count(102));
		fixture.eventBus.post(newItem());
		fixture.plugin.onVarbitChanged(new VarbitChanged());
		JsonObject uploaded = fixture.takeUpload();
		assertEquals(buffered.event().get().eventId(), uploaded.get("eventId").getAsString());
		assertLegacy(uploaded);
		assertEquals(reads, fixture.evidenceReads.get());
		assertFalse(Files.exists(continuity()));
		fixture.changeCountConsent(true);
		assertTrue((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		fixture.tick += 6;
		fixture.plugin.advanceDropEvidence();
		fixture.eventBus.post(count(103));
		DropCaptureResult resumedDrop = fixture.plugin.handleLoot(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		JsonObject resumed;
		do
		{
			resumed = fixture.takeUpload();
			assertNotNull(resumed);
		}
		while (!resumedDrop.event().get().eventId().equals(resumed.get("eventId").getAsString()));
		assertEquals(103, resumed.getAsJsonObject("sourceCount").get("value").getAsInt());
		assertEquals(103, resumed.getAsJsonObject("continuity").get("from").getAsInt());
	}

	@Test public void configEventStopsCollectionBeforeDeferredClientThreadWork() throws Exception
	{
		fixture.enableCounts();
		fixture.eventBus.post(count(100));
		fixture.plugin.onLootReceived(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		assertTrue(fixture.takeUpload().has("sourceCount"));
		fixture.drainTransport();
		assertTrue(Files.exists(continuity()));
		java.util.concurrent.atomic.AtomicReference<Runnable> deferred = new java.util.concurrent.atomic.AtomicReference<>();
		DropHandlerFixture.setField(fixture.plugin, "clientThread", new net.runelite.client.callback.ClientThread()
		{
			@Override public void invokeLater(Runnable task) { deferred.set(task); }
		});
		fixture.changeCountConsent(false);
		assertNotNull(deferred.get());
		assertFalse(fixture.dropClient.isConnected());
		assertFalse((Boolean) DropHandlerFixture.getField(fixture.plugin, "dropChatSubscribed"));
		fixture.plugin.onChatMessage(null);
		assertFalse(Files.exists(continuity()));
		deferred.get().run();
		fixture.drainTransport();
		assertTrue(fixture.dropClient.isConnected());
	}

	@Test public void quickOffOnCannotRestoreEvidenceFromARevokedBufferedDrop() throws Exception
	{
		fixture.enableCounts();
		fixture.eventBus.post(count(100));
		DropCaptureResult buffered = fixture.plugin.handleLoot(DropLootHandlerTest.loot("Vorkath", new ItemStack(21907, 1)));
		fixture.eventBus.post(newItem());
		java.util.List<Runnable> deferred = new java.util.ArrayList<>();
		DropHandlerFixture.setField(fixture.plugin, "clientThread", new net.runelite.client.callback.ClientThread()
		{
			@Override public void invokeLater(Runnable task) { deferred.add(task); }
		});
		fixture.changeCountConsent(false);
		fixture.changeCountConsent(true);
		assertFalse(fixture.dropClient.isConnected());
		deferred.forEach(Runnable::run);
		fixture.drainTransport();
		JsonObject uploaded = fixture.takeUpload();
		assertEquals(buffered.event().get().eventId(), uploaded.get("eventId").getAsString());
		assertLegacy(uploaded);
		assertFalse(Files.exists(continuity()));
	}

	static void assertLegacy(JsonObject event)
	{
		assertNotNull(event);
		assertEquals(Set.of("eventId", "sessionId", "occurredAt", "source", "rollCount", "items"), event.keySet());
		assertEquals(Set.of("type", "name"), event.getAsJsonObject("source").keySet());
		event.getAsJsonArray("items").forEach(item -> assertFalse(item.getAsJsonObject().has("newCollectionLogSlot")));
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
