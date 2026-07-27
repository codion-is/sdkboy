/*
 * This file is part of SDKBOY.
 *
 * SDKBOY is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SDKBOY is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SDKBOY.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2025 - 2026, Björn Darri Sigurðsson.
 */
package is.codion.sdkboy.ui;

import is.codion.common.reactive.state.State;
import is.codion.common.utilities.exceptions.Exceptions;
import is.codion.plugin.flatlaf.intellij.FlatLookAndFeelIntelliJThemes;
import is.codion.plugin.flatlaf.themes.FlatLookAndFeelThemes;
import is.codion.sdkboy.model.SDKBoyModel;
import is.codion.swing.common.ui.Utilities;
import is.codion.swing.common.ui.ancestor.Ancestor;
import is.codion.swing.common.ui.dialog.Dialogs;
import is.codion.swing.common.ui.frame.Frames;
import is.codion.swing.common.ui.key.KeyEvents;
import is.codion.swing.common.ui.laf.LookAndFeelEnabler;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import static is.codion.sdkboy.model.PreferencesModel.getLookAndFeelPreference;
import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.borderLayoutPanel;
import static is.codion.swing.common.ui.component.Components.textArea;
import static is.codion.swing.common.ui.control.Control.command;
import static is.codion.swing.common.ui.icon.SVGIcon.svgIcon;
import static is.codion.swing.common.ui.laf.LookAndFeelProvider.findLookAndFeel;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static java.awt.BorderLayout.*;
import static java.awt.Desktop.getDesktop;
import static java.awt.event.KeyEvent.*;
import static java.lang.Thread.setDefaultUncaughtExceptionHandler;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.*;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;

// tag::sdkboy_panel[]
public final class SDKBoyPanel extends JPanel {

	private static final String SHORTCUTS = """
					Alt           Mnemonics
					Enter         Navigate
					Up/PageUp     Previous
					Down/PageDown Next
					Escape        Cancel
					Alt-O         Description
					Alt-S         Shortcuts
					Alt-P         Preferences
					Alt-R         Refresh
					Alt-X         Exit
					Alt-I/Ins     Install
					Alt-D/Del     Uninstall
					Alt-U         Use
					Alt-C         Copy USE Command
					Double Click Version
					Uninstalled  :Install
					Installed    :Use
					Used         :Uninstall
					""";

	private final SDKBoyModel model = new SDKBoyModel();
	private final CandidatePanel candidate;
	private final VersionPanel version;
	private final State help = State.builder()
					.consumer(this::onHelp)
					.build();

	private PreferencesPanel preferences;

	private SDKBoyPanel() {
		super(borderLayout());
		setDefaultUncaughtExceptionHandler(new SDKBoyExceptionHandler());
		version = new VersionPanel(model, help);
		candidate = new CandidatePanel(model.candidate(), version.installing(),
						model.version().tableModel().items().refresher().active());
		initializeUI();
		setupKeyEvents();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		Utilities.updateUI(preferences);
	}

	private void initializeUI() {
		setBorder(emptyBorder());
		add(candidate, WEST);
		add(version, CENTER);
	}

