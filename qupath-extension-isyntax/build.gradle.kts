import io.github.qupath.gradle.Utils

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

// Auto-fetch libisyntax sources if missing
val prepareLibisyntaxSources by tasks.registering(Exec::class) {
    workingDir(project.rootDir)
    isIgnoreExitValue = false
    onlyIf { !libisyntaxSrcDir.exists() }
    val cloneCmd = listOf("git", "clone", "--depth", "1", "https://github.com/amspath/libisyntax", libisyntaxSrcDir.absolutePath)
    if (org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)) {
        commandLine(listOf("cmd", "/c") + cloneCmd)
    } else {
        commandLine(cloneCmd)
    }
}

fun Exec.configureCMakeCommandsFor(os: String) {
    workingDir(nativeBuildDir)
    doFirst { nativeBuildDir.mkdirs() }
    val src = libisyntaxSrcDir.absolutePath
    val build = nativeBuildDir.absolutePath
    when (os) {
        "linux" -> {
            // Build shared lib; remove -march=native if present (portable sed; no-op if file missing)
            commandLine(
                "bash", "-lc",
                "if [ -f \"$src/CMakeLists.txt\" ]; then sed -i 's/-march=native//g' \"$src/CMakeLists.txt\"; fi; " +
                    "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON && cmake --build $build --target isyntax --config Release -j"
            )
        }
        "mac" -> {
            // BSD sed requires empty extension after -i
            commandLine(
                "bash", "-lc",
                "if [ -f \"$src/CMakeLists.txt\" ]; then sed -i '' -e 's/-march=native//g' \"$src/CMakeLists.txt\"; fi; " +
                    "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON -DCMAKE_OSX_ARCHITECTURES=arm64 && cmake --build $build --target isyntax --config Release -j"
            )
        }
        else -> { // windows
            commandLine("cmd", "/c", "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON && cmake --build $build --target isyntax --config Release")
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
            include("**/libisyntax.so", "**/libisyntax.dylib", "**/isyntax.dll")
        }
    )
    into("build/packaged-natives/natives/${platform.classifier}")
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