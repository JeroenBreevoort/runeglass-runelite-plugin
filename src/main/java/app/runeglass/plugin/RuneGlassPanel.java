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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel showing link state, the pairing code, and what RuneGlass currently sees.
 * <p>
 * Public entry points may be called from the client thread or an OkHttp thread, so each one hops
 * to the EDT before touching Swing state. Everything private assumes it is already on the EDT.
 */
@Singleton
public class RuneGlassPanel extends PluginPanel
{
	private static final String PRIVACY_URL = "https://runeglass.app/privacy";

	private final RuneGlassConfig config;
	private final PairingService pairingService;

	private final JLabel linkValue = new JLabel();
	private final JLabel characterValue = new JLabel();
	private final JLabel syncValue = new JLabel();

	private final JPanel pairingCard = new JPanel();
	private final JLabel pairingCode = new JLabel();
	private final JLabel pairingHint = new JLabel();
	private final JLabel pairingExpiry = new JLabel();

	private final JLabel messageLabel = new JLabel();
	private final JButton primaryButton = new JButton();
	private final JButton cancelButton = new JButton("Cancel");

	/** Ticks the "expires in" countdown while a code is on screen. EDT-only. */
	private final Timer countdown = new Timer(1000, e -> updateCountdown());

	private long expiresAt;

	/** Latest status from the sync loop. EDT-only; null until the first update arrives. */
	private SyncStatus syncStatus;

	@Inject
	RuneGlassPanel(RuneGlassConfig config, PairingService pairingService)
	{
		this.config = config;
		this.pairingService = pairingService;

		setLayout(new BorderLayout(0, 10));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		render(pairingService.getState());
	}

	private JPanel buildHeader()
	{
		final JPanel header = new JPanel(new BorderLayout());

		final JLabel title = new JLabel("RuneGlass");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);

