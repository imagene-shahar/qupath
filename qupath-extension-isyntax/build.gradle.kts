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

fun Exec.configureCMakeCommandsFor(os: String) {
    workingDir(nativeBuildDir)
    doFirst { nativeBuildDir.mkdirs() }
    val src = libisyntaxSrcDir.absolutePath
    val build = nativeBuildDir.absolutePath
    when (os) {
        "linux", "mac" -> {
            // Remove -march=native to avoid runtime issues; build shared lib
            commandLine("bash", "-lc", "sed -i 's/-march=native//g' $src/CMakeLists.txt; cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON && cmake --build $build --target isyntax --config Release -j")
        }
        "windows" -> {
            commandLine("cmd", "/c", "cmake -S $src -B $build -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON && cmake --build $build --target isyntax --config Release")
        }
    }
}

val buildLibisyntax by tasks.registering(Exec::class) {
    onlyIf { libisyntaxSrcDir.exists() }
    val os = when {
        org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS) -> "windows"
        org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_MAC) -> "mac"
        else -> "linux"
    }
    configureCMakeCommandsFor(os)
}

val packageNative by tasks.registering(Copy::class) {
    dependsOn(buildLibisyntax)
    from({
        val candidates = listOf(
            file("${nativeBuildDir.absolutePath}/libisyntax.so"),
            file("${nativeBuildDir.absolutePath}/libisyntax.dylib"),
            file("${nativeBuildDir.absolutePath}/isyntax.dll")
        )
        candidates.filter { it.exists() }
    })
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