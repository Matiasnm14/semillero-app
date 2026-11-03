package edu.upb.semillero.controller.scouting;

import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackageClasses = ContactoController.class) @Order(0)
public class ScoutingExceptionHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(OptimisticLockingFailureException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El registro cambió; vuelve a consultarlo");
    }
}
