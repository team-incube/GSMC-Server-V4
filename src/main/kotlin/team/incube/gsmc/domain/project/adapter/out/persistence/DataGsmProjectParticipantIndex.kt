package team.incube.gsmc.domain.project.adapter.out.persistence

import team.incube.gsmc.domain.project.DataGsmProject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 전체 프로젝트 목록에서 Redis에 저장할 참여자별 파생 목록을 만든다.
 * SHA-256 필드는 Redis 키·모니터링에 이메일 원문이 노출되는 것을 피하기 위한 가명값이며,
 * 후보가 제한된 이메일을 강하게 익명화하는 수단은 아니다.
 */
internal object DataGsmProjectParticipantIndex {
    fun group(projects: List<DataGsmProject>): Map<String, List<DataGsmProject>> {
        val grouped = linkedMapOf<String, MutableList<DataGsmProject>>()

        projects.forEach { project ->
            project.participants
                .map { it.participantEmail }
                .distinct()
                .forEach { email -> grouped.getOrPut(participantField(email), ::mutableListOf).add(project) }
        }

        return grouped
    }

    fun participantField(email: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(email.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
