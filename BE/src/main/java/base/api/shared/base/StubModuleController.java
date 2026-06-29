package base.api.shared.base;

import base.api.shared.dto.TFUResponse;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Controller stub trả placeholder cho module chưa triển khai nghiệp vụ.
 */
public abstract class StubModuleController extends BaseAPIController {

    protected <T> ResponseEntity<TFUResponse<Map<String, String>>> stub(String module, String screen) {
        return success(Map.of(
                "module", module,
                "screen", screen,
                "status", "placeholder"
        ), "Module đang phát triển — phân quyền đã được áp dụng");
    }
}
