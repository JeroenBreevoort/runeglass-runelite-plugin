package com.runeglass.runelite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class SyncBatch
{
	private final int protocolVersion = 1;
	private final String batchId;
	private final String connectionId;
	private final String sessionId;
	private final String sequence;
	private final String capturedAt;
	private final ClientMetadata client;
	private final CharacterMetadata character;
	private final List<SkillsSnapshot> records;

	SyncBatch(
		String batchId,
		String connectionId,
		String sequence,
		Instant capturedAt,
		SyncContext context,
		List<SkillsSnapshot> records)
	{
		this.batchId = Objects.requireNonNull(batchId, "batchId");
		this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
		this.sessionId = Objects.requireNonNull(context, "context").getSessionId();
		this.sequence = Objects.requireNonNull(sequence, "sequence");
		this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt").toString();
		this.client = new ClientMetadata(context);
		this.character = new CharacterMetadata(context);
		if (records.isEmpty() || records.size() > 8)
		{
			throw new IllegalArgumentException("A V1 batch requires one to eight records");
		}
		this.records = Collections.unmodifiableList(new ArrayList<>(records));
	}

	private static final class ClientMetadata
	{
		private final String pluginVersion;
		private final String runeliteVersion;
		private final int gameRevision;

		private ClientMetadata(SyncContext context)
		{
			this.pluginVersion = context.getPluginVersion();
			this.runeliteVersion = context.getRuneLiteVersion();
			this.gameRevision = context.getGameRevision();
		}
	}

	private static final class CharacterMetadata
	{
		private final String displayName;
		private final String accountType;
		private final String profileType;

		private CharacterMetadata(SyncContext context)
		{
			this.displayName = context.getDisplayName();
			this.accountType = context.getAccountType();
			this.profileType = context.getProfileType();
		}
	}
}
