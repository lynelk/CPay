package net.citotech.cito;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * @author josephtabajjwa
 */
@ControllerAdvice
public class RestExceptionHandler {
    private static final Logger LOGGER = Logger.getLogger(RestExceptionHandler.class.getName());

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHandlingNoHandlerFound(NoHandlerFoundException ex) {
        String code = "125";
        String message = String.format(GeneralException.ERRORS_125, ex.getRequestURL());
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(GeneralException.getError(code, message));
    }

    // org.springframework.http.converter.HttpMessageNotReadableException
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        String code = "124";
        String message = GeneralException.ERRORS_124;
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(GeneralException.getError(code, message));
    }

    // org.springframework.web.HttpRequestMethodNotSupportedException
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex) {
        String code = "126";
        String message = String.format(GeneralException.ERRORS_126, ex.getMethod());
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(GeneralException.getError(code, message));
    }

    /**
     * Audit E3: a method-security denial surfaced by {@code @PreAuthorize} on the {@code
     * /api/v2/admin/**} controllers (e.g. HTTP Basic with an unknown/non-admin principal) must be
     * an explicit 403, not fall into the catch-all below and hide as a 500. The {@code
     * LegacySessionAuthorizationFilter} bridges a valid legacy administrator session into the
     * SecurityContext so interactive administrators and the machine ADMIN_API identity satisfy the
     * same {@code hasRole("ADMIN")} defense-in-depth check.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    protected ResponseEntity<String> requestAccessDenied(AccessDeniedException ex) {
        String code = "110";
        String message = GeneralException.ERRORS_110;
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("Content-Type", "application/json")
                .body(GeneralException.getError(code, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    protected ResponseEntity<String> requestValidationException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(GeneralException.getError("114", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    protected ResponseEntity<String> requestStateConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Content-Type", "application/json")
                .body(GeneralException.getError("130", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    protected ResponseEntity<String> requestResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String code = resolved == HttpStatus.BAD_REQUEST ? "114" : String.valueOf(resolved.value());
        String message = ex.getReason() == null ? resolved.getReasonPhrase() : ex.getReason();
        return ResponseEntity.status(resolved)
                .header("Content-Type", "application/json")
                .body(GeneralException.getError(code, message));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    protected ResponseEntity<String> requestUnhandledException(Exception ex) {
        LOGGER.log(Level.SEVERE, "Unhandled API exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(GeneralException.getError("102", GeneralException.ERRORS_102));
    }
    /*@Bean
    RouterFunction staticResourceLocator(){
            return RouterFunctions.resources("/**", new ClassPathResource("/"));
    }*/

}