		header.add(title, BorderLayout.NORTH);
		return header;
	}

	private JPanel buildBody()
	{
		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		body.add(statusRow("Account", linkValue));
		body.add(statusRow("Character", characterValue));
		body.add(statusRow("Sync", syncValue));

		body.add(Box.createVerticalStrut(8));
		body.add(buildPairingCard());

		messageLabel.setFont(FontManager.getRunescapeSmallFont());
		messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		messageLabel.setVisible(false);
		body.add(Box.createVerticalStrut(6));
		body.add(messageLabel);

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(body, BorderLayout.NORTH);
		return wrapper;
	}

	private JPanel buildPairingCard()
	{
		pairingCard.setLayout(new BoxLayout(pairingCard, BoxLayout.Y_AXIS));
		pairingCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		pairingCard.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_HOVER_COLOR),
			BorderFactory.createEmptyBorder(10, 8, 10, 8)));
		pairingCard.setVisible(false);

		pairingHint.setText("<html>Enter this code in the RuneGlass app</html>");
		pairingHint.setFont(FontManager.getRunescapeSmallFont());
		pairingHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pairingHint.setAlignmentX(Component.CENTER_ALIGNMENT);

		pairingCode.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
		pairingCode.setForeground(ColorScheme.BRAND_ORANGE);
		pairingCode.setAlignmentX(Component.CENTER_ALIGNMENT);

		pairingExpiry.setFont(FontManager.getRunescapeSmallFont());
		pairingExpiry.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		pairingExpiry.setAlignmentX(Component.CENTER_ALIGNMENT);

		cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		cancelButton.setFocusPainted(false);
		cancelButton.setFont(cancelButton.getFont().deriveFont(Font.PLAIN, 10f));
		cancelButton.addActionListener(e -> pairingService.cancel());

		pairingCard.add(pairingHint);
		pairingCard.add(Box.createVerticalStrut(6));
		pairingCard.add(pairingCode);
		pairingCard.add(Box.createVerticalStrut(4));
		pairingCard.add(pairingExpiry);
		pairingCard.add(Box.createVerticalStrut(8));
		pairingCard.add(cancelButton);

		return pairingCard;
	}

	private JPanel statusRow(String label, JLabel value)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
		row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

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

		primaryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		primaryButton.setFocusPainted(false);
		primaryButton.addActionListener(e -> onPrimaryClicked());

		final JButton privacy = new JButton("Privacy policy");
		privacy.setAlignmentX(Component.CENTER_ALIGNMENT);
		privacy.setFocusPainted(false);
		privacy.setFont(privacy.getFont().deriveFont(Font.PLAIN, 10f));
		privacy.addActionListener(e -> LinkBrowser.browse(PRIVACY_URL));

		footer.add(primaryButton);
		footer.add(Box.createVerticalStrut(6));
		footer.add(privacy);
		return footer;
	}

	// ------------------------------------------------------------------
	// Entry points from other threads
	// ------------------------------------------------------------------

	/** Called from the client thread when the logged-in account changes. */
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
		});
	}

	/** Called from an OkHttp thread as the pairing handshake progresses. */
	public void onPairingStateChanged(PairingState state)
	{
		SwingUtilities.invokeLater(() -> render(state));
	}

	/** Called from the executor thread as the sync loop makes progress. */
	public void onSyncStatusChanged(SyncStatus status)
	{
		SwingUtilities.invokeLater(() -> {
			syncStatus = status;
			updateSyncRow();
		});
	}

	/** Called when settings change, so the sync row reflects the new toggle. */
	public void refresh()
	{
		SwingUtilities.invokeLater(() -> render(pairingService.getState()));
	}

	// ------------------------------------------------------------------
	// EDT-only rendering
	// ------------------------------------------------------------------

	private void render(PairingState state)
	{
		final boolean linked = !config.deviceToken().isEmpty();

		if (linked)
		{
			final String name = config.linkedAccountName();
			linkValue.setText(name.isEmpty() ? "Linked" : name);
			linkValue.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else
		{
			linkValue.setText("Not linked");
			linkValue.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

		updateSyncRow();

		switch (state.getPhase())
		{
			case STARTING:
				hidePairingCard();
				showMessage("Requesting a code…", ColorScheme.LIGHT_GRAY_COLOR);
				primaryButton.setText("Link account");
				primaryButton.setEnabled(false);
				break;

			case WAITING:
				expiresAt = state.getExpiresAt();
				pairingCode.setText(state.getCode());
				pairingCard.setVisible(true);
				updateCountdown();
				if (!countdown.isRunning())
				{
					countdown.start();
				}
				hideMessage();
				primaryButton.setText("Link account");
				primaryButton.setEnabled(false);
				break;

			case LINKED:
				hidePairingCard();
				showMessage("Linked successfully.", ColorScheme.PROGRESS_COMPLETE_COLOR);
				primaryButton.setText("Unlink");
				primaryButton.setEnabled(true);
				break;

			case FAILED:
				hidePairingCard();
				showMessage(state.getMessage(), ColorScheme.PROGRESS_ERROR_COLOR);
				primaryButton.setText(linked ? "Unlink" : "Link account");
				primaryButton.setEnabled(true);
				break;

			case IDLE:
			default:
				hidePairingCard();
				hideMessage();
				primaryButton.setText(linked ? "Unlink" : "Link account");
				primaryButton.setEnabled(true);
				break;
		}

		revalidate();
		repaint();
	}

	private void updateSyncRow()
	{
		final SyncStatus status = syncStatus;

		// Before the first status arrives, derive what we can from settings alone.
		if (status == null)
		{
			if (!config.syncEnabled())
			{
				setSync("Off", ColorScheme.MEDIUM_GRAY_COLOR, null);
			}
			else if (config.deviceToken().isEmpty())
			{
				setSync("Needs linking", ColorScheme.PROGRESS_INPROGRESS_COLOR, null);
			}
			else
			{
				setSync("Starting…", ColorScheme.MEDIUM_GRAY_COLOR, null);
			}
			return;
		}

		switch (status.getPhase())
		{
			case OFF:
				setSync("Off", ColorScheme.MEDIUM_GRAY_COLOR, null);
				break;
			case NOT_LINKED:
				setSync("Needs linking", ColorScheme.PROGRESS_INPROGRESS_COLOR, null);
				break;
			case WAITING_FOR_LOGIN:
				setSync("Waiting for login", ColorScheme.MEDIUM_GRAY_COLOR, null);
				break;
			case PENDING:
				setSync("Sending " + status.getPendingCount(), ColorScheme.PROGRESS_INPROGRESS_COLOR, null);
				break;
			case ERROR:
				setSync("Error", ColorScheme.PROGRESS_ERROR_COLOR, status.getMessage());
				break;
			case IDLE:
			default:
				setSync("Synced", ColorScheme.PROGRESS_COMPLETE_COLOR, lastSyncTooltip(status));
				break;
		}
	}

	private void setSync(String text, java.awt.Color color, @Nullable String tooltip)
	{
		syncValue.setText(text);
		syncValue.setForeground(color);
		syncValue.setToolTipText(tooltip);
	}

	@Nullable
	private static String lastSyncTooltip(SyncStatus status)
	{
		if (status.getLastSuccessAt() <= 0)
		{
			return null;
		}

		final long seconds = (System.currentTimeMillis() - status.getLastSuccessAt()) / 1000L;
		if (seconds < 60)
		{
			return "Last sync " + seconds + "s ago";
		}
		return "Last sync " + (seconds / 60) + "m ago";
	}

	private void updateCountdown()
	{
		final long remaining = expiresAt - System.currentTimeMillis();
		if (remaining <= 0)
		{
			pairingExpiry.setText("Expired");
			countdown.stop();
			return;
		}

		final long seconds = remaining / 1000L;
		pairingExpiry.setText(String.format("Expires in %d:%02d", seconds / 60, seconds % 60));
	}

	private void hidePairingCard()
	{
		countdown.stop();
		pairingCard.setVisible(false);
	}

	private void showMessage(@Nullable String text, java.awt.Color color)
	{
		if (text == null || text.isEmpty())
		{
			hideMessage();
			return;
		}

		// HTML so long server messages wrap inside the narrow panel instead of clipping.
		messageLabel.setText("<html><body style='width:150px'>" + escape(text) + "</body></html>");
		messageLabel.setForeground(color);
		messageLabel.setVisible(true);
	}

	private void hideMessage()
	{
		messageLabel.setVisible(false);
		messageLabel.setText("");
	}

	private void onPrimaryClicked()
	{
		if (!config.deviceToken().isEmpty())
		{
			pairingService.unlink();
		}
		else
		{
			pairingService.start();
		}
	}

	/** Server-supplied text lands in an HTML label, so angle brackets must not become markup. */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
