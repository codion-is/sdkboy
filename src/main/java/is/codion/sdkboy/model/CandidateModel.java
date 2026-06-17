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

import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.common.utilities.exceptions.Exceptions;
import is.codion.swing.common.model.component.table.FilterTableModel;
import is.codion.swing.common.model.component.table.FilterTableModel.TableColumns;

import io.github.jagodevreede.sdkman.api.SdkManApi;
import io.github.jagodevreede.sdkman.api.domain.Candidate;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.github.jagodevreede.sdkman.api.SdkManApi.DEFAULT_SDKMAN_HOME;
import static javax.swing.SortOrder.ASCENDING;

// tag::candidate_model[]
public final class CandidateModel {

	private final SdkManApi sdkMan = new SdkManApi(DEFAULT_SDKMAN_HOME);
	private final FilterTableModel<CandidateRow, CandidateColumn> tableModel =
					FilterTableModel.builder()
									.columns(new CandidateColumns())
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

	CandidateModel() {
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

	public SdkManApi sdkMan() {
		return sdkMan;
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

	private static final class CandidateColumns implements TableColumns<CandidateRow, CandidateColumn> {

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
				case INSTALLED -> row.installed() == 0 ? null : row.installed();
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
				throw Exceptions.runtime(e);
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
// end::candidate_model[]