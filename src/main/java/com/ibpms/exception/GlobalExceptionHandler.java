package com.ibpms.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI("https://example.com/problems/email-already-exists"));
        pd.setTitle("Email already exists");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleUsernameAlreadyExists(UsernameAlreadyExistsException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI("https://example.com/problems/username-already-exists"));
        pd.setTitle("Username already exists");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI("https://example.com/problems/invalid-refresh-token"));
        pd.setTitle("Invalid refresh token");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleUsernameNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI("https://example.com/problems/username-not-found"));
        pd.setTitle("User not found");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        pd.setType(URI("https://example.com/problems/bad-credentials"));
        pd.setTitle("Bad credentials");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setType(URI("https://example.com/problems/validation-error"));
        pd.setTitle("Validation error");
        pd.setInstance(URI(request.getRequestURI()));
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        Map<String, List<String>> errors = fieldErrors.stream()
                .collect(Collectors.groupingBy(FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(PolicyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handlePolicyNotFound(PolicyNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI("https://example.com/problems/policy-not-found"));
        pd.setTitle("Policy not found");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(PolicyNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handlePolicyNotActive(PolicyNotActiveException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI("https://example.com/problems/policy-not-active"));
        pd.setTitle("Policy not active");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleTaskNotFound(TaskNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI("https://example.com/problems/task-not-found"));
        pd.setTitle("Task not found");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(InvalidTaskStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleInvalidTaskState(InvalidTaskStateException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI("https://example.com/problems/invalid-task-state"));
        pd.setTitle("Invalid task state");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(PolicyInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handlePolicyInUse(PolicyInUseException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI("https://example.com/problems/policy-in-use"));
        pd.setTitle("Policy in use");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(ProcessInstanceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleProcessInstanceNotFound(ProcessInstanceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI("https://example.com/problems/process-instance-not-found"));
        pd.setTitle("Process instance not found");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI("https://example.com/problems/user-not-found"));
        pd.setTitle("User not found");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        pd.setType(URI("https://example.com/problems/internal-server-error"));
        pd.setTitle("Internal server error");
        pd.setInstance(URI(request.getRequestURI()));
        return pd;
    }

    private static java.net.URI URI(String uri) {
        return java.net.URI.create(uri);
    }
}

