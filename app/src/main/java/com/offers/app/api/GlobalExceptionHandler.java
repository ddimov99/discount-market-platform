package com.offers.app.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String VALIDATION_FAILED = "Validation failed";
    private static final String RESOURCE_NOT_FOUND = "Requested resource was not found";
    private static final String UNEXPECTED_SERVER_ERROR = "Unexpected server error";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty("errors", fieldErrors(ex));
        return handleExceptionInternal(ex, problem, problemHeaders(headers), HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        return handleExceptionInternal(ex, problem, problemHeaders(headers), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty("errors", constraintErrors(ex));
        return problemResponse(problem);
    }

    @ExceptionHandler({
            EntityNotFoundException.class,
            EmptyResultDataAccessException.class,
            NoSuchElementException.class
    })
    public ResponseEntity<ProblemDetail> handleMissingEntity(RuntimeException ex) {
        return problemResponse(problem(HttpStatus.NOT_FOUND, detailOrDefault(ex, RESOURCE_NOT_FOUND)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled API exception", ex);
        return problemResponse(problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_SERVER_ERROR));
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static HttpHeaders problemHeaders(HttpHeaders headers) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return problemHeaders;
    }

    private static List<ValidationError> fieldErrors(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        if (!errors.isEmpty()) {
            return errors;
        }

        return ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> new ValidationError(error.getObjectName(), error.getDefaultMessage()))
                .toList();
    }

    private static List<ValidationError> constraintErrors(ConstraintViolationException ex) {
        return ex.getConstraintViolations()
                .stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();
    }

    private static String detailOrDefault(RuntimeException ex, String defaultDetail) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : defaultDetail;
    }

    private record ValidationError(String field, String message) {
    }
}
