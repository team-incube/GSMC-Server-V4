package team.incube.gsmc.domain.project.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.project.port.out.ProjectMemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class ProjectServiceSupportTest :
    BehaviorSpec({
        val projectMemberPersistencePort = mockk<ProjectMemberPersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val support = ProjectServiceSupport(projectMemberPersistencePort, filePersistencePort)

        beforeEach { clearAllMocks() }

        fun user(userId: Long) =
            User(
                userId = userId,
                userName = "학생$userId",
                userEmail = "student$userId@gsm.hs.kr",
                userGrade = 2,
                userClassNumber = 3,
                userNumber = 4,
                userRole = UserRole.STUDENT,
            )

        fun file(
            fileId: Long,
            userId: Long,
        ) = File(
            fileId = fileId,
            userId = userId,
            fileKey = "u/$userId/$fileId.png",
            fileOriginalName = "원본$fileId.png",
            fileStoredName = "$fileId.png",
        )

        val validDescription = "설".repeat(300)

        Given("제출 완료 프로젝트의 내용을 검증할 때") {
            When("제목 길이가 경계 안이면") {
                Then("1자와 100자는 통과한다") {
                    shouldNotThrowAny { support.validateFinalContent("a", validDescription) }
                    shouldNotThrowAny { support.validateFinalContent("a".repeat(100), validDescription) }
                }
            }

            When("제목 길이가 경계를 벗어나면") {
                Then("0자와 101자는 INVALID_PROJECT_INPUT 예외가 발생한다") {
                    shouldThrow<GsmcException> { support.validateFinalContent("", validDescription) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                    shouldThrow<GsmcException> { support.validateFinalContent("a".repeat(101), validDescription) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                }
            }

            When("설명 길이가 경계 안이면") {
                Then("300자와 2000자는 통과한다") {
                    shouldNotThrowAny { support.validateFinalContent("제목", "설".repeat(300)) }
                    shouldNotThrowAny { support.validateFinalContent("제목", "설".repeat(2000)) }
                }
            }

            When("설명 길이가 경계를 벗어나면") {
                Then("299자와 2001자는 INVALID_PROJECT_INPUT 예외가 발생한다") {
                    shouldThrow<GsmcException> { support.validateFinalContent("제목", "설".repeat(299)) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                    shouldThrow<GsmcException> { support.validateFinalContent("제목", "설".repeat(2001)) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                }
            }
        }

        Given("프로젝트 부분 수정 내용을 검증할 때") {
            When("제목과 설명이 모두 지정되지 않으면") {
                Then("검증을 건너뛴다") {
                    shouldNotThrowAny { support.validatePatchContent(null, null) }
                }
            }

            When("제목만 지정되고 길이가 잘못되면") {
                Then("INVALID_PROJECT_INPUT 예외가 발생한다") {
                    shouldThrow<GsmcException> { support.validatePatchContent("", null) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                }
            }

            When("설명만 지정되고 길이가 잘못되면") {
                Then("INVALID_PROJECT_INPUT 예외가 발생한다") {
                    shouldThrow<GsmcException> { support.validatePatchContent(null, "짧은 설명") }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                }
            }

            When("지정된 값이 모두 유효하면") {
                Then("통과한다") {
                    shouldNotThrowAny { support.validatePatchContent("제목", validDescription) }
                }
            }
        }

        Given("프로젝트 초안 내용을 검증할 때") {
            When("저장 가능한 길이면") {
                Then("초안은 최종본보다 느슨한 상한만 적용한다") {
                    shouldNotThrowAny { support.validateDraftContent("a".repeat(255), "b".repeat(65_535)) }
                    // 최종본에서는 실패할 짧은 설명도 초안에서는 허용된다.
                    shouldNotThrowAny { support.validateDraftContent("제목", "짧음") }
                }
            }

            When("저장 가능한 길이를 넘으면") {
                Then("INVALID_PROJECT_INPUT 예외가 발생한다") {
                    shouldThrow<GsmcException> { support.validateDraftContent("a".repeat(256), "설명") }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                    shouldThrow<GsmcException> { support.validateDraftContent("제목", "b".repeat(65_536)) }
                        .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
                }
            }
        }

        Given("참여자를 조회할 때") {
            When("소유자를 포함하도록 요청하면") {
                Then("중복을 제거하고 소유자를 포함해 조회한다") {
                    every { projectMemberPersistencePort.findAllByUserIds(listOf(2L, 3L, 1L)) } returns
                        listOf(user(3L), user(1L), user(2L))

                    val result = support.findParticipants(listOf(2L, 3L, 2L), ownerId = 1L)

                    // 포트가 뒤섞인 순서로 돌려줘도 입력 순서를 그대로 유지해야 한다.
                    result.map { it.userId } shouldBe listOf(2L, 3L, 1L)
                }
            }

            When("소유자를 제외하도록 요청하면") {
                Then("소유자를 포함하지 않는다") {
                    every { projectMemberPersistencePort.findAllByUserIds(listOf(2L, 3L)) } returns
                        listOf(user(2L), user(3L))

                    val result = support.findParticipants(listOf(2L, 3L), ownerId = 1L, includeOwner = false)

                    result.map { it.userId } shouldBe listOf(2L, 3L)
                }
            }

            When("존재하지 않는 참여자가 섞여 있으면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { projectMemberPersistencePort.findAllByUserIds(listOf(2L, 99L, 1L)) } returns
                        listOf(user(2L), user(1L))

                    shouldThrow<GsmcException> { support.findParticipants(listOf(2L, 99L), ownerId = 1L) }
                        .errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }
        }

        Given("첨부 파일을 검증할 때") {
            When("모두 본인 소유로 존재하면") {
                Then("입력 순서대로 파일을 반환한다") {
                    every { filePersistencePort.findAllByIdIn(listOf(10L, 11L)) } returns
                        listOf(file(11L, 1L), file(10L, 1L))

                    support.validateFiles(listOf(10L, 11L, 10L), ownerId = 1L).map { it.fileId } shouldBe
                        listOf(10L, 11L)
                }
            }

            When("존재하지 않는 파일이 있으면") {
                Then("FILE_NOT_FOUND 예외가 발생한다") {
                    every { filePersistencePort.findAllByIdIn(listOf(10L, 99L)) } returns listOf(file(10L, 1L))

                    shouldThrow<GsmcException> { support.validateFiles(listOf(10L, 99L), ownerId = 1L) }
                        .errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }

            When("타인 소유 파일이 섞여 있으면") {
                Then("존재 여부를 노출하지 않도록 동일한 FILE_NOT_FOUND로 처리한다") {
                    every { filePersistencePort.findAllByIdIn(listOf(10L, 11L)) } returns
                        listOf(file(10L, 1L), file(11L, 99L))

                    shouldThrow<GsmcException> { support.validateFiles(listOf(10L, 11L), ownerId = 1L) }
                        .errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }
        }
    })
