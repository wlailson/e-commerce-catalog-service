package io.wlailson.github.e_commerce_catalog_service.handlers;

import io.wlailson.github.e_commerce_catalog_service.exceptions.DatabaseException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ForbiddenException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNotFoundExceptionToNotFoundProblemDetail() {
        ProblemDetail problem = handler.handleResourceNotFoundException(
                new ResourceNotFoundException("Product not found")
        );

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Product not found", problem.getDetail());
    }

    @Test
    void mapsForbiddenExceptionToForbiddenProblemDetail() {
        ProblemDetail problem = handler.handleForbiddenException(new ForbiddenException("Access denied"));

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Access denied", problem.getDetail());
    }

    @Test
    void mapsSecurityAccessDeniedExceptionToForbiddenProblemDetail() {
        ProblemDetail problem = handler.handleAccessDeniedException(new AccessDeniedException("Access denied"));

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Access denied", problem.getDetail());
    }

    @Test
    void mapsDatabaseExceptionToConflictProblemDetail() {
        ProblemDetail problem = handler.handleDatabaseException(
                new DatabaseException("Referential integrity violation")
        );

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Referential integrity violation", problem.getDetail());
    }

    @Test
    void mapsIllegalStateExceptionToGenericInternalServerError() {
        ProblemDetail problem = handler.handleIllegalStateException(
                new IllegalStateException("Internal state details")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("An unexpected server error occurred", problem.getDetail());
        assertFalse(problem.getDetail().contains("Internal state details"));
    }

    @Test
    void mapsUnexpectedExceptionToGenericInternalServerError() {
        ProblemDetail problem = handler.handleUnexpectedException(new Exception("Sensitive internal details"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("An unexpected server error occurred", problem.getDetail());
        assertFalse(problem.getDetail().contains("Sensitive internal details"));
    }
}
