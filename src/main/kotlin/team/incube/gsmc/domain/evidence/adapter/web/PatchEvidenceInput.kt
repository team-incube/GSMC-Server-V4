package team.incube.gsmc.domain.evidence.adapter.web

data class PatchEvidenceInput(
    /** 스키마에서 deprecated된 필드로, 기존 클라이언트 요청 바인딩을 위해서만 남겨 두며 수정에 사용하지 않는다. */
    val scoreId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val fileIds: List<Long>? = null,
)
