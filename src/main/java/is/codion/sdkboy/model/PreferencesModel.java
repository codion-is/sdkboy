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

import is.codion.common.model.preferences.UserPreferences;
import is.codion.common.reactive.state.State;
import is.codion.common.reactive.value.Value;
import is.codion.common.utilities.exceptions.Exceptions;
import is.codion.common.utilities.logging.LoggerProxy;
import is.codion.plugin.flatlaf.intellij.themes.darkflat.DarkFlat;
import is.codion.swing.common.model.component.combobox.FilterComboBoxModel;

import ch.qos.logback.classic.Level;
import io.github.jagodevreede.sdkman.api.SdkManUiPreferences;

import java.io.File;
import java.util.Optional;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;

// tag::preferences_model[]
public final class PreferencesModel {

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

	PreferencesModel() {}

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

	public void setLookAndFeelPreference(String lookAndFeelClassName) {
		UserPreferences.put(LOOK_AND_FEEL, lookAndFeelClassName);
	}

	public static String getLookAndFeelPreference() {
		return UserPreferences.get(LOOK_AND_FEEL, DarkFlat.class.getName());
	}

	public void save() {
		UserPreferences.put(CONFIRM_ACTIONS, Boolean.toString(confirmActions.is()));
		UserPreferences.put(CONFIRM_EXIT, Boolean.toString(confirmExit.is()));
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
			throw Exceptions.runtime(e);
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
// end::preferences_model[]