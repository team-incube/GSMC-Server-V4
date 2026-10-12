CREATE TABLE score_status_history_tb (
    history_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    score_id         BIGINT       NOT NULL,
    action           VARCHAR(30)  NOT NULL,
    previous_status  VARCHAR(20)  NOT NULL,
    next_status      VARCHAR(20)  NOT NULL,
    actor_id         BIGINT       NOT NULL,
    actor_role       VARCHAR(50)  NOT NULL,
    occurred_at      DATETIME(6)  NOT NULL,
    reason           VARCHAR(500) NULL,
    INDEX idx_score_status_history_score_occurred (score_id, occurred_at, history_id),
    CONSTRAINT fk_score_status_history_score FOREIGN KEY (score_id) REFERENCES score_tb (score_id)
);
