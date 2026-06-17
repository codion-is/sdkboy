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

import is.codion.common.reactive.event.Event;
import is.codion.common.reactive.state.ObservableState;
import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.sdkboy.model.CandidateModel.CandidateRow;
import is.codion.sdkboy.model.PreferencesModel;
import is.codion.sdkboy.model.SDKBoyModel;
import is.codion.sdkboy.model.VersionModel;
import is.codion.sdkboy.model.VersionModel.VersionColumn;
import is.codion.sdkboy.model.VersionModel.VersionRow;
import is.codion.swing.common.model.action.DelayedAction;
import is.codion.swing.common.model.worker.ProgressWorker;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressReporter;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressTaskHandler;
import is.codion.swing.common.ui.Utilities;
import is.codion.swing.common.ui.component.table.FilterTable;
import is.codion.swing.common.ui.component.table.FilterTableColumn;
import is.codion.swing.common.ui.control.Control;
import is.codion.swing.common.ui.key.KeyEvents;

import org.jspecify.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import java.util.List;

import static is.codion.common.reactive.state.State.and;
import static is.codion.sdkboy.ui.CandidatePanel.createFilterField;
import static is.codion.swing.common.model.action.DelayedAction.delayedAction;
import static is.codion.swing.common.ui.Utilities.setClipboard;
import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.*;
import static is.codion.swing.common.ui.control.Control.command;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static java.awt.BorderLayout.*;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.*;
import static javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

// tag::version_panel[]
final class VersionPanel extends JPanel {

	private static final String JAVA = "Java";

	private final VersionModel version;
	private final PreferencesModel preferences;
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
	private final JButton helpButton;
	private final SouthComponent southComponent;
	private final VersionControls controls;

	VersionPanel(SDKBoyModel model, State help) {
		super(borderLayout());
		version = model.version();
		preferences = model.preferences();
		installTask = new InstallTask();
		controls = new VersionControls();
		model.candidate().tableModel().selection().item().addConsumer(this::onCandidateSelected);
		version.tableModel().items().refresher().active().addConsumer(this::onRefreshing);
		version.tableModel().selection().item().addConsumer(this::onVersionSelected);
		installTask.active.addConsumer(this::onInstalling);
		installTask.downloading.addConsumer(this::onDownloading);
		table = FilterTable.builder()
						.model(version.tableModel())
						.columns(this::configureColumns)
						.sortable(false)
						.focusable(false)
						.selectionMode(SINGLE_SELECTION)
						.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
						.columnReordering(false)
						.hideColumns(VersionColumn.VENDOR)
						.rowsFillViewport(true)
						.doubleClick(command(this::onVersionDoubleClick))
						.enabled(installTask.active.not())
						.build();
		filter = createFilterField(version.filter(), table, installTask.active);
		installedOnly = checkBox()
						.link(version.installedOnly())
						.text("Installed")
						.mnemonic('N')
						.focusable(false)
						.enabled(installTask.active.not())
						.build();
		downloadedOnly = checkBox()
						.link(version.downloadedOnly())
						.text("Downloaded")
						.mnemonic('A')
						.focusable(false)
						.enabled(installTask.active.not())
						.build();
		usedOnly = checkBox()
						.link(version.usedOnly())
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
		southComponent = new SouthComponent();
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

	VersionControls controls() {
		return controls;
	}

	ObservableState installing() {
		return installTask.active.observable();
	}

	private void onVersionDoubleClick() {
		if (version.selectedUsed().is()) {
			uninstall();
		}
		else if (version.selectedInstalled().is()) {
			use();
		}
		else {
			install();
		}
	}

	private void install() {
		install(() -> {});
	}

	private void install(Runnable onSuccess) {
		if (confirmInstall()) {
			ProgressWorker.builder()
							.task(installTask)
							.onSuccess(onSuccess)
							.execute();
		}
	}

	private void uninstall() {
		if (confirmUninstall()) {
			ProgressWorker.builder()
							.task(version::uninstall)
							.onSuccess(version::refresh)
							.execute();
		}
	}

	private void use() {
		VersionRow selected = version.selected();
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
							.task(version::use)
							.onSuccess(version::refresh)
							.execute();
		}
	}

	private void copyUseCommand() {
		VersionRow selected = version.selected();
		if (selected.version().installed()) {
			copyUseCommand(selected);
		}
		else {
			install(() -> copyUseCommand(selected));
		}
	}

