package com.runeglass.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

final class RuneGlassPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;
	private static final Color GOLD = new Color(212, 175, 55);
	private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
		.ofPattern("HH:mm")
		.withZone(ZoneId.systemDefault());

	private final transient Runnable startPairing;
	private final transient Runnable stopPairing;
	private final transient Runnable manualSync;
	private final JLabel statusLabel = new JLabel();
	private final JLabel codeLabel = new JLabel("—", SwingConstants.CENTER);
	private final JTextArea detailText = textArea("", 5);
	private final JButton actionButton = new JButton();
	private final JButton manualButton = new JButton("Sync now");

	private boolean stopAction;

	RuneGlassPanel(Runnable startPairing, Runnable stopPairing, Runnable manualSync)
	{
		this.startPairing = Objects.requireNonNull(startPairing, "startPairing");
		this.stopPairing = Objects.requireNonNull(stopPairing, "stopPairing");
		this.manualSync = Objects.requireNonNull(manualSync, "manualSync");
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(4, 2, 12, 2));

		JLabel title = new JLabel("RuneGlass");
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getDefaultBoldFont().deriveFont(18f));
		content.add(title);
		content.add(Box.createRigidArea(new Dimension(0, 6)));

		JTextArea intro = textArea(
			"Connect this RuneLite profile to a RuneGlass character. "
				+ "Your RuneScape login is never shared.",
			4);
		content.add(intro);
		content.add(Box.createRigidArea(new Dimension(0, 14)));

		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		statusLabel.setForeground(GOLD);
		statusLabel.setFont(FontManager.getDefaultBoldFont());
		content.add(statusLabel);
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		codeLabel.setAlignmentX(LEFT_ALIGNMENT);
		codeLabel.setMinimumSize(new Dimension(0, 50));
		codeLabel.setPreferredSize(new Dimension(0, 50));
		codeLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		codeLabel.setOpaque(true);
		codeLabel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		codeLabel.setForeground(Color.WHITE);
		codeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
		codeLabel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		content.add(codeLabel);
		content.add(Box.createRigidArea(new Dimension(0, 10)));

		detailText.setAlignmentX(LEFT_ALIGNMENT);
		content.add(detailText);
		content.add(Box.createRigidArea(new Dimension(0, 14)));

		manualButton.setAlignmentX(LEFT_ALIGNMENT);
		manualButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		manualButton.setFont(FontManager.getDefaultBoldFont());
		manualButton.setFocusPainted(false);
		manualButton.addActionListener(event -> this.manualSync.run());
		content.add(manualButton);
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		actionButton.setAlignmentX(LEFT_ALIGNMENT);
		actionButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		actionButton.setFont(FontManager.getDefaultBoldFont());
		actionButton.setFocusPainted(false);
		actionButton.addActionListener(event ->
		{
			if (stopAction)
			{
				stopPairing.run();
			}
			else
			{
				startPairing.run();
			}
		});
		content.add(actionButton);
		add(content, BorderLayout.NORTH);
		showUnavailable();
	}

	void showUnavailable()
	{
		onEdt(() -> update(
			"Development preview unavailable",
			"—",
			"Restart this development client with the preview environment enabled. No request is sent while it is unavailable.",
			"Generate code",
			false,
			false,
			false,
			false));
	}

	void showSyncDisabled()
	{
		onEdt(() -> update(
			"Sync is off",
			"—",
			"Enable RuneGlass sync in the plugin configuration before pairing.",
			"Generate code",
			false,
			false,
			false,
			false));
	}

	void showLoggedOut()
	{
		onEdt(() -> update(
			"Log in first",
			"—",
			"Pairing is available only for the profile currently logged in to RuneLite.",
			"Generate code",
			false,
			false,
			false,
			false));
	}

	void showReady()
	{
		onEdt(() -> update(
			"Ready to pair",
			"—",
			"Generate a temporary code, then approve it in Settings → RuneLite Sync.",
			"Generate code",
			true,
			false,
			false,
			false));
	}

	void showStarting()
	{
		onEdt(() -> update(
			"Creating a secure code…",
			"—",
			"Opening a short-lived pairing request. No RuneScape login details are sent.",
			"Cancel",
			true,
			true,
			false,
			false));
	}

	void showCode(String userCode, Instant expiresAt)
	{
		Objects.requireNonNull(userCode, "userCode");
		Objects.requireNonNull(expiresAt, "expiresAt");
		onEdt(() -> update(
			"Approve this code in RuneGlass",
			userCode,
			"Open Settings → RuneLite Sync in the app. This code expires at "
				+ EXPIRY_FORMAT.format(expiresAt)
				+ ".",
			"Cancel",
			true,
			true,
			false,
			false));
	}

	void showConnected()
	{
			onEdt(() -> update(
				"Connected",
				"CONNECTED",
				"Waiting for a complete skills snapshot. Forget locally clears only this client; revoke access in RuneGlass Settings.",
				"Forget locally",
			true,
			true,
			true,
			true));
	}

	void showUploading(int recordCount)
	{
		String detail = recordCount == 1
			? "Sending one complete skills and XP snapshot to RuneGlass."
			: "Sending " + recordCount + " complete skills and XP snapshots to RuneGlass.";
		onEdt(() -> update(
			"Connected · syncing",
			"SYNCING",
			detail,
				"Forget locally",
			true,
			true,
			true,
			false));
	}

	void showSynced(Instant serverTime)
	{
		Objects.requireNonNull(serverTime, "serverTime");
		onEdt(() -> update(
			"Connected · up to date",
			"SYNCED",
				"Skills and XP synced at " + EXPIRY_FORMAT.format(serverTime)
					+ ". The credential is memory-only; revoke access in RuneGlass Settings.",
				"Forget locally",
			true,
			true,
			true,
			true));
	}

	void showRetrying()
	{
		onEdt(() -> update(
			"Connected · waiting to retry",
			"WAITING",
			"RuneGlass is temporarily unavailable. The snapshot is retained in memory and will retry automatically.",
				"Forget locally",
			true,
			true,
			true,
			false));
	}

	void showSnapshotFailure(PreviewSnapshotClient.Failure failure)
	{
		Objects.requireNonNull(failure, "failure");
		String detail;
		switch (failure)
		{
			case CONFIGURATION_MISSING:
				detail = "The development upload preview is not configured. No snapshot was sent.";
				break;
			case INVALID_CONNECTION:
				detail = "This connection is no longer valid. Disconnect it in RuneGlass and pair again.";
				break;
			case BINDING_MISMATCH:
				detail = "The logged-in profile does not match the approved character. Sync stopped safely.";
				break;
			case UNSUPPORTED_PROFILE:
				detail = "This preview currently supports standard-profile worlds only.";
				break;
			case REJECTED_BATCH:
				detail = "RuneGlass rejected this snapshot without replacing stored progress. Pair again before retrying.";
				break;
			default:
				detail = "RuneGlass returned an unexpected response. Sync stopped safely.";
				break;
		}
		String finalDetail = detail;
		onEdt(() -> update(
			"Sync stopped",
			"NOT SYNCED",
			finalDetail,
			"Generate code",
			true,
			false,
			false,
			false));
	}

	void showFailure(PreviewPairingClient.Failure failure)
	{
		Objects.requireNonNull(failure, "failure");
		String detail;
		switch (failure)
		{
			case CONFIGURATION_MISSING:
				detail = "The local development preview is not configured. No request was sent.";
				break;
			case AUTHORIZATION_DENIED:
				detail = "RuneGlass denied this pairing request. Generate a new code to try again.";
				break;
			case EXPIRED:
				detail = "The pairing code expired. Generate a new code to try again.";
				break;
			case PROTOCOL_ERROR:
				detail = "RuneGlass returned an unexpected preview response. Pairing stopped safely.";
				break;
			default:
				detail = "RuneGlass could not complete pairing. Check the development backend and try again.";
				break;
		}
		String finalDetail = detail;
		onEdt(() -> update(
			"Pairing stopped",
			"—",
			finalDetail,
			"Generate code",
			true,
			false,
			false,
			false));
	}

	private void update(
		String status,
		String code,
		String detail,
		String action,
		boolean enabled,
		boolean actionStops,
		boolean manualVisible,
		boolean manualEnabled)
	{
		statusLabel.setText(status);
		codeLabel.setText(code);
		detailText.setText(detail);
		actionButton.setText(action);
		actionButton.setEnabled(enabled);
		manualButton.setVisible(manualVisible);
		manualButton.setEnabled(manualEnabled);
		stopAction = actionStops;
		revalidate();
		repaint();
	}

	private static JTextArea textArea(String text, int rows)
	{
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		area.setFont(FontManager.getDefaultFont());
		area.setBorder(null);
		area.setRows(rows);
		area.setColumns(1);
		area.setAlignmentX(LEFT_ALIGNMENT);
		int preferredHeight = area.getPreferredSize().height;
		area.setMinimumSize(new Dimension(0, preferredHeight));
		area.setPreferredSize(new Dimension(0, preferredHeight));
		area.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeight));
		return area;
	}

	private static void onEdt(Runnable update)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}
}
