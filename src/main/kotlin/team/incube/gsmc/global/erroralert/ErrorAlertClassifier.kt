package team.incube.gsmc.global.erroralert

import jakarta.validation.ConstraintViolationException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import team.incube.gsmc.global.exception.GsmcException

@Component
class ErrorAlertClassifier {
    fun shouldPublish(
        context: ErrorAlertContext,
        throwable: Throwable,
    ): Boolean {
        if (throwable is GsmcException) return false
        if (context.source == ErrorAlertSource.REST && context.endpoint.contains("/actuator/health")) return false
        return throwable !is MethodArgumentNotValidException &&
            throwable !is HandlerMethodValidationException &&
            throwable !is ConstraintViolationException &&
            throwable !is BindException &&
            throwable !is MissingServletRequestParameterException &&
            throwable !is MethodArgumentTypeMismatchException &&
            throwable !is HttpMessageNotReadableException &&
            throwable !is AuthenticationException &&
            throwable !is AccessDeniedException
    }
}
