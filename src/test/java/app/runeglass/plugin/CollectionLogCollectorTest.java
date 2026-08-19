/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class CollectionLogCollectorTest
{
	private SkillsCollectorTest.StubConfig config;
	private SkillsCollectorTest.RecordingSync sync;
	private CollectionLogCollector collector;

	@Before
	public void setUp()
	{
		config = new SkillsCollectorTest.StubConfig();
		sync = new SkillsCollectorTest.RecordingSync(config);
		// The client is only used for page scraping and the notification varbit.
		collector = new CollectionLogCollector(null, config, sync);
	}

	@Test
	public void anUnlockMessageIsRecorded()
	{
		chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: Twisted bow");

		assertEquals(1, sync.events.size());
		assertEquals(RuneGlassApi.Kind.COLLECTION_LOG, sync.events.get(0).kind);
		assertEquals("Twisted bow", sync.events.get(0).data.get("item"));
		assertEquals("chat", sync.events.get(0).data.get("source"));
	}

	@Test
	public void unlockMessagesAlsoArriveAsSpam()
	{
		// Which chat type carries it depends on the player's filter settings.
		chat(ChatMessageType.SPAM, "New item added to your collection log: Dragon warhammer");

		assertEquals(1, sync.events.size());
		assertEquals("Dragon warhammer", sync.events.get(0).data.get("item"));
	}

	@Test
	public void formattingTagsAreStrippedFromTheItemName()
	{
		chat(ChatMessageType.GAMEMESSAGE,
			"<colNORMAL>New item added to your collection log: <col=ff0000>Elysian sigil</col>");

		assertEquals("Elysian sigil", sync.events.get(0).data.get("item"));
	}

	@Test
	public void theSameUnlockIsNotReportedTwice()
	{
		chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: Twisted bow");
		chat(ChatMessageType.SPAM, "New item added to your collection log: Twisted bow");

		assertEquals("the message can arrive on more than one channel", 1, sync.events.size());
	}

	@Test
	public void ordinaryChatIsIgnored()
	{
		chat(ChatMessageType.GAMEMESSAGE, "You feel something weird sneaking into your backpack.");
		chat(ChatMessageType.GAMEMESSAGE, "Your Slayer level is now 88.");

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void publicChatIsIgnoredSoNobodyCanForgeAnUnlock()
	{
		// Another player could otherwise type the message and have it recorded as ours.
		chat(ChatMessageType.PUBLICCHAT, "New item added to your collection log: Twisted bow");

		assertTrue(sync.events.isEmpty());
	}

	@Test
	public void resetForgetsWhatWasAnnounced()
	{
		chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: Twisted bow");
		collector.reset();
		chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: Twisted bow");

		assertEquals("a different character can obtain the same item", 2, sync.events.size());
	}

	@Test
	public void nothingIsCollectedWhileTheProgressToggleIsOff()
	{
		config.syncProgress = false;

		chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: Twisted bow");

		assertTrue(sync.events.isEmpty());
	}

	// ------------------------------------------------------------------
	// Message parsing, exercised directly
	// ------------------------------------------------------------------

	@Test
	public void parsesWithAndWithoutTrailingPunctuation()
	{
		assertEquals("Bandos chestplate",
			CollectionLogCollector.matchNewItem("New item added to your collection log: Bandos chestplate"));
		assertEquals("Bandos chestplate",
			CollectionLogCollector.matchNewItem("New item added to your collection log: Bandos chestplate."));
	}

	@Test
	public void parsingIsCaseInsensitive()
	{
		assertEquals("Abyssal whip",
			CollectionLogCollector.matchNewItem("new item added to your collection log: Abyssal whip"));
	}

	@Test
	public void itemNamesWithPunctuationSurviveIntact()
	{
		assertEquals("Ring of 3rd age",
			CollectionLogCollector.matchNewItem("New item added to your collection log: Ring of 3rd age"));
		assertEquals("Bruma torch",
			CollectionLogCollector.matchNewItem("New item added to your collection log:Bruma torch"));
	}

	@Test
	public void nonMatchingMessagesReturnNull()
	{
		assertNull(CollectionLogCollector.matchNewItem(null));
		assertNull(CollectionLogCollector.matchNewItem(""));
		assertNull(CollectionLogCollector.matchNewItem("You add an item to your collection log"));
		assertNull(CollectionLogCollector.matchNewItem("New item added to your collection log:"));
	}

	private void chat(ChatMessageType type, String message)
	{
		collector.onChatMessage(new ChatMessage(null, type, "", message, "", 0));
	}
}
