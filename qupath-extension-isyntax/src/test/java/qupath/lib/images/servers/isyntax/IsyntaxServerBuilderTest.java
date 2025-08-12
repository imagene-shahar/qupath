package qupath.lib.images.servers.isyntax;

import org.junit.jupiter.api.Test;

import qupath.lib.images.servers.ImageServerBuilder;

import java.net.URI;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

public class IsyntaxServerBuilderTest {

    @Test
    void testMatchClassName() {
        var builder = new IsyntaxServerBuilder();
        assertTrue(builder.matchClassName("isyntax"));
        assertTrue(builder.matchClassName(builder.getClass().getSimpleName()));
        assertTrue(builder.matchClassName(builder.getClass().getName()));
    }

    @Test
    void testNonISyntaxReturnsNoSupport() {
        var builder = new IsyntaxServerBuilder();
        var support = builder.checkImageSupport(URI.create("file:/tmp/test.tif"));
        assertNotNull(support);
        assertEquals(0f, support.getSupportLevel(), 0.0001);
    }

    @Test
    void testServiceLoaderIncludesISyntaxBuilder() {
        boolean found = false;
        for (ImageServerBuilder<?> b : ServiceLoader.load(ImageServerBuilder.class)) {
            if (b.getClass().getName().equals(IsyntaxServerBuilder.class.getName())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "IsyntaxServerBuilder should be discoverable via ServiceLoader");
    }
}