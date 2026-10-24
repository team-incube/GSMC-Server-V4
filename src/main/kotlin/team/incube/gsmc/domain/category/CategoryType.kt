package team.incube.gsmc.domain.category

/**
 * 역량 평가 카테고리 유형
 *
 * | 값                    | 한글명          | 집계 방식    | 증빙 방식  |
 * |-----------------------|-----------------|--------------|------------|
 * | CERTIFICATE           | 자격증          | COUNT_BASED  | FILE       |
 * | TOPCIT                | TOPCIT          | SCORE_BASED  | FILE       |
 * | TOEIC                 | TOEIC           | SCORE_BASED  | FILE       |
 * | JLPT                  | JLPT            | SCORE_BASED  | FILE       |
 * | JPT                   | JPT             | SCORE_BASED  | FILE       |
 * | TOEIC_ACADEMY         | 토익사관학교    | COUNT_BASED  | UNREQUIRED |
 * | READ_A_THON           | 독서마라톤      | SCORE_BASED  | FILE       |
 * | VOLUNTEER             | 봉사활동        | SCORE_BASED  | FILE       |
 * | PROJECT_PARTICIPATION | 프로젝트 참여   | COUNT_BASED  | EVIDENCE   |
 * | AWARD                 | 수상경력        | COUNT_BASED  | FILE       |
 * | ACADEMIC_GRADE        | 교과성적        | SCORE_BASED  | UNREQUIRED |
 * | EXTERNAL_ACTIVITY     | 외부활동        | COUNT_BASED  | FILE       |
 * | NCS                   | 직업기초능력평가 | SCORE_BASED | FILE       |
 * | NEWRROW_SCHOOL        | 뉴로우스쿨참여  | SCORE_BASED  | FILE       |
 *
 * `VOLUNTEER`는 공식 기준상 1365 등 자원봉사 활동 증빙 파일 제출이 필요해 증빙 방식이 FILE이다
 * (교육과정 봉사활동 제외 여부는 제출된 증빙을 보고 검토 교사가 승인 단계에서 판단한다). `TOEIC_ACADEMY`의
 * 증빙 방식은 점수 추가 API 설계(`docs/plans/score-add-api-plan.md`)에서 재정의되었다 — 실제
 * `category_tb`의 `evidence_type` 값도 이에 맞게 갱신되어야 한다.
 *
 * `JLPT`/`JPT`는 별도 `category_tb` 행을 갖지 않고 `TOEIC`과 같은 행을 공유한다 — 제출 시 카테고리 조회가
 * TOEIC으로 캐노니컬 매핑되며([team.incube.gsmc.domain.score.service.AppendScoreSupport]), 인정점수도 TOEIC과
 * 동일한 캡 안에서 계산된다. 실제로 제출된 시험 종류(TOEIC/JLPT/JPT)는 감사 추적을 위해
 * [team.incube.gsmc.domain.score.Score.submittedCategoryType]에 별도로 보존된다. `TOEIC_ACADEMY`는
 * 참여 승인 시 TOEIC 인정점수에 가산되는 보너스로만 반영되며 총점에 독립적으로 집계되지 않는다
 * ([team.incube.gsmc.domain.score.ScoreAggregator]).
 */
enum class CategoryType {
    CERTIFICATE,
    TOPCIT,
    TOEIC,
    JLPT,
    JPT,
    TOEIC_ACADEMY,
    READ_A_THON,
    VOLUNTEER,
    PROJECT_PARTICIPATION,
    AWARD,
    ACADEMIC_GRADE,
    EXTERNAL_ACTIVITY,

    /** 직업기초능력평가 — 평균 등급 반올림, 최대 5점 */
    NCS,

    /** 뉴로우스쿨참여 — 참여 성실도 회고온도 20점당 1점, 최대 5점 */
    NEWRROW_SCHOOL,
}
