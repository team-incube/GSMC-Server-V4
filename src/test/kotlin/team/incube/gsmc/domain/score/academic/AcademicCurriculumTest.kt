package team.incube.gsmc.domain.score.academic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class AcademicCurriculumTest :
    BehaviorSpec({
        val curriculum = TestCurriculum.curriculum

        fun names(
            grade: Int,
            semester: Int,
            department: Department,
        ) = curriculum.subjectsOf(grade, semester, department).map { it.name }

        Given("subjectsOf") {
            When("1학년 1학기 스마트IoT과면") {
                Then("공통 과목과 IoT 전공 과목만 나온다") {
                    val subjects = names(1, 1, Department.SMART_IOT)

                    subjects shouldContainAll listOf("공통국어1", "전기전자일반", "디지털논리회로")
                    subjects shouldNotContain "웹프로그래밍기초"
                }
            }

            When("1학년 1학기 SW개발과면") {
                Then("웹프로그래밍기초가 있고 IoT 전공 과목은 없다") {
                    val subjects = names(1, 1, Department.SOFTWARE)

                    subjects shouldContain "웹프로그래밍기초"
                    subjects shouldNotContain "전기전자일반"
                }
            }

            When("석차등급이 없는 3단계 과목은") {
                Then("목록에서 빠져 있다") {
                    names(1, 1, Department.SOFTWARE) shouldNotContain "스포츠문화"
                    names(1, 2, Department.SOFTWARE) shouldNotContain "음악 감상과 비평"
                    names(2, 1, Department.AI) shouldNotContain "미술"
                    names(3, 1, Department.AI) shouldNotContain "실용국어"
                }
            }

            When("2학년 2학기 스마트IoT과면") {
                Then("택1 그룹에는 웹프로그래밍만 있다") {
                    curriculum
                        .subjectsOf(2, 2, Department.SMART_IOT)
                        .filter { it.electiveGroup != null }
                        .map { it.name } shouldBe listOf("웹프로그래밍")
                }
            }

            When("2학년 2학기 SW개발과면") {
                Then("택1 그룹에 웹프로그래밍과 인공지능 일반이 있다") {
                    curriculum
                        .subjectsOf(2, 2, Department.SOFTWARE)
                        .filter { it.electiveGroup != null }
                        .map { it.name } shouldBe listOf("웹프로그래밍", "인공지능 일반")
                }
            }

            When("목록이 없는 학년이면") {
                Then("빈 리스트다") {
                    names(4, 1, Department.SOFTWARE) shouldBe emptyList()
                }
            }
        }

        Given("usesAchievement") {
            When("3학년이면") {
                Then("성취도로 입력한다") {
                    curriculum.usesAchievement(3) shouldBe true
                    curriculum.usesAchievement(1) shouldBe false
                    curriculum.usesAchievement(2) shouldBe false
                }
            }
        }

        Given("설정 파일 academic-curriculum.yml") {
            When("바인딩하면") {
                Then("6개 학기와 성취도 학년(3)이 읽힌다") {
                    TestCurriculum.properties.curriculum.map { it.grade to it.semester } shouldBe
                        listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2)
                    TestCurriculum.properties.achievementGrades shouldBe setOf(3)
                }
            }
        }

        Given("생성 시점 검증") {
            val all = setOf(Department.SOFTWARE, Department.SMART_IOT, Department.AI)

            fun semester(
                semester: Int = 1,
                vararg subjects: AcademicSubject,
            ) = AcademicCurriculumSemester(grade = 1, semester = semester, subjects = subjects.toList())

            listOf(
                "학기가 1·2가 아니면" to listOf(semester(3, AcademicSubject("국어", all))),
                "한 학기에 과목명이 중복되면" to listOf(semester(1, AcademicSubject("국어", all), AcademicSubject("국어", all))),
                "수강 학과가 비어 있으면" to listOf(semester(1, AcademicSubject("국어", emptySet()))),
                "택1 그룹 과목이 선택자 과목이기도 하면" to
                    listOf(semester(1, AcademicSubject("일본어", all, electiveGroup = "제2외국어", optional = true))),
                "같은 학년·학기가 두 번 정의되면" to
                    listOf(semester(1, AcademicSubject("국어", all)), semester(1, AcademicSubject("수학", all))),
            ).forEach { (condition, semesters) ->
                When(condition) {
                    Then("IllegalArgumentException이 발생해 기동 단계에서 실패한다") {
                        shouldThrow<IllegalArgumentException> { AcademicCurriculum(semesters, emptySet()) }
                    }
                }
            }
        }
    })
