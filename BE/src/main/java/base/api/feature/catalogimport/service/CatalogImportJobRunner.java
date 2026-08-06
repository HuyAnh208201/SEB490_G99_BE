package base.api.feature.catalogimport.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Separate bean so {@code @Async} proxy applies (self-invocation would not).
 */
@Component
public class CatalogImportJobRunner {

    @Autowired
    @Lazy
    private CatalogImportService catalogImportService;

    @Async("catalogImportExecutor")
    public void run() {
        catalogImportService.executeImport();
    }
}
