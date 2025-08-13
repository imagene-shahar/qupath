import io.github.qupath.gradle.Utils
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File

plugins {
  id("qupath.common-conventions")
  id("qupath.javafx-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.isyntax"
base {
    archivesName = "qupath-extension-isyntax"
    description = "QuPath extension to support image reading using libisyntax."
}

dependencies {
    implementation(project(":qupath-core"))
    implementation(project(":qupath-gui-fx"))
    implementation(libs.jna)
    implementation(libs.qupath.fxtras)
}

val platform = Utils.currentPlatform()
val nativesClassifier = when (platform.classifier) {
    "darwin-x86_64" -> "macos-x86_64"
    "darwin-aarch64" -> "macos-aarch64"
    "win32-x86_64" -> "windows-x86_64"
    else -> platform.classifier
}

sourceSets {
    main {
        resources {
            // Include packaged natives and resources into the jar (for runtime loading)
            srcDir("build/packaged-natives")
            srcDir("src/main/resources")
            include("**/*")
        }
    }
}

tasks.jar {
    dependsOn(tasks.processResources)
}

val nativeBuildDir = layout.buildDirectory.dir("libisyntax-build").get().asFile
val libisyntaxSrcDir = file("${project.rootDir}/third_party/libisyntax")

// Portable prepare: try git clone, else download zip and extract
val prepareLibisyntaxSources by tasks.registering {
    outputs.dir(libisyntaxSrcDir)
    doLast {
        if (File(libisyntaxSrcDir, "CMakeLists.txt").exists()) return@doLast
        libisyntaxSrcDir.parentFile.mkdirs()
        // Try git clone first
        try {
            project.exec {
                workingDir(project.rootDir)
                commandLine("git", "clone", "--depth", "1", "https://github.com/imagene-shahar/libisyntax", libisyntaxSrcDir.absolutePath)
            }
        } catch (e: Exception) {
            logger.warn("git clone libisyntax failed, falling back to zip download: ${e.message}")
            val tmpZip = layout.buildDirectory.file("libisyntax.zip").get().asFile
            fun tryDownload(branch: String): Boolean {
                return try {
                    val url = URL("https://github.com/imagene-shahar/libisyntax/archive/refs/heads/${branch}.zip")
                    url.openStream().use { input ->
                        Files.copy(input, tmpZip.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    // Extract
                    val extractDir = layout.buildDirectory.dir("libisyntax-src").get().asFile
                    copy {
                        from(zipTree(tmpZip))
                        into(extractDir)
                    }
                    // Move extracted folder (libisyntax-<branch>) to third_party/libisyntax
                    val extracted = extractDir.listFiles()?.firstOrNull { it.isDirectory } ?: throw RuntimeException("libisyntax archive extraction failed")
                    libisyntaxSrcDir.mkdirs()
                    extracted.listFiles()?.forEach { child ->
                        val dest = libisyntaxSrcDir.resolve(child.name)
                        child.copyRecursively(target = dest, overwrite = true)
                    }
                    true
                } catch (ex: Exception) {
                    logger.warn("Download/extract for branch ${branch} failed: ${ex.message}")
                    false
                } finally {
                    runCatching { tmpZip.delete() }
                }
            }
            // Try known default branches
            if (!tryDownload("main") && !tryDownload("master")) {
                // Fallback to a fixed tag to avoid branch name changes breaking CI
                if (!tryDownload("v0.3.0")) {
                    throw RuntimeException("Unable to fetch libisyntax sources via git or zip on main/master/v0.3.0")
                }
            }
        }
    }
}

fun Exec.configureCMakeCommandsFor(os: String) {
    workingDir(nativeBuildDir)
    doFirst { nativeBuildDir.mkdirs() }
    val src = libisyntaxSrcDir.absolutePath
    val build = nativeBuildDir.absolutePath
    when (os) {
        "linux" -> {
            commandLine(
                "bash", "-lc",
                "set -euo pipefail; " +
                    "if [ -f \"$src/CMakeLists.txt\" ]; then sed -i 's/-march=native//g' \"$src/CMakeLists.txt\"; fi; " +
                    "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release -DCMAKE_VERBOSE_MAKEFILE=ON && " +
                    "cmake --build $build --target isyntax --config Release -j"
            )
        }
        "mac" -> {
            commandLine(
                "bash", "-lc",
                "set -euo pipefail; " +
                    "if [ -f \"$src/CMakeLists.txt\" ]; then sed -i '' -e 's/-march=native//g' \"$src/CMakeLists.txt\"; fi; " +
                    "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release -DCMAKE_VERBOSE_MAKEFILE=ON -DCMAKE_OSX_ARCHITECTURES=arm64 && " +
                    "cmake --build $build --target isyntax --config Release -j"
            )
        }
        else -> { // windows
            commandLine(
                "cmd", "/c",
                // Ensure 64-bit build and statically link MSVC runtime to avoid missing dependency issues on target machines
                "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release -A x64 -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded && " +
                    "cmake --build $build --target isyntax --config Release"
            )
        }
    }
}

val buildLibisyntax by tasks.registering(Exec::class) {
    dependsOn(prepareLibisyntaxSources)
    val os = when {
        org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS) -> "windows"
        org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_MAC) -> "mac"
        else -> "linux"
    }
    configureCMakeCommandsFor(os)
}

val packageNative by tasks.registering(Copy::class) {
    dependsOn(buildLibisyntax)
    from(
        fileTree(nativeBuildDir) {
            include("**/libisyntax.so", "**/libisyntax.dylib", "**/isyntax.dll", "**/libisyntax.dll")
        }
    )
    // Also pick up any manually-built natives inside third_party/libisyntax (e.g. built outside Gradle)
    from(
        fileTree(libisyntaxSrcDir) {
            include("**/libisyntax.so", "**/libisyntax.dylib", "**/isyntax.dll", "**/libisyntax.dll")
        }
    )
    into("build/packaged-natives/natives/${nativesClassifier}")
}

tasks.processResources {
    dependsOn(packageNative)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Ensure sourcesJar depends on native packaging but does not include the binaries
tasks.named<Jar>("sourcesJar") {
    dependsOn(packageNative)
    exclude("natives/**")
}