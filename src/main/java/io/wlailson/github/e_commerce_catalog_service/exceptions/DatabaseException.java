package io.wlailson.github.e_commerce_catalog_service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message) {
        super(message);
    }
}
