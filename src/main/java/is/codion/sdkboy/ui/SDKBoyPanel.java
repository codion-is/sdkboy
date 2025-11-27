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

import is.codion.common.model.selection.MultiSelection.Indexes;
import is.codion.common.reactive.event.Event;
import is.codion.common.reactive.state.ObservableState;
import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.sdkboy.model.SDKBoyModel;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateColumn;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateRow;
import is.codion.sdkboy.model.SDKBoyModel.PreferencesModel;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel.VersionColumn;
import is.codion.sdkboy.model.SDKBoyModel.VersionModel.VersionRow;
import is.codion.swing.common.model.action.DelayedAction;
import is.codion.swing.common.model.worker.ProgressWorker;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressReporter;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressTask;
import is.codion.swing.common.ui.Utilities;
import is.codion.swing.common.ui.ancestor.Ancestor;
import is.codion.swing.common.ui.component.table.FilterTable;
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
import org.jspecify.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static is.codion.common.reactive.state.State.and;
import static is.codion.sdkboy.model.SDKBoyModel.PreferencesModel.getLookAndFeelPreference;
import static is.codion.swing.common.model.action.DelayedAction.delayedAction;
import static is.codion.swing.common.ui.Utilities.setClipboard;
import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.*;
import static is.codion.swing.common.ui.control.Control.command;
import static is.codion.swing.common.ui.laf.LookAndFeelProvider.findLookAndFeel;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static java.awt.BorderLayout.*;
import static java.awt.Desktop.getDesktop;
import static java.awt.event.KeyEvent.*;
import static java.lang.Thread.setDefaultUncaughtExceptionHandler;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.*;
import static javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static javax.swing.UIManager.getIcon;
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
	private final CandidatePanel candidatePanel;
	private final VersionPanel versionPanel;
	private final State help = State.builder()
					.consumer(this::onHelp)
					.build();

	private PreferencesPanel preferencesPanel;

	private SDKBoyPanel() {
		super(borderLayout());
		setDefaultUncaughtExceptionHandler(new SDKBoyExceptionHandler());
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
		KeyEvents.builder()
						.condition(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
						.modifiers(ALT_DOWN_MASK)
						.keyCode(VK_O)
						.action(command(this::displayDescription))
						.enable(this)
						.keyCode(VK_P)
						.action(command(this::displayPreferences))
						.enable(this)
						.keyCode(VK_R)
						.action(command(versionPanel::refreshCandidates))
						.enable(this)
						.keyCode(VK_X)
						.action(command(this::exit))
						.enable(this)
						.keyCode(VK_INSERT)
						.action(versionPanel.install)
						.enable(this)
						.keyCode(VK_DELETE)
						.action(versionPanel.uninstall)
						.enable(this)
						.keyCode(VK_I)
						.action(versionPanel.install)
						.enable(this)
						.keyCode(VK_D)
						.action(versionPanel.uninstall)
						.enable(this)
						.keyCode(VK_U)
						.action(versionPanel.use)
						.enable(this)
						.keyCode(VK_C)
						.action(versionPanel.copyUseCommand)
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

	private void displayPreferences() {
		if (preferencesPanel == null) {
			preferencesPanel = new PreferencesPanel(model.preferencesModel());
		}
		Dialogs.okCancel()
						.component(preferencesPanel)
						.owner(this)
						.title("Preferences")
						.onOk(model.preferencesModel()::save)
						.onCancel(model.preferencesModel()::revert)
						.show();
	}

	private void displayDescription() {
		candidatePanel.table.model().selection().item().optional()
						.ifPresent(candidateRow -> Dialogs.builder()
										.component(textArea()
														.value(candidateRow.candidate().description())
														.rowsColumns(8, 40)
														.editable(false)
														.lineWrap(true)
														.wrapStyleWord(true)
														.scrollPane())
										.owner(this)
										.title(candidateRow.candidate().name() + " - Description")
										.show());
	}

	private void exit() {
		if (confirmExit()) {
			Ancestor.window().of(this).dispose();
		}
	}

	private boolean confirmExit() {
		if (versionPanel.installTask.active.is()) {
			return false;
		}

		return !model.preferencesModel().confirmExit().is() || showConfirmDialog(this,
						"Are you sure you want to exit?",
						"Confirm Exit", YES_NO_OPTION, QUESTION_MESSAGE) == YES_OPTION;
	}

	private static JTextField createFilterField(Value<String> filter, FilterTable<?, ?> table, ObservableState installing) {
		Indexes selectedIndexes = table.model().selection().indexes();

		return stringField()
						.link(filter)
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
						.keyEvent(KeyEvents.builder()
										.keyCode(VK_PAGE_UP)
										.action(pageUpControl(table)))
						.keyEvent(KeyEvents.builder()
										.keyCode(VK_PAGE_DOWN)
										.action(pageDownControl(table)))
						.enabled(installing.not())
						.build();
	}

	private static Control pageDownControl(FilterTable<?, ?> table) {
		return command(() -> {
			int visibleRowCount = Ancestor.ofType(JScrollPane.class).of(table).get().getViewport().getHeight() / table.getRowHeight();
			table.model().selection().index().map(index ->
							Math.min((index == -1 ? 0 : index) + visibleRowCount - 1, table.model().items().included().size() - 1));
		});
	}

	private static Control pageUpControl(FilterTable<?, ?> table) {
		return command(() -> {
			int visibleRowCount = Ancestor.ofType(JScrollPane.class).of(table).get().getViewport().getHeight() / table.getRowHeight();
			table.model().selection().index().map(index ->
							Math.max((index == -1 ? 0 : index) - visibleRowCount + 1, 0));
		});
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
							.columns(this::configureColumns)
							.sortable(false)
							.focusable(false)
							.selectionMode(SINGLE_SELECTION)
							.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
							.columnReordering(false)
							.enabled(and(installing.not(), refreshingVersions.not()))
							.cellRenderer(CandidateColumn.INSTALLED, Integer.class, renderer -> renderer
											.horizontalAlignment(SwingConstants.CENTER))
							.build();
			filter = createFilterField(candidateModel.filter(), table, installing);
			installedOnly = checkBox()
							.link(candidateModel.installedOnly())
							.text("Installed")
							.mnemonic('T')
							.focusable(false)
							.enabled(installing.not())
							.build();
			setBorder(createCompoundBorder(createTitledBorder("Candidates"), emptyBorder()));
			add(scrollPane()
							.view(table)
							.preferredWidth(220)
							.build(), CENTER);
			add(borderLayoutPanel()
							.center(filter)
							.east(installedOnly)
							.build(), SOUTH);
		}

		private void configureColumns(FilterTableColumn.Builder<CandidateColumn> column) {
			if (column.identifier() == CandidateColumn.INSTALLED) {
				column.fixedWidth(80);
			}
		}
	}

	private static final class VersionPanel extends JPanel {

		private static final String JAVA = "Java";

		private final SDKBoyModel model;
		private final CandidateModel candidateModel;
		private final VersionModel versionModel;
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
		private final Control install;
		private final Control uninstall;
		private final Control use;
		private final Control copyUseCommand;
		private final JButton helpButton;

		private @Nullable DelayedAction showSouthComponent;

		private VersionPanel(SDKBoyModel model, State help) {
			super(borderLayout());
			this.model = model;
			this.candidateModel = model.candidateModel();
			this.versionModel = model.versionModel();
			this.installTask = new InstallTask();
			this.install = Control.builder()
							.command(this::install)
							.enabled(and(
											versionModel.tableModel().selection().empty().not(),
											versionModel.selectedInstalled().not()))
							.build();
			this.uninstall = Control.builder()
							.command(this::uninstall)
							.enabled(and(
											versionModel.tableModel().selection().empty().not(),
											versionModel.selectedInstalled()))
							.build();
			this.use = Control.builder()
							.command(this::use)
							.enabled(versionModel.selectedUsed().not())
							.build();
			this.copyUseCommand = Control.builder()
							.command(this::copyUseCommand)
							.build();
			candidateModel.tableModel().selection().item().addConsumer(this::onCandidateSelected);
			versionModel.tableModel().items().refresher().active().addConsumer(this::onRefreshing);
			versionModel.tableModel().selection().item().addConsumer(this::onVersionSelected);
			installTask.active.addConsumer(this::onInstalling);
			installTask.downloading.addConsumer(this::onDownloading);
			table = FilterTable.builder()
							.model(versionModel.tableModel())
							.columns(this::configureColumns)
							.sortable(false)
							.focusable(false)
							.selectionMode(SINGLE_SELECTION)
							.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
							.columnReordering(false)
							.hiddenColumns(VersionColumn.VENDOR)
							.doubleClick(command(this::onVersionDoubleClick))
							.enabled(installTask.active.not())
							.build();
			filter = createFilterField(versionModel.filter(), table, installTask.active);
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
							.center(installProgress)
							.east(cancelDownload)
							.build();
			helpButton = button()
							.control(Control.builder()
											.command(help::toggle)
											.caption("?")
											.mnemonic('S'))
							.focusable(false)
							.build();
			southPanel = borderLayoutPanel()
							.center(filter)
							.east(flexibleGridLayoutPanel(1, 0)
											.add(installedOnly)
											.add(downloadedOnly)
											.add(usedOnly)
											.add(helpButton))
							.build();
			setBorder(createCompoundBorder(createTitledBorder("Versions"), emptyBorder()));
			add(scrollPane()
							.view(table)
							.build(), CENTER);
			add(southPanel, SOUTH);
		}

		@Override
		public void updateUI() {
			super.updateUI();
			Utilities.updateUI(southPanel, refreshProgress, installingPanel, installProgress, cancelDownload);
		}

		private void onVersionDoubleClick() {
			if (versionModel.selectedUsed().is()) {
				uninstall();
			}
			else if (versionModel.selectedInstalled().is()) {
				use();
			}
			else {
				install();
			}
		}

		private void install() {
			install(() -> {});
		}

		private void install(Runnable onInstalled) {
			if (confirmInstall()) {
				ProgressWorker.builder()
								.task(installTask)
								.onStarted(installTask::started)
								.onProgress(installTask::progress)
								.onPublish(installTask::publish)
								.onDone(installTask::done)
								.onResult(() -> installTask.result(onInstalled))
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
			return !model.preferencesModel().confirmActions().is() || showConfirmDialog(this,
							"Install " + versionName() + "?",
							"Confirm install", YES_NO_OPTION) == YES_OPTION;
		}

		private boolean confirmUninstall() {
			return !model.preferencesModel().confirmActions().is() || showConfirmDialog(this,
							"Uninstall " + versionName() + "?",
							"Confirm uninstall", YES_NO_OPTION) == YES_OPTION;
		}

		private boolean confirmUse() {
			return !model.preferencesModel().confirmActions().is() || showConfirmDialog(this,
							"Set " + versionName() + " as your global SDK?",
							"Confirm use", YES_NO_OPTION) == YES_OPTION;
		}

		private String versionName() {
			return selectedVersionName.get();
		}

		private void onCandidateSelected(CandidateRow candidateRow) {
			table.columnModel().visible(VersionColumn.VENDOR)
							.set(candidateRow != null && JAVA.equals(candidateRow.candidate().name()));
		}

		private void onRefreshing(boolean refreshing) {
			toggleSouthComponent(refreshProgress, refreshing);
		}

		private void onVersionSelected(VersionRow versionRow) {
			selectedVersionName.set(versionRow == null ? null :
							versionRow.candidate().name() + " " + versionRow.version().identifier());
		}

		private void onInstalling(boolean installing) {
			toggleSouthComponent(installingPanel, installing);
		}

		private void onDownloading(boolean downloading) {
			installProgress.setIndeterminate(!downloading);
			if (downloading) {
				cancelDownload.requestFocusInWindow();
			}
		}

		private void refreshCandidates() {
			candidateModel.tableModel().items().refresh();
		}

		private void toggleSouthComponent(JComponent component, boolean show) {
			if (show) {
				showSouthComponent = delayedAction(350, () -> showSouthComponent(component));
			}
			else {
				hideSouthComponent(component);
			}
		}

		private void showSouthComponent(JComponent component) {
			southPanel.add(component, NORTH);
			revalidate();
			repaint();
		}

		private void hideSouthComponent(JComponent component) {
			cancelShowSouthComponent();
			southPanel.remove(component);
			revalidate();
			repaint();
		}

		private void cancelShowSouthComponent() {
			if (showSouthComponent != null) {
				showSouthComponent.cancel();
				showSouthComponent = null;
			}
		}

		private void configureColumns(FilterTableColumn.Builder<VersionColumn> column) {
			switch (column.identifier()) {
				case INSTALLED -> column.fixedWidth(80);
				case DOWNLOADED -> column.fixedWidth(90);
				case USED -> column.fixedWidth(60);
			}
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

			private void result(Runnable onInstalled) {
				model.refresh();
				onInstalled.run();
			}
		}
	}

	private static final class PreferencesPanel extends JPanel {

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

		private PreferencesPanel(PreferencesModel preferences) {
			super(borderLayout());
			this.preferences = preferences;
			lookAndFeelComboBox = LookAndFeelComboBox.builder()
							.onSelection(preferences::setLookAndFeelPreference)
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
						throw new RuntimeException(e);
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