# 운영 헬스 체크

운영 환경에서는 Spring Boot Actuator의 두 probe 경로만 인증 없이 공개한다.

| 용도 | 경로 | 확인 대상 | 정상 응답 | 장애 응답 |
| --- | --- | --- | --- | --- |
| 생존 확인 | `GET /actuator/health/liveness` | 애플리케이션 생존 상태 | 200 | 503 |
| 준비 상태 확인 | `GET /actuator/health/readiness` | 애플리케이션, MySQL, Redis | 200 | 503 |

Liveness는 MySQL·Redis·외부 시스템을 호출하지 않는다. 컨테이너 재시작 판단에는 이 경로를 사용한다.

Readiness는 MySQL의 가벼운 연결 검증과 Redis의 읽기 전용 PING을 수행한다. MySQL 또는 Redis가 DOWN이면 503을 반환하므로, 로드밸런서의 트래픽 수신 가능 여부 판단에 사용한다. DataGSM OAuth/OpenAPI, S3, Discord는 외부 장애가 서비스 인스턴스 전체를 준비 불가로 판정하지 않도록 기본 Readiness에서 제외한다.

응답에는 `status` 및 컴포넌트 상태만 포함한다. JDBC URL, 호스트, 포트, 계정, 비밀번호, SQL, 예외 메시지, 스택트레이스는 공개하지 않는다. `/actuator/health`와 그 밖의 Actuator 경로는 인증이 필요하며, `env`, `configprops`, `beans`, `mappings` 등의 endpoint는 노출하지 않는다.

## 권장 probe 설정

- Liveness: interval 10초, timeout 2초, retries 3회
- Readiness: interval 10초, timeout 2초, retries 3회

애플리케이션은 DB 커넥션 획득·검증과 Redis 연결·명령에 각각 1초 제한을 둔다. 짧은 주기의 probe를 추가로 구성할 경우 DB 커넥션 풀 고갈 여부를 함께 관찰한다.

Dockerfile에는 curl·wget이 없으므로 이를 설치하거나 가정한 Docker `HEALTHCHECK`를 추가하지 않는다. 오케스트레이터 또는 외부 모니터링이 위 HTTP 경로를 호출하도록 설정한다.

## 배포 후 확인

```bash
curl -i http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

Readiness의 MySQL 또는 Redis 컴포넌트가 DOWN이면 원인을 복구한 뒤 같은 요청으로 UP 전환을 확인한다. 응답·로그에 연결 정보나 예외 원문을 복사해 공유하지 않는다.
