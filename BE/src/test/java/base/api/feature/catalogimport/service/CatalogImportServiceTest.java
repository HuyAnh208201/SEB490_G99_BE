package base.api.feature.catalogimport.service;

import base.api.feature.catalogimport.dto.CatalogImportStatusResponse;
import base.api.shared.exception.BadRequestException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for catalog import start/status guards and executeImport branching.
 */
@ExtendWith(MockitoExtension.class)
class CatalogImportServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ObjectMapper objectMapper;
    @Mock private CatalogImportJobRunner jobRunner;

    @InjectMocks
    private CatalogImportService service;

    @Test
    void statusStartsIdle() {
        CatalogImportStatusResponse status = service.status();

        assertEquals("IDLE", status.getStatus());
        assertEquals(0, status.getTotal());
        assertEquals(0, status.getProcessed());
        assertEquals("Idle", status.getMessage());
        assertNull(status.getStartedAt());
        assertNull(status.getFinishedAt());
    }

    @Test
    void startMarksRunningAndDelegatesToJobRunner() {
        CatalogImportStatusResponse status = service.start();

        assertEquals("RUNNING", status.getStatus());
        assertEquals("Import started", status.getMessage());
        assertNotNull(status.getStartedAt());
        assertNull(status.getFinishedAt());
        verify(jobRunner).run();
    }

    @Test
    void startRejectsWhenAlreadyRunning() {
        service.start();

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.start());

        assertTrue(error.getMessage().contains("already running"));
        verify(jobRunner).run();
    }

    @Test
    void executeImportCompletesWithEmptyCatalog() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(List.of());

        service.executeImport();

        CatalogImportStatusResponse status = service.status();
        assertEquals("COMPLETED", status.getStatus());
        assertEquals(0, status.getTotal());
        assertEquals(0, status.getProcessed());
        assertEquals("Import completed", status.getMessage());
        assertNotNull(status.getFinishedAt());
    }

    @Test
    void executeImportMarksFailedWhenCatalogCannotLoad() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("missing catalog file"));

        service.executeImport();

        CatalogImportStatusResponse status = service.status();
        assertEquals("FAILED", status.getStatus());
        assertTrue(status.getMessage().contains("missing catalog file"));
        assertNotNull(status.getFinishedAt());
    }

    @Test
    void executeImportCountsFailedWhenCategoryMissingAfterEnsure() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(List.of(sku("SKU-1", "UnknownCat")));
        // Category table stays empty even after ensureCategory reload.
        doAnswer(invocation -> null).when(jdbc).query(contains("categories"), any(RowCallbackHandler.class));
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);

        service.executeImport();

        CatalogImportStatusResponse status = service.status();
        assertEquals("COMPLETED", status.getStatus());
        assertEquals(1, status.getTotal());
        assertEquals(1, status.getProcessed());
        assertEquals(0, status.getCreated());
        assertEquals(1, status.getFailed());
    }

    @Test
    void executeImportCreatesSkuWhenCodeIsNew() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(List.of(sku("NEW-1", "Dairy")));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getString("name")).thenReturn("Dairy");
            when(rs.getInt("id")).thenReturn(3);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("categories"), any(RowCallbackHandler.class));

        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(101));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.executeImport();

        CatalogImportStatusResponse status = service.status();
        assertEquals("COMPLETED", status.getStatus());
        assertEquals(1, status.getCreated());
        assertEquals(0, status.getFailed());
        assertEquals(1, status.getProcessed());
    }

    @Test
    void executeImportClearsRunningFlagSoStartCanRunAgain() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(List.of());

        service.start();
        service.executeImport();

        CatalogImportStatusResponse again = service.start();
        assertEquals("RUNNING", again.getStatus());
    }

    private static CatalogImportService.CatalogSku sku(String code, String category) {
        return new CatalogImportService.CatalogSku(
                code,
                null,
                "Product " + code,
                category,
                "bottle",
                null,
                1,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null);
    }
}
