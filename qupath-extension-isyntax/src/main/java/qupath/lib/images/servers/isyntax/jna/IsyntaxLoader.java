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
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.Pointer;
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

    private static IsyntaxJNA tryToLoadJnaInstance(String... searchPath) {
        // 1) try packaged resource under natives/<platform>/
        try {
            File extracted = extractPackagedNative();
            if (extracted != null && extracted.exists()) {
                logger.debug("Loading libisyntax from packaged native {}", extracted);
                return Native.load(extracted.getAbsolutePath(), IsyntaxJNA.class);
            }
        } catch (Throwable t) {
            logger.debug("Failed to load packaged libisyntax", t);
        }

        // 2) use jna.library.path if provided
        String jnaPath = System.getProperty("jna.library.path", null);
        try {
            if (searchPath != null && searchPath.length > 0) {
                String path = Arrays.stream(searchPath)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(File.pathSeparator));
                if (!path.isBlank()) System.setProperty("jna.library.path", path);
            }
            return Native.load("isyntax", IsyntaxJNA.class);
        } finally {
            if (jnaPath == null) System.clearProperty("jna.library.path");
            else System.setProperty("jna.library.path", jnaPath);
        }
    }

    private static File extractPackagedNative() throws IOException {
        List<String> platforms = detectPlatformClassifiers();
        for (String platform : platforms) {
            String libName = platform.startsWith("windows") ? "isyntax.dll" : platform.startsWith("mac") || platform.startsWith("darwin") ? "libisyntax.dylib" : "libisyntax.so";
            String res = "/natives/" + platform + "/" + libName;
            try (InputStream in = IsyntaxLoader.class.getResourceAsStream(res)) {
                if (in == null) continue;
                File tmp = Files.createTempFile("libisyntax", libName).toFile();
                tmp.deleteOnExit();
                try (OutputStream out = new FileOutputStream(tmp)) {
                    in.transferTo(out);
                }
                return tmp;
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
}