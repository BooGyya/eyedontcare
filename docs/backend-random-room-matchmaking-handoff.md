# RANDOM WaitingRoom - Matchmaking 연동 계약

## WaitingRoom 구현 범위

WaitingRoom은 `RandomRoomCreator`를 구현하여 RANDOM 방과 두 참가자를 Redis에
원자 생성한다. 참가자는 전달 순서대로 slot 1, 2에 배치되며 모두 PLAYER,
PENDING, ready=false로 시작한다. 방에는 초대 코드를 만들지 않는다.

`RandomRoomCreator#createRandomRoom(GameName, List<String>)`는 정확히 두 개의
서로 다른 `USER:{id}` 또는 `GUEST:{uuid}`를 받는다. 활성 회원과 유효한 기존
게스트만 허용하며 새 게스트를 발급하지 않는다. 성공 시 roomId를 반환하고,
정상적인 생성 충돌이나 미지원 게임은 `Optional.empty()`를 반환한다. 내부
Redis 장애는 숨기지 않는다. 실패 참가자의 큐 복구는 Matchmaking 책임이다.

## Lifecycle port

Matchmaking은 WaitingRoom의 `RandomRoomLifecyclePort` 구현 bean을 제공해야 한다.

- `markParticipantEntered(roomId, participantKey)`: WaitingRoom AUTH, registry 등록,
  초기 ROOM_STATE 전송까지 성공한 뒤 호출된다. 동일 roomId의 ENTERING_ROOM을
  IN_WAITING_ROOM으로 전환하고, 이미 같은 상태이면 멱등 성공해야 한다.
- `completeRandomRoom(roomId, participantKeys)`: COUNTDOWN에서 IN_GAME으로 원자
  전환된 직후 slot 순서의 participantKeys와 함께 호출된다. 동일 roomId와
  IN_WAITING_ROOM인 entry만 compare-delete해야 한다.

현재 구현체 부재나 호출 실패는 WaitingRoom 연결과 게임 시작을 되돌리지 않고
경고만 남긴다. Matchmaking adapter가 합쳐지면 optional 의존을 required
의존으로 바꿀지 함께 결정한다.

## Matchmaking 필수 작업

- pair 예약에 `MATCHING`과 `matchAttemptId`를 사용해 cancel/중복 매칭 경쟁을 막는다.
- 방 생성 성공 시 같은 attempt만 ENTERING_ROOM + roomId로 finalize한다.
- 생성 실패 시 유효 참가자만 현재 시각 score로 재등록한다.
- WaitingRoom AUTH 후 lifecycle adapter에서 IN_WAITING_ROOM으로 전환한다.
- IN_GAME 완료에서는 roomId/status 조건이 일치하는 entry만 삭제한다.
- `/ws/match`의 교체된 이전 session 종료가 새 SEARCHING entry를 취소하지 않도록
  registry unregister 경쟁 조건을 수정한다.

## 통합 확인

member/member, member/guest, guest/guest 각각에 대해 MATCH_SUCCESS roomId,
ENTERING_ROOM, WaitingRoom AUTH 후 IN_WAITING_ROOM, IN_GAME 후 entry 삭제,
다음 랜덤 join 가능 여부를 확인한다. 알림 실패, lifecycle adapter 실패,
stale roomId compare-delete도 검증한다.

Matchmaking에서 `RandomRoomCreator` package/signature, `GameName`, participant pair
타입 또는 lifecycle 결과 타입을 변경하면 WaitingRoom compile 정합성과 호출
시점 테스트를 다시 확인해야 한다.

## RANDOM 이탈 후 자동 재매칭

WaitingRoom은 RANDOM 방이 WAITING 또는 COUNTDOWN일 때 최초 이탈을 Redis에서
CLOSED로 전환하고 두 participant snapshot을 30초간 보존한다. 퇴장자는
재매칭하지 않고, WaitingRoom session이 살아 있는 상대만
`RandomRematchRequester`로 전달한다.

```java
RandomRematchRequestResult requeueRemaining(
    UUID previousRoomId,
    GameName gameName,
    String participantKey
);
```

결과는 `REQUEUED`, `ALREADY_REQUEUED`, `PARTICIPANT_INVALID`, `FAILED`다.
WaitingRoom은 모든 결과에서 CLOSED를 유지하고 `/ws/match` 이벤트를 직접
전송하지 않는다.

Matchmaking adapter는 다음을 책임진다.

- previousRoomId 및 현재 entry 상태 비교
- USER 활성 여부 또는 기존 GUEST session 유효성 검증
- 신규 GuestSession 발급 금지
- 기존 roomId 제거 및 현재 시각 기준 queuedAt/Sorted Set score 적용
- 기존 queuedAt 승계 금지
- queue member 중복 방지 및 entry TTL 신규 적용
- 성공 시 `MATCH_REQUEUED`, 실패 시 `MATCH_ERROR` 전송
- 이후 새 상대가 정해지면 기존 계약의 `MATCH_SUCCESS` 전송

Lifecycle adapter와 rematch adapter는 책임이 다르므로 별도 구현체로 유지하는
것을 권장한다. 통합 테스트에서는 MATCH_SUCCESS 이후 한 명이 나갔을 때 CLOSED,
남은 참가자의 MATCH_REQUEUED, 새 상대와 다음 MATCH_SUCCESS까지 검증한다.
