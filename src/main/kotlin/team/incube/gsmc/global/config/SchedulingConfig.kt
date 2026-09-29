package team.incube.gsmc.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * `@Scheduled` 사용을 위한 설정입니다. 알림 SSE 연결의 Heartbeat 전송(
 * [team.incube.gsmc.domain.alert.adapter.sse.AlertEmitterRegistry])과 스토리지 객체 삭제 작업 처리(
 * [team.incube.gsmc.domain.file.adapter.scheduler.FileStorageDeletionScheduler])에서 사용한다.
 */
@Configuration
@EnableScheduling
class SchedulingConfig
