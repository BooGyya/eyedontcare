# Backend Guest Session

## 개요

게스트 세션은 회원 JWT와 별개인 임시 게임 참가자 식별 정보다. 독립적인 게스트
세션 발급 REST API는 제공하지 않는다. 게스트가 초대방을 생성·입장하거나 랜덤
매칭을 요청할 때 해당 도메인 서비스가 공용 `GuestSessionService`를 호출한다.

게스트를 `users` 또는 `social_accounts`에 저장하지 않으며 Access Token과 Refresh
Token도 발급하지 않는다.

## Redis 계약

키 형식:

```text
edc:{profile}:guest:session:{guestSessionId}
```

값:

```json
{
  "nickname": "용감한수달0123",
  "createdAt": "2026-07-30T12:00:00Z",
  "expiresAt": "2026-07-31T12:00:00Z"
}
```

- `guestSessionId`는 키에 있으므로 값에 중복 저장하지 않는다.
- TTL은 `app.guest.session-ttl`이며 현재 `PT24H`다.
- 최초 발급 시각을 기준으로 고정 만료되며 조회로 연장되지 않는다.
- `StringRedisTemplate`과 기존 `RedisKeyBuilder`, Spring `JsonMapper`를 사용한다.
- 저장은 `SET NX`와 TTL을 함께 적용하여 UUID 충돌과 만료 설정을 처리한다.

## 참가자 식별 계약

```text
GUEST:{guestSessionId}
```

`GuestParticipantKey.parse(String)`은 접두사와 UUID 형식을 검증한다. 문자열 형식이
맞더라도 Redis 세션의 존재와 만료를 확인하기 전에는 유효한 게스트로 신뢰하면 안
된다.

## 공용 컴포넌트

`GuestSessionService`가 다음 메서드를 제공한다.

- `issue()`: 새 UUID와 랜덤 닉네임으로 세션 발급
- `findById(UUID)`: 유효한 세션 조회
- `validate(UUID)`: 유효한 세션 반환 또는 `GUEST-001`
- `exists(UUID)`: 유효한 값과 TTL이 모두 있는지 확인
- `getRemainingTtl(UUID)`: Redis의 실제 남은 TTL 조회

발급 결과인 `GuestSession`에는 `guestSessionId`, `nickname`, `createdAt`,
`expiresAt`이 포함된다. Redis 값에는 ID가 포함되지 않는다.

## 도메인별 연결

### Matchmaking

랜덤 매칭 요청에서 기존 게스트 세션이 유효하면 재사용하고, 없거나 만료됐으면
`issue()`를 호출한다. 현재 접두사 뒤 문자열 존재만 확인하는 임시 검증을 최종
인증으로 사용하면 안 된다.

### WaitingRoom

초대방 생성·입장에서 Matchmaking과 같은 발급·재사용 정책을 적용한다. 발급된
세션의 닉네임을 참가자 표시 이름으로 사용하며 대기방 TTL과 게스트 세션 TTL을
혼동하지 않는다.

### GameResult

GameResult에서는 세션을 새로 발급하지 않는다. 제출된 게스트 UUID를 `validate()`로
검증하고 표시 이름이 필요하면 검증 결과의 닉네임을 사용한다.

### WebSocket

WebSocket handshake 공개는 게스트 인증 완료를 의미하지 않는다. 최초 참가자 식별
프레임에서 UUID 형식과 Redis 세션을 검증하며 WebSocket 흐름에서는 세션을 새로
발급하지 않는다.

## 보안 주의

- 임의의 `GUEST:` 문자열이나 UUID를 신뢰하지 않는다.
- 회원은 `USER:{userId}` 문자열이 아니라 JWT principal로 검증한다.
- 게스트 세션과 회원 JWT 인증을 같은 필터에 섞지 않는다.
- guestSessionId 전체와 Redis key를 일반 로그나 오류 메시지에 남기지 않는다.
- 닉네임은 중복될 수 있으므로 참가자 식별값으로 사용하지 않는다.
