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

import is.codion.common.model.selection.MultiSelection.Indexes;
import is.codion.common.reactive.state.ObservableState;
import is.codion.common.reactive.value.Value;
import is.codion.sdkboy.model.CandidateModel;
import is.codion.sdkboy.model.CandidateModel.CandidateColumn;
import is.codion.sdkboy.model.CandidateModel.CandidateRow;
import is.codion.sdkboy.model.SDKBoyModel;
import is.codion.swing.common.ui.ancestor.Ancestor;
import is.codion.swing.common.ui.component.table.FilterTable;
import is.codion.swing.common.ui.component.table.FilterTableColumn;
import is.codion.swing.common.ui.control.Control;
import is.codion.swing.common.ui.dialog.Dialogs;
import is.codion.swing.common.ui.key.KeyEvents;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import static is.codion.common.reactive.state.State.and;
import static is.codion.swing.common.ui.border.Borders.emptyBorder;
import static is.codion.swing.common.ui.component.Components.*;
import static is.codion.swing.common.ui.control.Control.command;
import static is.codion.swing.common.ui.layout.Layouts.borderLayout;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.event.KeyEvent.*;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

// tag::candidate_panel[]
final class CandidatePanel extends JPanel {

	private final CandidateModel candidate;
	private final FilterTable<CandidateRow, CandidateColumn> table;
	private final JTextField filter;
	private final JCheckBox installedOnly;
	private final CandidateControls controls;

	CandidatePanel(CandidateModel model, ObservableState installing, ObservableState refreshing) {
		super(borderLayout());
		candidate = model;
		controls = new CandidateControls();
		table = FilterTable.builder()
						.model(candidate.tableModel())
						.columns(this::configureColumns)
						.sortable(false)
						.focusable(false)
						.selectionMode(SINGLE_SELECTION)
						.autoResizeMode(AUTO_RESIZE_ALL_COLUMNS)
						.columnReordering(false)
						.rowsFillViewport(true)
						.enabled(and(installing.not(), refreshing.not()))
						.cellRenderer(CandidateColumn.INSTALLED, Integer.class, renderer -> renderer
										.horizontalAlignment(SwingConstants.CENTER))
						.build();
		filter = createFilterField(candidate.filter(), table, installing);
		installedOnly = checkBox()
						.link(candidate.installedOnly())
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

	CandidateControls controls() {
		return controls;
	}

	private void configureColumns(FilterTableColumn.Builder<CandidateColumn> column) {
		if (column.identifier() == CandidateColumn.INSTALLED) {
			column.fixedWidth(80);
		}
	}

	private void refresh() {
		candidate.tableModel().items().refresh();
	}

	private void displayDescription() {
		table.model().selection().item().optional()
						.ifPresent(candidateRow -> Dialogs.builder()
										.component(textArea()
														.value(candidateRow.candidate().description())
														.rowsColumns(8, 40)
														.editable(false)
														.lineWrap(true)
														.wrapStyleWord(true)
														.scrollPane())
										.owner(CandidatePanel.this)
										.title(candidateRow.candidate().name() + " - Description")
										.show());
	}

	static JTextField createFilterField(Value<String> filter, FilterTable<?, ?> table, ObservableState installing) {
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
			table.model().selection().index().update(index ->
							Math.min((index == -1 ? 0 : index) + visibleRowCount - 1, table.model().items().included().size() - 1));
		});
	}

	private static Control pageUpControl(FilterTable<?, ?> table) {
		return command(() -> {
			int visibleRowCount = Ancestor.ofType(JScrollPane.class).of(table).get().getViewport().getHeight() / table.getRowHeight();
			table.model().selection().index().update(index ->
							Math.max((index == -1 ? 0 : index) - visibleRowCount + 1, 0));
		});
	}

	final class CandidateControls {

		private final Control refresh;
		private final Control description;

		private CandidateControls() {
			refresh = command(CandidatePanel.this::refresh);
			description = command(CandidatePanel.this::displayDescription);
		}

		Control refresh() {
			return refresh;
		}

		Control description() {
			return description;
		}
	}
}
// end::candidate_panel[]