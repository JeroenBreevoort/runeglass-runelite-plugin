/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks the collection log.
 * <p>
 * There is no way to read the whole log on demand — its contents live behind the interface, and
 * only the page currently drawn is readable. So this works two ways, and neither alone is enough:
 * <ul>
 *   <li><b>New unlocks</b> are caught from the game's own chat message, which arrives whether or
 *       not the log is open. This is the real-time path and needs no interaction.</li>
 *   <li><b>Bulk state</b> is scraped when the player opens a page, which is the only moment the
 *       contents exist to be read. Pages never visited stay unknown rather than being reported
 *       as empty — those are very different claims.</li>
 * </ul>
 * The chat message depends on the in-game "collection log notification" option. When it is off,
 * unlocks are only noticed the next time the relevant page is opened, so the plugin reports
 * whether that option is enabled rather than silently under-reporting.
 */
@Slf4j
@Singleton
public class CollectionLogCollector
{
	/**
	 * The game's own wording when a slot is filled. Matched loosely on the prefix so a trailing
	 * colon, punctuation change or added suffix does not silently stop detection.
	 */
	private static final Pattern NEW_ITEM = Pattern.compile(
		"New item added to your collection log:?\\s*([^:\\s].*?)\\.?$", Pattern.CASE_INSENSITIVE);

	/** Unobtained entries are drawn faded; obtained ones are fully opaque. */
	private static final int OPAQUE = 0;

	private final Client client;
	private final RuneGlassConfig config;
	private final SyncService sync;

	/** Category name to what was on that page when we last saw it. */
	private final Map<String, PageState> pages = new LinkedHashMap<>();

	/** Item names seen unlocked this session, to avoid re-reporting the same one. */
	private final Set<String> announced = new LinkedHashSet<>();

	@Inject
	CollectionLogCollector(Client client, RuneGlassConfig config, SyncService sync)
	{
		this.client = client;
		this.config = config;
		this.sync = sync;
	}

	public void reset()
	{
		pages.clear();
		announced.clear();
	}

	// ------------------------------------------------------------------
	// Real-time: the game tells us
	// ------------------------------------------------------------------

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!enabled())
		{
			return;
		}

		final ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
		{
			return;
		}

		final String item = matchNewItem(event.getMessage());
		if (item == null || !announced.add(item))
		{
			return;
		}

		final Map<String, Object> data = new HashMap<>();
		data.put("item", item);
		data.put("source", "chat");
		sync.record(RuneGlassApi.Kind.COLLECTION_LOG, data);
	}

	/**
	 * Extracts the item name from a collection log unlock message, or null if it isn't one.
	 * <p>
	 * Chat arrives with formatting tags, which have to come off before matching.
	 */
	@Nullable
	static String matchNewItem(@Nullable String message)
	{
		if (message == null)
		{
			return null;
		}

		final String plain = stripTags(message).trim();
		final Matcher matcher = NEW_ITEM.matcher(plain);
		if (!matcher.matches())
		{
			return null;
		}

		final String item = matcher.group(1).trim();
		return item.isEmpty() ? null : item;
	}

	private static String stripTags(String text)
	{
		final StringBuilder out = new StringBuilder(text.length());
		boolean inTag = false;

		for (int i = 0; i < text.length(); i++)
		{
			final char c = text.charAt(i);
			if (c == '<')
			{
				inTag = true;
			}
			else if (c == '>')
			{
				inTag = false;
			}
			else if (!inTag)
			{
				out.append(c);
			}
		}

		return out.toString();
	}

	// ------------------------------------------------------------------
	// Bulk: scrape whatever page the player opens
	// ------------------------------------------------------------------

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST || !enabled())
		{
			return;
		}

		readVisiblePage();
	}

	/**
	 * Reads the collection log page currently drawn. Must run on the client thread.
	 */
	private void readVisiblePage()
	{
		final Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		final Widget items = client.getWidget(InterfaceID.Collection.ITEMS);
		if (header == null || items == null)
		{
			return;
		}

		final String category = stripTags(header.getText() == null ? "" : header.getText()).trim();
		if (category.isEmpty())
		{
			return;
		}

		final Widget[] children = items.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		final List<RuneGlassApi.ItemStack> obtained = new ArrayList<>();
		int total = 0;

		for (Widget child : children)
		{
			final int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			total++;

			// Faded entries are slots the player has not filled yet.
			if (child.getOpacity() == OPAQUE)
			{
				obtained.add(new RuneGlassApi.ItemStack(itemId, Math.max(child.getItemQuantity(), 1), -1));
			}
		}

		if (total == 0)
		{
			return;
		}

		final PageState previous = pages.get(category);
		final PageState current = new PageState(obtained, total);
		pages.put(category, current);

		// Reopening a page the player has not progressed since is not news.
		if (previous != null && previous.obtained.size() == current.obtained.size())
		{
			return;
		}

		final Map<String, Object> data = new HashMap<>();
		data.put("category", category);
		data.put("obtained", current.obtained.size());
		data.put("total", total);
		data.put("items", current.obtained);
		data.put("source", "page");
		sync.record(RuneGlassApi.Kind.COLLECTION_LOG, data);
	}

	// ------------------------------------------------------------------

	/**
	 * Everything read so far, for the snapshot. Categories the player has never opened are absent
	 * rather than empty — the backend must not read "not yet seen" as "nothing obtained".
	 */
	public Map<String, Object> buildSummary()
	{
		final Map<String, Object> summary = new LinkedHashMap<>();

		final Map<String, Object> categories = new LinkedHashMap<>();
		for (Map.Entry<String, PageState> entry : pages.entrySet())
		{
			final Map<String, Object> page = new LinkedHashMap<>();
			page.put("obtained", entry.getValue().obtained.size());
			page.put("total", entry.getValue().total);
			page.put("items", entry.getValue().obtained);
			categories.put(entry.getKey(), page);
		}

		summary.put("categories", categories);
		summary.put("notificationsEnabled", notificationsEnabled());
		return Collections.unmodifiableMap(summary);
	}

	/**
	 * Whether the in-game unlock message is switched on. Without it the real-time path is silent,
	 * which the app should be able to explain to the user rather than appearing stale.
	 */
	private boolean notificationsEnabled()
	{
		return client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) == 1;
	}

	private boolean enabled()
	{
		return config.syncEnabled() && config.syncProgress();
	}

	int getPageCount()
	{
		return pages.size();
	}

	private static final class PageState
	{
		private final List<RuneGlassApi.ItemStack> obtained;
		private final int total;

		PageState(List<RuneGlassApi.ItemStack> obtained, int total)
		{
			this.obtained = Collections.unmodifiableList(obtained);
			this.total = total;
		}
	}
}
