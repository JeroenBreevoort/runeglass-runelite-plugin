package com.runeglass.runelite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DropCountParser
{
	private static final String NUMBER = "([0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)";
	private static final Pattern COUNT = Pattern.compile("^Your (?:(completed|completion count for|subdued) )?(.+?)(?: (kill|completion|chest|harvest|success))? count is:\\s*" + NUMBER + "\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PREFIX_COUNT = Pattern.compile("^Your completion count for (.+?) is:\\s*" + NUMBER + "\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLUE = Pattern.compile("^You have completed " + NUMBER + " (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.?$", Pattern.CASE_INSENSITIVE);
	private static final String LOG_PREFIX = "New item added to your collection log: ";

	private DropCountParser() { }

	static String plain(String text)
	{
		if (text == null || text.length() > 1024) return "";
		return text.replaceAll("<[^>]*>", "").replaceAll("@[a-zA-Z0-9_]{1,40}@", "").replace('\u00a0', ' ').trim();
	}

	static Count parse(String message)
	{
		String text = plain(message);
		Matcher clue = CLUE.matcher(text);
		if (clue.matches()) return result(DropCounterRegistry.byChatName("Clue Scrolls (" + clue.group(2) + ")"), clue.group(1));
		Matcher prefix = PREFIX_COUNT.matcher(text);
		if (prefix.matches()) return result(DropCounterRegistry.byChatName(prefix.group(1)), prefix.group(2));
		Matcher kc = COUNT.matcher(text);
		if (!kc.matches() || (kc.group(1) == null && kc.group(3) == null)) return null;
		return result(DropCounterRegistry.byChatName(kc.group(2)), kc.group(4));
	}

	static String collectionItem(String message)
	{
		String text = plain(message);
		if (!text.startsWith(LOG_PREFIX)) return null;
		String name = text.substring(LOG_PREFIX.length());
		return DropEvent.saneLabel(name) ? name : null;
	}

	private static Count result(DropCounterRegistry.Counter counter, String number)
	{
		if (counter == null) return null;
		try
		{
			int value = Integer.parseInt(number.replace(",", ""));
			return value > 0 ? new Count(counter, value) : null;
		}
		catch (NumberFormatException exception) { return null; }
	}

	static final class Count
	{
		final DropCounterRegistry.Counter counter;
		final int value;
		Count(DropCounterRegistry.Counter counter, int value) { this.counter = counter; this.value = value; }
	}
}
