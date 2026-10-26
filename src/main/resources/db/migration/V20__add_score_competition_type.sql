-- 수상경력 점수의 대회 종류를 보존한다 (#282). 점수 계산에는 쓰이지 않는 심사 참고용 값이다.
-- 기존 행과 수상경력 외 카테고리는 NULL로 유지한다.
ALTER TABLE score_tb
    ADD COLUMN competition_type VARCHAR(40) NULL;
