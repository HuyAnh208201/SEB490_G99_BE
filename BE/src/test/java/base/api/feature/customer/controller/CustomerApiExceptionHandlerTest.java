package base.api.feature.customer.controller;

import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerApiExceptionHandlerTest {

    private final CustomerApiExceptionHandler handler = new CustomerApiExceptionHandler();

    @Test
    void preservesRawMobileErrorEnvelopeForExpectedAndUnexpectedFailures() {
        ResponseEntity<Map<String, Object>> bad =
                handler.badRequest(new BadRequestException("Bad request"));
        ResponseEntity<Map<String, Object>> missing =
                handler.notFound(new NotFoundException("Missing"));
        ResponseEntity<Map<String, Object>> constraint =
                handler.constraint(new ConstraintViolationException("Invalid", null));
        ResponseEntity<Map<String, Object>> unexpected =
                handler.unexpected(new IllegalStateException("secret detail"));

        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
        assertEquals(false, bad.getBody().get("success"));
        assertEquals("Missing", missing.getBody().get("message"));
        assertEquals("Invalid", constraint.getBody().get("message"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unexpected.getStatusCode());
        assertEquals("Internal error", unexpected.getBody().get("message"));
    }
}
