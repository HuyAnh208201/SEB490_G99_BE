package base.api.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * One-off CLI: trim catalog on the configured remote DB. Not part of app startup.
 *
 * Usage (from BE module):
 *   mvnw.cmd -q compile exec:java -Dexec.mainClass=base.api.tools.CatalogProductTrimRunner
 * Optional arg: target size (default 200).
 */
public final class CatalogProductTrimRunner {

    private CatalogProductTrimRunner() {
    }

    public static void main(String[] args) throws Exception {
        int target = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        Properties props = loadApplicationProperties();
        String url = props.getProperty("spring.datasource.url");
        String user = props.getProperty("spring.datasource.username");
        String pass = props.getProperty("spring.datasource.password");
        if (url == null || user == null) {
            throw new IllegalStateException("Missing spring.datasource.* in application.properties");
        }

        System.out.printf("Catalog trim: connecting to %s, target=%d%n", maskUrl(url), target);
        CatalogProductTrimmer trimmer = new CatalogProductTrimmer(
                CatalogProductTrimmer.jdbcFromUrl(url, user, pass), target);
        CatalogProductTrimmer.TrimResult result = trimmer.run();

        System.out.printf("Before: %d active products%n", result.before());
        for (String line : result.categoryLines()) {
            System.out.println(line);
        }
        System.out.printf("Deleted: %d products%n", result.deleted());
        System.out.printf("After: %d active products%n", result.after());
        System.out.println("Done. Remote DB updated — no app restart needed for trim.");
    }

    private static Properties loadApplicationProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = CatalogProductTrimRunner.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties not on classpath");
            }
            props.load(in);
        }
        return props;
    }

    private static String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
