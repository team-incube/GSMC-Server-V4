package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicGradeEntry
import team.incube.gsmc.domain.score.academic.AcademicGradeEntryCommand
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.score.port.out.AcademicGradeEntryPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.USER_ID
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.academicGradeCategory
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.completeCommands
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.score
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.student
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class ModifyMyAcademicGradeSheetServiceTest :
    BehaviorSpec({
        val support = mockk<AcademicGradeSheetSupport>()
        val academicGradeEntryPersistencePort = mockk<AcademicGradeEntryPersistencePort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            ModifyMyAcademicGradeSheetService(
                academicGradeSheetSupport = support,
                academicGradeEntryPersistencePort = academicGradeEntryPersistencePort,
                scorePersistencePort = scorePersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
            )
        val savedEntries = slot<List<AcademicGradeEntry>>()

        fun givenStudent(
            grade: Int,
            department: Department,
        ) {
            every { support.loadStudent(USER_ID) } returns
                AcademicGradeSheetSupport.Student(student(grade = grade), grade, department)
        }

        beforeEach {
            clearAllMocks()
            savedEntries.clear()
            every { memberUtil.getCurrentUserId() } returns USER_ID
            every { support.ensureEditable(USER_ID, any()) } just runs
            every { support.findPending(USER_ID) } returns null
            every { academicGradeEntryPersistencePort.replaceAll(USER_ID, any(), capture(savedEntries)) } answers
                { thirdArg() }
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
            givenStudent(1, Department.SOFTWARE)
        }

        Given("입력표를 저장할 때") {
            When("일부 과목만 입력하고 심사 중인 점수가 없으면") {
                Then("입력한 과목만 교체 저장하고 미완성 표를 돌려준다") {
                    val result = service.execute(listOf(AcademicGradeEntryCommand(1, "공통국어1", "2")))

                    savedEntries.captured shouldHaveSize 1
                    savedEntries.captured.first().subjectGrade shouldBe 2
                    result.isComplete() shouldBe false
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }

            When("3학년이 성취도로 입력하면") {
                Then("1~5로 환산해 저장한다") {
                    givenStudent(3, Department.AI)

                    service.execute(listOf(AcademicGradeEntryCommand(2, "알고리즘", "B")))

                    savedEntries.captured.first().subjectGrade shouldBe 2
                }
            }

            listOf(
                "다른 학과 과목이면" to listOf(AcademicGradeEntryCommand(1, "전기전자일반", "1")),
                "없는 학기이면" to listOf(AcademicGradeEntryCommand(3, "공통국어1", "1")),
                "같은 과목이 중복되면" to
                    listOf(AcademicGradeEntryCommand(1, "공통국어1", "1"), AcademicGradeEntryCommand(1, "공통국어1", "2")),
            ).forEach { (condition, commands) ->
                When(condition) {
                    Then("INVALID_ACADEMIC_SUBJECT 예외가 발생하고 저장하지 않는다") {
                        shouldThrow<GsmcException> {
                            service.execute(commands)
                        }.errorCode shouldBe ErrorCode.INVALID_ACADEMIC_SUBJECT
                        verify(exactly = 0) { academicGradeEntryPersistencePort.replaceAll(any(), any(), any()) }
                    }
                }
            }

            When("한 택1 그룹에서 2과목을 입력하면") {
                Then("INVALID_ACADEMIC_SUBJECT 예외가 발생한다") {
                    givenStudent(2, Department.SOFTWARE)

                    shouldThrow<GsmcException> {
                        service.execute(
                            listOf(
                                AcademicGradeEntryCommand(2, "웹프로그래밍", "1"),
                                AcademicGradeEntryCommand(2, "인공지능 일반", "1"),
                            ),
                        )
                    }.errorCode shouldBe ErrorCode.INVALID_ACADEMIC_SUBJECT
                }
            }

            When("등급이 범위를 벗어나면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    shouldThrow<GsmcException> {
                        service.execute(listOf(AcademicGradeEntryCommand(1, "공통국어1", "6")))
                    }.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("승인된 교과성적으로 잠겨 있으면") {
                Then("ACADEMIC_GRADE_LOCKED 예외가 발생하고 저장하지 않는다") {
                    every {
                        support.ensureEditable(USER_ID, any())
                    } throws GsmcException(ErrorCode.ACADEMIC_GRADE_LOCKED)

                    shouldThrow<GsmcException> {
                        service.execute(completeCommands())
                    }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_LOCKED
                    verify(exactly = 0) { academicGradeEntryPersistencePort.replaceAll(any(), any(), any()) }
                }
            }
        }

        Given("심사 중인 교과성적이 있을 때") {
            When("입력값을 바꿔 평균이 달라지면") {
                Then("같은 점수 행의 인정점수를 다시 계산해 저장하고 캐시를 무효화한다") {
                    val pending = score(ScoreStatus.PENDING, scoreValue = 8)
                    every { support.findPending(USER_ID) } returns pending
                    every { support.toScoreValue(any(), academicGradeCategory) } returns 7
                    val saved = slot<Score>()
                    every { scorePersistencePort.save(capture(saved)) } answers { firstArg() }

                    service.execute(completeCommands(value = "3"))

                    saved.captured.scoreId shouldBe pending.scoreId
                    saved.captured.scoreValue shouldBe 7
                    saved.captured.scoreStatus shouldBe ScoreStatus.PENDING
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(USER_ID) }
                }
            }

            When("인정점수가 그대로면") {
                Then("점수를 저장하지 않는다") {
                    every { support.findPending(USER_ID) } returns score(ScoreStatus.PENDING, scoreValue = 8)
                    every { support.toScoreValue(any(), academicGradeCategory) } returns 8

                    service.execute(completeCommands())

                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }

            When("입력표를 미완성으로 만들면") {
                Then("ACADEMIC_GRADE_INCOMPLETE 예외가 발생한다") {
                    every { support.findPending(USER_ID) } returns score(ScoreStatus.PENDING, scoreValue = 8)
                    every {
                        support.toScoreValue(any(), academicGradeCategory)
                    } throws GsmcException(ErrorCode.ACADEMIC_GRADE_INCOMPLETE)

                    shouldThrow<GsmcException> {
                        service.execute(listOf(AcademicGradeEntryCommand(1, "공통국어1", "2")))
                    }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_INCOMPLETE
                }
            }
        }
    })
