-- 봉사활동은 공식 기준상 1365 등 자원봉사 활동 증빙 파일 제출이 필요하다. 증빙 없이
-- 시간만 입력하던 기존 계약을 막기 위해 증빙 방식을 FILE로 바꾼다.
UPDATE category_tb
SET evidence_type = 'FILE'
WHERE category_type = 'VOLUNTEER';
