/*
 * Copyright (c) 2026, RuneGlass
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package app.runeglass.plugin;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel showing link state and what RuneGlass currently sees.
 * <p>
 * Every public entry point may be called from the client thread, so each one hops to the EDT
 * before touching Swing state.
 */
@Singleton
public class RuneGlassPanel extends PluginPanel
{
	private static final String PRIVACY_URL = "https://runeglass.app/privacy";

	private final RuneGlassConfig config;

	private final JLabel linkValue = new JLabel();
	private final JLabel characterValue = new JLabel();
	private final JLabel syncValue = new JLabel();
	private final JButton actionButton = new JButton();

	@Inject
	RuneGlassPanel(RuneGlassConfig config)
	{
		this.config = config;

		setLayout(new BorderLayout(0, 12));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildStatus(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		refresh();
	}

	private JPanel buildHeader()
	{
		final JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		final JLabel title = new JLabel("RuneGlass");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);

		header.add(title, BorderLayout.NORTH);
		return header;
	}

	private JPanel buildStatus()
	{
		final JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

		rows.add(statusRow("Account", linkValue));
		rows.add(statusRow("Character", characterValue));
		rows.add(statusRow("Sync", syncValue));

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(rows, BorderLayout.NORTH);
		return wrapper;
	}

	private JPanel statusRow(String label, JLabel value)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		final JLabel key = new JLabel(label);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(JLabel.RIGHT);

		row.add(key);
		row.add(value);
		return row;
	}

	private JPanel buildFooter()
	{
		final JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

		actionButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		actionButton.setFocusPainted(false);
		actionButton.addActionListener(e -> onActionClicked());

		final JButton privacy = new JButton("Privacy policy");
		privacy.setAlignmentX(Component.CENTER_ALIGNMENT);
		privacy.setFocusPainted(false);
		privacy.setFont(privacy.getFont().deriveFont(Font.PLAIN, 10f));
		privacy.addActionListener(e -> LinkBrowser.browse(PRIVACY_URL));

		footer.add(actionButton);
		footer.add(javax.swing.Box.createVerticalStrut(6));
		footer.add(privacy);
		return footer;
	}

	/**
	 * Called from the client thread when the logged-in account changes.
	 */
	public void onIdentityChanged(@Nullable AccountIdentity identity)
	{
		SwingUtilities.invokeLater(() -> {
			if (identity == null || !identity.isPresent())
			{
				characterValue.setText("Not logged in");
				characterValue.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
			else
			{
				final String name = identity.getDisplayName();
				characterValue.setText(name != null ? name : "Unknown");
				characterValue.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			}

			updateSyncRow();
		});
	}

	/**
	 * Re-reads config-backed state. Safe to call from any thread.
	 */
	public void refresh()
	{
		SwingUtilities.invokeLater(() -> {
			final boolean linked = !config.deviceToken().isEmpty();

			if (linked)
			{
				final String name = config.linkedAccountName();
				linkValue.setText(name.isEmpty() ? "Linked" : name);
				linkValue.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				actionButton.setText("Unlink");
			}
			else
			{
				linkValue.setText("Not linked");
				linkValue.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				actionButton.setText("Link account");
			}

			characterValue.setText("Not logged in");
			characterValue.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

			updateSyncRow();
		});
	}

	private void updateSyncRow()
	{
		if (!config.syncEnabled())
		{
			syncValue.setText("Off");
			syncValue.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
		else if (config.deviceToken().isEmpty())
		{
			syncValue.setText("Needs linking");
			syncValue.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		}
		else
		{
			syncValue.setText("Idle");
			syncValue.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
	}

	private void onActionClicked()
	{
		// Pairing lands in the next milestone; this keeps the unlink path honest in the meantime.
		if (!config.deviceToken().isEmpty())
		{
			config.deviceToken("");
			config.linkedAccountName("");
			refresh();
		}
	}
}
