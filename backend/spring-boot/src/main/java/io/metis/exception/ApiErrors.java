package io.metis.exception;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.time.Instant;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
 private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
 private ResponseEntity<?> error(int status,String code,String message) { return ResponseEntity.status(status).body(Map.of("code",code,"message",message,"timestamp",Instant.now())); }
 @ExceptionHandler(ApiException.class) public ResponseEntity<?> api(ApiException e) { return error(e.status,e.code,e.getMessage()); }
 @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
 public ResponseEntity<?> invalid(Exception e) { return error(400,"VALIDATION_ERROR","Check required fields, numeric bounds, dates and identifiers."); }
 @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) public ResponseEntity<?> method(Exception e) {return error(405,"METHOD_NOT_ALLOWED","This endpoint does not support the requested method.");}
 @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class) public ResponseEntity<?> media(Exception e) {return error(415,"UNSUPPORTED_MEDIA_TYPE","Use application/json for request bodies.");}
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(Exception e) { return error(409,"CONFLICT","This record already exists or violates a data constraint."); }
 @ExceptionHandler(NoResourceFoundException.class) public ResponseEntity<?> notFound(Exception e) { return error(404,"NOT_FOUND","Endpoint not found."); }
 @ExceptionHandler(Exception.class) public ResponseEntity<?> other(Exception e) { log.error("Request failed: {}",e.getClass().getSimpleName());return error(500,"INTERNAL_ERROR","The request could not be completed. Check service health."); }
}
