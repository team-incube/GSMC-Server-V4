package team.incube.gsmc.domain.score.academic

import team.incube.gsmc.domain.score.academic.Department.AI
import team.incube.gsmc.domain.score.academic.Department.SMART_IOT
import team.incube.gsmc.domain.score.academic.Department.SOFTWARE

/**
 * 2026학년도 학년·학기별 교과성적 입력 대상 과목 목록
 *
 * 출처는 2026학년도 교수학습 및 평가운영 계획(1학기 HWP, 2학기 PDF)과 과목별 성적 산출 방식 자료다.
 * 1·2학년은 석차등급이 있는 과목만 넣고, 성취도 3단계 과목(스포츠·음악·미술 등)은 뺐다.
 * 3학년 전공과목은 석차등급이 없어 성취도(A~E)로 입력받는다. 이 기준은 임시이며 #233에서 확정한다.
 * 학년도가 바뀌면 새 계획서에 맞춰 이 목록을 갱신해야 한다.
 */
object AcademicCurriculum {
    private val ALL = setOf(SOFTWARE, SMART_IOT, AI)
    private const val SECOND_LANGUAGE = "제2외국어"
    private const val GRADE2_SECOND_ELECTIVE = "2학년 2학기 선택"

    private val subjects: Map<Pair<Int, Int>, List<AcademicSubject>> =
        mapOf(
            (1 to 1) to
                listOf(
                    AcademicSubject("공통국어1", ALL),
                    AcademicSubject("공통수학1", ALL),
                    AcademicSubject("공통영어1", ALL),
                    AcademicSubject("통합과학1", ALL),
                    AcademicSubject("컴퓨터 시스템 일반", ALL),
                    AcademicSubject("성공적인 직업 생활", ALL),
                    AcademicSubject("파이썬 프로그래밍", ALL),
                    AcademicSubject("웹프로그래밍기초", setOf(SOFTWARE, AI)),
                    AcademicSubject("전기전자일반", setOf(SMART_IOT)),
                    AcademicSubject("디지털논리회로", setOf(SMART_IOT)),
                ),
            (1 to 2) to
                listOf(
                    AcademicSubject("공통국어2", ALL),
                    AcademicSubject("공통수학2", ALL),
                    AcademicSubject("공통영어2", ALL),
                    AcademicSubject("통합과학2", ALL),
                    AcademicSubject("자료 구조", ALL),
                    AcademicSubject("자바 프로그래밍", ALL),
                    AcademicSubject("운영체제", setOf(SOFTWARE, AI)),
                    AcademicSubject("사물 인터넷과 센서 제어", setOf(SMART_IOT)),
                    AcademicSubject("가전 기기 시스템 소프트웨어 개발", setOf(SMART_IOT)),
                ),
            (2 to 1) to
                listOf(
                    AcademicSubject("대수", ALL),
                    AcademicSubject("영어Ⅰ", ALL),
                    AcademicSubject("한국사1", ALL),
                    AcademicSubject("통합사회1", ALL),
                    AcademicSubject("데이터베이스 프로그래밍", setOf(SOFTWARE, AI)),
                    AcademicSubject("응용 프로그래밍 개발", setOf(SOFTWARE)),
                    AcademicSubject("네트워크 프로그래밍", setOf(SOFTWARE)),
                    AcademicSubject("사물 인터넷과 센서 제어", setOf(SMART_IOT)),
                    AcademicSubject("산업용 전자기기 소프트웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("가전 기기 하드웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("빅데이터분석", setOf(AI)),
                    AcademicSubject("인공지능 서비스 구현", setOf(AI)),
                ),
            (2 to 2) to
                listOf(
                    AcademicSubject("미적분Ⅰ", ALL),
                    AcademicSubject("영어Ⅱ", ALL),
                    AcademicSubject("한국사2", ALL),
                    AcademicSubject("통합사회2", ALL),
                    AcademicSubject("노동 인권과 산업 안전 보건", ALL),
                    AcademicSubject("응용 프로그래밍 개발", setOf(SOFTWARE)),
                    AcademicSubject("네트워크 프로그래밍", setOf(SOFTWARE)),
                    AcademicSubject("가전 기기 시스템 소프트웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("산업용 전자 기기 소프트웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("산업용 전자 기기 하드웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("빅 데이터 분석", setOf(AI)),
                    AcademicSubject("인공지능 서비스 구현", setOf(AI)),
                    AcademicSubject("웹프로그래밍", ALL, electiveGroup = GRADE2_SECOND_ELECTIVE),
                    AcademicSubject("인공지능 일반", setOf(SOFTWARE, AI), electiveGroup = GRADE2_SECOND_ELECTIVE),
                ),
            (3 to 1) to
                listOf(
                    AcademicSubject("소프트웨어공학실무", setOf(SOFTWARE)),
                    AcademicSubject("알고리즘", setOf(SOFTWARE, AI)),
                    AcademicSubject("IT 프로젝트 관리", setOf(SOFTWARE, AI)),
                    AcademicSubject("산업용 전자기기 소프트웨어 개발", setOf(SMART_IOT)),
                    AcademicSubject("로봇 지능 개발", setOf(SMART_IOT)),
                    AcademicSubject("착용형 스마트기기 개발", setOf(SMART_IOT)),
                    AcademicSubject("인공지능모델링", setOf(AI)),
                    AcademicSubject("인공지능 서비스 구현", setOf(AI)),
                    AcademicSubject("웹 프로그래밍 실무", ALL, optional = true),
                    AcademicSubject("일본어Ⅰ", ALL, electiveGroup = SECOND_LANGUAGE),
                    AcademicSubject("중국어Ⅰ", ALL, electiveGroup = SECOND_LANGUAGE),
                ),
            (3 to 2) to
                listOf(
                    AcademicSubject("소프트웨어공학실무", setOf(SOFTWARE)),
                    AcademicSubject("알고리즘", setOf(SOFTWARE, AI)),
                    AcademicSubject("IT 프로젝트 관리", setOf(SOFTWARE, AI)),
                    AcademicSubject("산업용 전자기기 소프트웨어개발", setOf(SMART_IOT)),
                    AcademicSubject("로봇 지능 개발", setOf(SMART_IOT)),
                    AcademicSubject("착용형 스마트기기 개발", setOf(SMART_IOT)),
                    AcademicSubject("인공지능 모델링", setOf(AI)),
                    AcademicSubject("인공지능 서비스 구현", setOf(AI)),
                    AcademicSubject("웹 프로그래밍 실무", ALL, optional = true),
                    AcademicSubject("일본어I", ALL, electiveGroup = SECOND_LANGUAGE),
                    AcademicSubject("중국어Ⅰ", ALL, electiveGroup = SECOND_LANGUAGE),
                ),
        )

    /** 성적을 입력받는 학기 */
    val SEMESTERS = listOf(1, 2)

    /** 성취도(A~E)로 입력받는 학년. 나머지 학년은 석차등급(1~5)으로 받는다 */
    private val ACHIEVEMENT_GRADES = setOf(3)

    /** [grade]학년 [semester]학기에 [department]가 수강하는 과목. 목록이 없으면 빈 리스트 */
    fun subjectsOf(
        grade: Int,
        semester: Int,
        department: Department,
    ): List<AcademicSubject> = subjects[grade to semester].orEmpty().filter { department in it.departments }

    /** [grade]학년이 석차등급 대신 성취도(A~E)로 입력하는지 */
    fun usesAchievement(grade: Int): Boolean = grade in ACHIEVEMENT_GRADES
}
