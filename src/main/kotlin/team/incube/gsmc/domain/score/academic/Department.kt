package team.incube.gsmc.domain.score.academic

import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/**
 * 학과
 *
 * 사용자 정보에는 학과가 없어 반 번호로 학과를 판정한다. 전 학년 공통으로 1·2반은 SW개발과,
 * 3반은 스마트IoT과, 4반은 인공지능과다.
 */
enum class Department {
    SOFTWARE,
    SMART_IOT,
    AI,
    ;

    companion object {
        /**
         * @throws GsmcException 반 번호가 없거나 1~4반이 아니면 [ErrorCode.INVALID_CLASS_NUMBER]
         */
        fun fromClassNumber(classNumber: Int?): Department =
            when (classNumber) {
                1, 2 -> SOFTWARE
                3 -> SMART_IOT
                4 -> AI
                else -> throw GsmcException(ErrorCode.INVALID_CLASS_NUMBER)
            }
    }
}
