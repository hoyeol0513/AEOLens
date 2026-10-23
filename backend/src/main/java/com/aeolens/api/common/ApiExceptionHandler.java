package com.aeolens.api.common;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidTarget(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid audit target", exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail handleValidation(WebExchangeBindException exception) {
        String detail = exception.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", detail);
    }

    @ExceptionHandler(WebClientResponseException.class)
    ProblemDetail handleRemoteStatus(WebClientResponseException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Target fetch failed",
                "The target returned HTTP " + exception.getStatusCode().value());
    }

    @ExceptionHandler(TimeoutException.class)
    ProblemDetail handleTimeout(TimeoutException exception) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Target fetch timed out",
                "The target did not respond within the configured crawl timeout");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://aeolens.dev/problems/" + status.value()));
        return problem;
    }
}