	private void copyUseCommand(VersionRow versionRow) {
		String command = "sdk use " + versionRow.candidate().id() + " " + versionRow.version().identifier();
		setClipboard(command);
		showMessageDialog(this, command + "\n\ncopied to clipboard", "Copied", INFORMATION_MESSAGE);
	}

	private boolean confirmInstall() {
		return !preferences.confirmActions().is() || showConfirmDialog(this,
						"Install " + versionName() + "?",
						"Confirm install", YES_NO_OPTION) == YES_OPTION;
	}

	private boolean confirmUninstall() {
		return !preferences.confirmActions().is() || showConfirmDialog(this,
						"Uninstall " + versionName() + "?",
						"Confirm uninstall", YES_NO_OPTION) == YES_OPTION;
	}

	private boolean confirmUse() {
		return !preferences.confirmActions().is() || showConfirmDialog(this,
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
		southComponent.toggle(refreshProgress, refreshing);
	}

	private void onVersionSelected(VersionRow versionRow) {
		selectedVersionName.set(versionRow == null ? null :
						versionRow.candidate().name() + " " + versionRow.version().identifier());
	}

	private void onInstalling(boolean installing) {
		southComponent.toggle(installingPanel, installing);
	}

	private void onDownloading(boolean downloading) {
		installProgress.setIndeterminate(!downloading);
		if (downloading) {
			cancelDownload.requestFocusInWindow();
		}
	}

	private void configureColumns(FilterTableColumn.Builder<VersionColumn> column) {
		switch (column.identifier()) {
			case INSTALLED -> column.fixedWidth(80);
			case DOWNLOADED -> column.fixedWidth(90);
			case USED -> column.fixedWidth(60);
		}
	}

	final class VersionControls {

		private final Control install;
		private final Control uninstall;
		private final Control use;
		private final Control copyUseCommand;

		private VersionControls() {
			install = Control.builder()
							.command(VersionPanel.this::install)
							.enabled(and(
											version.tableModel().selection().empty().not(),
											version.selectedInstalled().not()))
							.build();
			uninstall = Control.builder()
							.command(VersionPanel.this::uninstall)
							.enabled(and(
											version.tableModel().selection().empty().not(),
											version.selectedInstalled()))
							.build();
			use = Control.builder()
							.command(VersionPanel.this::use)
							.enabled(version.selectedUsed().not())
							.build();
			copyUseCommand = Control.builder()
							.command(VersionPanel.this::copyUseCommand)
							.build();
		}

		Control install() {
			return install;
		}

		Control uninstall() {
			return uninstall;
		}

		Control use() {
			return use;
		}

		Control copyUseCommand() {
			return copyUseCommand;
		}
	}

	private final class InstallTask implements ProgressTaskHandler<String> {

		private final State active = State.state();
		private final State downloading = State.state();
		private final Event<?> cancel = Event.event();

		@Override
		public void execute(ProgressReporter<String> progress) {
			version.install(progress, downloading, cancel);
		}

		@Override
		public void onStarted() {
			installProgress.setString("Procrastinating");
			active.set(true);
		}

		@Override
		public void onProgress(int progress) {
			installProgress.setValue(progress);
		}

		@Override
		public void onPublish(List<String> status) {
			installProgress.setString(status.getFirst() + " " + versionName());
		}

		@Override
		public void onDone() {
			installProgress.setString("");
			installProgress.setValue(0);
			filter.requestFocusInWindow();
			downloading.set(false);
			active.set(false);
		}

		@Override
		public void onSuccess() {
			version.refresh();
		}

		private void cancel() {
			cancel.run();
		}
	}

	private final class SouthComponent {

		private static final int SHOW_DELAY = 350;

		private @Nullable DelayedAction show;

		private void toggle(JComponent component, boolean visible) {
			if (visible) {
				show = delayedAction(() -> show(component), SHOW_DELAY);
			}
			else {
				hide(component);
			}
		}

		private void show(JComponent component) {
			southPanel.add(component, NORTH);
			revalidate();
			repaint();
		}

		private void hide(JComponent component) {
			cancel();
			southPanel.remove(component);
			revalidate();
			repaint();
		}

		private void cancel() {
			if (show != null) {
				show.cancel();
				show = null;
			}
		}
	}
}
// end::version_panel[]