package team.incube.gsmc.global.exception

import org.springframework.http.HttpStatus

// code: GraphQL errors[].extensions.code 값 (UNAUTHENTICATED는 GraphQL 관례상 UNAUTHORIZED 대신 사용)
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
    val code: String,
) {
    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", "INTERNAL_SERVER_ERROR"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.", "NOT_FOUND"),

    // 인증
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "유효하지 않은 OAuth state입니다.", "BAD_REQUEST"),
    OAUTH_TOKEN_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "OAuth 토큰 교환에 실패했습니다.", "UNAUTHENTICATED"),
    OAUTH_USER_INFO_FETCH_FAILED(HttpStatus.UNAUTHORIZED, "OAuth 사용자 정보 조회에 실패했습니다.", "UNAUTHENTICATED"),
    INVALID_REDIRECT_URI(HttpStatus.BAD_REQUEST, "허용되지 않은 리다이렉트 URI입니다.", "BAD_REQUEST"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.", "UNAUTHENTICATED"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다.", "UNAUTHENTICATED"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.", "UNAUTHENTICATED"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.", "FORBIDDEN"),

    // 멤버
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.", "BAD_REQUEST"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "데이터 제약 조건을 위반했습니다.", "DUPLICATE_RESOURCE"),

    // 점수
    SCORE_NOT_FOUND(HttpStatus.NOT_FOUND, "점수를 찾을 수 없습니다.", "NOT_FOUND"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_CATEGORY_TYPE(HttpStatus.BAD_REQUEST, "해당 요청에 사용할 수 없는 카테고리입니다.", "BAD_REQUEST"),
    INVALID_SCORE_VALUE(HttpStatus.BAD_REQUEST, "점수 값이 올바르지 않습니다.", "BAD_REQUEST"),
    SCORE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 신청한 카테고리입니다.", "CONFLICT"),
    EVIDENCE_DRAFT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 작성 중인 증빙 초안이 있습니다.", "CONFLICT"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_REJECTION_REASON(HttpStatus.BAD_REQUEST, "거절 사유는 1자 이상 500자 이하여야 합니다.", "BAD_REQUEST"),
    INVALID_FILE_SIZE(HttpStatus.BAD_REQUEST, "파일 크기는 20MB를 초과할 수 없습니다.", "BAD_REQUEST"),
    INVALID_GRADE(HttpStatus.BAD_REQUEST, "학년 범위가 올바르지 않습니다.", "BAD_REQUEST"),
    INVALID_CLASS_NUMBER(HttpStatus.BAD_REQUEST, "반 번호 범위가 올바르지 않습니다.", "BAD_REQUEST"),
    S3_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "S3에서 파일을 찾을 수 없습니다.", "NOT_FOUND"),
    SHEET_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Sheet 파일 생성에 실패했습니다.", "INTERNAL_SERVER_ERROR"),
    SHEET_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Sheet 파일 업로드에 실패했습니다.", "INTERNAL_SERVER_ERROR"),
    SHEET_PRESIGNED_URL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Sheet 다운로드 URL 생성에 실패했습니다.", "INTERNAL_SERVER_ERROR"),
    FILE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 업로드 확인이 완료된 파일입니다.", "CONFLICT"),
    FILE_LINKED_TO_APPROVED_SCORE(HttpStatus.CONFLICT, "승인된 점수 요청에 연결된 파일은 삭제할 수 없습니다.", "CONFLICT"),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "증빙자료를 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_EVIDENCE_INPUT(HttpStatus.BAD_REQUEST, "증빙자료 입력값이 올바르지 않습니다.", "BAD_REQUEST"),
    EVIDENCE_ALREADY_CONNECTED(HttpStatus.CONFLICT, "Score에 이미 증빙자료가 연결되어 있습니다.", "CONFLICT"),
    FILE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 자료에 연결된 파일입니다.", "CONFLICT"),

    // 프로젝트 참여
    DATAGSM_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "DataGSM 프로젝트를 찾을 수 없습니다.", "NOT_FOUND"),
    DATAGSM_PROJECT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "종료된 프로젝트는 제출할 수 없습니다.", "BAD_REQUEST"),
    DATAGSM_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "DataGSM 연동에 실패했습니다.", "BAD_GATEWAY"),
    NOT_A_DATAGSM_PROJECT_PARTICIPANT(HttpStatus.BAD_REQUEST, "해당 프로젝트의 참여자가 아닙니다.", "BAD_REQUEST"),
    INVALID_PROJECT_PARTICIPANT_COUNT(HttpStatus.BAD_REQUEST, "프로젝트 참여자는 2인 이상이어야 합니다.", "BAD_REQUEST"),
    PROJECT_PARTICIPATION_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출하거나 심사 중인 프로젝트입니다.", "CONFLICT"),

    // 내부 Project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_PROJECT_INPUT(HttpStatus.BAD_REQUEST, "프로젝트 입력값이 올바르지 않습니다.", "BAD_REQUEST"),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.", "BAD_REQUEST"),

    // 알림
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.", "NOT_FOUND"),
    INVALID_ALERT_PAGE_SIZE(HttpStatus.BAD_REQUEST, "알림 페이지 크기는 1 이상 100 이하이어야 합니다.", "BAD_REQUEST"),
    INVALID_ALERT_CURSOR(HttpStatus.BAD_REQUEST, "알림 커서가 올바르지 않습니다.", "BAD_REQUEST"),

    // 개발자
    INVALID_SCHOOL_INFO(HttpStatus.BAD_REQUEST, "학적 정보(학년·반·번호)가 올바르지 않습니다.", "BAD_REQUEST"),
    USER_HAS_RELATED_DATA(HttpStatus.CONFLICT, "관련 데이터가 있는 사용자는 삭제할 수 없습니다.", "CONFLICT"),
}
