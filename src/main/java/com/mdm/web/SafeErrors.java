package com.mdm.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletResponse;
@ControllerAdvice
public class SafeErrors {
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    String invalid(Model m,HttpServletResponse r){r.setStatus(400);m.addAttribute("message","Check your form values and submit again. No change was completed.");return "problem";}
    @ExceptionHandler(org.springframework.transaction.TransactionException.class)
    String unavailable(Model m,HttpServletResponse r){r.setStatus(503);m.addAttribute("message","The service is temporarily unavailable. Please retry later.");return "problem";}
}
