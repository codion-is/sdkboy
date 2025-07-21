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
 * Copyright (c) 2025, Björn Darri Sigurðsson.
 */
package is.codion.sdkboy.ui;

import is.codion.common.event.Event;
import is.codion.common.logging.LoggerProxy;
import is.codion.common.model.preferences.UserPreferences;
import is.codion.common.model.selection.MultiSelection.Indexes;
import is.codion.common.state.ObservableState;
import is.codion.common.state.State;
import is.codion.common.value.Value;
import is.codion.plugin.flatlaf.intellij.themes.darkflat.DarkFlat;
import is.codion.sdkboy.model.SDKBoyModel;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateColumn;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateRow;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel.VersionColumn;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel.VersionRow;
import is.codion.swing.common.model.component.combobox.FilterComboBoxModel;
import is.codion.swing.common.model.worker.ProgressWorker;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressReporter;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressTask;
import is.codion.swing.common.ui.Utilities;
import is.codion.swing.common.ui.component.Components;
import is.codion.swing.common.ui.component.table.FilterTable;
import is.codion.swing.common.ui.component.table.FilterTableCellRenderer;
import is.codion.swing.common.ui.component.table.FilterTableColumn;
import is.codion.swing.common.ui.component.value.ComponentValue;
import is.codion.swing.common.ui.control.Control;
import is.codion.swing.common.ui.dialog.Dialogs;
import is.codion.swing.common.ui.frame.Frames;
import is.codion.swing.common.ui.icon.Logos;
import is.codion.swing.common.ui.key.KeyEvents;
import is.codion.swing.common.ui.laf.LookAndFeelComboBox;
import is.codion.swing.common.ui.laf.LookAndFeelEnabler;

import ch.qos.logback.classic.Level;
import io.github.jagodevreede.sdkman.api.SdkManUiPreferences;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static is.codion.common.state.State.and;
import static is.codion.swing.common.ui.Utilities.parentWindow;
import static is.codion.swing.common.ui.Utilities.setClipboard;
import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.*;
import static is.codion.swing.common.ui.control.Control.command;
import static is.codion.swing.common.ui.laf.LookAndFeelProvider.findLookAndFeel;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static is.codion.swing.common.ui.layout.Layouts.flexibleGridLayout;
import static java.awt.BorderLayout.*;
import static java.awt.Desktop.getDesktop;
import static java.awt.event.KeyEvent.*;
import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Thread.setDefaultUncaughtExceptionHandler;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.*;
import static javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static javax.swing.SwingConstants.CENTER;
import static javax.swing.UIManager.getIcon;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;

// tag::sdkboy_panel[]
public final class SDKBoyPanel extends JPanel {

	private static final String SHORTCUTS = """
					Alt          Mnemonics
					Enter        Navigate
					Up           Previous
					Down         Next
					Escape       Cancel
					Alt-S        Shortcuts
					Alt-P        Preferences
					Alt-R        Refresh
					Alt-X        Exit
					Alt-I/Ins    Install
					Alt-D/Del    Uninstall
					Alt-U        Use
					Alt-C        Copy USE Command
					             To Clipboard
					Double Click Version
					Uninstalled :Install
					Installed   :Use
					Used        :Uninstall
					""";

	private static final String LOOK_AND_FEEL_KEY =
					SDKBoyPanel.class.getName() + ".lookAndFeel";
	private static final String CONFIRM_ACTIONS_KEY =
					SDKBoyPanel.class.getName() + ".confirmActions";
	private static final String CONFIRM_EXIT_KEY =
					SDKBoyPanel.class.getName() + ".confirmExit";

	private final CandidatePanel candidatePanel;
	private final VersionPanel versionPanel;
	private final State help = State.builder()
					.consumer(this::onHelpChanged)
					.build();

	private PreferencesPanel preferencesPanel;

	private SDKBoyPanel() {
		super(borderLayout());
		setDefaultUncaughtExceptionHandler(new SDKBoyExceptionHandler());
		SDKBoyModel model = new SDKBoyModel();
		versionPanel = new VersionPanel(model, help);
		candidatePanel = new CandidatePanel(model, versionPanel.installTask.active);
		initializeUI();
		setupKeyEvents();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		Utilities.updateUI(preferencesPanel);
	}

