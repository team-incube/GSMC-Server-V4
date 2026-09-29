package team.incube.gsmc.domain.category.adapter.out.persistence

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.RedisTemplate
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.port.out.CategoryPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 카테고리 조회 결과의 캐싱을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [CategoryPersistencePort]를 구현하며, DB 조회를 담당하는 [CategoryPersistenceAdapter]를 감싸는
 * 데코레이터로 동작한다. `findAll()` 결과를 Redis에 단일 키(`category:all`)로 캐싱하고,
 * `findByCategoryType`/`searchByKeyword`는 캐시된 전체 목록을 메모리에서 필터링해 구현한다.
 *
 * `category_tb`는 생성/수정/삭제 API가 없어 런타임에 사실상 불변이므로 TTL 없이 캐싱한다. Redis
 * 읽기/쓰기 실패는 각각 독립적으로 흡수해 DB로 폴백한다 — 캐싱은 순수 성능 최적화이므로 Redis 장애가
 * 카테고리 조회(특히 점수 제출 경로)를 막는 장애로 번져서는 안 된다.
 *
 * 서비스 계층에는 이 클래스가 `@Primary`로 우선 주입된다. [CategoryPersistenceAdapter]는 인터페이스가
 * 아닌 구체 클래스로 생성자 주입받는데, 인터페이스로 받으면 `@Primary`인 이 클래스 자신이 다시
 * 주입되어 순환 참조가 발생하기 때문이다.
 */

@Adapter(direction = PortDirection.OUTBOUND)
@Primary
class CategoryCachePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val categoryPersistenceAdapter: CategoryPersistenceAdapter,
) : CategoryPersistencePort {
    companion object {
        private const val KEY_ALL = "category:all"
    }

    private val log = LoggerFactory.getLogger(CategoryCachePersistenceAdapter::class.java)

    /**
     * 카테고리 타입으로 카테고리를 조회한다. [findAll]이 반환하는 캐시된 전체 목록에서 필터링하므로
     * 별도의 Redis 키를 사용하지 않는다.
     *
     * @param categoryType 조회할 카테고리 타입
     * @return 해당 타입의 카테고리, 없으면 null
     */
    override fun findByCategoryType(categoryType: CategoryType): Category? {
        val categories = findAll()
        return categories.find { it.categoryType == categoryType }
    }

    /**
     * 카테고리 전체 목록을 조회한다. Redis에 캐싱된 값이 있으면 DB를 거치지 않고 그대로 반환하고,
     * 캐시가 없거나(미스) 캐시 접근에 실패했다면 [categoryPersistenceAdapter]로 DB를 조회한 뒤
     * 그 결과를 Redis에 적재하고 반환한다.
     *
     * @return categoryId 오름차순으로 정렬된 카테고리 전체 목록
     */
    override fun findAll(): List<Category> {
        val cache = readFromCache()
        if (cache != null) return cache

        val fromDb = categoryPersistenceAdapter.findAll()
        writeToCache(fromDb)
        return fromDb
    }

    /**
     * 키워드로 카테고리를 검색한다. [findAll]이 반환하는 캐시된 전체 목록에서 영문명/한글명에
     * 대소문자 구분 없이 포함 여부를 필터링하므로 별도의 Redis 키를 사용하지 않는다.
     *
     * @param keyword 검색 키워드
     * @return 영문명 또는 한글명에 [keyword]가 포함된 카테고리 목록
     */
    override fun searchByKeyword(keyword: String): List<Category> {
        val categories = findAll()
        return categories.filter {
            it.categoryEnglishName.contains(keyword, ignoreCase = true) ||
                it.categoryKoreanName.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * Redis에서 캐시된 카테고리 목록을 읽어 역직렬화한다. Redis 연결 실패, JSON 역직렬화 실패 등
     * 어떤 예외가 발생하든 캐시 미스로 간주해 흡수하고 로그만 남긴다 — 호출부([findAll])가 이를
     * DB 폴백 신호로 사용한다.
     *
     * @return 캐시된 카테고리 목록, 캐시 미스거나 읽기/역직렬화에 실패하면 null
     */
    private fun readFromCache(): List<Category>? =
        try {
            val cache = redisTemplate.opsForValue().get(KEY_ALL)
            cache?.let { objectMapper.readValue(it, object : TypeReference<List<Category>>() {}) }
        } catch (e: Exception) {
            log.warn("카테고리 캐시 조회에 실패해 DB로 폴백합니다. key={}", KEY_ALL, e)
            null
        }

    /**
     * 카테고리 목록을 직렬화해 Redis에 적재한다. TTL 없이 저장하며, 적재 중 예외가 발생해도 흡수하고
     * 로그만 남긴다 — 캐싱은 순수 성능 최적화이므로 적재 실패가 [findAll]의 응답(이미 DB에서 조회한
     * 결과)에 영향을 주면 안 된다.
     *
     * @param categories Redis에 캐싱할 카테고리 목록
     */
    private fun writeToCache(categories: List<Category>) {
        try {
            val json = objectMapper.writeValueAsString(categories)
            redisTemplate.opsForValue().set(KEY_ALL, json)
        } catch (e: Exception) {
            log.warn("카테고리 캐시 적재에 실패했습니다. key={}", KEY_ALL, e)
        }
    }
}
