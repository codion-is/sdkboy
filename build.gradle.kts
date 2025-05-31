import org.gradle.internal.os.OperatingSystem

plugins {
    // The Badass Jlink Plugin provides jlink and jpackage
    // functionality and applies the java application plugin
    // https://badass-jlink-plugin.beryx.org
    id("org.beryx.jlink") version "3.1.1"
    // Just for managing the license headers
    id("com.diffplug.spotless") version "7.0.1"
    // For the asciidoctor docs
    id("org.asciidoctor.jvm.convert") version "4.0.4"
    // For Github Releases
    id("com.github.breadmoirai.github-release") version "2.5.2"
    // GraalVM Native Image support
    id("org.graalvm.buildtools.native") version "0.10.4"
}

dependencies {
    // The Codion Swing Common UI module
    implementation(libs.codion.swing.common.ui)
    // Include all the standard Flat Look and Feels
    implementation(libs.codion.plugin.flatlaf)
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

version = "1.0.1"

java {
    toolchain {
        // Use GraalVM 24 for native image support
        languageVersion.set(JavaLanguageVersion.of(24))
        // Request GraalVM for native image support
        vendor.set(JvmVendorSpec.GRAAL_VM)
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
    inputs.dir("src")
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
// application resources, see usage in TemplateAppModel
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

// Task to create a zip of the native image
tasks.register<Zip>("nativeImageZip") {
    group = "distribution"
    description = "Creates a zip of the native image"
    
    // Set the archive name with -native suffix
    archiveFileName.set(project.name + "-" + project.version + "-" + 
            OperatingSystem.current().familyName.replace(" ", "").lowercase() + "-native.zip")
    destinationDirectory.set(layout.buildDirectory)
    
    // Include the native executable
    from(layout.buildDirectory.dir("native/nativeCompile")) {
        include("sdkboy")
        // Make the binary executable in the zip
        filePermissions {
            unix("rwxr-xr-x")
        }
    }

    dependsOn(tasks.named("nativeCompile"))
}

if (properties.containsKey("githubAccessToken")) {
    githubRelease {
        token(properties["githubAccessToken"] as String)
        owner = "codion-is"
        allowUploadToExisting = true
        // Only add the native image zip to release assets
        releaseAssets.from(tasks.named("nativeImageZip").get().outputs.files)
    }
}

tasks.named("githubRelease") {
    dependsOn(tasks.named("nativeImageZip"))
}

tasks.register<Sync>("copyToGitHubPages") {
    group = "documentation"
    description = "Copies the documentation to the Codion github pages repository, nevermind"
    from(tasks.asciidoctor)
    into(
        "../codion-pages/doc/" + libs.versions.codion.get()
            .replace("-SNAPSHOT", "") + "/tutorials/sdkboy"
    )
}

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        named("main") {
            imageName = project.name
            mainClass = "is.codion.sdkboy.NativeMain"
            verbose = true
            fallback = false
            
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-Djava.awt.headless=false")
            buildArgs.add("-Djava.home=${System.getProperty("java.home")}")
            buildArgs.add("--initialize-at-run-time=sun.awt,com.sun.jna,sun.java2d,sun.font,java.awt.Toolkit,sun.awt.AWTAccessor")
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("-H:+IncludeAllLocales")
            
            // Enable AWT/Swing support
            buildArgs.add("-H:+EnableAllSecurityServices")
            
            // JNI support for native libraries
            buildArgs.add("-H:+JNI")
            buildArgs.add("-H:+ForeignAPISupport")
            buildArgs.add("-H:ConfigurationFileDirectories=${projectDir}/src/main/resources/META-INF/native-image/is.codion.sdkboy")
            
            // Resource configuration
            buildArgs.add("-H:IncludeResources=.*\\.(properties|xml|png|ico|icns)$")
            buildArgs.add("-H:IncludeResources=logback.xml")
            buildArgs.add("-H:IncludeResources=version.properties")
            
            // Module support
            buildArgs.add("--add-modules=ALL-MODULE-PATH")
            
            // Memory settings for build
            jvmArgs.add("-Xmx7G")
        }
    }
    
    agent {
        defaultMode = "standard"
        builtinCallerFilter = true
        builtinHeuristicFilter = true
        enableExperimentalPredefinedClasses = true
        trackReflectionMetadata = true
    }
}

// Task to run the application with the GraalVM agent to collect metadata
tasks.register<JavaExec>("runWithAgent") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "is.codion.sdkboy.ui.SDKBoyPanel"
    jvmArgs = listOf(
        "-agentlib:native-image-agent=config-merge-dir=${projectDir}/src/main/resources/META-INF/native-image/is.codion.sdkboy",
        "-Djava.awt.headless=false"
    )
    doFirst {
        println("Running with GraalVM agent to collect metadata...")
        println("IMPORTANT: Interact with as many UI components as possible!")
        println("Config will be written to: ${projectDir}/src/main/resources/META-INF/native-image/is.codion.sdkboy")
    }
}