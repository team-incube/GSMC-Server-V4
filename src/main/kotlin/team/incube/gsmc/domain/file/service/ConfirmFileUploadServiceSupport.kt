package team.incube.gsmc.domain.file.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

@Component
class ConfirmFileUploadServiceSupport(
    private val filePersistencePort: FilePersistencePort,
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
) {
    @Transactional(readOnly = true)
    fun validate(fileKey: String) {
        if (filePersistencePort.findByFileKey(fileKey) != null) {
            throw GsmcException(ErrorCode.FILE_ALREADY_CONFIRMED)
        }
        if (fileStorageDeletionTaskPersistencePort.existsByFileKey(fileKey)) {
            throw GsmcException(ErrorCode.S3_OBJECT_NOT_FOUND)
        }
    }
}
