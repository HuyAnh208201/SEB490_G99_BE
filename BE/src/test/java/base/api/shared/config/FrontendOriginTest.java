package base.api.shared.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendOriginTest {

    @Test
    void prefersRequestedLocalhostPort() {
        assertEquals(
                "http://localhost:5175",
                FrontendOrigin.resolve("http://localhost:5175", "http://localhost:5173"));
    }

    @Test
    void allowsLoopbackAndFallsBackToConfigured() {
        assertEquals(
                "http://127.0.0.1:5173",
                FrontendOrigin.resolve("http://127.0.0.1:5173", "http://localhost:5175"));
        assertEquals(
                "http://localhost:5175",
                FrontendOrigin.resolve("javascript:alert(1)", "http://localhost:5175"));
        assertFalse(FrontendOrigin.isAllowed("ftp://localhost", "http://localhost:5175"));
        assertTrue(FrontendOrigin.isAllowed("https://localhost:4173", "http://localhost:5175"));
    }

    @Test
    void allowsConfiguredPublicHost() {
        assertEquals(
                "https://app.chainstore.vn",
                FrontendOrigin.resolve("https://app.chainstore.vn", "https://app.chainstore.vn"));
        assertEquals(
                "https://app.chainstore.vn",
                FrontendOrigin.resolve("https://evil.example", "https://app.chainstore.vn"));
    }

    @Test
    void allowsWwwAndApexAsSameSite() {
        assertTrue(FrontendOrigin.isAllowed("https://www.chainstore.site", "https://chainstore.site"));
        assertTrue(FrontendOrigin.isAllowed("https://chainstore.site", "https://www.chainstore.site"));
        assertEquals(
                "https://www.chainstore.site",
                FrontendOrigin.resolve("https://www.chainstore.site", null, "https://chainstore.site"));
    }
}
