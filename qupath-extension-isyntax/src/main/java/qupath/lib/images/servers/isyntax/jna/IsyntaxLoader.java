/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers
 * %%
 * GPLv3
 * #L%
 */
package qupath.lib.images.servers.isyntax.jna;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class IsyntaxLoader {

    private static final Logger logger = LoggerFactory.getLogger(IsyntaxLoader.class);

    private static IsyntaxJNA INSTANCE;

    public static synchronized boolean tryToLoadQuietly(String... searchPath) {
        try {
            return tryToLoad(searchPath);
        } catch (Throwable t) {
            logger.debug("Unable to load libisyntax", t);
            return false;
        }
    }

    public static synchronized boolean tryToLoad(String... searchPath) {
        if (INSTANCE != null) return true;
        INSTANCE = tryToLoadJnaInstance(searchPath);
        if (INSTANCE != null) {
            try { INSTANCE.libisyntax_init(); } catch (Throwable t) { logger.debug("libisyntax_init failed", t); }
        }
        return INSTANCE != null;
    }

    private static IsyntaxJNA loadFromFilePath(File libFile) {
        try {
            // Ensure parent dir is on search path for dependent DLLs
            if (libFile != null && libFile.getParentFile() != null) {
                String dir = libFile.getParentFile().getAbsolutePath();
                try { NativeLibrary.addSearchPath("isyntax", dir); } catch (Throwable ignored) {}
                try { NativeLibrary.addSearchPath("libisyntax", dir); } catch (Throwable ignored) {}
                // On Windows, add to PATH for transitive dependencies
                if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                    String path = System.getenv("PATH");
                    if (path != null && !path.contains(dir)) {
                        try {
                            System.setProperty("jna.tmpdir", dir);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            return Native.load(libFile.getAbsolutePath(), IsyntaxJNA.class);
        } catch (Throwable t) {
            logger.debug("Failed loading libisyntax from {}", libFile, t);
            return null;
        }
    }
    private static IsyntaxJNA tryToLoadJnaInstance(String... searchPath) {
        // 1) try packaged resource under natives/<platform>/
        try {
            File extracted = extractPackagedNative();
            if (extracted != null && extracted.exists()) {
                logger.debug("Loading libisyntax from packaged native {}", extracted);
                IsyntaxJNA instance = loadFromFilePath(extracted);
                if (instance != null) return instance;
            }
        } catch (Throwable t) {
            logger.debug("Failed to load packaged libisyntax", t);
        }

        // 2) try explicit files within provided search directories
        try {
            File candidate = findFromSearchPaths(searchPath);
            if (candidate != null && candidate.exists()) {
                logger.debug("Loading libisyntax from explicit path {}", candidate);
                IsyntaxJNA instance = loadFromFilePath(candidate);
                if (instance != null) return instance;
            }
        } catch (Throwable t) {
            logger.debug("Failed to load libisyntax from explicit search paths", t);
        }

        // 3) use jna.library.path if provided, and add explicit search paths
        String jnaPath = System.getProperty("jna.library.path", null);
        try {
            if (searchPath != null && searchPath.length > 0) {
                String path = Arrays.stream(searchPath)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(File.pathSeparator));
                if (!path.isBlank()) System.setProperty("jna.library.path", path);
                // Help JNA by adding search paths for both potential base names
                for (String p : searchPath) {
                    if (p == null || p.isBlank()) continue;
                    try { NativeLibrary.addSearchPath("isyntax", p); } catch (Throwable ignored) {}
                    try { NativeLibrary.addSearchPath("libisyntax", p); } catch (Throwable ignored) {}
                }
            }
            try {
                return Native.load("isyntax", IsyntaxJNA.class);
            } catch (UnsatisfiedLinkError e1) {
                logger.debug("Native.load('isyntax') failed; trying 'libisyntax'", e1);
                return Native.load("libisyntax", IsyntaxJNA.class);
            }
        } finally {
            if (jnaPath == null) System.clearProperty("jna.library.path");
            else System.setProperty("jna.library.path", jnaPath);
        }
    }

    private static File extractPackagedNative() throws IOException {
        List<String> platforms = detectPlatformClassifiers();
        for (String platform : platforms) {
            List<String> libNames = new ArrayList<>();
            if (platform.startsWith("windows")) {
                libNames.add("isyntax.dll");
                libNames.add("libisyntax.dll"); // some toolchains (e.g. MinGW) add 'lib' prefix on Windows
            } else if (platform.startsWith("mac") || platform.startsWith("darwin")) {
                libNames.add("libisyntax.dylib");
                libNames.add("isyntax.dylib");
            } else {
                libNames.add("libisyntax.so");
                libNames.add("isyntax.so");
            }

            for (String libName : libNames) {
                String res = "/natives/" + platform + "/" + libName;
                try (InputStream in = IsyntaxLoader.class.getResourceAsStream(res)) {
                    if (in == null) continue;
                    File dir = Files.createTempDirectory("qupath-libisyntax").toFile();
                    dir.deleteOnExit();
                    File tmp = new File(dir, libName);
                    try (OutputStream out = new FileOutputStream(tmp)) {
                        in.transferTo(out);
                    }
                    tmp.deleteOnExit();
                    return tmp;
                }
            }
        }
        return null;
    }

    private static List<String> detectPlatformClassifiers() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String normArch;
        if (arch.contains("aarch64") || arch.contains("arm64")) normArch = "aarch64";
        else if (arch.contains("64")) normArch = "x86_64";
        else normArch = arch.replace('-', '_');
        String base;
        if (os.contains("win")) base = "windows-" + normArch;
        else if (os.contains("mac") || os.contains("darwin")) base = "macos-" + normArch;
        else base = "linux-" + normArch;
        Set<String> variants = new LinkedHashSet<>();
        variants.add(base);
        variants.add(base.replace('_', '-'));
        variants.add(base.replace('-', '_'));
        variants.add(base.replace("linux-", "linux"));
        variants.add(base.replace("windows-", "windows"));
        variants.add(base.replace("macos-", "macos"));
        // Add alternative classifier variants used by build tooling
        if (base.startsWith("macos-")) {
            variants.add(base.replace("macos-", "darwin-"));
        }
        if (base.startsWith("windows-")) {
            variants.add(base.replace("windows-", "win32-"));
        }
        return new ArrayList<>(variants);
    }

    public static String preferredClassifier() {
        return detectPlatformClassifiers().get(0);
    }

    public static boolean isAvailable() { return INSTANCE != null; }

    public static Isyntax openImage(String path) throws IOException {
        if (INSTANCE == null) tryToLoadQuietly();
        if (INSTANCE == null) throw new IOException("libisyntax not available");
        PointerByReference ref = new PointerByReference();
        int err = INSTANCE.libisyntax_open(path, 0, ref);
        if (err != 0) throw new IOException("libisyntax_open failed with code " + err);
        return new Isyntax(INSTANCE, ref.getValue());
    }
    
    private static File findFromSearchPaths(String... searchPath) {
        if (searchPath == null) return null;
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if (os.contains("win")) {
            names.add("isyntax.dll");
            names.add("libisyntax.dll");
        } else if (os.contains("mac") || os.contains("darwin")) {
            names.add("libisyntax.dylib");
            names.add("isyntax.dylib");
        } else {
            names.add("libisyntax.so");
            names.add("isyntax.so");
        }
        List<String> platforms = detectPlatformClassifiers();
        for (String dir : searchPath) {
            if (dir == null || dir.isBlank()) continue;
            File base = new File(dir);
            for (String n : names) {
                File f = new File(base, n);
                if (f.exists()) return f;
            }
            for (String platform : platforms) {
                File sub = new File(base, "natives" + File.separator + platform);
                for (String n : names) {
                    File f = new File(sub, n);
                    if (f.exists()) return f;
                }
            }
        }
        return null;
    }

    private static List<File> detectLikelyInstallDirs() {
        List<File> dirs = new ArrayList<>();
        try {
            File here = new File(IsyntaxLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = here.getParentFile();
            if (base != null && base.isDirectory()) dirs.add(base);
            if (base != null && base.getParentFile() != null) dirs.add(base.getParentFile());
            if (base != null) {
                dirs.add(new File(base, "natives"));
                for (String p : detectPlatformClassifiers()) {
                    dirs.add(new File(base, "natives" + File.separator + p));
                }
                dirs.add(new File(base, "lib"));
                dirs.add(new File(base, "bin"));
            }
        } catch (Throwable ignored) {}
        try {
            dirs.add(new File(System.getProperty("user.dir", ".")));
        } catch (Throwable ignored) {}
        LinkedHashSet<File> set = new LinkedHashSet<>();
        for (File d : dirs) {
            if (d != null && d.isDirectory()) set.add(d);
        }
        return new ArrayList<>(set);
    }
}