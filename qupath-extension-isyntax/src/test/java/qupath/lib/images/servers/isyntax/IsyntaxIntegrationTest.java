package qupath.lib.images.servers.isyntax;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
public class IsyntaxIntegrationTest {

    private static Path testDir;
    private static Path slidePath;

    @BeforeAll
    static void setup() throws Exception {
        // Ensure builder discoverable if using provider elsewhere
        ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, Thread.currentThread().getContextClassLoader()));
        // Load native from packaged resources if present
        Path nativesDir = Path.of("build", "resources", "main", "natives", qupath.lib.images.servers.isyntax.jna.IsyntaxLoader.preferredClassifier());
        qupath.lib.images.servers.isyntax.jna.IsyntaxLoader.tryToLoadQuietly(nativesDir.toAbsolutePath().toString());
        testDir = Path.of("build", "isyntax-tests");
        Files.createDirectories(testDir);
        slidePath = testDir.resolve("testslide.isyntax");
        if (!Files.exists(slidePath)) {
            URL url = new URL("https://zenodo.org/record/5037046/files/testslide.isyntax?download=1");
            try (var in = url.openStream()) {
                Files.copy(in, slidePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        assertTrue(Files.size(slidePath) > 0);
    }

    @AfterAll
    static void cleanup() {
        // keep file cached
    }

    @Test
    void testOpenAndReadMetadata() throws Exception {
        URI uri = slidePath.toAbsolutePath().toUri();
        try (IsyntaxImageServer server = new IsyntaxImageServer(uri)) {
            assertEquals("iSyntax", server.getServerType());
            assertTrue(server.getWidth() > 0);
            assertTrue(server.getHeight() > 0);
            assertTrue(server.nResolutions() >= 1);
            assertTrue(server.isRGB());
            assertEquals(3, server.nChannels());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "isyntax.decode", matches = "true")
    void testDecodeSmallRegion() throws Exception {
        URI uri = slidePath.toAbsolutePath().toUri();
        try (IsyntaxImageServer server = new IsyntaxImageServer(uri)) {
            int w = Math.min(128, server.getWidth());
            int h = Math.min(128, server.getHeight());
            var req = RegionRequest.createInstance(server.getPath(), 1.0, 0, 0, w, h);
            BufferedImage img = server.readRegion(req);
            assertNotNull(img);
            assertEquals(w, img.getWidth());
            assertEquals(h, img.getHeight());
        }
    }
}