package base.api.shared.config;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.Locale;

/**
 * Builds staff-facing links that work on any local Vite port, not a hardcoded 5173.
 */
public final class FrontendOrigin {

    private FrontendOrigin() {
    }

    public static String resolve(String requested, String configuredClientUrl) {
        return resolve(requested, currentOriginHeader(), configuredClientUrl);
    }

    public static String resolve(String requested, String originHeader, String configuredClientUrl) {
        String fallback = normalize(configuredClientUrl);
        if (fallback.isEmpty()) {
            fallback = "http://localhost:5175";
        }
        for (String candidate : new String[] { requested, originHeader }) {
            String normalized = normalize(candidate);
            if (isAllowed(normalized, fallback)) {
                return normalized;
            }
        }
        return fallback;
    }

    public static String path(String origin, String relativePath) {
        String base = origin == null ? "" : origin;
        String suffix = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return base + suffix;
    }

    static boolean isAllowed(String origin, String configured) {
        if (origin == null || origin.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            if (uri.getRawUserInfo() != null) {
                return false;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                return true;
            }
            URI configuredUri = URI.create(configured);
            String configuredHost = configuredUri.getHost() == null
                    ? ""
                    : configuredUri.getHost().toLowerCase(Locale.ROOT);
            return !configuredHost.isEmpty() && configuredHost.equals(host);
        } catch (Exception ex) {
            return false;
        }
    }

    private static String currentOriginHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest().getHeader("Origin");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
