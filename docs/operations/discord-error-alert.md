# Discord 서버 오류 알림 운영 가이드

Discord는 원본 로그 저장소가 아니라 운영 장애의 초기 인지 채널로만 사용합니다. 알림의 `요청 ID`와
`fingerprint`로 애플리케이션 로그를 검색해 상세 원인을 확인합니다.

## 활성화 설정

운영 배포 환경에서만 다음 환경 변수를 설정합니다. 실제 웹훅 주소는 저장소, 로그, 이슈, PR에 기록하지
않습니다.

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DISCORD_ERROR_ALERT_ENABLED` | `false` | 오류 알림 활성화 여부 |
| `DISCORD_ERROR_ALERT_WEBHOOK_URL` | 빈 값 | 오류 알림 전용 Discord 웹훅 주소 |
| `APP_ENVIRONMENT` | `local` | 알림에 표시할 환경 식별자 |
| `DISCORD_ERROR_ALERT_CONNECT_TIMEOUT` | `1s` | 연결 제한 시간 |
| `DISCORD_ERROR_ALERT_READ_TIMEOUT` | `2s` | 응답 읽기 제한 시간 |
| `DISCORD_ERROR_ALERT_SUPPRESSION_WINDOW` | `5m` | 같은 fingerprint의 중복 억제 시간 |
| `DISCORD_ERROR_ALERT_CACHE_MAX_SIZE` | `1000` | fingerprint 캐시 최대 항목 수 |
| `DISCORD_ERROR_ALERT_QUEUE_CAPACITY` | `100` | 비동기 전송 대기열 최대 크기 |

활성화 값이 `false`이거나 웹훅 주소가 비어 있으면 외부 요청을 보내지 않습니다. 제한 시간·억제 시간·캐시
크기·대기열 크기는 모두 양수여야 하며, 잘못된 값이면 설정 오류로 기동을 중단합니다.

## 알림 범위

- REST 전역 처리기까지 도달한 예상하지 못한 예외
- GraphQL에서 `INTERNAL_SERVER_ERROR`로 변환되는 예상하지 못한 예외
- 스토리지 삭제 재시도를 모두 소진한 최종 실패
- 파일 삭제 스케줄러 밖으로 전파되는 처리되지 않은 실행 예외

`GsmcException`, validation·인증·인가 오류, 일반 4xx, 정상 요청, health probe, SSE 연결 정리, 재시도 중간
실패는 보내지 않습니다. Sheet와 외부 연동 오류를 포함해 `GsmcException`으로 명시된 5xx도 현재는 계약된
오류로 분류해 제외합니다.

## 장애 격리와 제한

웹훅 전송은 단일 전용 스레드와 유한 대기열에서 수행합니다. 전송 제한 시간, HTTP 오류, 429, 대기열 거부는
원 요청 응답이나 비즈니스 트랜잭션에 전파하지 않고 애플리케이션 로그에 상태만 기록합니다. 자동 재시도는
중복 전송 위험을 피하기 위해 사용하지 않습니다. Discord가 429와 함께 제공한 `Retry-After` 헤더는 숫자일
때만 로그에 남겨 운영자가 제한 상황을 확인할 수 있게 합니다.

payload에는 요청 본문, GraphQL 문서·변수, 헤더, 예외 메시지, 전체 스택 추적을 넣지 않습니다. 예외 종류,
애플리케이션 내부의 첫 발생 위치, 요청 방식·경로 또는 GraphQL 작업 이름, 요청 ID, principal의 사용자 ID와
역할만 포함합니다.
