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

import is.codion.common.reactive.value.Value;
import is.codion.common.utilities.exceptions.Exceptions;
import is.codion.sdkboy.model.PreferencesModel;
import is.codion.swing.common.ui.component.value.ComponentValue;
import is.codion.swing.common.ui.control.Control;
import is.codion.swing.common.ui.dialog.Dialogs;
import is.codion.swing.common.ui.laf.LookAndFeelComboBox;
import is.codion.swing.common.ui.laf.LookAndFeelEnabler;

import ch.qos.logback.classic.Level;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;

import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.*;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static java.awt.BorderLayout.CENTER;
import static java.awt.Desktop.getDesktop;
import static javax.swing.UIManager.getIcon;

// tag::preferences_panel[]
final class PreferencesPanel extends JPanel {

	private final PreferencesModel preferences;
	private final LookAndFeelComboBox lookAndFeelComboBox;
	private final ComponentValue<JTextField, String> zipExecutable;
	private final ComponentValue<JTextField, String> unzipExecutable;
	private final ComponentValue<JTextField, String> tarExecutable;
	private final ComponentValue<JCheckBox, Boolean> keepDownloadsAvailable;
	private final ComponentValue<JCheckBox, Boolean> confirmActions;
	private final ComponentValue<JCheckBox, Boolean> confirmExit;
	private final ComponentValue<JComboBox<Level>, Level> logLevel;
	private final JButton browseZipExecutableButton;
	private final JButton browseUnzipExecutableButton;
	private final JButton browseTarExecutableButton;
	private final JButton logFileButton;
	private final JButton logDirectoryButton;

	PreferencesPanel(PreferencesModel preferences) {
		super(borderLayout());
		this.preferences = preferences;
		lookAndFeelComboBox = LookAndFeelComboBox.builder()
						.onSelection(this::setLookAndFeelPreference)
						.build();
		zipExecutable = stringField()
						.link(preferences.zipExecutable())
						.columns(20)
						.selectAllOnFocusGained(true)
						.buildValue();
		unzipExecutable = stringField()
						.link(preferences.unzipExecutable())
						.columns(20)
						.selectAllOnFocusGained(true)
						.buildValue();
		tarExecutable = stringField()
						.link(preferences.tarExecutable())
						.columns(20)
						.selectAllOnFocusGained(true)
						.buildValue();
		Icon directoryIcon = getIcon("FileView.directoryIcon");
		browseZipExecutableButton = button()
						.control(Control.builder()
										.command(() -> browseExecutable(zipExecutable))
										.smallIcon(directoryIcon))
						.build();
		browseUnzipExecutableButton = button()
						.control(Control.builder()
										.command(() -> browseExecutable(unzipExecutable))
										.smallIcon(directoryIcon))
						.build();
		browseTarExecutableButton = button()
						.control(Control.builder()
										.command(() -> browseExecutable(tarExecutable))
										.smallIcon(directoryIcon))
						.build();
		logFileButton = button()
						.control(Control.builder()
										.command(this::openLogFile)
										.smallIcon(getIcon("FileView.fileIcon"))
										.mnemonic('F')
										.description("Open Log File (Alt-F)"))
						.build();
		logDirectoryButton = button()
						.control(Control.builder()
										.command(this::openLogDirectory)
										.smallIcon(directoryIcon)
										.mnemonic('D')
										.description("Open Log Directory (Alt-D)"))
						.build();
		keepDownloadsAvailable = checkBox()
						.link(preferences.keepDownloadsAvailable())
						.text("Keep downloads available")
						.mnemonic('K')
						.buildValue();
		confirmActions = checkBox()
						.link(preferences.confirmActions())
						.text("Confirm install, uninstall and use")
						.mnemonic('I')
						.buildValue();
		confirmExit = checkBox()
						.link(preferences.confirmExit())
						.text("Confirm exit")
						.mnemonic('X')
						.buildValue();
		logLevel = comboBox()
						.model(preferences.logLevels())
						.value(preferences.logLevel())
						.buildValue();
		setBorder(emptyBorder());
		add(flexibleGridLayoutPanel(0, 1)
						.add(label("Look & Feel")
										.displayedMnemonic('L')
										.labelFor(lookAndFeelComboBox))
						.add(lookAndFeelComboBox)
						.add(label("Select zip path")
										.displayedMnemonic('Z')
										.labelFor(zipExecutable.component()))
						.add(borderLayoutPanel()
										.layout(new BorderLayout(0, 5))
										.center(zipExecutable.component())
										.east(browseZipExecutableButton))
						.add(label("Select unzip path")
										.displayedMnemonic('U')
										.labelFor(unzipExecutable.component()))
						.add(borderLayoutPanel()
										.layout(new BorderLayout(0, 5))
										.center(unzipExecutable.component())
										.east(browseUnzipExecutableButton))
						.add(label("Select tar path")
										.displayedMnemonic('T')
										.labelFor(tarExecutable.component()))
						.add(borderLayoutPanel()
										.layout(new BorderLayout(0, 5))
										.center(tarExecutable.component())
										.east(browseTarExecutableButton))
						.add(label("Log level")
										.displayedMnemonic('V')
										.labelFor(logLevel.component()))
						.add(borderLayoutPanel()
										.layout(new BorderLayout(0, 5))
										.center(logLevel.component())
										.east(panel()
														.layout(new GridLayout(1, 0, 0, 5))
														.add(logFileButton)
														.add(logDirectoryButton)))
						.add(keepDownloadsAvailable.component())
						.add(confirmActions.component())
						.add(confirmExit.component())
						.build(), CENTER);
	}

	private void setLookAndFeelPreference(LookAndFeelEnabler lookAndFeelEnabler) {
		preferences.setLookAndFeelPreference(lookAndFeelEnabler.lookAndFeel().getClass().getName());
	}

	private void openLogFile() {
		preferences.logFile().ifPresent(this::open);
	}

	private void openLogDirectory() {
		preferences.logDirectory().ifPresent(this::open);
	}

	private void open(File file) {
		try {
			getDesktop().open(file);
		}
		catch (IOException e) {
			throw Exceptions.runtime(e);
		}
	}

	private void browseExecutable(Value<String> executable) {
		executable.set(Dialogs.select()
						.files()
						.owner(this)
						.title("Select executable")
						.selectFile()
						.toPath()
						.toString());
	}
}
// end::preferences_panel[]