	private void setupKeyEvents() {
		KeyEvents.builder()
						.condition(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
						.modifiers(ALT_DOWN_MASK)
						.keyCode(VK_P)
						.action(command(this::preferences))
						.enable(this)
						.keyCode(VK_X)
						.action(command(this::exit))
						.enable(this)
						.keyCode(VK_O)
						.action(candidate.controls().description())
						.enable(this)
						.keyCode(VK_R)
						.action(candidate.controls().refresh())
						.enable(this)
						.keyCode(VK_INSERT)
						.action(version.controls().install())
						.enable(this)
						.keyCode(VK_DELETE)
						.action(version.controls().uninstall())
						.enable(this)
						.keyCode(VK_I)
						.action(version.controls().install())
						.enable(this)
						.keyCode(VK_D)
						.action(version.controls().uninstall())
						.enable(this)
						.keyCode(VK_U)
						.action(version.controls().use())
						.enable(this)
						.keyCode(VK_C)
						.action(version.controls().copyUseCommand())
						.enable(this);
	}

	private void onHelp(boolean visible) {
		if (visible) {
			add(new HelpPanel(), EAST);
		}
		else {
			BorderLayout layout = (BorderLayout) getLayout();
			remove(layout.getLayoutComponent(EAST));
		}
		revalidate();
		repaint();
	}

	private void preferences() {
		if (preferences == null) {
			preferences = new PreferencesPanel(model.preferences());
		}
		Dialogs.okCancel()
						.component(preferences)
						.owner(this)
						.title("Preferences")
						.onOk(model.preferences()::save)
						.onCancel(model.preferences()::revert)
						.show();
	}

	private void exit() {
		if (confirmExit()) {
			Ancestor.window().of(this).dispose();
		}
	}

	private boolean confirmExit() {
		if (version.installing().is()) {
			return false;
		}

		return !model.preferences().confirmExit().is() || showConfirmDialog(this,
						"Are you sure you want to exit?",
						"Confirm Exit", YES_NO_OPTION, QUESTION_MESSAGE) == YES_OPTION;
	}

	private final class SDKBoyExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread thread, Throwable throwable) {
			throwable.printStackTrace();
			Dialogs.exception()
							.owner(SDKBoyPanel.this)
							.show(throwable);
		}
	}

	private static final class HelpPanel extends JPanel {

		private final JTextArea shortcuts = textArea()
						.value(SHORTCUTS)
						.font(HelpPanel::monospaceFont)
						.editable(false)
						.focusable(false)
						.build();
		private final AboutPanel aboutPanel = new AboutPanel();

		private HelpPanel() {
			super(borderLayout());
			add(borderLayoutPanel()
							.center(borderLayoutPanel()
											.border(createTitledBorder("Shortcuts"))
											.center(shortcuts))
							.south(borderLayoutPanel()
											.border(createTitledBorder("About"))
											.center(aboutPanel))
							.build(), CENTER);
		}

		@Override
		public void updateUI() {
			super.updateUI();
			Utilities.updateUI(shortcuts, aboutPanel);
		}

		private static Font monospaceFont(Font font) {
			return new Font(Font.MONOSPACED, font.getStyle(), font.getSize());
		}

		private static final class AboutPanel extends JPanel {

			private final JEditorPane editorPane = new JEditorPane("text/html", """
							<html><table>
							  <tr><td>Copyright:</td><td>Björn Darri</td></tr>
							  <tr><td>License:</td><td><a href="https://www.gnu.org/licenses/gpl-3.0.en.html">GPL</a></td></tr>
							  <tr><td>Source:</td><td><a href="https://github.com/codion-is/sdkboy">SDKBOY</a></td></tr>
							  <tr><td></td><td><a href="https://github.com/sdkman/sdkman-cli">SDKMAN</a></td></tr>
							</table></html>
							""");

			private AboutPanel() {
				super(borderLayout());
				editorPane.setFont(monospaceFont(editorPane.getFont()));
				editorPane.setEditable(false);
				editorPane.setFocusable(false);
				editorPane.addHyperlinkListener(new OpenLink());
				add(editorPane, CENTER);
			}
		}

		private static final class OpenLink implements HyperlinkListener {

			@Override
			public void hyperlinkUpdate(HyperlinkEvent event) {
				if (ACTIVATED.equals(event.getEventType())) {
					try {
						getDesktop().browse(event.getURL().toURI());
					}
					catch (Exception e) {
						throw Exceptions.runtime(e);
					}
				}
			}
		}
	}

	static void main() {
		setDefaultUncaughtExceptionHandler((_, throwable) -> {
			throwable.printStackTrace();
			Dialogs.exception().show(throwable);
		});
		FlatLookAndFeelThemes.addAll();
		FlatLookAndFeelIntelliJThemes.addAll();
		findLookAndFeel(getLookAndFeelPreference())
						.ifPresent(LookAndFeelEnabler::enable);

		SDKBoyPanel sdkBoyPanel = new SDKBoyPanel();

		Frames.builder()
						.component(sdkBoyPanel)
						.title("SDKBOY " + SDKBoyModel.VERSION)
						.icon(svgIcon(SDKBoyPanel.class.getResource("logo.svg"), 68, Color.BLACK))
						.centerFrame(true)
						.defaultCloseOperation(DO_NOTHING_ON_CLOSE)
						.onClosing(_ -> sdkBoyPanel.exit())
						.show();
	}
}
// end::sdkboy_panel[]