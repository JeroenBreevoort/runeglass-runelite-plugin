package com.runeglass.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class RuneGlassPluginTestClient
{
	private RuneGlassPluginTestClient()
	{
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RuneGlassPlugin.class);
		RuneLite.main(args);
	}
}
