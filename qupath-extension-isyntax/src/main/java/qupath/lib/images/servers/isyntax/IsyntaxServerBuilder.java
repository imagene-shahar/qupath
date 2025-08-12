/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers
 * %%
 * GPLv3
 * #L%
 */
package qupath.lib.images.servers.isyntax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.images.servers.isyntax.jna.IsyntaxLoader;

import java.awt.image.BufferedImage;
import java.net.URI;

public class IsyntaxServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(IsyntaxServerBuilder.class);

    private boolean failedToLoad = false;

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) {
        if (!IsyntaxLoader.isAvailable() && !IsyntaxLoader.tryToLoadQuietly()) {
            logger.debug("libisyntax unavailable - skipped");
            return null;
        }
        try {
            return new IsyntaxImageServer(uri, args);
        } catch (Exception e) {
            logger.error("Unable to open {} with libisyntax: {}", uri, e.getMessage(), e);
        } catch (NoClassDefFoundError e) {
            logger.warn("libisyntax library not found");
            logger.debug(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String... args) {
        float support = supportLevel(uri);
        return UriImageSupport.createInstance(this.getClass(), support, DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
    }

    private float supportLevel(URI uri) {
        if (!IsyntaxLoader.isAvailable() && !failedToLoad && !IsyntaxLoader.tryToLoadQuietly()) {
            failedToLoad = true;
            return 0;
        }
        String path = uri.toString().toLowerCase();
        if (!path.endsWith(".isyntax")) return 0;
        return 3.5f;
    }

    @Override
    public String getName() { return "iSyntax builder"; }

    @Override
    public String getDescription() { return "Provides access to Philips iSyntax using libisyntax"; }

    @Override
    public Class<BufferedImage> getImageType() { return BufferedImage.class; }

    @Override
    public boolean matchClassName(String... classNames) {
        for (var n : classNames) {
            if (this.getClass().getName().equals(n) || this.getClass().getSimpleName().equals(n) || "isyntax".equalsIgnoreCase(n))
                return true;
        }
        return false;
    }
}