# DataGSM 프로젝트 목록 갱신

GSMC의 `myWritableDataGsmProjects`는 전체 활성 프로젝트 목록과 참여자별 파생 인덱스를 최대 24시간 캐시한다. DataGSM의 `project.updated` 알림을 받으면 두 캐시의 활성 키를 원자적으로 무효화하고, 다음 목록 요청에서 DataGSM 전체 활성 목록을 다시 조회한다. 제출과 재제출의 단건 재검증 경로는 이 캐시를 사용하지 않는다.

## 연결 방법

1. GSMC 서버에 `DATAGSM_EVENT_SECRET`을 DataGSM Event 구독의 비밀값으로 설정한다. 설정하지 않으면 수신 경로는 503을 반환한다.
2. DataGSM Event 콘솔에 GSMC의 `https://<호스트>/api/events/datagsm/projects`를 수신 URL로 등록하고 `project.updated`를 구독한다.
3. 구독 상태가 `VERIFIED`인지 확인한다. 서명 헤더는 원문 요청 본문의 HMAC-SHA256 값으로 검증한다.

이벤트 수신 성공은 204, 서명 오류는 401, 잘못된 본문은 400을 반환한다. Redis 무효화에 실패하면 성공을 반환하지 않아 DataGSM의 재시도를 유도한다. 중복 이벤트는 캐시 무효화를 반복할 뿐이므로 결과는 동일하다.

## 운영상 한계

- DataGSM 이벤트는 비동기로 전달되며 재시도가 모두 실패하면 유실된다. 구독이 없거나 유실된 경우에는 기존 24시간 만료가 최종 갱신 경로다.
- 변경 직후 이미 진행 중인 목록 응답에는 이전 값이 남을 수 있다. 재조회 중 이벤트가 도착하면 이전 버전의 캐시 활성화를 막고 목록을 다시 조회한다.
- 첫 갱신은 전체 프로젝트 목록을 읽으므로 DataGSM 변경 빈도가 높으면 외부 요청량이 늘어난다. 기존 캐시 미스 단일화와 참여자별 적중 경로는 유지된다.
- 프로젝트의 실제 운영 건수와 이벤트 발생량은 확인되지 않았다. 구독 후 DataGSM 호출량과 이벤트 전달 상태를 관찰해야 한다.
- 심사 화면의 제출 시점 정보 보존 정책은 별도 결정이 필요하다. 이 변경은 목록 갱신만 다룬다.

참고: [DataGSM Event](https://docs.datagsm.kr/event), [프로젝트 이벤트 명세](https://docs.datagsm.kr/event/payloads/project), [서명 검증](https://docs.datagsm.kr/event/signature)
