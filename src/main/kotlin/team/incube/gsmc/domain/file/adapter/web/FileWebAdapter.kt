package team.incube.gsmc.domain.file.adapter.web

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.`in`.ConfirmFileUploadUseCase
import team.incube.gsmc.domain.file.port.`in`.CreatePresignedUploadUrlUseCase
import team.incube.gsmc.domain.file.port.`in`.FetchFileUseCase
import team.incube.gsmc.domain.file.port.`in`.FetchMyFilesUseCase
import team.incube.gsmc.domain.file.port.`in`.GenerateFileAccessUrlUseCase
import team.incube.gsmc.domain.file.port.`in`.RemoveFileUseCase

/**
 * 파일 조회/삭제 및 업로드 확정 GraphQL Query·Mutation 리졸버입니다.
 * 각 Query/Mutation을 대응하는 UseCase에 위임하는 것 외의 비즈니스 로직은 갖지 않습니다.
 * `File.uri`/`File.fileUrl` 필드는 별도 필드 리졸버로 처리해, `Score.file`처럼 다른 도메인이 반환하는
 * `File`에도 동일하게 presigned URL이 적용되도록 합니다.
 */
@Controller
class FileWebAdapter(
    private val fetchMyFilesUseCase: FetchMyFilesUseCase,
    private val fetchFileUseCase: FetchFileUseCase,
    private val removeFileUseCase: RemoveFileUseCase,
    private val createPresignedUploadUrlUseCase: CreatePresignedUploadUrlUseCase,
    private val confirmFileUploadUseCase: ConfirmFileUploadUseCase,
    private val generateFileAccessUrlUseCase: GenerateFileAccessUrlUseCase,
) {
    @QueryMapping
    fun myFiles(): List<File> = fetchMyFilesUseCase.execute()

    @QueryMapping
    fun file(
        @Argument fileId: Long,
    ): File = fetchFileUseCase.execute(fileId)

    @MutationMapping
    fun deleteFile(
        @Argument fileId: Long,
    ): Boolean = removeFileUseCase.execute(fileId)

    @MutationMapping
    fun confirmFileUpload(
        @Argument input: ConfirmFileUploadInput,
    ): File = confirmFileUploadUseCase.execute(input.fileKey, input.originalFileName)

    @MutationMapping
    fun createPresignedUploadUrl(
        @Argument input: CreatePresignedUploadUrlInput,
    ): PresignedUploadUrlPayload =
        createPresignedUploadUrlUseCase
            .execute(input.fileName, input.fileSize, input.contentType)
            .toPayload()

    /**
     * 스키마에서 deprecated된 필드로, [uri]와 동일한 presigned URL을 반환한다.
     * 하위 호환을 위해 유지하며 신규 클라이언트는 `uri`를 사용해야 한다.
     */
    @SchemaMapping(typeName = "File", field = "fileUrl")
    fun fileUrl(file: File): String = uri(file)

    @SchemaMapping(typeName = "File", field = "uri")
    fun uri(file: File): String = generateFileAccessUrlUseCase.execute(file.fileKey)
}
