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
import is.codion.common.model.component.table.FilterTableModel.TableColumns;
import is.codion.common.model.worker.ProgressWorker.ProgressReporter;
import is.codion.common.reactive.observer.Observable;
import is.codion.common.reactive.observer.Observer;
import is.codion.common.reactive.state.ObservableState;
import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.common.utilities.exceptions.Exceptions;
import is.codion.common.utilities.version.Version;
import is.codion.sdkboy.model.CandidateModel.CandidateRow;
import is.codion.swing.common.model.component.table.SwingFilterTableModel;

import io.github.jagodevreede.sdkman.api.ProgressInformation;
import io.github.jagodevreede.sdkman.api.SdkManApi;
import io.github.jagodevreede.sdkman.api.domain.Candidate;
import io.github.jagodevreede.sdkman.api.domain.CandidateVersion;
import io.github.jagodevreede.sdkman.api.http.DownloadTask;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static is.codion.common.model.filter.SortOrder.ASCENDING;
import static is.codion.common.model.filter.SortOrder.DESCENDING;

// tag::version_model[]
public final class VersionModel {

	private static final int DONE = 100;

	private final SdkManApi sdkMan;
	private final Observable<CandidateRow> selectedCandidate;
	private final SwingFilterTableModel<VersionRow, VersionColumn> tableModel =
					SwingFilterTableModel.builder()
									.columns(new VersionColumns())
									.items(new VersionItems())
									.included(new VersionIncluded())
									.onItemSelected(this::onVersionSelected)
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

	VersionModel(CandidateModel candidateModel) {
		sdkMan = candidateModel.sdkMan();
		selectedCandidate = candidateModel.tableModel().selection().item().observable();
		selectedCandidate.addListener(this::onCandidateSelected);
		tableModel.sort().order(VersionColumn.VENDOR).set(ASCENDING);
		tableModel.sort().order(VersionColumn.VERSION).add(DESCENDING);
	}

	public SwingFilterTableModel<VersionRow, VersionColumn> tableModel() {
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
			throw Exceptions.runtime(e);
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
			if (version != null) {
				return -1;
			}
			if (versionInfo.version != null) {
				return 1;
			}

			return versionName.compareTo(versionInfo.versionName);
		}
	}

	private static final class VersionColumns implements TableColumns<VersionRow, VersionColumn> {

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
				case USED -> row.used();
			};
		}
	}

	private class VersionItems implements Supplier<Collection<VersionRow>> {

		@Override
		public Collection<VersionRow> get() {
			return selectedCandidate.optional()
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
				throw Exceptions.runtime(e);
			}
		}
	}

	private final class VersionIncluded implements Predicate<VersionRow> {

		@Override
		public boolean test(VersionRow versionRow) {
			CandidateVersion version = versionRow.version;
			if (installedOnly.is() && !version.installed()) {
				return false;
			}
			if (downloadedOnly.is() && !version.available()) {
				return false;
			}
			if (usedOnly.is() && !versionRow.used) {
				return false;
			}
			if (filter.isNull()) {
				return true;
			}

			Stream<String> strings = Stream.of(filter.getOrThrow().split(" "))
							.map(String::trim)
							.filter(s -> !s.isEmpty());
			String versionString = version.version().toLowerCase();
			if (version.vendor() == null) {
				return strings.allMatch(versionString::contains);
			}
			String vendor = version.vendor().toLowerCase();

			return strings.allMatch(filter -> versionString.contains(filter) || vendor.contains(filter));
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
// end::version_model[]