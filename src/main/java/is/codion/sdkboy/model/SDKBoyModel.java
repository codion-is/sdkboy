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
package is.codion.sdkboy.model;

import is.codion.common.model.CancelException;
import is.codion.common.observable.Observer;
import is.codion.common.state.ObservableState;
import is.codion.common.state.State;
import is.codion.common.value.Value;
import is.codion.common.version.Version;
import is.codion.sdkboy.model.SDKBoyModel.CandidateModel.CandidateRow;
import is.codion.swing.common.model.component.table.FilterTableModel;
import is.codion.swing.common.model.component.table.FilterTableModel.TableColumns;
import is.codion.swing.common.model.worker.ProgressWorker.ProgressReporter;

import io.github.jagodevreede.sdkman.api.ProgressInformation;
import io.github.jagodevreede.sdkman.api.SdkManApi;
import io.github.jagodevreede.sdkman.api.domain.Candidate;
import io.github.jagodevreede.sdkman.api.domain.CandidateVersion;
import io.github.jagodevreede.sdkman.api.http.DownloadTask;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.jagodevreede.sdkman.api.SdkManApi.DEFAULT_SDKMAN_HOME;
import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;

// tag::sdkboy_model[]
public final class SDKBoyModel {

	public static final Version VERSION = Version.parse(SDKBoyModel.class, "/version.properties");

	private final VersionModel versionModel = new VersionModel();

	public VersionModel versionModel() {
		return versionModel;
	}

	public static final class CandidateModel {

		private final SdkManApi sdkMan;

		private final FilterTableModel<CandidateRow, CandidateColumn> tableModel =
						FilterTableModel.builder(new CandidateTableColumns())
										.supplier(new CandidateSupplier())
										.visible(new CandidateVisible())
										.build();
		private final Value<String> filter = Value.builder()
						.<String>nullable()
						.listener(this::onFilterChanged)
						.build();
		private final State installedOnly = State.builder()
						.listener(this::onFilterChanged)
						.build();

		private CandidateModel(SdkManApi sdkMan) {
			this.sdkMan = sdkMan;
			tableModel.sort().order(CandidateColumn.NAME).set(ASCENDING);
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

		private class CandidateSupplier implements Supplier<Collection<CandidateRow>> {

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

		private final class CandidateVisible implements Predicate<CandidateRow> {

			@Override
			public boolean test(CandidateRow candidateRow) {
				if (installedOnly.get() && candidateRow.installed() == 0) {
					return false;
				}
				if (filter.isNull()) {
					return true;
				}

				return candidateRow.candidate.name().toLowerCase().contains(filter.getOrThrow());
			}
		}
	}

	public static final class VersionModel {

		private static final int DONE = 100;

		private final SdkManApi sdkMan = new SdkManApi(DEFAULT_SDKMAN_HOME);

		private final CandidateModel candidateModel;

		private final FilterTableModel<VersionRow, VersionColumn> tableModel =
						FilterTableModel.builder(new VersionTableColumns())
										.supplier(new VersionSupplier())
										.visible(new VersionVisible())
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
			this.candidateModel = new CandidateModel(sdkMan);
			this.candidateModel.tableModel.selection().item().addListener(this::onCandidateSelected);
			tableModel.selection().item().addConsumer(this::onVersionSelected);
			tableModel.sort().order(VersionColumn.VENDOR).set(ASCENDING);
			tableModel.sort().order(VersionColumn.VERSION).add(DESCENDING);
		}

		public FilterTableModel<VersionRow, VersionColumn> tableModel() {
			return tableModel;
		}

		public CandidateModel candidateModel() {
			return candidateModel;
		}

		public ObservableState selectedInstalled() {
			return selectedInstalled.observable();
		}

		public State selectedVersionUsed() {
			return selectedUsed;
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

		public VersionRow selectedVersion() {
			return tableModel.selection().item().getOrThrow();
		}

		public void refresh() {
			tableModel.items().refresh();
		}

		public void refreshCandidates() {
			VersionRow selected = selectedVersion();
			candidateModel.tableModel.items().refresh(candidates ->
							tableModel.items().refresh(versions ->
											tableModel.selection().item().set(selected)));
		}

		public void install(ProgressReporter<String> progress, State downloading, Observer<?> cancel) {
			VersionRow versionRow = selectedVersion();
			if (versionRow.version.available()) {
				progress.report(DONE);
			}
			else {
				download(versionRow, progress, downloading, cancel);
			}
			progress.publish("Installing");
			sdkMan.install(versionRow.candidate.id(), versionRow.version.identifier());
			progress.publish("Done");
		}

		public void uninstall() {
			VersionRow versionRow = selectedVersion();
			sdkMan.uninstall(versionRow.candidate.id(), versionRow.version.identifier());
		}

		public void use() {
			VersionRow versionRow = selectedVersion();
			try {
				sdkMan.changeGlobal(versionRow.candidate.id(), versionRow.version.identifier());
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
			if (!filter.isNull() || tableModel.selection().empty().get()) {
				tableModel.selection().indexes().clear();
				tableModel.selection().indexes().increment();
			}
		}

		private void onCandidateSelected() {
			tableModel.items().refresh(_ -> {
				if (tableModel.selection().empty().get()) {
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
		 * Sort "normal" version strings correctly, that is,
		 * ones using the major.minor.patch-metadata format.
		 * For other formats, textual sorting is used.
		 * @param version the standard Version, if downloaded
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

		private class VersionSupplier implements Supplier<Collection<VersionRow>> {

			@Override
			public Collection<VersionRow> get() {
				CandidateRow candidateRow = candidateModel.tableModel.selection().item().get();
				if (candidateRow == null) {
					return List.of();
				}
				try {
					String inUse = sdkMan.resolveCurrentVersion(candidateRow.candidate().id());

					return sdkMan.getVersions(candidateRow.candidate().id()).stream()
									.map(version -> createRow(candidateRow, version, inUse))
									.toList();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			private static VersionRow createRow(CandidateRow candidateRow, CandidateVersion version, String inUse) {
				return new VersionRow(candidateRow.candidate(), version,
								VersionInfo.of(version.version()), version.identifier().equals(inUse));
			}
		}

		private final class VersionVisible implements Predicate<VersionRow> {

			@Override
			public boolean test(VersionRow versionRow) {
				CandidateVersion candidateVersion = versionRow.version;
				if (installedOnly.get() && !candidateVersion.installed()) {
					return false;
				}
				if (downloadedOnly.get() && !candidateVersion.available()) {
					return false;
				}
				if (usedOnly.get() && !versionRow.used) {
					return false;
				}
				if (filter.isNull()) {
					return true;
				}

				Stream<String> strings = Stream.of(filter.getOrThrow().toLowerCase().split(" "))
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
				if (downloading.get()) {
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
}
// end::sdkboy_model[]