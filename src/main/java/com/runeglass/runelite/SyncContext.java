package com.runeglass.runelite;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLiteProperties;

final class SyncContext
{
	static final String PLUGIN_VERSION = "1.0.0";

	private static final Pattern DISPLAY_NAME = Pattern.compile("^[A-Za-z0-9 _-]{1,12}$");

	private final String sessionId;
	private final String displayName;
	private final String accountType;
	private final String profileType;
	private final String pluginVersion;
	private final String runeLiteVersion;
	private final int gameRevision;

	static SyncContext capture(Client client, UUID sessionId)
	{
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(sessionId, "sessionId");
		Player localPlayer = Objects.requireNonNull(client.getLocalPlayer(), "local player");
		String name = Objects.requireNonNull(localPlayer.getName(), "display name")
			.replace('\u00a0', ' ');
		if (!DISPLAY_NAME.matcher(name).matches())
		{
			throw new IllegalStateException("Unsupported display name");
		}

		return new SyncContext(
			sessionId.toString(),
			name,
			accountType(client.getVarbitValue(VarbitID.IRONMAN)),
			profileType(client.getWorldType()),
			PLUGIN_VERSION,
			RuneLiteProperties.getVersion(),
			client.getRevision());
	}

	SyncContext(
		String sessionId,
		String displayName,
		String accountType,
		String profileType,
		String pluginVersion,
		String runeLiteVersion,
		int gameRevision)
	{
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.accountType = Objects.requireNonNull(accountType, "accountType");
		this.profileType = Objects.requireNonNull(profileType, "profileType");
		this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
		this.runeLiteVersion = Objects.requireNonNull(runeLiteVersion, "runeLiteVersion");
		this.gameRevision = gameRevision;
	}

	String getSessionId()
	{
		return sessionId;
	}

	String getDisplayName()
	{
		return displayName;
	}

	String getAccountType()
	{
		return accountType;
	}

	String getProfileType()
	{
		return profileType;
	}

	String getPluginVersion()
	{
		return pluginVersion;
	}

	String getRuneLiteVersion()
	{
		return runeLiteVersion;
	}

	int getGameRevision()
	{
		return gameRevision;
	}

	static String accountType(int accountType)
	{
		switch (accountType)
		{
			case 0:
				return "regular";
			case 1:
			case 4:
				return "ironman";
			case 2:
				return "ultimate";
			case 3:
			case 5:
				return "hardcore";
			default:
				throw new IllegalStateException("Unsupported account type");
		}
	}

	static String profileType(EnumSet<WorldType> worldTypes)
	{
		if (worldTypes != null && worldTypes.contains(WorldType.DEADMAN))
		{
			return "deadman";
		}
		if (worldTypes != null && worldTypes.contains(WorldType.SEASONAL))
		{
			return "league";
		}
		return "standard";
	}
}
