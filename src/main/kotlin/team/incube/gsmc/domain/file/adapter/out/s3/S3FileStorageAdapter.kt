package team.incube.gsmc.domain.file.adapter.out.s3

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import team.incube.gsmc.domain.file.PresignedUpload
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.time.Duration
import java.time.Instant

private val UPLOAD_URL_TTL: Duration = Duration.ofMinutes(10)

/**
 * AWS S3 연동을 담당하는 아웃바운드 어댑터 클래스입니다. [FileStoragePort]를 구현합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class S3FileStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val s3Properties: S3Properties,
) : FileStoragePort {
    override fun createPresignedUploadUrl(
        key: String,
        contentType: String,
        fileSize: Long,
    ): PresignedUpload {
        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(fileSize)
                .build()
        val presignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build()
        val presignedRequest = s3Presigner.presignPutObject(presignRequest)

        return PresignedUpload(
            key = key,
            url = presignedRequest.url().toString(),
            expiresAt = Instant.now().plus(UPLOAD_URL_TTL),
        )
    }

    override fun createPresignedDownloadUrl(key: String): String {
        val getObjectRequest =
            GetObjectRequest
                .builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .build()
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(FileStoragePort.DOWNLOAD_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build()

        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    override fun getObjectSize(key: String): Long? =
        try {
            s3Client
                .headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(s3Properties.bucket)
                        .key(key)
                        .build(),
                ).contentLength()
        } catch (_: NoSuchKeyException) {
            null
        }

    override fun deleteObject(key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest
                .builder()
                .bucket(s3Properties.bucket)
                .key(key)
                .build(),
        )
    }
}
