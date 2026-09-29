package team.incube.gsmc.domain.sheet.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.sheet.ScoreSheetRow
import team.incube.gsmc.domain.sheet.SheetStudent
import team.incube.gsmc.domain.sheet.port.out.SheetGeneratorPort
import team.incube.gsmc.domain.sheet.port.out.SheetMemberPersistencePort
import team.incube.gsmc.domain.sheet.port.out.SheetScorePersistencePort
import team.incube.gsmc.domain.sheet.port.out.SheetStoragePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchScoreSheetServiceTest :
    BehaviorSpec({
        val memberPort = mockk<SheetMemberPersistencePort>()
        val scorePort = mockk<SheetScorePersistencePort>()
        val generator = mockk<SheetGeneratorPort>()
        val storage = mockk<SheetStoragePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchScoreSheetService(memberPort, scorePort, generator, storage, memberUtil)
        val category =
            Category(
                categoryId = 1L,
                weight = 1,
                categoryEnglishName = "academic",
                categoryKoreanName = "교과성적",
                categoryMaximumValue = 100,
                isAccumulated = true,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.ACADEMIC_GRADE,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )
        val approvedScore =
            ScoreCalculationRow(
                userId = 1L,
                category = category,
                scoreStatus = ScoreStatus.APPROVED,
                scoreValue = 12,
                updatedAt = LocalDateTime.now(),
            )
        val students =
            listOf(
                SheetStudent(2L, 2, 3, 2, "둘째", UserRole.STUDENT),
                SheetStudent(1L, 2, 3, 1, "첫째", UserRole.STUDENT),
                SheetStudent(3L, 2, 3, 3, "교사", UserRole.TEACHER),
            )

        beforeTest {
            clearMocks(memberPort, scorePort, generator, storage, memberUtil)
            every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
            every { scorePort.findApprovedScoresByUserIds(any()) } returns mapOf(1L to listOf(approvedScore))
            every { generator.generate(any(), any()) } returns byteArrayOf(1, 2, 3)
            every { storage.upload(any(), any(), any()) } just runs
            every { storage.createPresignedDownloadUrl(any()) } returns "https://download.example/sheet"
        }

        Given("학급 점수 Sheet를 요청하면") {
            listOf(1 to 1, 3 to 4).forEach { (grade, classNumber) ->
                When("학년 ${grade}와 반 ${classNumber}를 전달하면") {
                    Then("정상적인 범위로 처리한다") {
                        every { memberPort.findAllStudentsByGradeAndClass(grade, classNumber) } returns emptyList()
                        every { scorePort.findApprovedScoresByUserIds(emptyList()) } returns emptyMap()

                        service.execute(grade, classNumber)

                        verify(exactly = 1) { memberPort.findAllStudentsByGradeAndClass(grade, classNumber) }
                    }
                }
            }

            When("ROOT가 정상적인 학년과 반을 전달하면") {
                Then("학생을 번호순으로 정렬하고 URL을 반환한다") {
                    every { memberPort.findAllStudentsByGradeAndClass(2, 3) } returns students
                    val rowSlot = slot<List<ScoreSheetRow>>()
                    every { generator.generate(capture(rowSlot), "2학년 3반") } returns byteArrayOf(1, 2, 3)

                    val result = service.execute(2, 3)

                    result shouldBe "https://download.example/sheet"
                    rowSlot.captured.map { it.number } shouldBe listOf(1, 2)
                    rowSlot.captured.map { it.totalScore } shouldBe listOf(12, 0)
                    verify {
                        scorePort.findApprovedScoresByUserIds(listOf(1L, 2L))
                        storage.upload(
                            match { it.startsWith("sheets/class/2/3/") && it.endsWith(".xlsx") },
                            byteArrayOf(1, 2, 3),
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        )
                    }
                }
            }

            listOf(0, 4).forEach { invalidGrade ->
                When("학년이 ${invalidGrade}이면") {
                    Then("BAD_REQUEST 오류를 반환한다") {
                        val exception = shouldThrow<GsmcException> { service.execute(invalidGrade, 1) }

                        exception.errorCode shouldBe ErrorCode.INVALID_GRADE
                    }
                }
            }

            listOf(0, 5).forEach { invalidClassNumber ->
                When("반이 ${invalidClassNumber}이면") {
                    Then("BAD_REQUEST 오류를 반환한다") {
                        val exception = shouldThrow<GsmcException> { service.execute(2, invalidClassNumber) }

                        exception.errorCode shouldBe ErrorCode.INVALID_CLASS_NUMBER
                    }
                }
            }
        }

        Given("학년 점수 Sheet를 요청하면") {
            When("ROOT가 정상적인 학년을 전달하면") {
                Then("해당 학년 학생만 전달하고 URL을 반환한다") {
                    every { memberPort.findAllStudentsByGrade(2) } returns students
                    val rowSlot = slot<List<ScoreSheetRow>>()
                    every { generator.generate(capture(rowSlot), "2학년") } returns byteArrayOf(1)

                    service.execute(2)

                    rowSlot.captured.map { it.number } shouldBe listOf(1, 2)
                    verify(exactly = 1) { memberPort.findAllStudentsByGrade(2) }
                }
            }

            When("학생이 없는 학년이면") {
                Then("헤더만 있는 파일 생성을 위해 빈 목록을 전달한다") {
                    every { memberPort.findAllStudentsByGrade(3) } returns emptyList()
                    every { scorePort.findApprovedScoresByUserIds(emptyList()) } returns emptyMap()

                    service.execute(3)

                    verify(exactly = 1) { generator.generate(emptyList(), "3학년") }
                }
            }
        }

        Given("인증 사용자가 ROOT가 아니면") {
            When("학생 또는 교사가 Sheet를 요청하면") {
                Then("FORBIDDEN 오류를 반환한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    shouldThrow<GsmcException> { service.execute(2) }.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }

            When("인증 정보가 없으면") {
                Then("기존 인증 오류를 반환한다") {
                    every { memberUtil.getCurrentUserRole() } throws GsmcException(ErrorCode.INVALID_TOKEN)

                    shouldThrow<GsmcException> { service.execute(2) }.errorCode shouldBe ErrorCode.INVALID_TOKEN
                }
            }
        }

        Given("Sheet 생성 또는 저장에 실패하면") {
            When("Excel 생성기가 오류를 던지면") {
                Then("오류를 그대로 전파하고 저장하지 않는다") {
                    every { memberPort.findAllStudentsByGrade(2) } returns students
                    val expected = GsmcException(ErrorCode.SHEET_GENERATION_FAILED)
                    every { generator.generate(any(), any()) } throws expected

                    shouldThrow<GsmcException> { service.execute(2) }.errorCode shouldBe expected.errorCode
                    verify(exactly = 0) { storage.upload(any(), any(), any()) }
                }
            }

            When("S3 업로드가 오류를 던지면") {
                Then("오류를 그대로 전파한다") {
                    every { memberPort.findAllStudentsByGrade(2) } returns students
                    every { storage.upload(any(), any(), any()) } throws GsmcException(ErrorCode.SHEET_UPLOAD_FAILED)

                    shouldThrow<GsmcException> { service.execute(2) }.errorCode shouldBe ErrorCode.SHEET_UPLOAD_FAILED
                }
            }

            When("Presigned URL 생성이 오류를 던지면") {
                Then("오류를 그대로 전파한다") {
                    every { memberPort.findAllStudentsByGrade(2) } returns students
                    every { storage.createPresignedDownloadUrl(any()) } throws
                        GsmcException(ErrorCode.SHEET_PRESIGNED_URL_FAILED)

                    shouldThrow<GsmcException> { service.execute(2) }.errorCode shouldBe
                        ErrorCode.SHEET_PRESIGNED_URL_FAILED
                }
            }
        }
    })
