-- 사용자별 증빙 초안을 최대 1건으로 제한한다 (#178)
-- is_draft가 TRUE면 user_id, 아니면 NULL을 갖는 생성 컬럼에 UNIQUE를 건다.
-- MySQL UNIQUE는 NULL 중복을 허용하므로 제출본(is_draft = FALSE)은 여러 건 저장된다.
-- 기존에 중복 초안이 있으면 이 마이그레이션은 실패한다 (V9와 같은 정책).
ALTER TABLE evidence_tb
    ADD COLUMN draft_user_id BIGINT
        GENERATED ALWAYS AS (IF(is_draft, user_id, NULL)) STORED,
    ADD UNIQUE KEY uk_evidence_draft_user (draft_user_id);
