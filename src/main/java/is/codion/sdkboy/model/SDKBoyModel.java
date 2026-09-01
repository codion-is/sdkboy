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

import is.codion.common.utilities.version.Version;

// tag::sdkboy_model[]
public final class SDKBoyModel {

	public static final Version VERSION = Version.parse(SDKBoyModel.class, "/version.properties");

	private final CandidateModel candidate = new CandidateModel();
	private final VersionModel version = new VersionModel(candidate);
	private final PreferencesModel preferences = new PreferencesModel();

	public SDKBoyModel() {}

	public CandidateModel candidate() {
		return candidate;
	}

	public VersionModel version() {
		return version;
	}

	public PreferencesModel preferences() {
		return preferences;
	}
}
// end::sdkboy_model[]