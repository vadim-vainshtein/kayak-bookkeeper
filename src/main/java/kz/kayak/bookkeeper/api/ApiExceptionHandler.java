package kz.kayak.bookkeeper.api;

import kz.kayak.bookkeeper.service.DomainException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, String>> conflict(DomainException ex) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage())); }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(java.util.NoSuchElementException ex) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage())); }
}
