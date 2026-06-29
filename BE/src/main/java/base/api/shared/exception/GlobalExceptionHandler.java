package base.api.shared.exception;

import base.api.shared.dto.TFUResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TFUResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "Giá trị không hợp lệ" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        String firstMessage = errors.values().stream().findFirst().orElse("Dữ liệu không hợp lệ");
        log.warn("[{}] Validation failed: {}", req.getDescription(false), errors);

        TFUResponse<Map<String, String>> body = TFUResponse.<Map<String, String>>builder()
                .success(false)
                .statusCode(400)
                .message(firstMessage)
                .data(errors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TFUResponse<Void>> handleAny(Exception ex, WebRequest req) {
        log.error("[{}] {}", req.getDescription(false), ex.getMessage(), ex);
        TFUResponse<Void> body = TFUResponse.<Void>builder()
                .success(false)
                .statusCode(400)
                .message(ex.getMessage())
                .build();

        return ResponseEntity.badRequest().body(body);
    }
}
