-- 심사 대기 점수 전체 조회(pendingScores)용 복합 인덱스
--  - ScorePersistenceAdapter.findAllPendingBySearchCondition / countPendingBySearchCondition
--    (score_status = 'PENDING' ORDER BY updated_at DESC, score_id DESC)
CREATE INDEX idx_score_status_updated_at ON score_tb (score_status, updated_at);
