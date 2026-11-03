package edu.upb.semillero.controller.scouting;

import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackageClasses = ContactoController.class) @Order(0)
public class ScoutingExceptionHandler {
    @ExceptionHandler(edu.upb.semillero.service.scouting.LimiteContactoException.class)
    public ResponseEntity<ProblemDetail> cuota(edu.upb.semillero.service.scouting.LimiteContactoException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(error.retryAfter()))
                .cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, error.getMessage()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(OptimisticLockingFailureException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El registro cambió; vuelve a consultarlo");
    }
}
