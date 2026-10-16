# GraphQL 클라이언트 문서

이 디렉터리는 클라이언트 개발자가 GraphQL 스키마를 탐색하고 실제 요청을 작성할 때 사용하는 안내와 실행 예제를 관리합니다. operation 예제는 `src/main/resources/graphql/*.graphqls`의 로컬 SDL에 대해 Gradle 테스트로 검증합니다.

## 문서 기능 실행

애플리케이션 실행 환경에 `API_DOCS_ENABLED=true`를 설정하면 다음 경로가 활성화됩니다.

| 경로 | 용도 | 인증 |
| --- | --- | --- |
| `/graphiql` | 스키마 탐색과 operation 실행 | 화면 접근은 불필요, operation 실행은 JWT 필요 |
| `/graphql/schema` | 서버가 병합한 SDL 확인 | 불필요 |
| `/graphql` | GraphQL 요청 전송 | JWT 필요 |

환경변수가 없거나 `false`이면 GraphiQL과 schema printer는 제공되지 않습니다. `/graphql`의 기존 인증 정책은 문서 기능과 관계없이 유지됩니다.

GraphiQL에서 인증이 필요한 operation을 실행할 때 요청 헤더 영역에 개발용 액세스 토큰을 설정합니다. 운영 토큰이나 개인정보는 예제 또는 저장소에 저장하지 않습니다.

```json
{
  "Authorization": "Bearer <개발용_액세스_토큰>"
}
```

operation 파일 내용을 왼쪽 편집기에 붙여 넣고, 대응하는 variables 파일이 있으면 하단 변수 영역에 JSON 내용을 붙여 넣은 다음 실행합니다.

## 실행 예제

| 예제 | 목적 | 확인된 권한 | variables |
| --- | --- | --- | --- |
| [`my-member.graphql`](operations/my-member.graphql) | 인증 후 현재 사용자 정보 조회 | 인증된 사용자 | 없음 |
| [`my-projects.graphql`](operations/my-projects.graphql) | 소유하거나 참여한 프로젝트 목록 조회 | 인증된 사용자 | 없음 |
| [`project-detail.graphql`](operations/project-detail.graphql) | 프로젝트 상세 조회 | 인증된 사용자 | [`project-detail.variables.json`](operations/project-detail.variables.json) |
| [`my-evidences.graphql`](operations/my-evidences.graphql) | 현재 사용자의 제출 완료 증빙 목록 조회 | 인증된 사용자 | 없음 |
| [`create-evidence.graphql`](operations/create-evidence.graphql) | 본인 점수에 증빙 생성 | 점수 소유자 | [`create-evidence.variables.json`](operations/create-evidence.variables.json) |
| [`my-scores.graphql`](operations/my-scores.graphql) | 현재 사용자의 점수 목록 조회 | 인증된 사용자 | [`my-scores.variables.json`](operations/my-scores.variables.json) |
| [`my-alerts.graphql`](operations/my-alerts.graphql) | 현재 사용자의 알림을 커서 방식으로 조회 | 인증된 사용자 | [`my-alerts.variables.json`](operations/my-alerts.variables.json) |
| [`read-alerts.graphql`](operations/read-alerts.graphql) | 지정 알림까지 읽음 처리 | 알림 수신자 | [`read-alerts.variables.json`](operations/read-alerts.variables.json) |

예제의 식별자와 문자열은 형식만 보여 주는 값입니다. 개발 환경에서 본인에게 허용된 실제 식별자로 바꿔 실행합니다.

GraphQL 오류는 HTTP 응답의 `errors` 배열로 반환되며, 프로젝트에서 처리한 오류는 `errors[].extensions.code`에 `NOT_FOUND`, `FORBIDDEN`, `BAD_REQUEST` 같은 코드가 포함됩니다. 구체적인 발생 조건은 각 operation의 서비스 구현으로 확인된 범위만 SDL 설명에 기록합니다.

## 정적 문서 도구 검토

SpectaQL `3.0.9`로 현재의 여러 SDL 파일을 병합한 정적 문서 생성은 확인했습니다. 하지만 깨끗한 설치에서 392개 패키지와 높은 심각도 취약점 18건을 포함한 감사 결과가 발생했고, 현재 버전 범위에서 호환되는 수정 경로가 없어 이번 변경에는 포함하지 않았습니다.

클라이언트는 GraphiQL의 문서 패널과 schema printer를 사용합니다. 정적 문서 생성기는 취약한 전이 의존성이 해소되거나 별도 문서 배포 정책이 정해진 뒤 다시 검토합니다.

## 운영 정책과 후속 작업

운영 환경에서는 `API_DOCS_ENABLED`의 기본값 `false`를 유지합니다. 개발 서버에 적용할 때는 DevOps 담당자가 개발 환경에만 `API_DOCS_ENABLED=true`를 설정하고 새 이미지를 배포한 뒤 Reverse Proxy의 `/graphiql`, `/graphql`, `/graphql/schema` 전달, HTTPS 연결, 인증 헤더 또는 쿠키 전달, CORS·CSP·혼합 콘텐츠 오류를 확인해야 합니다.

문서를 별도로 배포하려면 생성 도구, 배포 위치와 접근 제한을 먼저 결정해야 합니다. 운영 공개가 필요하면 IP 허용 목록, 인증 또는 내부망 제한을 선행합니다.
