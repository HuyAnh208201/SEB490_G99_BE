package base.api.shared.exception;

import base.api.shared.dto.TFUResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
                        fe -> fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        String firstMessage = errors.values().stream().findFirst().orElse("Invalid request data");
        log.warn("[{}] Validation failed: {}", req.getDescription(false), errors);

        TFUResponse<Map<String, String>> body = TFUResponse.<Map<String, String>>builder()
                .success(false)
                .statusCode(400)
                .message(firstMessage)
                .data(errors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<TFUResponse<Void>> handleBadRequest(BadRequestException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<TFUResponse<Void>> handleBusiness(BusinessException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TFUResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TFUResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException ex, WebRequest req) {
        return buildErrorResponse(
                new BadRequestException("Invalid request body."),
                req,
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<TFUResponse<Void>> handleNotFound(NotFoundException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<TFUResponse<Void>> handleConflict(ConflictException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<TFUResponse<Void>> handleForbidden(ForbiddenException ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<TFUResponse<Void>> handleAccessDenied(AccessDeniedException ex, WebRequest req) {
        return buildErrorResponse(
                new ForbiddenException("Access denied."),
                req,
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TFUResponse<Void>> handleAny(Exception ex, WebRequest req) {
        return buildErrorResponse(ex, req, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<TFUResponse<Void>> buildErrorResponse(Exception ex, WebRequest req, HttpStatus status) {
        if (status.is5xxServerError()) {
            log.error("[{}] {}", req.getDescription(false), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {}", req.getDescription(false), ex.getMessage());
        }

        TFUResponse<Void> body = TFUResponse.<Void>builder()
                .success(false)
                .statusCode(status.value())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
