package com.runeglass.runelite;

import java.util.LinkedHashMap;
import java.util.Map;

final class SemanticSnapshotPayload
{
	private SemanticSnapshotPayload()
	{
	}

	static Map<String, Object> create(
		SyncContext context,
		String connectionId,
		String snapshotId,
		String observedAt,
		String domainKey,
		Object domainValue)
	{
		Map<String, Object> client = new LinkedHashMap<>();
		client.put("pluginVersion", context.getPluginVersion());
		client.put("runeliteVersion", context.getRuneLiteVersion());
		client.put("gameRevision", context.getGameRevision());

		Map<String, Object> character = new LinkedHashMap<>();
		character.put("displayName", context.getDisplayName());
		character.put("accountType", context.getAccountType());
		character.put("profileType", context.getProfileType());

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("protocolVersion", 1);
		payload.put("snapshotId", snapshotId);
		payload.put("connectionId", connectionId);
		payload.put("sessionId", context.getSessionId());
		payload.put("observedAt", observedAt);
		payload.put("client", client);
		payload.put("character", character);
		payload.put(domainKey, domainValue);
		return payload;
	}
}
