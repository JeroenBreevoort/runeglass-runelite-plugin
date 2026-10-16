package com.runeglass.runelite;

import java.util.Set;

final class DropPolicy
{
	static final long MINIMUM_VALUE = 50000L;
	static final Set<String> REWARD_EVENTS = Set.of(
		"Barrows",
		"Lunar Chest",
		"Fortis Colosseum",
		"Doom of Mokhaiotl",
		"Chambers of Xeric",
		"Theatre of Blood",
		"Tombs of Amascut",
		"Clue Scroll (Beginner)",
		"Clue Scroll (Easy)",
		"Clue Scroll (Medium)",
		"Clue Scroll (Hard)",
		"Clue Scroll (Elite)",
		"Clue Scroll (Master)",
		"Reward pool (Tempoross)",
		"Reward cart (Wintertodt)",
		"Supply crate (Wintertodt)",
		"Guardians of the Rift",
		"Dossier",
		"Unsired");

	private DropPolicy()
	{
	}

	static boolean qualifies(String type, String name, int itemId, Long capturedStackGp)
	{
		return (capturedStackGp != null && capturedStackGp >= MINIMUM_VALUE
			&& capturedStackGp <= 9007199254740991L) || isNotableReward(type, name, itemId);
	}

	static boolean isNotableReward(String type, String name, int itemId)
	{
		if ("npc".equals(type) && "Abyssal demon".equals(name))
		{
			return itemId == 4151;
		}
		if ("npc".equals(type) && "Greater abyssal demon".equals(name))
		{
			return itemId == 4151;
		}
		if ("npc".equals(type) && "Vorkath".equals(name))
		{
			return itemId == 11286 || itemId == 21907 || itemId == 22006 || itemId == 22106 || itemId == 22111;
		}
		if ("npc".equals(type) && "Zulrah".equals(name))
		{
			return itemId == 6571 || itemId == 12922 || itemId == 12927 || itemId == 12932 || itemId == 12936 || itemId == 12938 || itemId == 13200 || itemId == 13201;
		}
		if ("event".equals(type) && "Clue Scroll (Medium)".equals(name))
		{
			return itemId == 2577 || itemId == 2579 || itemId == 2599 || itemId == 2601 || itemId == 2603 || itemId == 2605 || itemId == 2607 || itemId == 2609 || itemId == 2611 || itemId == 2613 || itemId == 2645 || itemId == 2647 || itemId == 2649 || itemId == 3474 || itemId == 3475 || itemId == 3827 || itemId == 3828 || itemId == 3829 || itemId == 3830 || itemId == 3831 || itemId == 3832 || itemId == 3833 || itemId == 3834 || itemId == 3835 || itemId == 3836 || itemId == 3837 || itemId == 3838 || itemId == 7319 || itemId == 7321 || itemId == 7323 || itemId == 7325 || itemId == 7327 || itemId == 7329 || itemId == 7330 || itemId == 7331 || itemId == 7334 || itemId == 7340 || itemId == 7346 || itemId == 7352 || itemId == 7358 || itemId == 7370 || itemId == 7372 || itemId == 7378 || itemId == 7380 || itemId == 10282 || itemId == 10296 || itemId == 10298 || itemId == 10300 || itemId == 10302 || itemId == 10304 || itemId == 10326 || itemId == 10327 || itemId == 10364 || itemId == 10400 || itemId == 10402 || itemId == 10416 || itemId == 10418 || itemId == 10420 || itemId == 10422 || itemId == 10436 || itemId == 10438 || itemId == 10446 || itemId == 10448 || itemId == 10450 || itemId == 10452 || itemId == 10454 || itemId == 10456 || itemId == 10476 || itemId == 12197 || itemId == 12199 || itemId == 12201 || itemId == 12203 || itemId == 12257 || itemId == 12259 || itemId == 12261 || itemId == 12263 || itemId == 12269 || itemId == 12271 || itemId == 12273 || itemId == 12275 || itemId == 12277 || itemId == 12279 || itemId == 12281 || itemId == 12283 || itemId == 12285 || itemId == 12287 || itemId == 12289 || itemId == 12291 || itemId == 12293 || itemId == 12295 || itemId == 12299 || itemId == 12301 || itemId == 12303 || itemId == 12305 || itemId == 12307 || itemId == 12309 || itemId == 12311 || itemId == 12313 || itemId == 12315 || itemId == 12317 || itemId == 12319 || itemId == 12339 || itemId == 12341 || itemId == 12343 || itemId == 12345 || itemId == 12347 || itemId == 12349 || itemId == 12359 || itemId == 12361 || itemId == 12377 || itemId == 12402 || itemId == 12403 || itemId == 12404 || itemId == 12405 || itemId == 12406 || itemId == 12407 || itemId == 12408 || itemId == 12409 || itemId == 12410 || itemId == 12411 || itemId == 12428 || itemId == 12598 || itemId == 12613 || itemId == 12614 || itemId == 12615 || itemId == 12616 || itemId == 12617 || itemId == 12618 || itemId == 12619 || itemId == 12620 || itemId == 12621 || itemId == 12622 || itemId == 12623 || itemId == 12624 || itemId == 12642 || itemId == 20220 || itemId == 20223 || itemId == 20226 || itemId == 20229 || itemId == 20232 || itemId == 20235 || itemId == 20238 || itemId == 20240 || itemId == 20243 || itemId == 20246 || itemId == 20251 || itemId == 20254 || itemId == 20257 || itemId == 20260 || itemId == 20263 || itemId == 20266 || itemId == 20269 || itemId == 20272 || itemId == 20275 || itemId == 21387 || itemId == 23389 || itemId == 23392 || itemId == 23395 || itemId == 23398 || itemId == 23401 || itemId == 23404 || itemId == 23407 || itemId == 23410 || itemId == 23413;
		}
		if ("event".equals(type) && "Chambers of Xeric".equals(name))
		{
			return itemId == 13652 || itemId == 20997 || itemId == 21000 || itemId == 21003 || itemId == 21012 || itemId == 21015 || itemId == 21018 || itemId == 21021 || itemId == 21024 || itemId == 21027 || itemId == 21034 || itemId == 21043 || itemId == 21047 || itemId == 21079 || itemId == 22386 || itemId == 24670;
		}
		if ("npc".equals(type) && "Abyssal Sire".equals(name))
		{
			return itemId == 13273;
		}
		if ("npc".equals(type) && "Alchemical Hydra".equals(name))
		{
			return itemId == 20849 || itemId == 22804 || itemId == 22966 || itemId == 22969 || itemId == 22971 || itemId == 22973 || itemId == 22983 || itemId == 22988 || itemId == 23064 || itemId == 23077;
		}
		if ("npc".equals(type) && "Amoxliatl".equals(name))
		{
			return itemId == 29889 || itemId == 29892 || itemId == 29895;
		}
		if ("npc".equals(type) && "Artio".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12603 || itemId == 27667 || itemId == 27681;
		}
		if ("npc".equals(type) && "Calvar'ion".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12601 || itemId == 27673 || itemId == 27684;
		}
		if ("npc".equals(type) && "Spindel".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12605 || itemId == 27670 || itemId == 27687;
		}
		if ("npc".equals(type) && "Obor (Chest)".equals(name))
		{
			return itemId == 20756;
		}
		if ("npc".equals(type) && "Bryophyta (Chest)".equals(name))
		{
			return itemId == 22372;
		}
		if ("npc".equals(type) && "Cerberus".equals(name))
		{
			return itemId == 13227 || itemId == 13229 || itemId == 13231 || itemId == 13233 || itemId == 13245 || itemId == 13249;
		}
		if ("npc".equals(type) && "Chaos Elemental".equals(name))
		{
			return itemId == 7158 || itemId == 11920;
		}
		if ("npc".equals(type) && "Chaos Fanatic".equals(name))
		{
			return itemId == 11928 || itemId == 11931;
		}
		if ("npc".equals(type) && "Crazy archaeologist".equals(name))
		{
			return itemId == 11929 || itemId == 11932 || itemId == 11990;
		}
		if ("npc".equals(type) && "Scorpia".equals(name))
		{
			return itemId == 7158 || itemId == 11930 || itemId == 11933;
		}
		if ("npc".equals(type) && "Commander Zilyana".equals(name))
		{
			return itemId == 11785 || itemId == 11814 || itemId == 11818 || itemId == 11820 || itemId == 11822 || itemId == 11838 || itemId == 13256;
		}
		if ("npc".equals(type) && "General Graardor".equals(name))
		{
			return itemId == 11812 || itemId == 11818 || itemId == 11820 || itemId == 11822 || itemId == 11832 || itemId == 11834 || itemId == 11836;
		}
		if ("npc".equals(type) && "Kree'arra".equals(name))
		{
			return itemId == 11810 || itemId == 11818 || itemId == 11820 || itemId == 11822 || itemId == 11826 || itemId == 11828 || itemId == 11830;
		}
		if ("npc".equals(type) && "K'ril Tsutsaroth".equals(name))
		{
			return itemId == 11787 || itemId == 11791 || itemId == 11816 || itemId == 11818 || itemId == 11820 || itemId == 11822 || itemId == 11824;
		}
		if ("npc".equals(type) && "Corporeal Beast".equals(name))
		{
			return itemId == 12819 || itemId == 12823 || itemId == 12827 || itemId == 12829 || itemId == 12833 || itemId == 25521;
		}
		if ("npc".equals(type) && "Dagannoth Prime".equals(name))
		{
			return itemId == 6562 || itemId == 6731 || itemId == 6739;
		}
		if ("npc".equals(type) && "Dagannoth Rex".equals(name))
		{
			return itemId == 6735 || itemId == 6737 || itemId == 6739;
		}
		if ("npc".equals(type) && "Dagannoth Supreme".equals(name))
		{
			return itemId == 6724 || itemId == 6733 || itemId == 6739;
		}
		if ("npc".equals(type) && "Deranged archaeologist".equals(name))
		{
			return itemId == 30895;
		}
		if ("npc".equals(type) && "Giant Mole".equals(name))
		{
			return itemId == 33382;
		}
		if ("npc".equals(type) && "Dusk".equals(name))
		{
			return itemId == 21730 || itemId == 21736 || itemId == 21739 || itemId == 21742 || itemId == 21745;
		}
		if ("npc".equals(type) && "Hespori".equals(name))
		{
			return itemId == 22881 || itemId == 22883 || itemId == 22885 || itemId == 22994;
		}
		if ("npc".equals(type) && "Kalphite Queen".equals(name))
		{
			return itemId == 3140 || itemId == 7158 || itemId == 7981 || itemId == 11920 || itemId == 12885;
		}
		if ("npc".equals(type) && "King Black Dragon".equals(name))
		{
			return itemId == 7980 || itemId == 11286 || itemId == 11920;
		}
		if ("npc".equals(type) && "Kraken".equals(name))
		{
			return itemId == 11905 || itemId == 12004 || itemId == 12007;
		}
		if ("npc".equals(type) && "Phosani's Nightmare".equals(name))
		{
			return itemId == 24417 || itemId == 24419 || itemId == 24420 || itemId == 24421 || itemId == 24422 || itemId == 24495 || itemId == 24511 || itemId == 24514 || itemId == 24517 || itemId == 25837 || itemId == 25838;
		}
		if ("npc".equals(type) && "Phantom Muspah".equals(name))
		{
			return itemId == 27614 || itemId == 27622 || itemId == 27627 || itemId == 27643;
		}
		if ("npc".equals(type) && "Sarachnis".equals(name))
		{
			return itemId == 23517 || itemId == 23525 || itemId == 23528 || itemId == 33133;
		}
		if ("npc".equals(type) && "Shellbane gryphon".equals(name))
		{
			return itemId == 31245 || itemId == 32921;
		}
		if ("npc".equals(type) && "Skotizo".equals(name))
		{
			return itemId == 6571 || itemId == 19685 || itemId == 19701 || itemId == 21275;
		}
		if ("npc".equals(type) && "Crystalline Hunllef".equals(name))
		{
			return itemId == 4207 || itemId == 23956 || itemId == 25859;
		}
		if ("npc".equals(type) && "Corrupted Hunllef".equals(name))
		{
			return itemId == 4207 || itemId == 23859 || itemId == 23956 || itemId == 25859;
		}
		if ("npc".equals(type) && "Thermonuclear smoke devil".equals(name))
		{
			return itemId == 3140 || itemId == 11998 || itemId == 12002 || itemId == 25524;
		}
		if ("npc".equals(type) && "Araxxor".equals(name))
		{
			return itemId == 29781 || itemId == 29782 || itemId == 29784 || itemId == 29786 || itemId == 29788 || itemId == 29790 || itemId == 29792 || itemId == 29794 || itemId == 29799;
		}
		if ("npc".equals(type) && "Callisto".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12603 || itemId == 27667 || itemId == 27681;
		}
		if ("npc".equals(type) && "Vet'ion".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12601 || itemId == 27673 || itemId == 27684;
		}
		if ("npc".equals(type) && "Venenatis".equals(name))
		{
			return itemId == 7158 || itemId == 11920 || itemId == 12605 || itemId == 27670 || itemId == 27687;
		}
		if ("event".equals(type) && "Barrows".equals(name))
		{
			return itemId == 4708 || itemId == 4710 || itemId == 4712 || itemId == 4714 || itemId == 4716 || itemId == 4718 || itemId == 4720 || itemId == 4722 || itemId == 4724 || itemId == 4726 || itemId == 4728 || itemId == 4730 || itemId == 4732 || itemId == 4734 || itemId == 4736 || itemId == 4738 || itemId == 4740 || itemId == 4745 || itemId == 4747 || itemId == 4749 || itemId == 4751 || itemId == 4753 || itemId == 4755 || itemId == 4757 || itemId == 4759;
		}
		if ("event".equals(type) && "Doom of Mokhaiotl".equals(name))
		{
			return itemId == 31088 || itemId == 31099 || itemId == 31109 || itemId == 31115;
		}
		if ("npc".equals(type) && "Duke Sucellus".equals(name))
		{
			return itemId == 26241 || itemId == 26243 || itemId == 26245 || itemId == 28270 || itemId == 28276 || itemId == 28281 || itemId == 28321 || itemId == 28333 || itemId == 28334;
		}
		if ("npc".equals(type) && "The Leviathan".equals(name))
		{
			return itemId == 26241 || itemId == 26243 || itemId == 26245 || itemId == 28274 || itemId == 28276 || itemId == 28283 || itemId == 28325 || itemId == 28332 || itemId == 28334;
		}
		if ("npc".equals(type) && "The Whisperer".equals(name))
		{
			return itemId == 26241 || itemId == 26243 || itemId == 26245 || itemId == 28272 || itemId == 28276 || itemId == 28279 || itemId == 28323 || itemId == 28331 || itemId == 28334;
		}
		if ("npc".equals(type) && "Vardorvis".equals(name))
		{
			return itemId == 26241 || itemId == 26243 || itemId == 26245 || itemId == 28268 || itemId == 28276 || itemId == 28285 || itemId == 28319 || itemId == 28330 || itemId == 28334;
		}
		if ("event".equals(type) && "Lunar Chest".equals(name))
		{
			return itemId == 28988 || itemId == 28991 || itemId == 28997 || itemId == 29000 || itemId == 29004 || itemId == 29007 || itemId == 29010 || itemId == 29013 || itemId == 29016 || itemId == 29019 || itemId == 29022 || itemId == 29025 || itemId == 29028;
		}
		if ("npc".equals(type) && "The Nightmare".equals(name))
		{
			return itemId == 24417 || itemId == 24419 || itemId == 24420 || itemId == 24421 || itemId == 24422 || itemId == 24495 || itemId == 24511 || itemId == 24514 || itemId == 24517;
		}
		if ("npc".equals(type) && "Nex".equals(name))
		{
			return itemId == 26231 || itemId == 26235 || itemId == 26370 || itemId == 26372 || itemId == 26376 || itemId == 26378 || itemId == 26380;
		}
		if ("npc".equals(type) && "Scurrius".equals(name))
		{
			return itemId == 28798;
		}
		if ("npc".equals(type) && "The Hueycoatl".equals(name))
		{
			return itemId == 30066 || itemId == 30068 || itemId == 30070 || itemId == 30085 || itemId == 30088;
		}
		if ("npc".equals(type) && "Branda the Fire Queen".equals(name))
		{
			return itemId == 30627 || itemId == 30631 || itemId == 30638;
		}
		if ("npc".equals(type) && "Eldric the Ice King".equals(name))
		{
			return itemId == 30626 || itemId == 30628 || itemId == 30638;
		}
		if ("event".equals(type) && "Theatre of Blood".equals(name))
		{
			return itemId == 22324 || itemId == 22326 || itemId == 22327 || itemId == 22328 || itemId == 22446 || itemId == 22477 || itemId == 22481 || itemId == 22486 || itemId == 25742 || itemId == 25744 || itemId == 25746;
		}
		if ("event".equals(type) && "Tombs of Amascut".equals(name))
		{
			return itemId == 25975 || itemId == 25985 || itemId == 26219 || itemId == 27226 || itemId == 27229 || itemId == 27232 || itemId == 27248 || itemId == 27255 || itemId == 27277 || itemId == 27279 || itemId == 27283 || itemId == 27285 || itemId == 27289 || itemId == 27293 || itemId == 27372 || itemId == 27377 || itemId == 27378 || itemId == 27379 || itemId == 27380 || itemId == 27381 || itemId == 30893;
		}
		if ("event".equals(type) && "Reward pool (Tempoross)".equals(name))
		{
			return itemId == 21028 || itemId == 25559 || itemId == 25576 || itemId == 25578 || itemId == 25580 || itemId == 25582 || itemId == 25588;
		}
		if ("event".equals(type) && "Reward cart (Wintertodt)".equals(name))
		{
			return itemId == 6739 || itemId == 20704 || itemId == 20706 || itemId == 20708 || itemId == 20710 || itemId == 20712 || itemId == 20716 || itemId == 20718 || itemId == 20720;
		}
		if ("event".equals(type) && "Supply crate (Wintertodt)".equals(name))
		{
			return itemId == 6739 || itemId == 20704 || itemId == 20706 || itemId == 20708 || itemId == 20710 || itemId == 20712 || itemId == 20716 || itemId == 20718 || itemId == 20720;
		}
		if ("event".equals(type) && "Guardians of the Rift".equals(name))
		{
			return itemId == 26792 || itemId == 26798 || itemId == 26807 || itemId == 26809 || itemId == 26811 || itemId == 26813 || itemId == 26822 || itemId == 26908;
		}
		if ("npc".equals(type) && "Zalcano".equals(name))
		{
			return itemId == 6571 || itemId == 23908 || itemId == 23953;
		}
		if ("npc".equals(type) && "Yama".equals(name))
		{
			return itemId == 30750 || itemId == 30753 || itemId == 30756 || itemId == 30759 || itemId == 30763 || itemId == 30765 || itemId == 30775 || itemId == 30795 || itemId == 30803;
		}
		if ("event".equals(type) && "Dossier".equals(name))
		{
			return itemId == 30806;
		}
		if ("event".equals(type) && "Unsired".equals(name))
		{
			return itemId == 4151 || itemId == 7979 || itemId == 13265 || itemId == 13274 || itemId == 13275 || itemId == 13276 || itemId == 13277;
		}
		if ("event".equals(type) && "Clue Scroll (Beginner)".equals(name))
		{
			return itemId == 12297 || itemId == 23285 || itemId == 23288 || itemId == 23291 || itemId == 23294 || itemId == 23297 || itemId == 23300 || itemId == 23303 || itemId == 23306 || itemId == 23309 || itemId == 23312 || itemId == 23315 || itemId == 23318 || itemId == 23321 || itemId == 23324 || itemId == 23327;
		}
		if ("event".equals(type) && "Clue Scroll (Easy)".equals(name))
		{
			return itemId == 2583 || itemId == 2585 || itemId == 2587 || itemId == 2589 || itemId == 2591 || itemId == 2593 || itemId == 2595 || itemId == 2597 || itemId == 2631 || itemId == 2633 || itemId == 2635 || itemId == 2637 || itemId == 3472 || itemId == 3473 || itemId == 3827 || itemId == 3828 || itemId == 3829 || itemId == 3830 || itemId == 3831 || itemId == 3832 || itemId == 3833 || itemId == 3834 || itemId == 3835 || itemId == 3836 || itemId == 3837 || itemId == 3838 || itemId == 7329 || itemId == 7330 || itemId == 7331 || itemId == 7332 || itemId == 7338 || itemId == 7344 || itemId == 7350 || itemId == 7356 || itemId == 7362 || itemId == 7364 || itemId == 7366 || itemId == 7368 || itemId == 7386 || itemId == 7388 || itemId == 7390 || itemId == 7392 || itemId == 7394 || itemId == 7396 || itemId == 10280 || itemId == 10306 || itemId == 10308 || itemId == 10310 || itemId == 10312 || itemId == 10314 || itemId == 10316 || itemId == 10318 || itemId == 10320 || itemId == 10322 || itemId == 10324 || itemId == 10326 || itemId == 10327 || itemId == 10366 || itemId == 10392 || itemId == 10394 || itemId == 10396 || itemId == 10398 || itemId == 10404 || itemId == 10406 || itemId == 10408 || itemId == 10410 || itemId == 10412 || itemId == 10414 || itemId == 10424 || itemId == 10426 || itemId == 10428 || itemId == 10430 || itemId == 10432 || itemId == 10434 || itemId == 10458 || itemId == 10460 || itemId == 10462 || itemId == 10464 || itemId == 10466 || itemId == 10468 || itemId == 10476 || itemId == 12193 || itemId == 12195 || itemId == 12205 || itemId == 12207 || itemId == 12209 || itemId == 12211 || itemId == 12213 || itemId == 12215 || itemId == 12217 || itemId == 12219 || itemId == 12221 || itemId == 12223 || itemId == 12225 || itemId == 12227 || itemId == 12229 || itemId == 12231 || itemId == 12233 || itemId == 12235 || itemId == 12237 || itemId == 12239 || itemId == 12241 || itemId == 12243 || itemId == 12245 || itemId == 12247 || itemId == 12249 || itemId == 12251 || itemId == 12253 || itemId == 12255 || itemId == 12265 || itemId == 12267 || itemId == 12297 || itemId == 12375 || itemId == 12402 || itemId == 12403 || itemId == 12404 || itemId == 12405 || itemId == 12406 || itemId == 12407 || itemId == 12408 || itemId == 12409 || itemId == 12410 || itemId == 12411 || itemId == 12445 || itemId == 12447 || itemId == 12449 || itemId == 12451 || itemId == 12453 || itemId == 12455 || itemId == 12613 || itemId == 12614 || itemId == 12615 || itemId == 12616 || itemId == 12617 || itemId == 12618 || itemId == 12619 || itemId == 12620 || itemId == 12621 || itemId == 12622 || itemId == 12623 || itemId == 12624 || itemId == 12642 || itemId == 20166 || itemId == 20169 || itemId == 20172 || itemId == 20175 || itemId == 20178 || itemId == 20181 || itemId == 20184 || itemId == 20187 || itemId == 20190 || itemId == 20193 || itemId == 20196 || itemId == 20199 || itemId == 20202 || itemId == 20205 || itemId == 20208 || itemId == 20211 || itemId == 20214 || itemId == 20217 || itemId == 20220 || itemId == 20223 || itemId == 20226 || itemId == 20229 || itemId == 20232 || itemId == 20235 || itemId == 20238 || itemId == 21387 || itemId == 23351 || itemId == 23354 || itemId == 23357 || itemId == 23360 || itemId == 23363 || itemId == 23366 || itemId == 23369 || itemId == 23372 || itemId == 23375 || itemId == 23378 || itemId == 23381 || itemId == 23384;
		}
		if ("event".equals(type) && "Clue Scroll (Hard)".equals(name))
		{
			return itemId == 2581 || itemId == 2615 || itemId == 2617 || itemId == 2619 || itemId == 2621 || itemId == 2623 || itemId == 2625 || itemId == 2627 || itemId == 2629 || itemId == 2639 || itemId == 2641 || itemId == 2643 || itemId == 2651 || itemId == 2653 || itemId == 2655 || itemId == 2657 || itemId == 2659 || itemId == 2661 || itemId == 2663 || itemId == 2665 || itemId == 2667 || itemId == 2669 || itemId == 2671 || itemId == 2673 || itemId == 2675 || itemId == 3476 || itemId == 3477 || itemId == 3478 || itemId == 3479 || itemId == 3480 || itemId == 3481 || itemId == 3483 || itemId == 3485 || itemId == 3486 || itemId == 3488 || itemId == 3827 || itemId == 3828 || itemId == 3829 || itemId == 3830 || itemId == 3831 || itemId == 3832 || itemId == 3833 || itemId == 3834 || itemId == 3835 || itemId == 3836 || itemId == 3837 || itemId == 3838 || itemId == 7329 || itemId == 7330 || itemId == 7331 || itemId == 7336 || itemId == 7342 || itemId == 7348 || itemId == 7354 || itemId == 7360 || itemId == 7374 || itemId == 7376 || itemId == 7382 || itemId == 7384 || itemId == 7398 || itemId == 7399 || itemId == 7400 || itemId == 10284 || itemId == 10286 || itemId == 10288 || itemId == 10290 || itemId == 10292 || itemId == 10294 || itemId == 10326 || itemId == 10327 || itemId == 10330 || itemId == 10332 || itemId == 10334 || itemId == 10336 || itemId == 10338 || itemId == 10340 || itemId == 10342 || itemId == 10344 || itemId == 10346 || itemId == 10348 || itemId == 10350 || itemId == 10352 || itemId == 10354 || itemId == 10368 || itemId == 10370 || itemId == 10372 || itemId == 10374 || itemId == 10376 || itemId == 10378 || itemId == 10380 || itemId == 10382 || itemId == 10384 || itemId == 10386 || itemId == 10388 || itemId == 10390 || itemId == 10440 || itemId == 10442 || itemId == 10444 || itemId == 10470 || itemId == 10472 || itemId == 10474 || itemId == 10476 || itemId == 12321 || itemId == 12323 || itemId == 12325 || itemId == 12327 || itemId == 12329 || itemId == 12331 || itemId == 12333 || itemId == 12379 || itemId == 12402 || itemId == 12403 || itemId == 12404 || itemId == 12405 || itemId == 12406 || itemId == 12407 || itemId == 12408 || itemId == 12409 || itemId == 12410 || itemId == 12411 || itemId == 12460 || itemId == 12462 || itemId == 12464 || itemId == 12466 || itemId == 12468 || itemId == 12470 || itemId == 12472 || itemId == 12474 || itemId == 12476 || itemId == 12478 || itemId == 12480 || itemId == 12482 || itemId == 12484 || itemId == 12486 || itemId == 12488 || itemId == 12490 || itemId == 12492 || itemId == 12494 || itemId == 12496 || itemId == 12498 || itemId == 12500 || itemId == 12502 || itemId == 12504 || itemId == 12506 || itemId == 12508 || itemId == 12510 || itemId == 12512 || itemId == 12514 || itemId == 12516 || itemId == 12518 || itemId == 12520 || itemId == 12522 || itemId == 12524 || itemId == 12613 || itemId == 12614 || itemId == 12615 || itemId == 12616 || itemId == 12617 || itemId == 12618 || itemId == 12619 || itemId == 12620 || itemId == 12621 || itemId == 12622 || itemId == 12623 || itemId == 12624 || itemId == 12642 || itemId == 19915 || itemId == 19918 || itemId == 19921 || itemId == 19924 || itemId == 19927 || itemId == 19930 || itemId == 19933 || itemId == 19936 || itemId == 20146 || itemId == 20149 || itemId == 20152 || itemId == 20155 || itemId == 20158 || itemId == 20161 || itemId == 20220 || itemId == 20223 || itemId == 20226 || itemId == 20229 || itemId == 20232 || itemId == 20235 || itemId == 20238 || itemId == 21387 || itemId == 22231 || itemId == 23188 || itemId == 23191 || itemId == 23194 || itemId == 23197 || itemId == 23200 || itemId == 23203 || itemId == 23206 || itemId == 23209 || itemId == 23212 || itemId == 23215 || itemId == 23218 || itemId == 23221 || itemId == 23224 || itemId == 23227 || itemId == 23232 || itemId == 23237 || itemId == 23242;
		}
		if ("event".equals(type) && "Clue Scroll (Elite)".equals(name))
		{
			return itemId == 3481 || itemId == 3483 || itemId == 3485 || itemId == 3486 || itemId == 3488 || itemId == 3827 || itemId == 3828 || itemId == 3829 || itemId == 3830 || itemId == 3831 || itemId == 3832 || itemId == 3833 || itemId == 3834 || itemId == 3835 || itemId == 3836 || itemId == 3837 || itemId == 3838 || itemId == 7329 || itemId == 7330 || itemId == 7331 || itemId == 10326 || itemId == 10327 || itemId == 10330 || itemId == 10332 || itemId == 10334 || itemId == 10336 || itemId == 10338 || itemId == 10340 || itemId == 10342 || itemId == 10344 || itemId == 10346 || itemId == 10348 || itemId == 10350 || itemId == 10352 || itemId == 10476 || itemId == 12335 || itemId == 12337 || itemId == 12351 || itemId == 12353 || itemId == 12355 || itemId == 12357 || itemId == 12363 || itemId == 12365 || itemId == 12367 || itemId == 12369 || itemId == 12371 || itemId == 12373 || itemId == 12381 || itemId == 12383 || itemId == 12385 || itemId == 12387 || itemId == 12389 || itemId == 12391 || itemId == 12393 || itemId == 12395 || itemId == 12397 || itemId == 12402 || itemId == 12403 || itemId == 12404 || itemId == 12405 || itemId == 12406 || itemId == 12407 || itemId == 12408 || itemId == 12409 || itemId == 12410 || itemId == 12411 || itemId == 12422 || itemId == 12424 || itemId == 12426 || itemId == 12430 || itemId == 12432 || itemId == 12437 || itemId == 12439 || itemId == 12441 || itemId == 12443 || itemId == 12526 || itemId == 12528 || itemId == 12530 || itemId == 12532 || itemId == 12534 || itemId == 12536 || itemId == 12538 || itemId == 12540 || itemId == 12596 || itemId == 12613 || itemId == 12614 || itemId == 12615 || itemId == 12616 || itemId == 12617 || itemId == 12618 || itemId == 12619 || itemId == 12620 || itemId == 12621 || itemId == 12622 || itemId == 12623 || itemId == 12624 || itemId == 12642 || itemId == 19943 || itemId == 19946 || itemId == 19949 || itemId == 19952 || itemId == 19955 || itemId == 19958 || itemId == 19961 || itemId == 19964 || itemId == 19967 || itemId == 19970 || itemId == 19973 || itemId == 19976 || itemId == 19979 || itemId == 19982 || itemId == 19985 || itemId == 19988 || itemId == 19991 || itemId == 19994 || itemId == 19997 || itemId == 20002 || itemId == 20005 || itemId == 20011 || itemId == 20014 || itemId == 20146 || itemId == 20149 || itemId == 20152 || itemId == 20155 || itemId == 20158 || itemId == 20161 || itemId == 20220 || itemId == 20223 || itemId == 20226 || itemId == 20229 || itemId == 20232 || itemId == 20235 || itemId == 20238 || itemId == 21387 || itemId == 23185 || itemId == 23242 || itemId == 23246 || itemId == 23249 || itemId == 23252 || itemId == 23255 || itemId == 23258 || itemId == 23261 || itemId == 23264 || itemId == 23267 || itemId == 23270 || itemId == 23273 || itemId == 23276 || itemId == 23279 || itemId == 23282 || itemId == 23336 || itemId == 23339 || itemId == 23342 || itemId == 23345 || itemId == 30926;
		}
		if ("event".equals(type) && "Clue Scroll (Master)".equals(name))
		{
			return itemId == 3481 || itemId == 3483 || itemId == 3485 || itemId == 3486 || itemId == 3488 || itemId == 3827 || itemId == 3828 || itemId == 3829 || itemId == 3830 || itemId == 3831 || itemId == 3832 || itemId == 3833 || itemId == 3834 || itemId == 3835 || itemId == 3836 || itemId == 3837 || itemId == 3838 || itemId == 7329 || itemId == 7330 || itemId == 7331 || itemId == 10326 || itemId == 10327 || itemId == 10330 || itemId == 10332 || itemId == 10334 || itemId == 10336 || itemId == 10338 || itemId == 10340 || itemId == 10342 || itemId == 10344 || itemId == 10346 || itemId == 10348 || itemId == 10350 || itemId == 10352 || itemId == 10476 || itemId == 12389 || itemId == 12391 || itemId == 12402 || itemId == 12403 || itemId == 12404 || itemId == 12405 || itemId == 12406 || itemId == 12407 || itemId == 12408 || itemId == 12409 || itemId == 12410 || itemId == 12411 || itemId == 12422 || itemId == 12424 || itemId == 12426 || itemId == 12437 || itemId == 12613 || itemId == 12614 || itemId == 12615 || itemId == 12616 || itemId == 12617 || itemId == 12618 || itemId == 12619 || itemId == 12620 || itemId == 12621 || itemId == 12622 || itemId == 12623 || itemId == 12624 || itemId == 12642 || itemId == 19724 || itemId == 20008 || itemId == 20011 || itemId == 20014 || itemId == 20017 || itemId == 20020 || itemId == 20023 || itemId == 20026 || itemId == 20029 || itemId == 20032 || itemId == 20035 || itemId == 20038 || itemId == 20041 || itemId == 20044 || itemId == 20047 || itemId == 20050 || itemId == 20053 || itemId == 20056 || itemId == 20059 || itemId == 20062 || itemId == 20065 || itemId == 20068 || itemId == 20071 || itemId == 20074 || itemId == 20077 || itemId == 20080 || itemId == 20083 || itemId == 20086 || itemId == 20089 || itemId == 20092 || itemId == 20095 || itemId == 20098 || itemId == 20101 || itemId == 20104 || itemId == 20107 || itemId == 20110 || itemId == 20113 || itemId == 20116 || itemId == 20119 || itemId == 20122 || itemId == 20125 || itemId == 20128 || itemId == 20131 || itemId == 20134 || itemId == 20137 || itemId == 20140 || itemId == 20143 || itemId == 20146 || itemId == 20149 || itemId == 20152 || itemId == 20155 || itemId == 20158 || itemId == 20161 || itemId == 20220 || itemId == 20223 || itemId == 20226 || itemId == 20229 || itemId == 20232 || itemId == 20235 || itemId == 20238 || itemId == 21387 || itemId == 22236 || itemId == 22239 || itemId == 22246 || itemId == 23185 || itemId == 23242 || itemId == 23258 || itemId == 23261 || itemId == 23264 || itemId == 23267 || itemId == 23276 || itemId == 23279 || itemId == 23282 || itemId == 23336 || itemId == 23339 || itemId == 23342 || itemId == 23345 || itemId == 23348 || itemId == 30926;
		}
		if ("event".equals(type) && "Fortis Colosseum".equals(name))
		{
			return itemId == 6571 || itemId == 28919 || itemId == 28933 || itemId == 28936 || itemId == 28939 || itemId == 28942 || itemId == 28947;
		}
		return false;
	}
}
