package com.assistia.controller;

import com.assistia.dto.ApiErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "La requête contient des données invalides ou une valeur d'enum inconnue.";
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Accès refusé.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handlePersistenceFailure(
            DataAccessException exception,
            HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur est survenue lors de l'accès aux données.", request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
