/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development launcher. Starts RuneLite with RuneGlass loaded as a built-in plugin.
 */
public class RuneGlassPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RuneGlassPlugin.class);
		RuneLite.main(args);
	}
}
