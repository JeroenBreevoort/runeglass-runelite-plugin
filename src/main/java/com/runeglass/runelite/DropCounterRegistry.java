package com.runeglass.runelite;

import java.util.List;
import java.util.Locale;

final class DropCounterRegistry
{
	static final List<Counter> COUNTERS = List.of(
		new Counter("abyssal-sire", "Abyssal Sire", "kill", List.of("Abyssal Sire"), List.of("npc"), List.of("Abyssal Sire")),
		new Counter("alchemical-hydra", "Alchemical Hydra", "kill", List.of("Alchemical Hydra"), List.of("npc"), List.of("Alchemical Hydra")),
		new Counter("amoxliatl", "Amoxliatl", "kill", List.of("Amoxliatl"), List.of("npc"), List.of("Amoxliatl")),
		new Counter("araxxor", "Araxxor", "kill", List.of("Araxxor"), List.of("npc"), List.of("Araxxor")),
		new Counter("artio", "Artio", "kill", List.of("Artio"), List.of("npc"), List.of("Artio")),
		new Counter("barrows-chests", "Barrows Chests", "chest", List.of("Barrows Chests", "Barrows"), List.of("event"), List.of("Barrows Chests", "Barrows")),
		new Counter("bryophyta", "Bryophyta", "chest", List.of("Bryophyta"), List.of("npc"), List.of("Bryophyta (Chest)")),
		new Counter("callisto", "Callisto", "kill", List.of("Callisto"), List.of("npc"), List.of("Callisto")),
		new Counter("calvarion", "Calvar'ion", "kill", List.of("Calvar'ion"), List.of("npc"), List.of("Calvar'ion")),
		new Counter("cerberus", "Cerberus", "kill", List.of("Cerberus"), List.of("npc"), List.of("Cerberus")),
		new Counter("chambers-of-xeric", "Chambers of Xeric", "completion", List.of("Chambers of Xeric"), List.of("event"), List.of("Chambers of Xeric")),
		new Counter("chambers-of-xeric-challenge-mode", "Chambers of Xeric: Challenge Mode", "completion", List.of("Chambers of Xeric: Challenge Mode", "Chambers of Xeric Challenge Mode"), List.of("event"), List.of("Chambers of Xeric")),
		new Counter("chaos-elemental", "Chaos Elemental", "kill", List.of("Chaos Elemental"), List.of("npc"), List.of("Chaos Elemental")),
		new Counter("chaos-fanatic", "Chaos Fanatic", "kill", List.of("Chaos Fanatic"), List.of("npc"), List.of("Chaos Fanatic")),
		new Counter("commander-zilyana", "Commander Zilyana", "kill", List.of("Commander Zilyana"), List.of("npc"), List.of("Commander Zilyana")),
		new Counter("corporeal-beast", "Corporeal Beast", "kill", List.of("Corporeal Beast"), List.of("npc"), List.of("Corporeal Beast")),
		new Counter("crazy-archaeologist", "Crazy Archaeologist", "kill", List.of("Crazy Archaeologist"), List.of("npc"), List.of("Crazy archaeologist")),
		new Counter("dagannoth-prime", "Dagannoth Prime", "kill", List.of("Dagannoth Prime"), List.of("npc"), List.of("Dagannoth Prime")),
		new Counter("dagannoth-rex", "Dagannoth Rex", "kill", List.of("Dagannoth Rex"), List.of("npc"), List.of("Dagannoth Rex")),
		new Counter("dagannoth-supreme", "Dagannoth Supreme", "kill", List.of("Dagannoth Supreme"), List.of("npc"), List.of("Dagannoth Supreme")),
		new Counter("deranged-archaeologist", "Deranged Archaeologist", "kill", List.of("Deranged Archaeologist"), List.of("npc"), List.of("Deranged archaeologist")),
		new Counter("doom-of-mokhaiotl", "Doom of Mokhaiotl", "completion", List.of("Doom of Mokhaiotl"), List.of("npc", "event"), List.of("Doom of Mokhaiotl")),
		new Counter("duke-sucellus", "Duke Sucellus", "kill", List.of("Duke Sucellus"), List.of("npc"), List.of("Duke Sucellus")),
		new Counter("general-graardor", "General Graardor", "kill", List.of("General Graardor"), List.of("npc"), List.of("General Graardor")),
		new Counter("giant-mole", "Giant Mole", "kill", List.of("Giant Mole"), List.of("npc"), List.of("Giant Mole")),
		new Counter("grotesque-guardians", "Grotesque Guardians", "kill", List.of("Grotesque Guardians"), List.of("npc"), List.of("Dusk")),
		new Counter("hespori", "Hespori", "kill", List.of("Hespori"), List.of("npc"), List.of("Hespori")),
		new Counter("kalphite-queen", "Kalphite Queen", "kill", List.of("Kalphite Queen"), List.of("npc"), List.of("Kalphite Queen")),
		new Counter("king-black-dragon", "King Black Dragon", "kill", List.of("King Black Dragon"), List.of("npc"), List.of("King Black Dragon")),
		new Counter("kraken", "Kraken", "kill", List.of("Kraken"), List.of("npc"), List.of("Kraken")),
		new Counter("kreearra", "Kree'Arra", "kill", List.of("Kree'Arra", "Kree'arra"), List.of("npc"), List.of("Kree'Arra", "Kree'arra")),
		new Counter("kril-tsutsaroth", "K'ril Tsutsaroth", "kill", List.of("K'ril Tsutsaroth"), List.of("npc"), List.of("K'ril Tsutsaroth")),
		new Counter("lunar-chests", "Lunar Chests", "chest", List.of("Lunar Chests", "Lunar Chest"), List.of("event"), List.of("Lunar Chests", "Lunar Chest")),
		new Counter("mimic", "Mimic", "kill", List.of("Mimic"), List.of("npc"), List.of("Mimic")),
		new Counter("nex", "Nex", "kill", List.of("Nex"), List.of("npc"), List.of("Nex")),
		new Counter("nightmare", "Nightmare", "kill", List.of("Nightmare", "The Nightmare"), List.of("npc"), List.of("Nightmare", "The Nightmare")),
		new Counter("phosanis-nightmare", "Phosani's Nightmare", "kill", List.of("Phosani's Nightmare"), List.of("npc"), List.of("Phosani's Nightmare")),
		new Counter("obor", "Obor", "chest", List.of("Obor"), List.of("npc"), List.of("Obor (Chest)")),
		new Counter("phantom-muspah", "Phantom Muspah", "kill", List.of("Phantom Muspah"), List.of("npc"), List.of("Phantom Muspah")),
		new Counter("sarachnis", "Sarachnis", "kill", List.of("Sarachnis"), List.of("npc"), List.of("Sarachnis")),
		new Counter("scorpia", "Scorpia", "kill", List.of("Scorpia"), List.of("npc"), List.of("Scorpia")),
		new Counter("scurrius", "Scurrius", "kill", List.of("Scurrius"), List.of("npc"), List.of("Scurrius")),
		new Counter("shellbane-gryphon", "Shellbane Gryphon", "kill", List.of("Shellbane Gryphon"), List.of("npc"), List.of("Shellbane gryphon")),
		new Counter("skotizo", "Skotizo", "kill", List.of("Skotizo"), List.of("npc"), List.of("Skotizo")),
		new Counter("sol-heredit", "Sol Heredit", "completion", List.of("Sol Heredit", "Fortis Colosseum", "Colosseum"), List.of("npc", "event"), List.of("Sol Heredit", "Fortis Colosseum")),
		new Counter("spindel", "Spindel", "kill", List.of("Spindel"), List.of("npc"), List.of("Spindel")),
		new Counter("tempoross", "Tempoross", "completion", List.of("Tempoross"), List.of("npc"), List.of("Tempoross")),
		new Counter("the-gauntlet", "The Gauntlet", "completion", List.of("The Gauntlet", "Gauntlet", "Crystalline Hunllef"), List.of("npc"), List.of("The Gauntlet", "Gauntlet", "Crystalline Hunllef")),
		new Counter("the-corrupted-gauntlet", "The Corrupted Gauntlet", "completion", List.of("The Corrupted Gauntlet", "Corrupted Gauntlet", "Corrupted Hunllef"), List.of("npc"), List.of("The Corrupted Gauntlet", "Corrupted Gauntlet", "Corrupted Hunllef")),
		new Counter("the-hueycoatl", "The Hueycoatl", "kill", List.of("The Hueycoatl", "Hueycoatl"), List.of("npc"), List.of("The Hueycoatl", "Hueycoatl")),
		new Counter("the-leviathan", "The Leviathan", "kill", List.of("The Leviathan", "Leviathan"), List.of("npc"), List.of("The Leviathan", "Leviathan")),
		new Counter("the-royal-titans", "The Royal Titans", "kill", List.of("The Royal Titans", "Royal Titans", "Branda the Fire Queen", "Eldric the Ice King"), List.of("npc"), List.of("The Royal Titans", "Royal Titans", "Branda the Fire Queen", "Eldric the Ice King")),
		new Counter("the-whisperer", "The Whisperer", "kill", List.of("The Whisperer", "Whisperer"), List.of("npc"), List.of("The Whisperer", "Whisperer")),
		new Counter("theatre-of-blood", "Theatre of Blood", "completion", List.of("Theatre of Blood"), List.of("event"), List.of("Theatre of Blood")),
		new Counter("theatre-of-blood-hard-mode", "Theatre of Blood: Hard Mode", "completion", List.of("Theatre of Blood: Hard Mode", "Theatre of Blood Hard Mode"), List.of("event"), List.of("Theatre of Blood")),
		new Counter("thermonuclear-smoke-devil", "Thermonuclear Smoke Devil", "kill", List.of("Thermonuclear Smoke Devil"), List.of("npc"), List.of("Thermonuclear smoke devil")),
		new Counter("tombs-of-amascut", "Tombs of Amascut", "completion", List.of("Tombs of Amascut"), List.of("event"), List.of("Tombs of Amascut")),
		new Counter("tombs-of-amascut-expert-mode", "Tombs of Amascut: Expert Mode", "completion", List.of("Tombs of Amascut: Expert Mode", "Tombs of Amascut Expert Mode"), List.of("event"), List.of("Tombs of Amascut")),
		new Counter("tzkal-zuk", "TzKal-Zuk", "kill", List.of("TzKal-Zuk"), List.of("npc"), List.of("TzKal-Zuk")),
		new Counter("tztok-jad", "TzTok-Jad", "kill", List.of("TzTok-Jad"), List.of("npc"), List.of("TzTok-Jad")),
		new Counter("vardorvis", "Vardorvis", "kill", List.of("Vardorvis"), List.of("npc"), List.of("Vardorvis")),
		new Counter("venenatis", "Venenatis", "kill", List.of("Venenatis"), List.of("npc"), List.of("Venenatis")),
		new Counter("vetion", "Vet'ion", "kill", List.of("Vet'ion"), List.of("npc"), List.of("Vet'ion")),
		new Counter("vorkath", "Vorkath", "kill", List.of("Vorkath"), List.of("npc"), List.of("Vorkath")),
		new Counter("wintertodt", "Wintertodt", "completion", List.of("Wintertodt"), List.of("npc"), List.of("Wintertodt")),
		new Counter("yama", "Yama", "kill", List.of("Yama"), List.of("npc"), List.of("Yama")),
		new Counter("zalcano", "Zalcano", "kill", List.of("Zalcano"), List.of("npc"), List.of("Zalcano")),
		new Counter("zulrah", "Zulrah", "kill", List.of("Zulrah"), List.of("npc"), List.of("Zulrah")),
		new Counter("theatre-of-blood-entry-mode", "Theatre of Blood: Entry Mode", "completion", List.of("Theatre of Blood: Entry Mode", "Theatre of Blood Entry Mode"), List.of("event"), List.of("Theatre of Blood")),
		new Counter("tombs-of-amascut-entry-mode", "Tombs of Amascut: Entry Mode", "completion", List.of("Tombs of Amascut: Entry Mode", "Tombs of Amascut Entry Mode"), List.of("event"), List.of("Tombs of Amascut")),
		new Counter("clue-scrolls-beginner", "Clue Scrolls (Beginner)", "casket", List.of("Clue Scrolls (Beginner)", "Clue Scroll (Beginner)"), List.of("event"), List.of("Clue Scrolls (Beginner)", "Clue Scroll (Beginner)")),
		new Counter("clue-scrolls-easy", "Clue Scrolls (Easy)", "casket", List.of("Clue Scrolls (Easy)", "Clue Scroll (Easy)"), List.of("event"), List.of("Clue Scrolls (Easy)", "Clue Scroll (Easy)")),
		new Counter("clue-scrolls-medium", "Clue Scrolls (Medium)", "casket", List.of("Clue Scrolls (Medium)", "Clue Scroll (Medium)"), List.of("event"), List.of("Clue Scrolls (Medium)", "Clue Scroll (Medium)")),
		new Counter("clue-scrolls-hard", "Clue Scrolls (Hard)", "casket", List.of("Clue Scrolls (Hard)", "Clue Scroll (Hard)"), List.of("event"), List.of("Clue Scrolls (Hard)", "Clue Scroll (Hard)")),
		new Counter("clue-scrolls-elite", "Clue Scrolls (Elite)", "casket", List.of("Clue Scrolls (Elite)", "Clue Scroll (Elite)"), List.of("event"), List.of("Clue Scrolls (Elite)", "Clue Scroll (Elite)")),
		new Counter("clue-scrolls-master", "Clue Scrolls (Master)", "casket", List.of("Clue Scrolls (Master)", "Clue Scroll (Master)"), List.of("event"), List.of("Clue Scrolls (Master)", "Clue Scroll (Master)")));

	static Counter byKey(String key)
	{
		return COUNTERS.stream().filter(c -> c.key.equals(key)).findFirst().orElse(null);
	}

	static Counter byChatName(String name)
	{
		String normalized = normalize(name);
		return COUNTERS.stream().filter(c -> c.aliases.stream().anyMatch(a -> normalize(a).equals(normalized))).findFirst().orElse(null);
	}

	static String normalize(String name)
	{
		return name.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	static final class Counter
	{
		final String key;
		final String name;
		final String unit;
		final List<String> aliases;
		final List<String> sourceTypes;
		final List<String> sources;
		Counter(String key, String name, String unit, List<String> aliases, List<String> sourceTypes, List<String> sources)
		{
			this.key = key; this.name = name; this.unit = unit; this.aliases = aliases; this.sourceTypes = sourceTypes; this.sources = sources;
		}
		boolean matches(String type, String source) { return sourceTypes.contains(type) && sources.contains(source); }
		boolean raid() { return key.startsWith("chambers-of-xeric") || key.startsWith("theatre-of-blood") || key.startsWith("tombs-of-amascut"); }
		String raidFamily() { return raid() ? sources.get(0) : null; }
	}
}
