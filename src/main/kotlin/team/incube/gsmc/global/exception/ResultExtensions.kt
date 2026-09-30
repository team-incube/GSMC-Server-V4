package team.incube.gsmc.global.exception

fun <T> Result<T>.orThrow(errorCode: ErrorCode): T = getOrElse { throw GsmcException(errorCode) }
