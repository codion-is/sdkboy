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
package is.codion.sdkboy.model;

import is.codion.common.model.CancelException;
import is.codion.common.model.preferences.UserPreferences;
import is.codion.common.reactive.observer.Observer;
import is.codion.common.reactive.state.ObservableState;
import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.common.utilities.logging.LoggerProxy;
import is.codion.common.utilities.version.Version;
import is.codion.plugin.flatlaf.intellij.themes.darkflat.DarkFlat;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateRow;
import is.codion.swing.common.model.component.combobox.FilterComboBoxModel;
import is.codion.swing.common.model.component.table.FilterTableModel;
import is.codion.swing.common.model.component.table.FilterTableModel.TableColumns;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressReporter;
import is.codion.swing.common.ui.laf.LookAndFeelEnabler;

import ch.qos.logback.classic.Level;
import io.github.jagodevreede.sdkman.api.ProgressInformation;
import io.github.jagodevreede.sdkman.api.SdkManApi;
import io.github.jagodevreede.sdkman.api.SdkManUiPreferences;
import io.github.jagodevreede.sdkman.api.domain.Candidate;
import io.github.jagodevreede.sdkman.api.domain.CandidateVersion;
import io.github.jagodevreede.sdkman.api.http.DownloadTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.jagodevreede.sdkman.api.SdkManApi.DEFAULT_SDKMAN_HOME;
import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;
import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;

// tag::sdkboy_model[]
public final class SDKBoyModel {

	public static final Version VERSION = Version.parse(SDKBoyModel.class, "/version.properties");

	private final SdkManApi sdkMan = new SdkManApi(DEFAULT_SDKMAN_HOME);

	private final CandidateModel candidateModel;
	private final VersionModel versionModel;
	private final PreferencesModel preferencesModel;

	public SDKBoyModel() {
		candidateModel = new CandidateModel();
		versionModel = new VersionModel();
		preferencesModel = new PreferencesModel();
	}

	public CandidateModel candidateModel() {
		return candidateModel;
	}

	public VersionModel versionModel() {
		return versionModel;
	}

	public PreferencesModel preferencesModel() {
		return preferencesModel;
	}

	public void refresh() {
		candidateModel.tableModel.items().refresh(_ ->
						versionModel.tableModel.items().refresh());
	}

	public final class CandidateModel {

		private final FilterTableModel<CandidateRow, CandidateColumn> tableModel =
						FilterTableModel.builder()
										.columns(new CandidateTableColumns())
										.items(new CandidateItems())
										.included(new CandidateIncluded())
										.build();
		private final Value<String> filter = Value.builder()
						.<String>nullable()
						.listener(this::onFilterChanged)
						.build();
		private final State installedOnly = State.builder()
						.listener(this::onFilterChanged)
						.build();

		private CandidateModel() {
			tableModel.sort().order(CandidateColumn.NAME).set(ASCENDING);
			tableModel.items().refresh();
		}

		public FilterTableModel<CandidateRow, CandidateColumn> tableModel() {
			return tableModel;
		}

		public Value<String> filter() {
			return filter;
		}

		public State installedOnly() {
			return installedOnly;
		}

		private void onFilterChanged() {
			tableModel.items().filter();
			tableModel.selection().indexes().clear();
			tableModel.selection().indexes().increment();
		}

		public enum CandidateColumn {
			NAME, INSTALLED
		}

		public record CandidateRow(Candidate candidate, int installed) {

			@Override
			public String toString() {
				return candidate.name();
			}

			@Override
			public boolean equals(Object object) {
				if (object == null || getClass() != object.getClass()) {
					return false;
				}
				CandidateRow candidateRow = (CandidateRow) object;

				return Objects.equals(candidate.id(), candidateRow.candidate.id());
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(candidate.id());
			}
		}

		private static final class CandidateTableColumns implements TableColumns<CandidateRow, CandidateColumn> {

			private static final List<CandidateColumn> IDENTIFIERS = List.of(CandidateColumn.values());

			@Override
			public List<CandidateColumn> identifiers() {
				return IDENTIFIERS;
			}

			@Override
			public Class<?> columnClass(CandidateColumn column) {
				return switch (column) {
					case NAME -> String.class;
					case INSTALLED -> Integer.class;
				};
			}

			@Override
			public String caption(CandidateColumn column) {
				return switch (column) {
					case NAME -> "Name";
					case INSTALLED -> "Installed";
				};
			}

			@Override
			public Object value(CandidateRow row, CandidateColumn column) {
				return switch (column) {
					case NAME -> row.candidate.name();
					case INSTALLED -> row.installed == 0 ? null : row.installed;
				};
			}

