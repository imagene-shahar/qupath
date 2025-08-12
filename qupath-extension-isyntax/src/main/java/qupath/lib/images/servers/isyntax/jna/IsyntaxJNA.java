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

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

public interface IsyntaxJNA extends Library {

    int libisyntax_init();
    int libisyntax_open(String filename, int flags, PointerByReference out_isyntax);
    void libisyntax_close(Pointer isyntax);

    int libisyntax_get_tile_width(Pointer isyntax);
    int libisyntax_get_tile_height(Pointer isyntax);

    Pointer libisyntax_get_wsi_image(Pointer isyntax);
    Pointer libisyntax_get_label_image(Pointer isyntax);
    Pointer libisyntax_get_macro_image(Pointer isyntax);
    String libisyntax_get_barcode(Pointer isyntax);

    int libisyntax_image_get_level_count(Pointer image);
    int libisyntax_image_get_offset_x(Pointer image);
    int libisyntax_image_get_offset_y(Pointer image);
    Pointer libisyntax_image_get_level(Pointer image, int index);

    int libisyntax_level_get_scale(Pointer level);
    int libisyntax_level_get_width_in_tiles(Pointer level);
    int libisyntax_level_get_height_in_tiles(Pointer level);
    int libisyntax_level_get_width(Pointer level);
    int libisyntax_level_get_height(Pointer level);
    float libisyntax_level_get_mpp_x(Pointer level);
    float libisyntax_level_get_mpp_y(Pointer level);

    int libisyntax_cache_create(String debugNameOrNull, int cacheSize, PointerByReference out_cache);
    int libisyntax_cache_inject(Pointer isyntax_cache, Pointer isyntax);
    void libisyntax_cache_destroy(Pointer isyntax_cache);

    int libisyntax_tile_read(Pointer isyntax, Pointer isyntax_cache,
                             int level, long tile_x, long tile_y,
                             int[] pixels_buffer, int pixel_format);

    int libisyntax_read_region(Pointer isyntax, Pointer isyntax_cache, int level,
                               long x, long y, long width, long height, int[] pixels_buffer,
                               int pixel_format);

    int libisyntax_read_label_image(Pointer isyntax, int[] width, int[] height,
                                    PointerByReference pixels_buffer, int pixel_format);

    int libisyntax_read_macro_image(Pointer isyntax, int[] width, int[] height,
                                    PointerByReference pixels_buffer, int pixel_format);

    int libisyntax_read_label_image_jpeg(Pointer isyntax, PointerByReference jpeg_buffer, int[] jpeg_size);
    int libisyntax_read_macro_image_jpeg(Pointer isyntax, PointerByReference jpeg_buffer, int[] jpeg_size);
}