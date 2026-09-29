package team.incube.gsmc.global.util

import java.nio.charset.StandardCharsets

/** MySQL TEXT에 저장할 수 있는 UTF-8 문자열의 최대 바이트 수입니다. */
const val MYSQL_TEXT_MAX_BYTES = 65_535

/** 문자열을 UTF-8로 인코딩했을 때 필요한 바이트 수를 반환합니다. */
fun String.utf8ByteLength(): Int = toByteArray(StandardCharsets.UTF_8).size
