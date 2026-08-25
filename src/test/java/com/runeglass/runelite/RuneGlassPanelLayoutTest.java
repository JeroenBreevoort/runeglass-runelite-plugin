package com.runeglass.runelite;

import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneGlassPanelLayoutTest
{
	@Test
	public void allSyncStatesFitTheStandardRuneLiteSidebarWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RuneGlassPanel panel = new RuneGlassPanel(() -> { }, () -> { }, () -> { });
			panel.setSize(PluginPanel.PANEL_WIDTH, 700);

			List<Runnable> states = new ArrayList<>();
			states.add(panel::showReady);
			states.add(panel::showConnected);
			states.add(() -> panel.showUploading(8));
			states.add(() -> panel.showSynced(Instant.parse("2026-08-24T14:15:00Z")));
			states.add(panel::showRetrying);
			states.add(() -> panel.showSnapshotFailure(
				PreviewSnapshotClient.Failure.BINDING_MISMATCH));

			for (Runnable state : states)
			{
				state.run();
				layoutTree(panel);
				assertReadable(panel);
			}
		});
	}

	private static void assertReadable(RuneGlassPanel panel)
	{
		List<JTextArea> textAreas = descendants(panel, JTextArea.class);
		assertEquals(2, textAreas.size());
		for (JTextArea textArea : textAreas)
		{
			assertTrue("Text must use the sidebar width", textArea.getWidth() >= 190);
			assertTrue(
				"Text must not be vertically clipped",
				textArea.getHeight() >= textArea.getPreferredSize().height);
		}

		for (JButton button : descendants(panel, JButton.class))
		{
			if (!button.isVisible())
			{
				continue;
			}
			FontMetrics metrics = button.getFontMetrics(button.getFont());
			Insets insets = button.getInsets();
			int requiredButtonWidth = metrics.stringWidth(button.getText())
				+ insets.left
				+ insets.right;
			assertTrue("Button label must fit", button.getWidth() >= requiredButtonWidth);
		}

		List<JLabel> labels = descendants(panel, JLabel.class);
		assertFalse(labels.isEmpty());
		JLabel title = labels.stream()
			.filter(label -> "RuneGlass".equals(label.getText()))
			.findFirst()
			.orElseThrow(AssertionError::new);
		assertTrue(
			"Title must fit without ellipsis",
			title.getWidth() >= title.getPreferredSize().width);
	}

	private static void layoutTree(Container container)
	{
		container.doLayout();
		for (Component component : container.getComponents())
		{
			if (component instanceof Container)
			{
				layoutTree((Container) component);
			}
		}
	}

	private static <T extends Component> List<T> descendants(Container root, Class<T> type)
	{
		List<T> matches = new ArrayList<>();
		for (Component component : root.getComponents())
		{
			if (type.isInstance(component))
			{
				matches.add(type.cast(component));
			}
			if (component instanceof Container)
			{
				matches.addAll(descendants((Container) component, type));
			}
		}
		return matches;
	}
}
