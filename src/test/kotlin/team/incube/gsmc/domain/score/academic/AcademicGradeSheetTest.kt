package team.incube.gsmc.domain.score.academic

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class AcademicGradeSheetTest :
    BehaviorSpec({
        val userId = 1L
        val curriculum = TestCurriculum.curriculum

        fun entry(
            grade: Int,
            semester: Int,
            subjectName: String,
            subjectGrade: Int,
        ) = AcademicGradeEntry(
            userId = userId,
            grade = grade,
            semester = semester,
            subjectName = subjectName,
            subjectGrade = subjectGrade,
        )

        /** [grade]학년 [department]의 [semester]학기 필수 과목을 전부 [subjectGrade]로 채운다 */
        fun required(
            grade: Int,
            semester: Int,
            department: Department,
            subjectGrade: Int,
        ) = curriculum
            .subjectsOf(grade, semester, department)
            .filter { it.electiveGroup == null && !it.optional }
            .map { entry(grade, semester, it.name, subjectGrade) }

        Given("rawAverage") {
            When("두 학기 과목 수가 달라도") {
                Then("학기 평균을 먼저 낸 뒤 두 평균의 평균을 낸다") {
                    // 1학년 IoT: 1학기 9과목 전부 1등급, 2학기 8과목 중 1과목만 5등급, 나머지 1등급
                    val first = required(1, 1, Department.SMART_IOT, 1)
                    val second =
                        required(1, 2, Department.SMART_IOT, 1).mapIndexed { index, e ->
                            if (index == 0) e.copy(subjectGrade = 5) else e
                        }
                    val sheet = AcademicGradeSheet.of(curriculum, 1, Department.SMART_IOT, first + second)

                    val firstAverage = 1.0
                    val secondAverage = (5.0 + (second.size - 1) * 1.0) / second.size
                    sheet.rawAverage()!! shouldBe ((firstAverage + secondAverage) / 2 plusOrMinus 1e-9)
                    sheet.semesters[1].average()!! shouldBe (secondAverage plusOrMinus 1e-9)
                }
            }

            When("한 학기라도 미완성이면") {
                Then("null이다") {
                    val sheet =
                        AcademicGradeSheet.of(
                            curriculum,
                            1,
                            Department.SOFTWARE,
                            required(1, 1, Department.SOFTWARE, 2),
                        )

                    sheet.isComplete() shouldBe false
                    sheet.rawAverage() shouldBe null
                    sheet.semesters[0].average() shouldBe 2.0
                    sheet.semesters[1].average() shouldBe null
                }
            }

            When("목록에 없는 과목 입력이 섞여 있으면") {
                Then("무시한다") {
                    val entries =
                        required(1, 1, Department.SOFTWARE, 2) + required(1, 2, Department.SOFTWARE, 2) +
                            entry(1, 1, "전기전자일반", 5)
                    val sheet = AcademicGradeSheet.of(curriculum, 1, Department.SOFTWARE, entries)

                    sheet.rawAverage() shouldBe 2.0
                }
            }
        }

        Given("택1 그룹") {
            val base = required(2, 1, Department.SOFTWARE, 3) + required(2, 2, Department.SOFTWARE, 3)

            When("그룹에서 아무것도 입력하지 않으면") {
                Then("미완성이다") {
                    AcademicGradeSheet.of(curriculum, 2, Department.SOFTWARE, base).isComplete() shouldBe false
                }
            }

            When("그룹에서 1개를 입력하면") {
                Then("완성이고 그 과목이 평균에 들어간다") {
                    val sheet =
                        AcademicGradeSheet.of(curriculum, 2, Department.SOFTWARE, base + entry(2, 2, "인공지능 일반", 1))

                    sheet.isComplete() shouldBe true
                    val requiredCount = required(2, 2, Department.SOFTWARE, 3).size
                    sheet.semesters[1].average()!! shouldBe
                        ((requiredCount * 3.0 + 1.0) / (requiredCount + 1) plusOrMinus 1e-9)
                }
            }

            When("그룹에서 2개를 입력하면") {
                Then("미완성이다") {
                    val sheet =
                        AcademicGradeSheet.of(
                            curriculum,
                            2,
                            Department.SOFTWARE,
                            base + entry(2, 2, "인공지능 일반", 1) + entry(2, 2, "웹프로그래밍", 1),
                        )

                    sheet.isComplete() shouldBe false
                }
            }
        }

        Given("3학년 입력표") {
            val base =
                required(3, 1, Department.AI, 2) + required(3, 2, Department.AI, 2) +
                    entry(3, 1, "일본어Ⅰ", 2) + entry(3, 2, "일본어I", 2)

            When("선택자 과목(웹 프로그래밍 실무)을 입력하지 않아도") {
                Then("완성이다") {
                    AcademicGradeSheet.of(curriculum, 3, Department.AI, base).isComplete() shouldBe true
                }
            }

            When("선택자 과목을 입력하면") {
                Then("평균에 들어가고 성취도 문자도 함께 보인다") {
                    val sheet = AcademicGradeSheet.of(curriculum, 3, Department.AI, base + entry(3, 1, "웹 프로그래밍 실무", 5))

                    sheet.semesters[0].average()!! shouldBe
                        ((sheet.semesters[0].rows.count { it.subjectGrade != null } - 1) * 2.0 + 5.0) /
                        sheet.semesters[0].rows.count { it.subjectGrade != null }
                    sheet.semesters[0]
                        .rows
                        .first { it.subjectName == "웹 프로그래밍 실무" }
                        .achievement shouldBe "E"
                }
            }
        }
    })
