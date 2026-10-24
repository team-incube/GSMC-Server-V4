package team.incube.gsmc.global.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import team.incube.gsmc.global.auth.CustomUserDetails
import team.incube.gsmc.global.erroralert.ErrorAlertContext
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher
import team.incube.gsmc.global.erroralert.ErrorAlertSource
import team.incube.gsmc.global.security.filter.RequestIdFilter
import team.themoment.sdk.logging.logger.logger

@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorAlertPublisher: ErrorAlertPublisher,
) {
    @ExceptionHandler(GsmcException::class)
    fun handleGsmcException(ex: GsmcException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ex.errorCode.status)
            .body(ErrorResponse(ex.errorCode.status.value(), ex.errorCode.message))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ErrorCode.DUPLICATE_RESOURCE.status)
            .body(ErrorResponse(ErrorCode.DUPLICATE_RESOURCE.status.value(), ErrorCode.DUPLICATE_RESOURCE.message))

    // 매핑되지 않은 경로(꺼진 GraphiQL 포함)가 아래 Exception 핸들러에 잡혀 500이 되지 않도록 404로 응답한다.
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ErrorCode.RESOURCE_NOT_FOUND.status)
            .body(ErrorResponse(ErrorCode.RESOURCE_NOT_FOUND.status.value(), ErrorCode.RESOURCE_NOT_FOUND.message))

    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CustomUserDetails
        val requestId =
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) as? String
                ?: MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)
        logger().error(
            "처리되지 않은 REST 예외: method={}, path={}, requestId={}",
            request.method,
            request.requestURI,
            requestId,
            ex,
        )
        errorAlertPublisher.publish(
            ErrorAlertContext(
                source = ErrorAlertSource.REST,
                classification = "HTTP 500",
                endpoint = "${request.method} ${request.requestURI}",
                requestId = requestId,
                userId = principal?.userId,
                userRole = principal?.userRole,
            ),
            ex,
        )
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.INTERNAL_SERVER_ERROR.message))
    }
}
