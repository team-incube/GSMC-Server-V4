package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.score.port.out.AcademicGradeEntryPersistencePort
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.USER_ID
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.academicGradeCategory
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.completeEntries
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.score
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.student
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDateTime

class AcademicGradeSheetSupportTest :
    BehaviorSpec({
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val academicGradeEntryPersistencePort = mockk<AcademicGradeEntryPersistencePort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val support =
            AcademicGradeSheetSupport(memberPersistencePort, academicGradeEntryPersistencePort, scorePersistencePort)

        beforeEach { clearAllMocks() }

        Given("loadStudent") {
            When("3반 2학년 학생이면") {
                Then("학년과 스마트IoT과로 판정한다") {
                    every { memberPersistencePort.findByUserId(USER_ID) } returns student(grade = 2, classNumber = 3)

                    val result = support.loadStudent(USER_ID)

                    result.grade shouldBe 2
                    result.department shouldBe Department.SMART_IOT
                }
            }

            When("학생이 없으면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberPersistencePort.findByUserId(USER_ID) } returns null

                    shouldThrow<GsmcException> { support.loadStudent(USER_ID) }.errorCode shouldBe
                        ErrorCode.USER_NOT_FOUND
                }
            }

            When("학년이 없으면") {
                Then("INVALID_GRADE 예외가 발생한다") {
                    every { memberPersistencePort.findByUserId(USER_ID) } returns student(grade = null)

                    shouldThrow<GsmcException> { support.loadStudent(USER_ID) }.errorCode shouldBe
                        ErrorCode.INVALID_GRADE
                }
            }

            When("반 번호가 없으면") {
                Then("INVALID_CLASS_NUMBER 예외가 발생한다") {
                    every { memberPersistencePort.findByUserId(USER_ID) } returns student(classNumber = null)

                    shouldThrow<GsmcException> {
                        support.loadStudent(USER_ID)
                    }.errorCode shouldBe ErrorCode.INVALID_CLASS_NUMBER
                }
            }
        }

        Given("toScoreValue") {
            When("입력표가 완성되면") {
                Then("10 - 반올림(평균)으로 환산한다") {
                    val sheet = AcademicGradeSheet.of(1, Department.SOFTWARE, completeEntries(subjectGrade = 3))

                    support.toScoreValue(sheet, academicGradeCategory) shouldBe 7
                }
            }

            When("입력표가 미완성이면") {
                Then("ACADEMIC_GRADE_INCOMPLETE 예외가 발생한다") {
                    val sheet = AcademicGradeSheet.of(1, Department.SOFTWARE, emptyList())

                    shouldThrow<GsmcException> {
                        support.toScoreValue(sheet, academicGradeCategory)
                    }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_INCOMPLETE
                }
            }
        }

        Given("isInCurrentSchoolYear") {
            val now = LocalDateTime.of(2026, 10, 1, 12, 0)

            When("이번 학년도 3월 1일 이후면") {
                Then("true다") {
                    support.isInCurrentSchoolYear(LocalDateTime.of(2026, 3, 1, 0, 0), now) shouldBe true
                }
            }

            When("이번 학년도 시작 전이면") {
                Then("false다") {
                    support.isInCurrentSchoolYear(LocalDateTime.of(2026, 2, 28, 23, 59), now) shouldBe false
                }
            }
        }

        Given("ensureEditable") {
            val now = LocalDateTime.of(2026, 10, 1, 12, 0)

            When("이번 학년도에 승인된 점수가 있고 심사 중인 재신청이 없으면") {
                Then("ACADEMIC_GRADE_LOCKED 예외가 발생한다") {
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns null
                    every {
                        scorePersistencePort.findApprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns score(ScoreStatus.APPROVED, 7, updatedAt = LocalDateTime.of(2026, 3, 1, 0, 0))

                    shouldThrow<GsmcException> {
                        support.ensureEditable(USER_ID, now)
                    }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_LOCKED
                }
            }

            When("승인된 점수가 지난 학년도(2월 이전)의 것이면") {
                Then("새 학년 입력을 막지 않는다") {
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns null
                    every {
                        scorePersistencePort.findApprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns score(ScoreStatus.APPROVED, 7, updatedAt = LocalDateTime.of(2026, 2, 28, 23, 59))

                    shouldNotThrowAny { support.ensureEditable(USER_ID, now) }
                }
            }

            When("학년도 경계(1월)에 판단하면") {
                Then("전년도 3월 이후 승인은 이번 학년도로 본다") {
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns null
                    every {
                        scorePersistencePort.findApprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns score(ScoreStatus.APPROVED, 7, updatedAt = LocalDateTime.of(2026, 12, 20, 0, 0))

                    shouldThrow<GsmcException> {
                        support.ensureEditable(USER_ID, LocalDateTime.of(2027, 1, 10, 0, 0))
                    }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_LOCKED
                }
            }

            When("심사 중이거나 반려된 재신청이 있으면") {
                Then("승인된 점수가 있어도 수정할 수 있다") {
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(USER_ID, CategoryType.ACADEMIC_GRADE)
                    } returns score(ScoreStatus.REJECTED)

                    shouldNotThrowAny { support.ensureEditable(USER_ID, now) }
                }
            }
        }
    })
