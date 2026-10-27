package team.incube.gsmc.domain.file.port.out

/**
 * 파일 접근용 presigned GET URL의 캐싱을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 같은 파일을 조회할 때마다 URL을 새로 서명하지 않고 유효 기간 안에서 같은 URL을 재사용해,
 * 서명 연산을 줄이고 URL이 안정되어 브라우저/CDN 캐시가 동작하도록 한다.
 *
 * 구현체는 캐시 TTL을 [FileStoragePort.DOWNLOAD_URL_TTL]보다 짧게 잡아야 한다. 캐시는 순수 성능
 * 최적화이므로 조회 실패는 캐시 미스(null)로, 저장 실패는 무시하는 것으로 처리해 호출부가 서명으로
 * 폴백할 수 있게 한다.
 */
interface FileAccessUrlCachePort {
    /**
     * 캐싱된 presigned URL을 조회한다.
     *
     * @param fileKey 스토리지 객체 key
     * @return 캐싱된 presigned URL, 캐시가 없거나 조회에 실패하면 null
     */
    fun find(fileKey: String): String?

    /**
     * presigned URL을 캐싱한다.
     *
     * @param fileKey 스토리지 객체 key
     * @param url 캐싱할 presigned URL
     */
    fun save(
        fileKey: String,
        url: String,
    )
}
