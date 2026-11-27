package team.incube.gsmc.domain.project.adapter.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import team.incube.gsmc.domain.project.port.`in`.ModifyDataGsmProjectCacheUseCase
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 서명된 DataGSM 프로젝트 변경 알림을 수신한다. */
@RestController
class DataGsmProjectEventWebAdapter(
    @param:Value($$"${datagsm.event.secret:}") private val secret: String,
    private val objectMapper: ObjectMapper,
    private val modifyDataGsmProjectCacheUseCase: ModifyDataGsmProjectCacheUseCase,
) {
    @PostMapping("/api/events/datagsm/projects")
    fun receive(
        @RequestHeader("X-DataGSM-Signature", required = false) signature: String?,
        @RequestBody body: ByteArray,
    ): ResponseEntity<Void> {
        if (secret.isBlank()) return ResponseEntity.status(503).build()
        if (!validSignature(signature, body)) return ResponseEntity.status(401).build()

        val event =
            try {
                objectMapper.readTree(body)
            } catch (_: Exception) {
                return ResponseEntity.badRequest().build()
            }
        if (event.path("event").asText() != "project.updated" || event.path("id").asText().isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        modifyDataGsmProjectCacheUseCase.execute()
        return ResponseEntity.noContent().build()
    }

    private fun validSignature(
        signature: String?,
        body: ByteArray,
    ): Boolean {
        if (signature == null || !signature.startsWith("sha256=")) return false
        val received = signature.removePrefix("sha256=")
        if (!received.matches(Regex("[0-9a-f]{64}"))) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(body).joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            received.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
