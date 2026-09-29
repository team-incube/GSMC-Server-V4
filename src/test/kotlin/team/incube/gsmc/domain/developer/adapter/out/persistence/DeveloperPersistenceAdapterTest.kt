package team.incube.gsmc.domain.developer.adapter.out.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import team.incube.gsmc.domain.developer.adapter.out.persistence.repository.DeveloperAlertJpaRepository
import team.incube.gsmc.domain.developer.adapter.out.persistence.repository.DeveloperEvidenceJpaRepository
import team.incube.gsmc.domain.developer.adapter.out.persistence.repository.DeveloperFileJpaRepository
import team.incube.gsmc.domain.developer.adapter.out.persistence.repository.DeveloperScoreJpaRepository
import team.incube.gsmc.domain.developer.adapter.out.persistence.repository.DeveloperUserJpaRepository
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.util.Optional

class DeveloperPersistenceAdapterTest :
    BehaviorSpec({
        val developerUserJpaRepository = mockk<DeveloperUserJpaRepository>()
        val developerAlertJpaRepository = mockk<DeveloperAlertJpaRepository>()
        val developerEvidenceJpaRepository = mockk<DeveloperEvidenceJpaRepository>()
        val developerScoreJpaRepository = mockk<DeveloperScoreJpaRepository>()
        val developerFileJpaRepository = mockk<DeveloperFileJpaRepository>()
        val adapter =
            DeveloperPersistenceAdapter(
                developerUserJpaRepository,
                developerAlertJpaRepository,
                developerEvidenceJpaRepository,
                developerScoreJpaRepository,
                developerFileJpaRepository,
            )

        beforeEach { clearAllMocks() }

        val userId = 1L

        fun userEntity() =
            UserJpaEntity(
                userId = userId,
                userName = "학생",
                userEmail = "student@gsm.hs.kr",
                userGrade = 2,
                userClassNumber = 3,
                userNumber = 4,
                userRole = UserRole.STUDENT,
            )

        fun user() =
            User(
                userId = userId,
                userName = "학생",
                userEmail = "student@gsm.hs.kr",
                userGrade = 2,
                userClassNumber = 3,
                userNumber = 4,
                userRole = UserRole.STUDENT,
            )

        Given("findByMemberId로 사용자를 조회할 때") {
            When("일치하는 사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every { developerUserJpaRepository.findById(userId) } returns Optional.of(userEntity())

                    adapter.findByMemberId(userId)?.userId shouldBe userId
                }
            }

            When("일치하는 사용자가 없으면") {
                Then("null을 반환한다") {
                    every { developerUserJpaRepository.findById(999L) } returns Optional.empty()

                    adapter.findByMemberId(999L).shouldBeNull()
                }
            }
        }

        Given("findBySchoolInfo로 학적정보 조합으로 사용자를 조회할 때") {
            When("일치하는 사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every {
                        developerUserJpaRepository.findByUserGradeAndUserClassNumberAndUserNumber(2, 3, 4)
                    } returns userEntity()

                    adapter.findBySchoolInfo(2, 3, 4)?.userId shouldBe userId
                }
            }

            When("일치하는 사용자가 없으면") {
                Then("null을 반환한다") {
                    every {
                        developerUserJpaRepository.findByUserGradeAndUserClassNumberAndUserNumber(3, 1, 1)
                    } returns null

                    adapter.findBySchoolInfo(3, 1, 1).shouldBeNull()
                }
            }
        }

        Given("findByEmail로 사용자를 조회할 때") {
            When("일치하는 사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every {
                        developerUserJpaRepository.findByUserEmail("student@gsm.hs.kr")
                    } returns userEntity()

                    adapter.findByEmail("student@gsm.hs.kr")?.userId shouldBe userId
                }
            }

            When("일치하는 사용자가 없으면") {
                Then("null을 반환한다") {
                    every { developerUserJpaRepository.findByUserEmail("none@gsm.hs.kr") } returns null

                    adapter.findByEmail("none@gsm.hs.kr").shouldBeNull()
                }
            }
        }

        Given("hasRelatedData로 참조 데이터 존재 여부를 확인할 때") {
            When("참조 데이터가 하나도 없으면") {
                Then("false를 반환한다") {
                    every { developerAlertJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerEvidenceJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerScoreJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerFileJpaRepository.existsByUserUserId(userId) } returns false

                    adapter.hasRelatedData(userId) shouldBe false
                }
            }

            When("알림만 존재하면") {
                Then("true를 반환하고 나머지 저장소는 조회하지 않는다") {
                    every { developerAlertJpaRepository.existsByUserUserId(userId) } returns true

                    adapter.hasRelatedData(userId) shouldBe true

                    verify(exactly = 0) { developerEvidenceJpaRepository.existsByUserUserId(any()) }
                    verify(exactly = 0) { developerScoreJpaRepository.existsByUserUserId(any()) }
                    verify(exactly = 0) { developerFileJpaRepository.existsByUserUserId(any()) }
                }
            }

            When("근거 자료만 존재하면") {
                Then("true를 반환한다") {
                    every { developerAlertJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerEvidenceJpaRepository.existsByUserUserId(userId) } returns true

                    adapter.hasRelatedData(userId) shouldBe true

                    verify(exactly = 0) { developerScoreJpaRepository.existsByUserUserId(any()) }
                    verify(exactly = 0) { developerFileJpaRepository.existsByUserUserId(any()) }
                }
            }

            When("점수 요청만 존재하면") {
                Then("true를 반환한다") {
                    every { developerAlertJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerEvidenceJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerScoreJpaRepository.existsByUserUserId(userId) } returns true

                    adapter.hasRelatedData(userId) shouldBe true

                    verify(exactly = 0) { developerFileJpaRepository.existsByUserUserId(any()) }
                }
            }

            When("파일만 존재하면") {
                Then("true를 반환한다") {
                    every { developerAlertJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerEvidenceJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerScoreJpaRepository.existsByUserUserId(userId) } returns false
                    every { developerFileJpaRepository.existsByUserUserId(userId) } returns true

                    adapter.hasRelatedData(userId) shouldBe true
                }
            }
        }

        Given("save로 사용자를 저장할 때") {
            When("정상적으로 저장되면") {
                Then("저장된 도메인 객체를 반환한다") {
                    every { developerUserJpaRepository.saveAndFlush(any()) } returns userEntity()

                    adapter.save(user()).userId shouldBe userId
                }
            }

            When("학적정보 중복 제약을 위반하면") {
                Then("DUPLICATE_RESOURCE 예외로 변환해 던진다") {
                    every { developerUserJpaRepository.saveAndFlush(any()) } throws
                        DataIntegrityViolationException("duplicate")

                    shouldThrow<GsmcException> { adapter.save(user()) }
                        .errorCode shouldBe ErrorCode.DUPLICATE_RESOURCE
                }
            }
        }

        Given("delete로 사용자를 삭제할 때") {
            When("정상적으로 삭제되면") {
                Then("리포지토리에 삭제와 flush를 위임한다") {
                    every { developerUserJpaRepository.deleteById(userId) } returns Unit
                    every { developerUserJpaRepository.flush() } returns Unit

                    adapter.delete(user())

                    verify(exactly = 1) { developerUserJpaRepository.deleteById(userId) }
                    verify(exactly = 1) { developerUserJpaRepository.flush() }
                }
            }

            When("참조 데이터가 있어 삭제 제약을 위반하면") {
                Then("USER_HAS_RELATED_DATA 예외로 변환해 던진다") {
                    every { developerUserJpaRepository.deleteById(userId) } returns Unit
                    every { developerUserJpaRepository.flush() } throws
                        DataIntegrityViolationException("related data exists")

                    shouldThrow<GsmcException> { adapter.delete(user()) }
                        .errorCode shouldBe ErrorCode.USER_HAS_RELATED_DATA
                }
            }
        }
    })
