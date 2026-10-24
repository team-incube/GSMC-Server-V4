package team.incube.gsmc.domain.project.adapter.out.persistence

import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity
import team.incube.gsmc.domain.project.Project
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.QProjectJpaEntity.projectJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.toSummaryDomain
import team.incube.gsmc.domain.project.adapter.out.persistence.repository.ProjectJpaRepository
import team.incube.gsmc.domain.project.port.out.ProjectPersistencePort
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.QUserJpaEntity.userJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/** 내부 프로젝트의 JPA 영속성을 담당하는 아웃바운드 어댑터입니다. */
@Adapter(direction = PortDirection.OUTBOUND)
class ProjectPersistenceAdapter(
    private val projectJpaRepository: ProjectJpaRepository,
    private val entityManager: EntityManager,
    private val queryFactory: JPAQueryFactory,
) : ProjectPersistencePort {
    /** 프로젝트 상세와 연결된 점수 식별자를 조회합니다. */
    override fun findById(projectId: Long): Project? =
        queryFactory
            .selectFrom(projectJpaEntity)
            .where(projectJpaEntity.projectId.eq(projectId))
            .fetchOne()
            ?.toDomain(findScoreIds(projectId))

    /** 사용자가 소유하거나 참여한 프로젝트 요약을 최신 프로젝트부터 조회합니다. */
    override fun findAllByUserId(userId: Long): List<Project> {
        val entities =
            queryFactory
                .selectFrom(projectJpaEntity)
                .distinct()
                .leftJoin(projectJpaEntity.participants, userJpaEntity)
                .where(
                    projectJpaEntity.owner.userId
                        .eq(userId)
                        .or(userJpaEntity.userId.eq(userId)),
                ).orderBy(projectJpaEntity.projectId.desc())
                .fetch()
        return entities.map { it.toSummaryDomain() }
    }

    /** 제목에 검색어가 포함된 프로젝트를 페이지 단위로 조회합니다. */
    override fun findAllByTitleContaining(
        title: String,
        page: Int,
        size: Int,
    ): List<Project> =
        queryFactory
            .selectFrom(projectJpaEntity)
            .where(titleSearchCondition(title))
            .orderBy(projectJpaEntity.projectId.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
            .map { it.toSummaryDomain() }

    /** 제목 검색 조건에 해당하는 프로젝트 전체 개수를 조회합니다. */
    override fun countByTitleContaining(title: String): Long =
        queryFactory
            .select(projectJpaEntity.count())
            .from(projectJpaEntity)
            .where(titleSearchCondition(title))
            .fetchOne() ?: 0L

    /**
     * 제목 검색 조건을 구성합니다.
     * 공백이면 조건 없음, 한 글자면 LIKE(ngram 최소 토큰 미만), 두 글자 이상이면 FULLTEXT ngram 검색을 사용합니다.
     */
    private fun titleSearchCondition(title: String): Predicate? {
        val trimmedTitle = title.trim()
        return when {
            trimmedTitle.isEmpty() -> null
            trimmedTitle.length == 1 -> projectJpaEntity.title.containsIgnoreCase(trimmedTitle)
            else -> matchAgainstPredicate(trimmedTitle)
        }
    }

    /**
     * relevance 비교(`> 0`) 없이 `match_against` 함수 호출 자체를 predicate로 사용합니다.
     * InnoDB FULLTEXT는 모든 행에 등장하는 단어의 relevance를 0으로 계산하므로,
     * 비교 연산을 두면 실제 매치된 행이 조건에서 제외될 수 있습니다.
     */
    private fun matchAgainstPredicate(trimmedTitle: String): Predicate =
        Expressions.booleanTemplate(
            "match_against({0}, {1})",
            projectJpaEntity.title,
            toBooleanModePhrase(trimmedTitle),
        )

    private fun toBooleanModePhrase(trimmedTitle: String): String = "\"${trimmedTitle.replace("\"", "")}\""

    /** 프로젝트와 참여자·파일 관계를 저장하고 저장된 도메인을 반환합니다. */
    override fun save(project: Project): Project {
        val owner = entityManager.getReference(UserJpaEntity::class.java, project.ownerId)
        val participants =
            project.participants
                .map { entityManager.getReference(UserJpaEntity::class.java, it.id) }
                .toMutableSet()
        val files =
            project.files
                .map { entityManager.getReference(FileJpaEntity::class.java, it.id) }
                .toMutableSet()
        val saved = projectJpaRepository.save(project.toEntity(owner, participants, files))
        return findById(saved.projectId) ?: project.copy(projectId = saved.projectId)
    }

    /** 프로젝트 식별자로 프로젝트를 삭제합니다. */
    override fun deleteById(projectId: Long) {
        projectJpaRepository.deleteById(projectId)
    }

    private fun findScoreIds(projectId: Long): List<Long> =
        queryFactory
            .select(scoreJpaEntity.scoreId)
            .from(scoreJpaEntity)
            .where(scoreJpaEntity.project.projectId.eq(projectId))
            .fetch()
}
