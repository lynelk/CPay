/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 *
 * @author josephtabajjwa
 */
@ControllerAdvice
public class RestExceptionHandler {
    private static final Logger LOGGER = Logger.getLogger(RestExceptionHandler.class.getName());
   
   
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(value= HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHandlingNoHandlerFound(NoHandlerFoundException ex) {
        String code = "125";
        String message =  String.format(GeneralException.ERRORS_125, ex.getRequestURL());
        return ResponseEntity.ok()
               .header("Content-Type", "application/json")
               .body(GeneralException.getError(code, message));
    }
    
    //org.springframework.http.converter.HttpMessageNotReadableException
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(value= HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String code = "124";
        String message =  GeneralException.ERRORS_124;
        return ResponseEntity.ok()
               .header("Content-Type", "application/json")
               .body(GeneralException.getError(code, message));
    }
    
    //org.springframework.web.HttpRequestMethodNotSupportedException
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(value= HttpStatus.NOT_FOUND)
    @ResponseBody
    protected ResponseEntity<String> requestHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        String code = "126";
        String message =  String.format(GeneralException.ERRORS_126,ex.getMethod());
        return ResponseEntity.ok()
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
