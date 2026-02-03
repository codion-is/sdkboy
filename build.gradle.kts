import org.gradle.internal.os.OperatingSystem

plugins {
    // The Badass Jlink Plugin provides jlink and jpackage
    // functionality and applies the java application plugin
    // https://badass-jlink-plugin.beryx.org
    id("org.beryx.jlink") version "3.1.2"
    // Just for managing the license headers
    id("com.diffplug.spotless") version "8.2.1"
    // For the asciidoc docs
    id("org.asciidoctor.jvm.convert") version "4.0.4"
    // For GitHub Releases
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

dependencies {
    // Import the Codion Common BOM for dependency version management
    implementation(platform(libs.codion.common.bom))
    
    // The Codion Swing Common UI module
    implementation(libs.codion.swing.common.ui)
    // Include all the standard Flat Look and Feels
    implementation(libs.codion.plugin.flatlaf.themes)
    // and a bunch of IntelliJ theme based ones
    implementation(libs.codion.plugin.flatlaf.intellij.themes)
    // The Codion logback plugin so we can configure
    // the log level and open the log file/dir
    implementation(libs.codion.plugin.logback)
    // logback implementation for the log level
    implementation(libs.logback)

    // SdkManApi + dependencies
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("sdkman-api-0.3.2-SNAPSHOT.jar"))))
    implementation(libs.commons.compress)
    implementation(libs.jna.platform)
}

version = "1.1.3"

java {
    toolchain {
        // Use the latest possible Java version
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ORACLE)
    }
}

spotless {
    // Just the license headers
    java {
        licenseHeaderFile("${rootDir}/license_header").yearSeparator(" - ")
    }
    format("javaMisc") {
        target("src/**/package-info.java", "src/**/module-info.java")
        licenseHeaderFile("${rootDir}/license_header", "\\/\\*\\*").yearSeparator(" - ")
    }
}

// Configure the application plugin, the jlink plugin relies
// on this configuration when building the runtime image
application {
    mainModule = "is.codion.sdkboy"
    mainClass = "is.codion.sdkboy.ui.SDKBoyPanel"
    applicationDefaultJvmArgs = listOf(
        // This app doesn't require a lot of memory
        "-Xmx32m"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.isDeprecation = true
}

// Configure the docs generation
tasks.asciidoctor {
    dependsOn(tasks.build)
    inputs.files(sourceSets.main.get().allSource)
    inputs.file(project.buildFile)

    baseDirFollowsSourceFile()

    attributes(
        mapOf(
            "source-highlighter" to "prettify",
            "tabsize" to "2"
        )
    )
    asciidoctorj {
        setVersion("2.5.13")
    }
}

// Create a version.properties file containing the application version
tasks.register<WriteProperties>("writeVersion") {
    destinationFile = file("${temporaryDir.absolutePath}/version.properties")
    property("version", "${project.version}")
}

// Include the version.properties file from above in the
// application resources, see usage in SDKBoyModel
tasks.processResources {
    from(tasks.named("writeVersion"))
}

// Configure the Jlink plugin
jlink {
    // Specify the jlink image name
    imageName = project.name + "-" + project.version + "-" +
            OperatingSystem.current().familyName.replace(" ", "").lowercase()
    // The options for the jlink task
    options = listOf(
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        // Add the logback plugin module
        "--add-modules",
        "is.codion.plugin.logback.proxy"
    )

    jpackage {
        if (OperatingSystem.current().isLinux) {
            icon = "src/main/icons/sdkboy.png"
            installerType = "deb"
            installerOptions = listOf(
                "--linux-shortcut"
            )
        }
        if (OperatingSystem.current().isWindows) {
            icon = "src/main/icons/sdkboy.ico"
            installerType = "msi"
            installerOptions = listOf(
                "--win-menu",
                "--win-shortcut"
            )
        }
        if (OperatingSystem.current().isMacOsX) {
            icon = "src/main/icons/sdkboy.icns"
            installerType = "dmg"
        }
    }
}

if (properties.containsKey("githubAccessToken")) {
    githubRelease {
        token(properties["githubAccessToken"] as String)
        owner = "codion-is"
        allowUploadToExisting = true
        releaseAssets.from(tasks.named("jlinkZip").get().outputs.files)
        releaseAssets.from(fileTree(tasks.named("jpackage").get().outputs.files.singleFile) {
            exclude(project.name + "/**", project.name + ".app/**")
        })
    }
}

tasks.named("githubRelease") {
    dependsOn(tasks.named("jlinkZip"))
    dependsOn(tasks.named("jpackage"))
}