-- 당해연도 실적은 학년별 공식 활동 기간 안의 활동만 인정한다 (#226).
-- 실제 활동일과 검증에 적용된 정책 버전을 보존한다. 기존 행은 활동일을 알 수 없으므로
-- NULL로 유지하며 자동 인정하거나 보정하지 않는다.
ALTER TABLE score_tb
    ADD COLUMN activity_date DATE NULL,
    ADD COLUMN activity_period_policy_version VARCHAR(20) NULL;
