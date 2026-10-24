-- 교과성적(ACADEMIC_GRADE) 과목별 입력값. 행 하나가 "학년·학기·과목 한 칸의 등급"이다.
-- 과목 목록은 코드 상수(AcademicCurriculum)로 관리하므로 과목명만 저장한다.
-- 1·2학년은 석차등급, 3학년은 성취도(A~E)를 1~5로 바꾼 값을 subject_grade에 담는다.
-- 반올림 전 평균은 이 입력값으로 조회 시점에 계산하므로 따로 저장하지 않는다.
CREATE TABLE academic_grade_entry_tb (
    entry_id      BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    grade         INT         NOT NULL,
    semester      INT         NOT NULL,
    subject_name  VARCHAR(50) NOT NULL,
    subject_grade INT         NOT NULL,
    CONSTRAINT uk_academic_grade_entry UNIQUE (user_id, grade, semester, subject_name),
    CONSTRAINT fk_academic_grade_entry_user FOREIGN KEY (user_id) REFERENCES user_tb (user_id)
);