			@Override
			public Comparator<?> comparator(CandidateColumn identifier) {
				if (identifier == CandidateColumn.NAME) {
					return Comparator.<String, String>comparing(String::toLowerCase);
				}

				return TableColumns.super.comparator(identifier);
			}
		}

		private class CandidateItems implements Supplier<Collection<CandidateRow>> {

			@Override
			public Collection<CandidateRow> get() {
				try {
					return sdkMan.getCandidates().get().stream()
									.map(candidate -> new CandidateRow(candidate,
													sdkMan.getLocalInstalledVersions(candidate.id()).size()))
									.toList();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		private final class CandidateIncluded implements Predicate<CandidateRow> {

			@Override
			public boolean test(CandidateRow candidateRow) {
				if (installedOnly.is() && candidateRow.installed() == 0) {
					return false;
				}
				if (filter.isNull()) {
					return true;
				}

				return candidateRow.candidate.name().toLowerCase().contains(filter.getOrThrow());
			}
		}
	}

	public final class VersionModel {

		private static final int DONE = 100;

		private final FilterTableModel<VersionRow, VersionColumn> tableModel =
						FilterTableModel.builder()
										.columns(new VersionTableColumns())
										.items(new VersionItems())
										.included(new VersionIncluded())
										.build();
		private final State selectedInstalled = State.state();
		private final State selectedUsed = State.state();
		private final Value<String> filter = Value.builder()
						.<String>nullable()
						.listener(this::onFilterChanged)
						.build();
		private final State installedOnly = State.builder()
						.listener(this::onFilterChanged)
						.build();
		private final State downloadedOnly = State.builder()
						.listener(this::onFilterChanged)
						.build();
		private final State usedOnly = State.builder()
						.listener(this::onFilterChanged)
						.build();

		private VersionModel() {
			tableModel.selection().item().addConsumer(this::onVersionSelected);
			tableModel.sort().order(VersionColumn.VENDOR).set(ASCENDING);
			tableModel.sort().order(VersionColumn.VERSION).add(DESCENDING);
			candidateModel.tableModel.selection().item().addListener(this::onCandidateSelected);
		}

		public FilterTableModel<VersionRow, VersionColumn> tableModel() {
			return tableModel;
		}

		public ObservableState selectedInstalled() {
			return selectedInstalled.observable();
		}

		public ObservableState selectedUsed() {
			return selectedUsed.observable();
		}

		public Value<String> filter() {
			return filter;
		}

		public State installedOnly() {
			return installedOnly;
		}

		public State downloadedOnly() {
			return downloadedOnly;
		}

		public State usedOnly() {
			return usedOnly;
		}

		public VersionRow selected() {
			return tableModel.selection().item().getOrThrow();
		}

		public void refresh() {
			tableModel.items().refresh();
		}

		public void install(ProgressReporter<String> progress, State downloading, Observer<?> cancel) {
			VersionRow selected = selected();
			if (selected.version.available()) {
				progress.report(DONE);
			}
			else {
				download(selected, progress, downloading, cancel);
			}
			progress.publish("Installing");
			sdkMan.install(selected.candidate.id(), selected.version.identifier());
			progress.publish("Done");
		}

		public void uninstall() {
			VersionRow selected = selected();
			sdkMan.uninstall(selected.candidate.id(), selected.version.identifier());
		}

		public void use() {
			VersionRow selected = selected();
			try {
				sdkMan.changeGlobal(selected.candidate.id(), selected.version.identifier());
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private void download(VersionRow versionRow, ProgressReporter<String> progress,
													State downloading, Observer<?> cancel) {
			DownloadTask task = sdkMan.download(versionRow.candidate.id(), versionRow.version.identifier());
			task.setProgressInformation(new DownloadProgress(progress, downloading));
			Runnable cancelTask = task::cancel;
			cancel.addListener(cancelTask);
			try {
				task.download();
			}
			finally {
				// Prevent a memory leak, the cancel Observer
				// comes from a single InstallTask instance
				cancel.removeListener(cancelTask);
			}
			if (task.isCancelled()) {
				throw new CancelException();
			}
		}

		private void onFilterChanged() {
			tableModel.items().filter();
			if (!filter.isNull() || tableModel.selection().empty().is()) {
				tableModel.selection().indexes().clear();
				tableModel.selection().indexes().increment();
			}
		}

		private void onCandidateSelected() {
			tableModel.items().refresh(_ -> {
				if (tableModel.selection().empty().is()) {
					tableModel.selection().indexes().increment();
				}
			});
		}

		private void onVersionSelected(VersionRow versionRow) {
			selectedInstalled.set(versionRow != null && versionRow.version.installed());
			selectedUsed.set(versionRow != null && versionRow.used());
		}

		public enum VersionColumn {
			VENDOR, VERSION, INSTALLED, DOWNLOADED, USED
		}

		public record VersionRow(Candidate candidate, CandidateVersion version, VersionInfo versionInfo, boolean used) {

			@Override
			public boolean equals(Object object) {
				if (object == null || getClass() != object.getClass()) {
					return false;
				}

				VersionRow row = (VersionRow) object;

				return Objects.equals(candidate.id(), row.candidate.id()) &&
								Objects.equals(version.identifier(), row.version.identifier());
			}

			@Override
			public int hashCode() {
				return Objects.hash(candidate.id(), version.identifier());
			}
		}

		/**
		 * Sort semantic version strings correctly, that is,
		 * ones using the major.minor.patch-metadata format.
		 * For other formats, textual sorting is used.
		 * @param version the semantic Version, if available
		 * @param versionName the version name
		 */
		public record VersionInfo(Version version, String versionName) implements Comparable<VersionInfo> {

			public static VersionInfo of(String version) {
				long dots = version.chars().filter(ch -> ch == '.').count();
				if (dots > 2) {
					return new VersionInfo(null, version);
				}
				try {
					return new VersionInfo(Version.parse(version), version);
				}
				catch (Exception e) {
					return new VersionInfo(null, version);
				}
			}

			@Override
			public String toString() {
				return version == null ? versionName : version.toString();
			}

			@Override
			public int compareTo(VersionInfo versionInfo) {
				if (version != null && versionInfo.version != null) {
					return version.compareTo(versionInfo.version);
				}

				return versionName.compareTo(versionInfo.versionName);
			}
		}

		private static final class VersionTableColumns implements TableColumns<VersionRow, VersionColumn> {

			private static final List<VersionColumn> IDENTIFIERS = List.of(VersionColumn.values());

			@Override
			public List<VersionColumn> identifiers() {
				return IDENTIFIERS;
			}

			@Override
			public Class<?> columnClass(VersionColumn column) {
				return switch (column) {
					case VENDOR -> String.class;
					case VERSION -> VersionInfo.class;
					case INSTALLED, DOWNLOADED, USED -> Boolean.class;
				};
			}

			@Override
			public String caption(VersionColumn column) {
				return switch (column) {
					case VENDOR -> "Vendor";
					case VERSION -> "Version";
					case INSTALLED -> "Installed";
					case DOWNLOADED -> "Downloaded";
					case USED -> "Used";
				};
			}

			@Override
			public Object value(VersionRow row, VersionColumn column) {
				return switch (column) {
					case VENDOR -> row.version.vendor();
					case VERSION -> row.versionInfo();
					case INSTALLED -> row.version.installed();
					case DOWNLOADED -> row.version.available();
					case USED -> row.used;
				};
			}
		}

		private class VersionItems implements Supplier<Collection<VersionRow>> {

			@Override
			public Collection<VersionRow> get() {
				return candidateModel.tableModel.selection().item().optional()
								.map(this::candidateVersions)
								.orElse(List.of());
			}

			private Collection<VersionRow> candidateVersions(CandidateRow candidateRow) {
				try {
					String inUse = sdkMan.resolveCurrentVersion(candidateRow.candidate().id());

					return sdkMan.getVersions(candidateRow.candidate().id()).stream()
									.map(version -> new VersionRow(candidateRow.candidate(), version,
													VersionInfo.of(version.version()), version.identifier().equals(inUse)))
									.toList();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		private final class VersionIncluded implements Predicate<VersionRow> {

			@Override
			public boolean test(VersionRow versionRow) {
				CandidateVersion candidateVersion = versionRow.version;
				if (installedOnly.is() && !candidateVersion.installed()) {
					return false;
				}
				if (downloadedOnly.is() && !candidateVersion.available()) {
					return false;
				}
				if (usedOnly.is() && !versionRow.used) {
					return false;
				}
				if (filter.isNull()) {
					return true;
				}

				Stream<String> strings = Stream.of(filter.getOrThrow().split(" "))
								.map(String::trim);
				String version = candidateVersion.version().toLowerCase();
				if (candidateVersion.vendor() == null) {
					return strings.allMatch(version::contains);
				}
				String vendor = candidateVersion.vendor().toLowerCase();

				return strings.allMatch(filter -> version.contains(filter) || vendor.contains(filter));
			}
		}

		private static final class DownloadProgress implements ProgressInformation {

			private final ProgressReporter<String> progress;
			private final State downloading;

			private DownloadProgress(ProgressReporter<String> progress, State downloading) {
				this.progress = progress;
				this.downloading = downloading;
			}

			@Override
			public void publishProgress(int value) {
				downloading.set(value >= 1 && value < 100);
				if (downloading.is()) {
					progress.publish("Downloading");
					progress.report(value);
				}
				else {
					progress.publish("Extracting");
				}
			}

			@Override
			public void publishState(String state) {}
		}
	}

	public static final class PreferencesModel {

		private static final String LOOK_AND_FEEL = "SDKBOY.lookAndFeel";
		private static final String CONFIRM_ACTIONS = "SDKBOY.confirmActions";
		private static final String CONFIRM_EXIT = "SDKBOY.confirmExit";

		private final LoggerProxy logger = LoggerProxy.instance();
		private final SdkManUiPreferences sdkManUi = SdkManUiPreferences.getInstance();
		private final Value<String> zipExecutable = Value.nullable(sdkManUi.zipExecutable);
		private final Value<String> unzipExecutable = Value.nullable(sdkManUi.unzipExecutable);
		private final Value<String> tarExecutable = Value.nullable(sdkManUi.tarExecutable);
		private final State keepDownloadsAvailable = State.state(sdkManUi.keepDownloadsAvailable);
		private final State confirmActions = State.state(getConfirmActionsPreference());
		private final State confirmExit = State.state(getConfirmExitPreference());
		private final FilterComboBoxModel<Level> logLevels = FilterComboBoxModel.builder()
						.items(logger.levels().stream()
										.map(Level.class::cast)
										.toList())
						.build();

		private PreferencesModel() {}

		public Value<String> zipExecutable() {
			return zipExecutable;
		}

		public Value<String> unzipExecutable() {
			return unzipExecutable;
		}

		public Value<String> tarExecutable() {
			return tarExecutable;
		}

		public State keepDownloadsAvailable() {
			return keepDownloadsAvailable;
		}

		public State confirmActions() {
			return confirmActions;
		}

		public State confirmExit() {
			return confirmExit;
		}

		public FilterComboBoxModel<Level> logLevels() {
			return logLevels;
		}

		public Level logLevel() {
			return (Level) logger.getLogLevel(logger.rootLogger());
		}

		public Optional<File> logFile() {
			return logger.files().stream()
							.map(File::new)
							.findFirst();
		}

		public Optional<File> logDirectory() {
			return logger.files().stream()
							.map(File::new)
							.map(File::getParentFile)
							.findFirst();
		}

		public void setLookAndFeelPreference(LookAndFeelEnabler lookAndFeelEnabler) {
			UserPreferences.set(LOOK_AND_FEEL, lookAndFeelEnabler.lookAndFeel().getClass().getName());
		}

		public static String getLookAndFeelPreference() {
			return UserPreferences.get(LOOK_AND_FEEL, DarkFlat.class.getName());
		}

		public void save() {
			UserPreferences.set(CONFIRM_ACTIONS, Boolean.toString(confirmActions.is()));
			UserPreferences.set(CONFIRM_EXIT, Boolean.toString(confirmExit.is()));
			logger.setLogLevel(logger.rootLogger(), logLevels.selection().item().getOrThrow());
			sdkManUi.zipExecutable = zipExecutable.get();
			sdkManUi.unzipExecutable = unzipExecutable.get();
			sdkManUi.tarExecutable = tarExecutable.get();
			sdkManUi.keepDownloadsAvailable = keepDownloadsAvailable.is();
			try {
				UserPreferences.flush();
				sdkManUi.save();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		public void revert() {
			confirmActions.set(getConfirmActionsPreference());
			confirmExit.set(getConfirmExitPreference());
			logLevels.selection().item().set((Level) logger.getLogLevel(logger.rootLogger()));
			zipExecutable.set(sdkManUi.zipExecutable);
			unzipExecutable.set(sdkManUi.unzipExecutable);
			tarExecutable.set(sdkManUi.tarExecutable);
			keepDownloadsAvailable.set(sdkManUi.keepDownloadsAvailable);
		}

		private static boolean getConfirmActionsPreference() {
			return parseBoolean(UserPreferences.get(CONFIRM_ACTIONS, TRUE.toString()));
		}

		private static boolean getConfirmExitPreference() {
			return parseBoolean(UserPreferences.get(CONFIRM_EXIT, TRUE.toString()));
		}
	}
}
// end::sdkboy_model[]