package base.api.feature.system.controller;

import base.api.shared.dto.TFUResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemSettingsControllerTimeTest {

    @Test
    void returnsCurrentServerEpochMilliseconds() {
        long before = System.currentTimeMillis();

        ResponseEntity<TFUResponse<Map<String, Long>>> response =
                new SystemSettingsController().time();

        long after = System.currentTimeMillis();
        assertNotNull(response.getBody());
        Long epochMs = response.getBody().getData().get("epochMs");
        assertNotNull(epochMs);
        assertTrue(epochMs >= before && epochMs <= after);
    }
}
