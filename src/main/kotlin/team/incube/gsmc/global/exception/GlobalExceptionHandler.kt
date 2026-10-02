package team.incube.gsmc.global.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
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
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.INTERNAL_SERVER_ERROR.message))
}
