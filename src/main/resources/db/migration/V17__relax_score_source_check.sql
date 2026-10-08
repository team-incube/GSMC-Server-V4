-- V8의 chk_score_source(출처 최대 1개)는 DataGSM 참여 신청이 evidence_id와
-- dg_project_id를 함께 저장하는 정상 계약과 충돌해 신청을 모두 거부했다 (#174).
-- 증빙은 프로젝트 출처와 공존할 수 있고, 내부/외부 프로젝트 출처만 배타적이다.
ALTER TABLE score_tb DROP CHECK chk_score_source;
ALTER TABLE score_tb
    ADD CONSTRAINT chk_score_source CHECK (
        project_id IS NULL OR dg_project_id IS NULL
        );
