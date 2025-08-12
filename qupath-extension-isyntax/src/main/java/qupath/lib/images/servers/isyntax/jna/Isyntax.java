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

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Isyntax implements Closeable {

    public static final int LIBISYNTAX_PIXEL_FORMAT_RGBA = 0x101; // from enum in header
    public static final int LIBISYNTAX_PIXEL_FORMAT_BGRA = 0x102;

    private final IsyntaxJNA jna;
    private Pointer isyntax;
    private Pointer cache;

    private final Pointer wsiImage;
    private final int levelCount;
    private final int[] levelWidths;
    private final int[] levelHeights;
    private final float mppX;
    private final float mppY;

    Isyntax(IsyntaxJNA jna, Pointer isyntax) throws IOException {
        this.jna = jna;
        this.isyntax = isyntax;

        // create cache and inject
        PointerByReference cacheRef = new PointerByReference();
        int err = jna.libisyntax_cache_create("qupath-cache", 4000, cacheRef);
        if (err != 0) throw new IOException("libisyntax_cache_create failed: " + err);
        this.cache = cacheRef.getValue();
        err = jna.libisyntax_cache_inject(cache, isyntax);
        if (err != 0) throw new IOException("libisyntax_cache_inject failed: " + err);

        this.wsiImage = jna.libisyntax_get_wsi_image(isyntax);
        this.levelCount = jna.libisyntax_image_get_level_count(wsiImage);
        this.levelWidths = new int[levelCount];
        this.levelHeights = new int[levelCount];
        for (int i = 0; i < levelCount; i++) {
            var level = jna.libisyntax_image_get_level(wsiImage, i);
            levelWidths[i] = jna.libisyntax_level_get_width(level);
            levelHeights[i] = jna.libisyntax_level_get_height(level);
        }
        // Prefer level 0 pixel size
        var level0 = jna.libisyntax_image_get_level(wsiImage, 0);
        this.mppX = jna.libisyntax_level_get_mpp_x(level0);
        this.mppY = jna.libisyntax_level_get_mpp_y(level0);
    }

    public int getLevelCount() { return levelCount; }
    public int getLevelWidth(int level) { return levelWidths[level]; }
    public int getLevelHeight(int level) { return levelHeights[level]; }
    public float getMppX() { return mppX; }
    public float getMppY() { return mppY; }
    public int getTileWidth() { return jna.libisyntax_get_tile_width(isyntax); }
    public int getTileHeight() { return jna.libisyntax_get_tile_height(isyntax); }

    public void readRegionBGRA(int[] dest, long x, long y, int level, int w, int h) throws IOException {
        int err = jna.libisyntax_read_region(isyntax, cache, level, x, y, w, h, dest, LIBISYNTAX_PIXEL_FORMAT_BGRA);
        if (err != 0) throw new IOException("libisyntax_read_region failed: " + err);
    }

    public void readRegionIntoARGB(int[] dest, long x, long y, int level, int w, int h) throws IOException {
        readRegionBGRA(dest, x, y, level, w, h);
        int n = w * h;
        for (int i = 0; i < n; i++) {
            int c = dest[i];
            int b = (c) & 0xFF;
            int g = (c >>> 8) & 0xFF;
            int r = (c >>> 16) & 0xFF;
            int a = (c >>> 24) & 0xFF;
            dest[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    public BufferedImage readRegionImage(long x, long y, int level, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        readRegionIntoARGB(data, x, y, level, w, h);
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(img, 0, 0, null);
        return rgb;
    }

    public List<String> getAssociatedImageNames() {
        List<String> names = new ArrayList<>();
        if (jna.libisyntax_get_label_image(isyntax) != null) names.add("Label");
        if (jna.libisyntax_get_macro_image(isyntax) != null) names.add("Macro");
        return names;
    }

    @Override
    public void close() {
        try {
            if (cache != null) { jna.libisyntax_cache_destroy(cache); cache = null; }
        } finally {
            if (isyntax != null) { jna.libisyntax_close(isyntax); isyntax = null; }
        }
    }
}