/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.util.List;
import java.util.Map;

/**
 * The wire contract between the plugin and the RuneGlass backend.
 * <p>
 * This file is the single source of truth for the HTTP API. The Convex implementation and the
 * local mock server in {@code tools/mock-server} both mirror these shapes; change them together.
 */
public final class RuneGlassApi
{
	private RuneGlassApi()
	{
	}

	public static final String DEFAULT_BASE_URL = "https://api.runeglass.app";

	public static final String PAIR_START = "/v1/pair/start";
	public static final String PAIR_POLL = "/v1/pair/poll";
	public static final String INGEST = "/v1/ingest";
	public static final String COMMANDS = "/v1/commands";

	/** Sent on every request so the backend can reject builds that are too old. */
	public static final String HEADER_PLUGIN_VERSION = "X-RuneGlass-Plugin";

	public static final String STATUS_PENDING = "pending";
	public static final String STATUS_CLAIMED = "claimed";
	public static final String STATUS_EXPIRED = "expired";

	// ------------------------------------------------------------------
	// Pairing
	// ------------------------------------------------------------------

	public static final class PairStartRequest
	{
		/** Random per-attempt secret. Proves the poller is the client that began the pairing. */
		public String clientNonce;
		public String pluginVersion;
	}

	public static final class PairStartResponse
	{
		public String pairingId;
		/** Short, human-typeable, e.g. {@code K7P-M2Q}. Displayed in the side panel. */
		public String code;
		public long expiresAt;
	}

	public static final class PairPollRequest
	{
		public String pairingId;
		public String clientNonce;
	}

	public static final class PairPollResponse
	{
		/** One of {@link #STATUS_PENDING}, {@link #STATUS_CLAIMED}, {@link #STATUS_EXPIRED}. */
		public String status;
		/** Present only when status is {@code claimed}. */
		public String deviceToken;
		public String accountName;
	}

	// ------------------------------------------------------------------
	// Ingest
	// ------------------------------------------------------------------

	public static final class IngestRequest
	{
		/**
		 * Decimal string, never a JSON number. The account hash is a 64-bit long and JavaScript
		 * silently loses precision above 2^53, which would collide distinct accounts.
		 */
		public String accountHash;
		public String displayName;
		public String accountType;
		public List<String> worldTypes;
		/** Identifies one login session; scopes the {@code seq} counter. */
		public String sessionId;
		public long sentAt;
		public List<Event> events;
		/** Null on batches that carry only events. */
		public Snapshot snapshot;
	}

	public static final class Event
	{
		/** Monotonic within a session. Lets the backend dedupe retries and detect gaps. */
		public long seq;
		public long at;
		public String kind;
		public Map<String, Object> data;

		public Event(long seq, long at, String kind, Map<String, Object> data)
		{
			this.seq = seq;
			this.at = at;
			this.kind = kind;
			this.data = data;
		}
	}

	public static final class Snapshot
	{
		public long capturedAt;
		/** Skill name to total experience. */
		public Map<String, Integer> skills;
		public List<ItemStack> inventory;
		public List<ItemStack> equipment;
		public List<ItemStack> bank;
		public Map<String, String> quests;
		public Map<String, Integer> varbits;
	}

	public static final class ItemStack
	{
		public int id;
		public int qty;
		public int slot;

		public ItemStack(int id, int qty, int slot)
		{
			this.id = id;
			this.qty = qty;
			this.slot = slot;
		}
	}

	public static final class IngestResponse
	{
		public boolean ok;
		/** Highest seq the backend has durably stored; lets the plugin trim its buffer. */
		public long ackSeq;
	}

	public static final class ErrorResponse
	{
		public String error;
		public String message;
	}

	// ------------------------------------------------------------------
	// Event kinds
	// ------------------------------------------------------------------

	public static final class Kind
	{
		public static final String LEVEL_UP = "levelUp";
		public static final String LOOT = "loot";
		public static final String QUEST = "quest";
		public static final String COLLECTION_LOG = "collectionLog";
		public static final String CONTAINER = "container";
		public static final String SESSION_START = "sessionStart";
		public static final String SESSION_END = "sessionEnd";

		private Kind()
		{
		}
	}
}