	private void initializeUI() {
		setBorder(emptyBorder());
		add(candidatePanel, WEST);
		add(versionPanel, CENTER);
	}

	private void setupKeyEvents() {
		KeyEvents.Builder keyEvent = KeyEvents.builder()
						.condition(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
						.modifiers(ALT_DOWN_MASK);
		keyEvent.keyCode(VK_P)
						.action(command(this::displayPreferences))
						.enable(this);
		keyEvent.keyCode(VK_R)
						.action(command(versionPanel::refreshCandidates))
						.enable(this);
		keyEvent.keyCode(VK_X)
						.action(command(this::exit))
						.enable(this);
		keyEvent.keyCode(VK_INSERT)
						.action(versionPanel.installControl)
						.enable(this);
		keyEvent.keyCode(VK_DELETE)
						.action(versionPanel.uninstallControl)
						.enable(this);
		keyEvent.keyCode(VK_I)
						.action(versionPanel.installControl)
						.enable(this);
		keyEvent.keyCode(VK_D)
						.action(versionPanel.uninstallControl)
						.enable(this);
		keyEvent.keyCode(VK_U)
						.action(versionPanel.useControl)
						.enable(this);
		keyEvent.keyCode(VK_C)
						.modifiers(ALT_DOWN_MASK)
						.action(versionPanel.copyUseCommandControl)
						.enable(this);
	}

	private void onHelpChanged(boolean visible) {
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

	private void displayPreferences() {
		if (preferencesPanel == null) {
			preferencesPanel = new PreferencesPanel(versionPanel.preferences);
		}
		Dialogs.okCancel()
						.component(preferencesPanel)
						.owner(this)
						.title("Preferences")
						.onOk(preferencesPanel::save)
						.onCancel(preferencesPanel::cancel)
						.show();
	}

	private void exit() {
		if (confirmExit()) {
			parentWindow(this).dispose();
		}
	}

	private boolean confirmExit() {
		if (versionPanel.installTask.active.get()) {
			return false;
		}

		return !versionPanel.preferences.confirmExit.get() || showConfirmDialog(this,
						"Are you sure you want to exit?",
						"Confirm Exit", YES_NO_OPTION, QUESTION_MESSAGE) == YES_OPTION;
	}

	private static void setConfirmActionsPreference(boolean confirmActions) {
		UserPreferences.set(CONFIRM_ACTIONS_KEY, Boolean.toString(confirmActions));
	}

	private static boolean getConfirmActionsPreference() {
		return parseBoolean(UserPreferences.get(CONFIRM_ACTIONS_KEY, TRUE.toString()));
	}

	private static void setConfirmExitPreference(boolean confirmExit) {
		UserPreferences.set(CONFIRM_EXIT_KEY, Boolean.toString(confirmExit));
	}

	private static boolean getConfirmExitPreference() {
		return parseBoolean(UserPreferences.get(CONFIRM_EXIT_KEY, TRUE.toString()));
	}

	private static void setLookAndFeelPreference(LookAndFeelEnabler lookAndFeelEnabler) {
		UserPreferences.set(LOOK_AND_FEEL_KEY, lookAndFeelEnabler.lookAndFeel().getClass().getName());
	}

	private static String getLookAndFeelPreference() {
		return UserPreferences.get(LOOK_AND_FEEL_KEY, DarkFlat.class.getName());
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

	private static final class CandidatePanel extends JPanel {

		private final FilterTable<CandidateRow, CandidateColumn> table;
		private final JTextField filter;
		private final JCheckBox installedOnly;

		private CandidatePanel(SDKBoyModel model, ObservableState installing) {
			super(borderLayout());
			CandidateModel candidateModel = model.candidateModel();
			ObservableState refreshingVersions = model.versionModel()
							.tableModel().items().refresher().active();
			table = FilterTable.builder()
							.model(candidateModel.tableModel())
							.columns(createColumns())
							.sortable(false)
							.focusable(false)
							.selectionMode(SINGLE_SELECTION)
							.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
							.enabled(and(installing.not(), refreshingVersions.not()))
							.cellRenderer(CandidateColumn.INSTALLED,
											FilterTableCellRenderer.builder()
															.columnClass(Integer.class)
															.horizontalAlignment(CENTER)
															.build())
							.build();
			Indexes selectedIndexes = candidateModel.tableModel().selection().indexes();
			filter = stringField()
							.link(candidateModel.filter())
							.hint("Filter...")
							.lowerCase(true)
							.selectAllOnFocusGained(true)
							.transferFocusOnEnter(true)
							.keyEvent(KeyEvents.builder()
											.keyCode(VK_UP)
											.action(Control.builder()
															.command(selectedIndexes::decrement)
															.enabled(installing.not())
															.build()))
							.keyEvent(KeyEvents.builder()
											.keyCode(VK_DOWN)
											.action(Control.builder()
															.command(selectedIndexes::increment)
															.enabled(installing.not())
															.build()))
							.enabled(installing.not())
							.build();
			installedOnly = checkBox()
							.link(candidateModel.installedOnly())
							.text("Installed")
							.mnemonic('T')
							.focusable(false)
							.enabled(installing.not())
							.build();
			candidateModel.tableModel().items().refresh();
			setBorder(createCompoundBorder(createTitledBorder("Candidates"), emptyBorder()));
			add(scrollPane()
							.view(table)
							.preferredWidth(220)
							.build(), BorderLayout.CENTER);
			add(borderLayoutPanel()
							.centerComponent(filter)
							.eastComponent(installedOnly)
							.build(), SOUTH);
		}

		private List<FilterTableColumn<CandidateColumn>> createColumns() {
			return List.of(
							FilterTableColumn.builder()
											.identifier(CandidateColumn.NAME)
											.headerValue("Name")
											.build(),
							FilterTableColumn.builder()
											.identifier(CandidateColumn.INSTALLED)
											.headerValue("Installed")
											.fixedWidth(80)
											.build());
		}
	}

	private static final class VersionPanel extends JPanel {

		private static final String JAVA = "Java";

		private final SDKBoyModel model;
		private final CandidateModel candidateModel;
		private final VersionModel versionModel;
		private final PreferencesModel preferences = new PreferencesModel();
		private final State help;
		private final InstallTask installTask;
		private final FilterTable<VersionRow, VersionColumn> table;
		private final Value<String> selectedVersionName = Value.nullable();
		private final JTextField filter;
		private final JCheckBox installedOnly;
		private final JCheckBox downloadedOnly;
		private final JCheckBox usedOnly;
		private final JProgressBar refreshProgress;
		private final JProgressBar installProgress;
		private final JButton cancelDownload;
		private final JPanel installingPanel;
		private final JPanel southPanel;
		private final Control installControl;
		private final Control uninstallControl;
		private final Control useControl;
		private final Control copyUseCommandControl;
		private final JButton helpButton;

		private VersionPanel(SDKBoyModel model, State help) {
			super(borderLayout());
			this.model = model;
			this.candidateModel = model.candidateModel();
			this.versionModel = model.versionModel();
			this.help = help;
			this.installTask = new InstallTask();
			this.installControl = Control.builder()
							.command(this::install)
							.enabled(and(
											versionModel.tableModel().selection().empty().not(),
											versionModel.selectedInstalled().not()))
							.build();
			this.uninstallControl = Control.builder()
							.command(this::uninstall)
							.enabled(and(
											versionModel.tableModel().selection().empty().not(),
											versionModel.selectedInstalled()))
							.build();
			this.useControl = Control.builder()
							.command(this::use)
							.enabled(versionModel.selectedUsed().not())
							.build();
			this.copyUseCommandControl = Control.builder()
							.command(this::copyUseCommand)
							.build();
			candidateModel.tableModel().selection().item().addConsumer(this::onCandidateChanged);
			versionModel.tableModel().items().refresher().active().addConsumer(this::onRefreshingChanged);
			versionModel.tableModel().selection().item().addConsumer(this::onVersionChanged);
			installTask.active.addConsumer(this::onInstallActiveChanged);
			installTask.downloading.addConsumer(this::onDownloadingChanged);
			table = FilterTable.builder()
							.model(versionModel.tableModel())
							.columns(createColumns())
							.sortable(false)
							.focusable(false)
							.selectionMode(SINGLE_SELECTION)
							.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
							.columnReorderingAllowed(false)
							.doubleClick(command(this::onVersionDoubleClick))
							.enabled(installTask.active.not())
							.build();
			table.columnModel().visible(VersionColumn.VENDOR).set(false);
			Indexes selectedIndexes = versionModel.tableModel().selection().indexes();
			filter = stringField()
							.link(versionModel.filter())
							.hint("Filter...")
							.lowerCase(true)
							.selectAllOnFocusGained(true)
							.transferFocusOnEnter(true)
							.keyEvent(KeyEvents.builder()
											.keyCode(VK_UP)
											.action(command(selectedIndexes::decrement)))
							.keyEvent(KeyEvents.builder()
											.keyCode(VK_DOWN)
											.action(command(selectedIndexes::increment)))
							.enabled(installTask.active.not())
							.build();
			installedOnly = checkBox()
							.link(versionModel.installedOnly())
							.text("Installed")
							.mnemonic('N')
							.focusable(false)
							.enabled(installTask.active.not())
							.build();
			downloadedOnly = checkBox()
							.link(versionModel.downloadedOnly())
							.text("Downloaded")
							.mnemonic('A')
							.focusable(false)
							.enabled(installTask.active.not())
							.build();
			usedOnly = checkBox()
							.link(versionModel.usedOnly())
							.text("Used")
							.mnemonic('E')
							.focusable(false)
							.enabled(installTask.active.not())
							.build();
			cancelDownload = button()
							.control(Control.builder()
											.command(installTask::cancel)
											.caption("Cancel")
											.enabled(installTask.downloading))
							.keyEvent(KeyEvents.builder()
											.keyCode(VK_ESCAPE)
											.action(command(installTask::cancel)))
							.build();
			refreshProgress = progressBar()
							.string("Refreshing...")
							.stringPainted(true)
							.build();
			installProgress = progressBar()
							.stringPainted(true)
							.build();
			installingPanel = borderLayoutPanel()
							.centerComponent(installProgress)
							.eastComponent(cancelDownload)
							.build();
			helpButton = button()
							.control(Control.builder()
											.command(this::toggleHelp)
											.caption("?")
											.mnemonic('S'))
							.focusable(false)
							.build();
			southPanel = borderLayoutPanel()
							.centerComponent(borderLayoutPanel()
											.centerComponent(filter)
											.eastComponent(flexibleGridLayoutPanel(1, 0)
															.add(installedOnly)
															.add(downloadedOnly)
															.add(usedOnly)
															.add(helpButton)
															.build())
											.build())
							.build();
			setBorder(createCompoundBorder(createTitledBorder("Versions"), emptyBorder()));
			add(scrollPane()
							.view(table)
							.build(), BorderLayout.CENTER);
			add(southPanel, SOUTH);
		}

		@Override
		public void updateUI() {
			super.updateUI();
			Utilities.updateUI(southPanel, refreshProgress, installingPanel, installProgress, cancelDownload);
		}

		private void onVersionDoubleClick() {
			if (versionModel.selectedUsed().get()) {
				uninstall();
			}
			else if (versionModel.selectedInstalled().get()) {
				use();
			}
			else {
				install();
			}
		}

		private void toggleHelp() {
			help.set(!help.get());
		}

		private void install() {
			install(null);
		}

		private void install(Runnable onResult) {
			if (confirmInstall()) {
				ProgressWorker.builder()
								.task(installTask)
								.onStarted(installTask::started)
								.onProgress(installTask::progress)
								.onPublish(installTask::publish)
								.onDone(installTask::done)
								.onResult(() -> {
									model.refresh();
									if (onResult != null) {
										onResult.run();
									}
								})
								.execute();
			}
		}

		private void uninstall() {
			if (confirmUninstall()) {
				ProgressWorker.builder()
								.task(versionModel::uninstall)
								.onResult(model::refresh)
								.execute();
			}
		}

		private void use() {
			VersionRow selected = versionModel.selected();
			if (selected.version().installed()) {
				useInstalled();
			}
			else {
				install(this::useInstalled);
			}
		}

		private void useInstalled() {
			if (confirmUse()) {
				ProgressWorker.builder()
								.task(versionModel::use)
								.onResult(versionModel::refresh)
								.execute();
			}
		}

		private void copyUseCommand() {
			VersionRow selected = versionModel.selected();
			if (!selected.version().installed()) {
				install(() -> copyUseCommand(selected));
			}
			else {
				copyUseCommand(selected);
			}
		}

		private void copyUseCommand(VersionRow versionRow) {
			String command = "sdk use " + versionRow.candidate().id() + " " + versionRow.version().identifier();
			setClipboard(command);
			showMessageDialog(this, command + "\n\ncopied to clipboard", "Copied", INFORMATION_MESSAGE);
		}

		private boolean confirmInstall() {
			return !preferences.confirmActions.get() || showConfirmDialog(this,
							"Install " + versionName() + "?",
							"Confirm install", YES_NO_OPTION) == YES_OPTION;
		}

		private boolean confirmUninstall() {
			return !preferences.confirmActions.get() || showConfirmDialog(this,
							"Uninstall " + versionName() + "?",
							"Confirm uninstall", YES_NO_OPTION) == YES_OPTION;
		}

		private boolean confirmUse() {
			return !preferences.confirmActions.get() || showConfirmDialog(this,
							"Set " + versionName() + " as your global SDK?",
							"Confirm use", YES_NO_OPTION) == YES_OPTION;
		}

		private String versionName() {
			return selectedVersionName.get();
		}

		private void onCandidateChanged(CandidateRow candidateRow) {
			table.columnModel().visible(VersionColumn.VENDOR)
							.set(candidateRow != null && JAVA.equals(candidateRow.candidate().name()));
		}

		private void onRefreshingChanged(boolean refreshing) {
			toggleSouthPanel(refreshProgress, refreshing);
		}

		private void onVersionChanged(VersionRow versionRow) {
			selectedVersionName.set(versionRow == null ? null :
							versionRow.candidate().name() + " " + versionRow.version().identifier());
		}

		private void onInstallActiveChanged(boolean installActive) {
			toggleSouthPanel(installingPanel, installActive);
		}

		private void onDownloadingChanged(boolean downloading) {
			installProgress.setIndeterminate(!downloading);
			if (downloading) {
				cancelDownload.requestFocusInWindow();
			}
		}

		private void refreshCandidates() {
			candidateModel.tableModel().items().refresh();
		}

		private void toggleSouthPanel(JComponent component, boolean embed) {
			if (embed) {
				southPanel.add(component, NORTH);
			}
			else {
				southPanel.remove(component);
			}
			revalidate();
			repaint();
		}

		private List<FilterTableColumn<VersionColumn>> createColumns() {
			return List.of(
							FilterTableColumn.builder()
											.identifier(VersionColumn.VENDOR)
											.headerValue("Vendor")
											.build(),
							FilterTableColumn.builder()
											.identifier(VersionColumn.VERSION)
											.headerValue("Version")
											.build(),
							FilterTableColumn.builder()
											.identifier(VersionColumn.INSTALLED)
											.headerValue("Installed")
											.fixedWidth(80)
											.build(),
							FilterTableColumn.builder()
											.identifier(VersionColumn.DOWNLOADED)
											.headerValue("Downloaded")
											.fixedWidth(90)
											.build(),
							FilterTableColumn.builder()
											.identifier(VersionColumn.USED)
											.headerValue("Used")
											.fixedWidth(60)
											.build());
		}

		private final class InstallTask implements ProgressTask<String> {

			private final State active = State.state();
			private final State downloading = State.state();
			private final Event<?> cancel = Event.event();

			@Override
			public void execute(ProgressReporter<String> progress) {
				versionModel.install(progress, downloading, cancel);
			}

			private void cancel() {
				cancel.run();
			}

			private void started() {
				installProgress.setString("Procrastinating");
				active.set(true);
			}

			private void progress(int progress) {
				installProgress.getModel().setValue(progress);
			}

			private void publish(List<String> strings) {
				installProgress.setString(strings.getFirst() + " " + versionName());
			}

			private void done() {
				installProgress.setString("");
				installProgress.getModel().setValue(0);
				filter.requestFocusInWindow();
				downloading.set(false);
				active.set(false);
			}
		}
	}

	private static final class PreferencesModel {

		private static final LoggerProxy LOGGER = LoggerProxy.instance();

		private final SdkManUiPreferences sdkManUi = SdkManUiPreferences.getInstance();
		private final State confirmActions = State.state(getConfirmActionsPreference());
		private final State confirmExit = State.state(getConfirmExitPreference());
		private final FilterComboBoxModel<Level> logLevels = FilterComboBoxModel.builder()
						.items(LOGGER.levels().stream()
										.map(Level.class::cast)
										.toList())
						.build();
	}

	private static final class PreferencesPanel extends JPanel {

		private final SDKBoyPanel.PreferencesModel preferences;

		private final ComponentValue<String, JTextField> zipExecutable;
		private final ComponentValue<String, JTextField> unzipExecutable;
		private final ComponentValue<String, JTextField> tarExecutable;
		private final ComponentValue<Boolean, JCheckBox> keepDownloadsAvailable;
		private final ComponentValue<Boolean, JCheckBox> confirmActions;
		private final ComponentValue<Boolean, JCheckBox> confirmExit;
		private final ComponentValue<Level, JComboBox<Level>> logLevel;
		private final JButton browseZipExecutableButton;
		private final JButton browseUnzipExecutableButton;
		private final JButton browseTarExecutableButton;
		private final JButton logFileButton;
		private final JButton logDirectoryButton;
		private final LookAndFeelComboBox lookAndFeelComboBox = LookAndFeelComboBox.builder()
						.onSelection(SDKBoyPanel::setLookAndFeelPreference)
						.build();

		private PreferencesPanel(PreferencesModel preferences) {
			super(flexibleGridLayout(0, 1));
			this.preferences = preferences;
			zipExecutable = stringField()
							.value(preferences.sdkManUi.zipExecutable)
							.columns(20)
							.selectAllOnFocusGained(true)
							.buildValue();
			unzipExecutable = stringField()
							.value(preferences.sdkManUi.unzipExecutable)
							.columns(20)
							.selectAllOnFocusGained(true)
							.buildValue();
			tarExecutable = stringField()
							.value(preferences.sdkManUi.tarExecutable)
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
							.value(preferences.sdkManUi.keepDownloadsAvailable)
							.text("Keep downloads available")
							.mnemonic('K')
							.buildValue();
			confirmActions = checkBox()
							.link(preferences.confirmActions)
							.text("Confirm install, uninstall and use")
							.mnemonic('I')
							.buildValue();
			confirmExit = checkBox()
							.link(preferences.confirmExit)
							.text("Confirm exit")
							.mnemonic('X')
							.buildValue();
			logLevel = Components.comboBox()
							.model(preferences.logLevels)
							.value((Level) PreferencesModel.LOGGER.getLogLevel())
							.buildValue();
			setBorder(emptyBorder());
			add(label()
							.text("Look & Feel")
							.displayedMnemonic('L')
							.labelFor(lookAndFeelComboBox)
							.build());
			add(lookAndFeelComboBox);
			add(label()
							.text("Select zip path")
							.displayedMnemonic('Z')
							.labelFor(zipExecutable.component())
							.build());
			add(borderLayoutPanel()
							.layout(new BorderLayout(0, 5))
							.centerComponent(zipExecutable.component())
							.eastComponent(browseZipExecutableButton)
							.build());
			add(label()
							.text("Select unzip path")
							.displayedMnemonic('U')
							.labelFor(unzipExecutable.component())
							.build());
			add(borderLayoutPanel()
							.layout(new BorderLayout(0, 5))
							.centerComponent(unzipExecutable.component())
							.eastComponent(browseUnzipExecutableButton)
							.build());
			add(label()
							.text("Select tar path")
							.displayedMnemonic('T')
							.labelFor(tarExecutable.component())
							.build());
			add(borderLayoutPanel()
							.layout(new BorderLayout(0, 5))
							.centerComponent(tarExecutable.component())
							.eastComponent(browseTarExecutableButton)
							.build());
			add(label()
							.text("Log level")
							.displayedMnemonic('V')
							.labelFor(logLevel.component())
							.build());
			add(borderLayoutPanel()
							.layout(new BorderLayout(0, 5))
							.centerComponent(logLevel.component())
							.eastComponent(panel()
											.layout(new GridLayout(1, 0, 0, 5))
											.add(logFileButton)
											.add(logDirectoryButton)
											.build())
							.build());
			add(keepDownloadsAvailable.component());
			add(confirmActions.component());
			add(confirmExit.component());
		}

		private void save() {
			setConfirmActionsPreference(preferences.confirmActions.get());
			setConfirmExitPreference(preferences.confirmExit.get());
			PreferencesModel.LOGGER.setLogLevel(logLevel.getOrThrow());
			preferences.sdkManUi.zipExecutable = zipExecutable.get();
			preferences.sdkManUi.unzipExecutable = unzipExecutable.get();
			preferences.sdkManUi.tarExecutable = tarExecutable.get();
			preferences.sdkManUi.keepDownloadsAvailable = keepDownloadsAvailable.getOrThrow();
			try {
				UserPreferences.flush();
				preferences.sdkManUi.save();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private void cancel() {
			preferences.confirmActions.set(getConfirmActionsPreference());
			preferences.confirmExit.set(getConfirmExitPreference());
			logLevel.set((Level) PreferencesModel.LOGGER.getLogLevel());
			zipExecutable.set(preferences.sdkManUi.zipExecutable);
			unzipExecutable.set(preferences.sdkManUi.unzipExecutable);
			tarExecutable.set(preferences.sdkManUi.tarExecutable);
			keepDownloadsAvailable.set(preferences.sdkManUi.keepDownloadsAvailable);
		}

		private void openLogFile() {
			PreferencesModel.LOGGER.files().stream()
							.map(File::new)
							.findFirst()
							.ifPresent(this::open);
		}

		private void openLogDirectory() {
			PreferencesModel.LOGGER.files().stream()
							.map(File::new)
							.map(File::getParentFile)
							.findFirst()
							.ifPresent(this::open);
		}

		private void open(File file) {
			try {
				getDesktop().open(file);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
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

	private static final class HelpPanel extends JPanel {

		private final JTextArea shortcuts = textArea()
						.value(SHORTCUTS)
						.font(monospaceFont())
						.editable(false)
						.focusable(false)
						.build();
		private final AboutPanel aboutPanel = new AboutPanel();

		private HelpPanel() {
			super(borderLayout());
			add(borderLayoutPanel()
							.centerComponent(borderLayoutPanel()
											.border(createTitledBorder("Shortcuts"))
											.centerComponent(shortcuts)
											.build())
							.southComponent(borderLayoutPanel()
											.border(createTitledBorder("About"))
											.centerComponent(aboutPanel)
											.build())
							.build(), BorderLayout.CENTER);
		}

		@Override
		public void updateUI() {
			super.updateUI();
			Utilities.updateUI(shortcuts, aboutPanel);
		}

		private static Font monospaceFont() {
			Font font = UIManager.getFont("TextArea.font");

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
				editorPane.setFont(monospaceFont());
				editorPane.setEditable(false);
				editorPane.setFocusable(false);
				editorPane.addHyperlinkListener(new OpenLink());
				add(editorPane, BorderLayout.CENTER);
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
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		setDefaultUncaughtExceptionHandler((_, throwable) -> {
			throwable.printStackTrace();
			Dialogs.exception().show(throwable);
		});

		findLookAndFeel(getLookAndFeelPreference())
						.ifPresent(LookAndFeelEnabler::enable);

		SDKBoyPanel sdkBoyPanel = new SDKBoyPanel();

		Frames.builder()
						.component(sdkBoyPanel)
						.title("SDKBOY " + SDKBoyModel.VERSION)
						.icon(Logos.logoTransparent())
						.centerFrame(true)
						.defaultCloseOperation(DO_NOTHING_ON_CLOSE)
						.onClosing(_ -> sdkBoyPanel.exit())
						.show();
	}
}
// end::sdkboy_panel[]