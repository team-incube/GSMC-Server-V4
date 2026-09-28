-- 파일 DB 행 삭제와 같은 트랜잭션에 스토리지 객체 삭제 작업을 기록하는 아웃박스 테이블.
-- 이전에는 커밋 후 콜백(afterCommit)에서 바로 S3를 호출하고 실패하면 로그만 남겨, S3 장애나
-- 커밋 직후 프로세스 종료 시 재처리할 근거가 사라졌다. 워커가 이 테이블을 주기적으로 읽어 삭제하고,
-- 성공하면 행을 지운다. 파일 행은 이미 지워진 뒤이므로 file_tb에 FK를 걸지 않는다.
CREATE TABLE file_storage_deletion_task_tb (
    task_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key          VARCHAR(255)  NOT NULL,
    task_status       VARCHAR(20)   NOT NULL,
    attempt_count     INT           NOT NULL DEFAULT 0,
    next_attempt_at   DATETIME      NOT NULL,
    last_error        VARCHAR(1000) NULL,
    last_attempted_at DATETIME      NULL,
    created_at        DATETIME      NOT NULL
);

-- 워커의 "처리 시각이 된 PENDING 작업" 조회(FOR UPDATE SKIP LOCKED)와 상태별 적체 집계용
CREATE INDEX idx_file_storage_deletion_task_status_next_attempt_at
    ON file_storage_deletion_task_tb (task_status, next_attempt_at);
