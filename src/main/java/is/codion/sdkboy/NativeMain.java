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
 * Copyright (c) 2024, Björn Darri Sigurðsson.
 */
package is.codion.sdkboy;

import is.codion.sdkboy.ui.SDKBoyPanel;

/**
 * Main class for GraalVM native image that sets required system properties.
 */
public class NativeMain {
	
	public static void main(String[] args) {
		// Set required system properties for AWT/Swing
		System.setProperty("java.home", System.getenv("JAVA_HOME") != null ? 
				System.getenv("JAVA_HOME") : "/usr/lib/jvm/java-21-openjdk-amd64");
		System.setProperty("java.awt.headless", "false");
		System.setProperty("sun.java2d.fontpath", "/usr/share/fonts");
		
		// Call the original main method
		SDKBoyPanel.main(args);
	}
}