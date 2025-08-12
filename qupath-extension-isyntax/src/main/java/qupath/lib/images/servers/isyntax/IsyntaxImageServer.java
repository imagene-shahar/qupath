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

import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.isyntax.jna.Isyntax;
import qupath.lib.images.servers.isyntax.jna.IsyntaxLoader;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import qupath.lib.common.GeneralTools;

public class IsyntaxImageServer extends AbstractTileableImageServer {

    private static final Cleaner cleaner = Cleaner.create();

    private static final class State implements Runnable {
        private final Isyntax isyntax;
        private State(Isyntax i) { this.isyntax = i; }
        @Override public void run() { try { isyntax.close(); } catch (Exception ignored) {} }
    }

    private final Isyntax isyntax;
    private final Cleaner.Cleanable cleanable;

    private ImageServerMetadata originalMetadata;
    private URI uri;
    private String[] args;

    public IsyntaxImageServer(URI uri, String... args) throws IOException {
        super();
        this.uri = uri;
        this.args = args;

        System.gc();
        Path filePath = GeneralTools.toPath(uri);
        String name;
        if (filePath != null && Files.exists(filePath)) {
            isyntax = IsyntaxLoader.openImage(filePath.toRealPath().toString());
            name = filePath.getFileName().toString();
        } else {
            isyntax = IsyntaxLoader.openImage(uri.toString());
            name = null;
        }
        cleanable = cleaner.register(this, new State(isyntax));

        int width = isyntax.getLevelWidth(0);
        int height = isyntax.getLevelHeight(0);
        int tileWidth = Math.max(1, isyntax.getTileWidth());
        int tileHeight = Math.max(1, isyntax.getTileHeight());

        var resolutionBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(width, height);
        for (int i = 0; i < isyntax.getLevelCount(); i++) {
            resolutionBuilder.addLevel(isyntax.getLevelWidth(i), isyntax.getLevelHeight(i));
        }
        var levels = resolutionBuilder.build();

        double mppX = isyntax.getMppX();
        double mppY = isyntax.getMppY();
        if (!(mppX > 0) || Double.isInfinite(mppX)) mppX = Double.NaN;
        if (!(mppY > 0) || Double.isInfinite(mppY)) mppY = Double.NaN;

        String path = uri.toString();
        originalMetadata = new ImageServerMetadata.Builder(getClass(), path, width, height)
                .channels(ImageChannel.getDefaultRGBChannels())
                .name(name)
                .rgb(true)
                .pixelType(PixelType.UINT8)
                .preferredTileSize(tileWidth, tileHeight)
                .pixelSizeMicrons(mppX, mppY)
                .levels(levels)
                .build();

        // Test read small region to fail fast
        try {
            BufferedImage thumb = getDefaultThumbnail(0, 0);
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public Collection<URI> getURIs() { return Collections.singletonList(uri); }

    @Override
    protected String createID() { return getClass().getName() + ": " + uri; }

    @Override
    public void close() { cleanable.clean(); }

    @Override
    public String getServerType() { return "iSyntax"; }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
        int tileX = tileRequest.getImageX();
        int tileY = tileRequest.getImageY();
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();

        BufferedImage img = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        isyntax.readRegionBGRA(data, tileX, tileY, tileRequest.getLevel(), tileWidth, tileHeight);

        BufferedImage rgb = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
        var g2d = rgb.createGraphics();
        g2d.drawImage(img, 0, 0, tileWidth, tileHeight, null);
        g2d.dispose();
        return rgb;
    }

    @Override
    protected ServerBuilder<BufferedImage> createServerBuilder() {
        return DefaultImageServerBuilder.createInstance(IsyntaxServerBuilder.class, getMetadata(), uri, args);
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() { return originalMetadata; }
}