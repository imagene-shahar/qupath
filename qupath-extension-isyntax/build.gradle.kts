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

// Package platform-native libisyntax if available under build resources
val platform = Utils.currentPlatform()

sourceSets {
    main {
        resources {
            // Expect native libraries to be copied into build/resources/main/natives/<platform.classifier>/
            srcDir("build/packaged-natives")
        }
    }
}

// Optional: build libisyntax for Linux using CMake if available
val nativeBuildDir = layout.buildDirectory.dir("libisyntax-build").get().asFile
val libisyntaxSrcDir = file("${project.rootDir}/third_party/libisyntax")

val buildLibisyntax by tasks.registering(Exec::class) {
    onlyIf { libisyntaxSrcDir.exists() && org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_UNIX) }
    workingDir(nativeBuildDir)
    doFirst { nativeBuildDir.mkdirs() }
    // Configure to build only the library; examples can fail due to pedantic warnings
    commandLine("bash", "-lc", "cmake -S ${libisyntaxSrcDir.absolutePath} -B ${nativeBuildDir.absolutePath} -DBUILD_TESTING=OFF -DBUILD_SHARED_LIBS=ON && cmake --build ${nativeBuildDir.absolutePath} --target isyntax --config Release -j")
}

val packageNative by tasks.registering(Copy::class) {
    dependsOn(buildLibisyntax)
    from({
        val so = file("${nativeBuildDir.absolutePath}/libisyntax.so")
        if (so.exists()) so else emptyList<File>()
    })
    into("build/packaged-natives/natives/${platform.classifier}")
}

tasks.processResources {
    dependsOn(packageNative)
}

// Ensure sourcesJar depends on native packaging but does not include the binaries
tasks.named<Jar>("sourcesJar") {
    dependsOn(packageNative)
    exclude("natives/**")
}