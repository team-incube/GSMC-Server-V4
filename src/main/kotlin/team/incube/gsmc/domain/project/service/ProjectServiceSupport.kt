package team.incube.gsmc.domain.project.service

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.project.port.out.ProjectMemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MYSQL_TEXT_MAX_BYTES
import team.incube.gsmc.global.util.utf8ByteLength

private const val MIN_TITLE_LENGTH = 1
private const val MAX_TITLE_LENGTH = 100
private const val MIN_DESCRIPTION_LENGTH = 300
private const val MAX_DESCRIPTION_LENGTH = 2000
private const val MAX_DRAFT_TITLE_LENGTH = 255

/**
 * 프로젝트 서비스에서 공통으로 사용하는 입력값과 관계 검증을 제공합니다.
 * 제목·설명 길이, 참여자 존재 여부, 파일 존재 여부와 소유권을 검증합니다.
 */
@Component
class ProjectServiceSupport(
    private val projectMemberPersistencePort: ProjectMemberPersistencePort,
    private val filePersistencePort: FilePersistencePort,
) {
    /** 제출 완료 프로젝트의 제목과 설명 길이를 검증합니다. */
    fun validateFinalContent(
        title: String,
        description: String,
    ) {
        if (title.length !in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH ||
            description.length !in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH
        ) {
            throw GsmcException(ErrorCode.INVALID_PROJECT_INPUT)
        }
    }

    /** 프로젝트 부분 수정 요청에 포함된 제목과 설명을 검증합니다. */
    fun validatePatchContent(
        title: String?,
        description: String?,
    ) {
        title?.let { if (it.length !in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH) invalidInput() }
        description?.let {
            if (it.length !in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH) invalidInput()
        }
    }

    /** 프로젝트 초안의 제목과 설명이 저장 가능한 길이인지 검증합니다. */
    fun validateDraftContent(
        title: String,
        description: String,
    ) {
        if (title.length > MAX_DRAFT_TITLE_LENGTH || description.utf8ByteLength() > MYSQL_TEXT_MAX_BYTES) {
            invalidInput()
        }
    }

    /** 참여자 식별자를 사용자 목록으로 변환하고 필요하면 소유자를 포함합니다. */
    fun findParticipants(
        participantIds: Collection<Long>,
        ownerId: Long,
        includeOwner: Boolean = true,
    ): List<User> {
        val ids = if (includeOwner) (participantIds + ownerId).distinct() else participantIds.distinct()
        val members = projectMemberPersistencePort.findAllByUserIds(ids)
        if (members.size != ids.size) throw GsmcException(ErrorCode.USER_NOT_FOUND)
        return ids.map { id -> members.first { it.userId == id } }
    }

    /** 파일 식별자의 존재 여부와 현재 소유자의 파일인지 검증합니다. */
    fun validateFiles(
        fileIds: Collection<Long>,
        ownerId: Long,
    ): List<File> {
        val ids = fileIds.distinct()
        val files = filePersistencePort.findAllByIdIn(ids)
        if (files.size != ids.size) throw GsmcException(ErrorCode.FILE_NOT_FOUND)
        if (files.any { it.userId != ownerId }) throw GsmcException(ErrorCode.FILE_NOT_FOUND)
        return ids.map { id -> files.first { it.fileId == id } }
    }

    private fun invalidInput(): Nothing = throw GsmcException(ErrorCode.INVALID_PROJECT_INPUT)
}
