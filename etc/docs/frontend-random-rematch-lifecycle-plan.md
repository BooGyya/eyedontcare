# RANDOM 대기방 프론트 자동 재매칭 생명주기 구현 계획

## 1. 현재 흐름

- 랜덤 매칭 버튼은 `frontend/src/components/games/GameRoomDialog.vue`의 `startMatching()`에서 `POST /api/v1/match/join`을 호출한다.
- 응답에 `waitingRoomId`가 있으면 즉시 입장하고, 없으면 `frontend/src/composables/useMatchSocket.ts`의 `connect()`로 `/ws/match`를 연결한다.
- `MATCH_SUCCESS`는 `GameRoomDialog`의 `onMatchSuccess` → `enterRandomRoom()`에서 처리한다.
- 이때 `enterRandomRoom()`이 `stopMatching()`을 호출하여 Match WebSocket을 먼저 닫는다.
- `frontend/src/pages/GameDetailPage.vue`의 `handleEnterRoom()`이 다음 값을 query에 넣어 `GameReadyPage`로 이동한다.
  - `mode=random`
  - `roomId={매칭된 UUID}`
- `gameType`은 route에 직접 저장하지 않고 `gameId`와 `GAME_NAME_BY_ID`로 다시 계산한다.
- `roomType`도 직접 저장하지 않고 `mode=random`으로 판단하며, 실제 `RANDOM` 여부는 첫 `ROOM_STATE`에서 재검증할 수 있다.
- `frontend/src/pages/GameReadyPage.vue`의 `initRandomSession()`은 다음 순서로 동작한다.
  1. query의 `roomId` 확인
  2. `resolveIdentity()` 호출
  3. `liveRoomId`, `liveIdentity` 저장
  4. `/ws/waiting-rooms/{roomId}` 연결
- `GameReadyPage`는 Match WebSocket을 다시 연결하지 않는다.
- WaitingRoom의 `ROOM_STATE`는 composable에 저장되지만 페이지에서는 `COUNTDOWN`만 감시한다. `roomStatus=CLOSED` 처리는 없다.
- 따라서 현재 상대 퇴장 시 실제 프론트 동작은 다음과 같다.

```text
ROOM_STATE:CLOSED 수신
→ roomState에는 저장
→ REMATCHING 전환 없음
→ 서버가 WaitingRoom WebSocket 종료
→ /ws/match가 이미 닫혀 MATCH_REQUEUED·새 MATCH_SUCCESS 수신 불가
→ 기존 준비 화면에 머무름
```

- query의 `roomId`만 바뀌면 동일한 `game-ready` route record와 컴포넌트를 사용하므로 `GameReadyPage`는 재사용된다. `onMounted()`는 다시 실행되지 않는다.
- unmount 시 `GameReadyPage`에서 카메라, countdown, WaitingRoom WebSocket만 정리한다.
- 현재 나가기 버튼의 `handleLeaveRoom()`은 REST leave/cancel을 호출하지 않고 카메라를 끈 뒤 게임 상세로 이동한다.
- 상단 `RouterLink`도 REST 정리 없이 직접 이동한다.
- 새로고침과 브라우저 이탈은 WebSocket disconnect에 의한 서버 정리에 의존한다.
- 회원 identity는 `{accessToken}`, 게스트는 `{guestSessionId}`이다. `participantKey`는 `frontend/src/api/identity.ts`에서 각각 `USER:{userId}`, `GUEST:{guestSessionId}`로 계산할 수 있다.

## 2. 백엔드 이벤트 계약

| 이벤트 | WebSocket | payload | 현재 처리 | 필요한 처리 |
|---|---|---|---|---|
| `MATCH_REQUEUED` | `/ws/match` | `{type, gameType}` | composable은 지원하지만 `GameReadyPage`에 연결되지 않음 | `REMATCHING` 유지, gameType 검증, 중복 무시 |
| `MATCH_SUCCESS` | `/ws/match` | `{type, roomId, gameType}` | 최초 매칭만 `GameRoomDialog`에서 처리 | 새 roomId 검증 후 방 교체 |
| `MATCH_ERROR` | `/ws/match` | `{type, code, message}` | dialog에서만 오류 표시 | 재매칭 실패를 `ERROR`로 전환 |
| `ROOM_STATE` | `/ws/waiting-rooms/{roomId}` | `{type, data:{roomId, roomType, gameName, roomStatus, countdownEndsAt, participants, createdAt}}` | 상태 저장, `COUNTDOWN`만 감시 | 현재 roomId 검증, `CLOSED`/신규 방 연결 완료 처리 |
| `GAME_START` | `/ws/waiting-rooms/{roomId}` | `{type, data:{roomId, gameName, startedAt, openviduUrl, token}}` | 즉시 게임 화면 이동 | activeRoomId 및 socket generation 검증 후 이동 |
| `ERROR` | `/ws/waiting-rooms/{roomId}` | `{type, data:{code, message}}` | toast와 socket error 상태 | 현재 연결의 오류인지 검증 후 재시도/ERROR 분기 |
| `ROOM_CLOSED` | 없음 | 없음 | 없음 | 별도 이벤트로 가정하지 않음 |

계약상 중요한 점은 다음과 같다.

- `CLOSED`는 별도 이벤트가 아니라 `ROOM_STATE.data.roomStatus`이다.
- `MATCH_REQUEUED`에는 `previousRoomId`나 `rematchToken`이 없고 `gameType`만 있다.
- `MATCH_SUCCESS`와 `ROOM_STATE`에는 roomId가 있어 방 구분이 가능하다.
- Match WebSocket 재연결 시 별도 REST 신청은 필요하지 않다. 첫 프레임으로 다시 인증하면 된다.
- 다만 entry가 없으면 WebSocket 연결만으로 매칭 신청이 생성되지는 않는다.
- Match WebSocket 종료 시 서버는 현재 entry가 `SEARCHING`이면 취소한다. `ENTERING_ROOM`과 `IN_WAITING_ROOM`은 보호된다.
- 서버의 `InMemoryMatchSessionRegistry.unregister()`는 현재 등록된 세션과 일치할 때만 제거하므로, 새 세션 등록 뒤 늦게 닫히는 이전 세션은 보호된다.
- `MATCH_SUCCESS`는 재연결 인증 시 `ENTERING_ROOM` 상태라면 재전송될 수 있으므로 중복 수신을 전제로 해야 한다.
- `MATCH_REQUEUED`는 같은 요청의 `ALREADY_REQUEUED`에서는 재전송하지 않지만 프론트는 멱등 처리해야 한다.

## 3. 발견된 문제

### 3.1 Match WebSocket 조기 종료

- `GameRoomDialog.enterRandomRoom()`이 최초 성공 즉시 소켓을 닫는다.
- `GameReadyPage`는 `/ws/match`를 다시 연결하지 않는다.
- 자동 재매칭 이벤트를 전혀 받을 수 없다.

### 3.2 `CLOSED` 미처리

- `GameReadyPage`는 `COUNTDOWN`만 감시한다.
- 상대 퇴장 뒤 기존 상대·ready·countdown 상태가 남는다.

### 3.3 새 roomId 반영 경로 부재

- 새 `MATCH_SUCCESS`를 처리하는 함수와 route 교체 로직이 없다.
- query만 바꿔도 컴포넌트가 재사용되므로 `onMounted()`에만 의존해서는 재연결되지 않는다.

### 3.4 오래된 이벤트 차단 장치 부족

- 두 WebSocket composable 모두 connection generation을 관리하지 않는다.
- 새 방 연결 중 이전 방 이벤트가 새 상태를 덮는 경쟁을 명시적으로 막지 않는다.

### 3.5 나가기 정리 누락

- 활성 방에서도 `leaveRoom()`을 호출하지 않는다.
- 재매칭 중에도 `cancelMatch()`을 호출하지 않는다.
- 뒤로가기 링크 역시 정리 흐름을 우회한다.

### 3.6 RANDOM 상대 식별 오류

- 현재 `liveOpponent`는 `roomRole !== myRoomRole`로 상대를 찾는다.
- 백엔드 RANDOM 참가자는 둘 다 `PLAYER`이므로 RANDOM에서는 상대를 찾지 못한다.
- RANDOM만 `participantKey !== currentParticipantKey()` 기준으로 변경해야 한다.

### 3.7 방 종속 상태 누수

- `isReady`, countdown modal, 상대 정보, WaitingRoom 오류를 새 방 전환 시 초기화하는 흐름이 없다.
- `GAME_START`도 roomId 검증 없이 처리한다.

### 3.8 Match WebSocket close 분류 부재

- 의도적인 종료와 네트워크 종료를 구분하지 않는다.
- `status=open`은 TCP/WebSocket open일 뿐 서버의 AUTH 완료를 뜻하지 않는다.

## 4. 제안 상태 머신

상태는 다음과 같이 제한한다.

```text
CONNECTING_ROOM
WAITING_ROOM
COUNTDOWN
REMATCHING
CONNECTING_NEW_ROOM
LEAVING
ERROR
```

주요 전이는 다음과 같다.

```text
최초 페이지 진입
→ CONNECTING_ROOM
→ 첫 유효 ROOM_STATE:WAITING
→ WAITING_ROOM

ROOM_STATE:COUNTDOWN
→ COUNTDOWN

현재 방 ROOM_STATE:CLOSED
→ REMATCHING

MATCH_REQUEUED
→ REMATCHING 유지

다른 roomId의 유효 MATCH_SUCCESS
→ CONNECTING_NEW_ROOM
→ route replace
→ 새 WaitingRoom 연결
→ 첫 유효 ROOM_STATE
→ WAITING_ROOM 또는 COUNTDOWN

사용자 나가기
→ LEAVING
→ REST 정리
→ 소켓 종료
→ 게임 상세 이동

복구 불가능 오류
→ ERROR
```

소유 범위는 다음이 적합하다.

- `GameReadyPage`
  - 카메라, 캘리브레이션 UI, ready, countdown, 오류 UI
- 신규 작은 lifecycle composable
  - `flowState`
  - `activeRoomId`
  - `roomGeneration`
  - 현재 `gameType`
  - `isLeaving`
  - 이벤트 수락/거절 규칙
- 각 WebSocket composable
  - 실제 socket
  - connection generation
  - 연결 상태
  - 의도적 종료 여부

`activeRoomId`가 런타임 source of truth가 되어야 한다. route query는 최초 복원값이자 URL 미러로만 사용한다.

새 roomId는 `router.replace()`로 반영한다. 이전 방이 브라우저 history에 남아 뒤로가기로 CLOSED 방에 복귀하면 안 되기 때문이다. query만 변경하므로 강제 remount는 하지 않는다.

## 5. Match WebSocket lifecycle

현재 `useMatchSocket()` 인스턴스는 함수 내부 closure와 `onScopeDispose()`에 묶여 있어 이전 화면에서 그대로 넘겨받을 수 없다.

최소 변경안은 다음과 같다.

1. `GameRoomDialog`의 현재 초기 매칭 흐름은 유지한다.
2. RANDOM `GameReadyPage` mount 시 Match WebSocket을 새로 연결한다.
3. Match WebSocket을 WaitingRoom WebSocket보다 먼저 연결한다.
4. 같은 페이지에서 이미 `connecting/open`이면 추가 연결하지 않는다.
5. 기존 roomId의 재전송 `MATCH_SUCCESS`는 동일 roomId이므로 무시한다.
6. 준비방·REMATCHING·새 준비방 연결 후에도 계속 유지한다.
7. 게임 시작, 사용자 나가기, 실제 route 이탈에서만 종료한다.
8. INVITE에서는 생성하지 않는다.

전역 singleton이나 Pinia socket store는 이번 범위에서는 도입하지 않는 편이 낫다. 페이지가 다시 연결해도 기존 entry는 `ENTERING_ROOM` 이상이므로 최초 dialog 소켓 종료로 취소되지 않고, 인증 시 현재 `MATCH_SUCCESS`도 재전송된다.

예상하지 못한 종료는 상태별로 처리한다.

- `WAITING_ROOM`, `COUNTDOWN`, `CONNECTING_NEW_ROOM`
  - entry가 SEARCHING이 아니므로 1회 정도의 제한된 재연결 가능
- `REMATCHING`
  - 서버가 socket close 시 SEARCHING entry를 삭제할 수 있으므로 소켓만 재연결하면 부족
  - 자동 무한 복구 대신 `ERROR`와 "다시 찾기"를 제공
  - 사용자가 재시도하면 WebSocket 연결 후 `joinMatch()`을 1회 호출
- `LEAVING`
  - 재연결하지 않음

현재 소켓을 건강한 상태에서 교체하는 동작은 피해야 한다. 기존 `connect()`가 이전 소켓부터 닫기 때문에 REMATCHING 중에는 SEARCHING entry를 취소할 수 있다.

## 6. WaitingRoom 재연결 흐름

현재 방에서 `ROOM_STATE:CLOSED` 수신 시:

1. `data.roomId === activeRoomId` 확인
2. `roomType === RANDOM`, `gameName` 일치 확인
3. 이미 `REMATCHING`이면 no-op
4. countdown timer와 game-start modal 종료
5. `REMATCHING` 전환
6. `isReady=false`
7. 상대·이전 roomState·WaitingRoom 오류 초기화
8. 기존 WaitingRoom socket 종료
9. 기존 connection generation 무효화
10. Match WebSocket은 유지

새 `MATCH_SUCCESS` 수신 시:

1. `LEAVING`이면 화면 전환은 무시
2. `gameType`이 현재 게임과 다르면 무시
3. 현재 `activeRoomId`와 같으면 중복이므로 무시
4. 새 generation 발급
5. `CONNECTING_NEW_ROOM` 전환
6. `activeRoomId`를 새 값으로 먼저 교체해 이전 이벤트 차단
7. 기존 WaitingRoom handler 제거 및 socket 정리
8. `router.replace()`로 query의 `roomId` 갱신
9. replace 완료 후 generation이 여전히 유효한지 확인
10. 새 roomId로 WaitingRoom WebSocket 연결
11. 첫 `ROOM_STATE` 수신 전까지 연결 중 UI 표시
12. 유효한 첫 `ROOM_STATE` 후 `WAITING_ROOM`/`COUNTDOWN` 전환

일반적인 route watcher로 자동 연결하지 않고 `switchRandomRoom()` 같은 명시적 함수에서 route와 socket을 함께 바꾸는 편이 안전하다. watcher와 직접 연결을 같이 사용하면 중복 socket 생성 가능성이 크다.

WebSocket handler는 연결 당시의 `roomId`, socket instance, generation을 closure로 캡처하고 현재 값과 하나라도 다르면 상태를 변경하지 않아야 한다.

## 7. 카메라/calibration/ready state

선택안은 A이다.

```text
A. 카메라와 완료된 로컬 캘리브레이션 유지, 새 방에 상태 재전송
```

유지할 상태:

- 카메라 권한
- 살아 있는 `MediaStream`
- MediaPipe/눈 인식 모델
- 완료된 눈·시선 캘리브레이션 프로필
- `isCalibrated=true`

초기화할 상태:

- `isReady=false`
- 상대 participant
- 상대 ready/calibration 상태
- countdown 및 게임 시작 modal
- 이전 WaitingRoom 오류
- 이전 방 미디어 연결
- 진행 중이던 미완료 캘리브레이션 sampling 작업

새 방의 첫 `ROOM_STATE` 이후:

- 내 participant를 `currentParticipantKey()`로 찾는다.
- 로컬 캘리브레이션이 완료됐고 서버 상태가 `PENDING`이면 같은 generation에서 한 번만 다음 순서로 보낸다.
  1. `CALIBRATION_STATUS:IN_PROGRESS`
  2. `CALIBRATION_STATUS:COMPLETED`
- 백엔드가 `PENDING → IN_PROGRESS → COMPLETED` 순서를 요구하기 때문이다.
- `READY_STATUS:true`는 자동 재전송하지 않는다. 새 상대와 의도치 않게 즉시 게임이 시작되지 않도록 사용자가 다시 준비하도록 한다.
- 서버가 이미 `COMPLETED`라면 중복 전송하지 않는다.

RANDOM 상대는 `roomRole`이 아니라 `participantKey !== currentParticipantKey()`로 찾는다. INVITE의 기존 HOST/PLAYER 판정은 그대로 둔다.

## 8. 사용자 나가기

활성 RANDOM 방:

```text
LEAVING 전환
→ POST /api/v1/waiting-rooms/{activeRoomId}/leave
→ WaitingRoom socket 종료
→ Match socket 종료
→ 카메라 및 상태 정리
→ 기존 게임 상세로 이동
```

REST가 실패해도 화면은 떠나는 정책이 적합하다. 오류를 toast로 알리고 socket 종료를 서버의 disconnect fallback으로 사용한다.

REMATCHING:

```text
LEAVING 전환
→ DELETE /api/v1/match/cancel
→ Match socket 종료
→ WaitingRoom socket 정리
→ 게임 상세 이동
```

추가 조건:

- `isLeaving`으로 중복 클릭과 중복 REST를 막는다.
- REST 요청 전에 Match WebSocket을 닫으면 SEARCHING entry가 먼저 사라져 cancel이 404가 될 수 있으므로 REST가 먼저다.
- 나가기 뒤 도착한 `MATCH_SUCCESS`는 화면 전환에 사용하지 않는다.
- 단, 이미 새 방이 생성된 경쟁이라면 해당 roomId로 best-effort `leaveRoom()`을 호출할 수 있도록 cleanup 경로를 둔다.
- 내부 `router.replace()`는 사용자 나가기로 처리하지 않는다.
- SPA 뒤로가기는 `onBeforeRouteLeave()`에서 동일한 정리 함수를 사용한다.
- 새로고침과 탭 종료에서는 비동기 REST 완료를 보장할 수 없으므로 현재처럼 WebSocket disconnect 정리를 유지한다.
- INVITE 나가기 정책은 이번 작업에서 바꾸지 않는다.

## 9. 파일별 구현 계획

| 파일 | 계획 | 충돌 위험 |
|---|---|---|
| `frontend/src/pages/GameReadyPage.vue` | RANDOM 상태 머신 연동, Match WebSocket 연결, CLOSED/새 MATCH_SUCCESS 처리, route replace, 상태 초기화, 나가기 API 분기, REMATCHING/ERROR UI | 높음. 최근 카메라·countdown 변경이 집중됨 |
| `frontend/src/composables/useMatchSocket.ts` | 연결 generation, 중복 connect 방지, 의도적/비의도적 close 구분, stale socket 이벤트 차단, close 콜백 추가 | 중간 |
| `frontend/src/composables/useWaitingRoomSocket.ts` | roomId/connection generation 캡처, 이전 socket 메시지·close 무시, 현재 방 상태 명시적 reset | 중간 |
| `frontend/src/composables/useRandomRematchLifecycle.ts` | 상태, activeRoomId, generation, 이벤트 수락 규칙만 분리한 작은 composable 추가 | 신규 파일이라 충돌 낮음 |
| `frontend/src/pages/GameReadyPage.spec.ts` | RANDOM 전체 생명주기와 UI/REST 통합 테스트 확장 | 중간 |
| `frontend/src/composables/useMatchSocket.spec.ts` | 유지·종료·중복·stale 이벤트 테스트 | 낮음 |
| `frontend/src/composables/useWaitingRoomSocket.spec.ts` | 방 교체와 이전 이벤트 차단 테스트 | 낮음 |
| `frontend/src/composables/useRandomRematchLifecycle.spec.ts` | 순수 상태 전이 테스트 | 신규 파일 |
| `frontend/src/api/identity.spec.ts` | USER/GUEST participantKey 회귀 테스트 | 신규 파일 |

`GameRoomDialog.vue`는 수정하지 않는 방향이다. 초대코드 처리와 HOST 판정도 그대로 유지한다.

`match.ts`, `waitingRoom.ts`, router, 계약 type 파일도 현재 API가 준비되어 있어 수정할 필요가 없다.

## 10. 구현 순서

1. `useMatchSocket`에 generation·중복 연결·close 분류 추가 및 테스트
2. `useWaitingRoomSocket`에 room generation과 stale 이벤트 차단 추가 및 테스트
3. 작은 RANDOM lifecycle composable과 상태 전이 테스트 추가
4. `GameReadyPage` RANDOM mount에서 Match WebSocket 연결
5. 현재 방 `CLOSED`와 `MATCH_REQUEUED` 처리
6. 새 `MATCH_SUCCESS` → route replace → WaitingRoom 재연결 구현
7. opponent/ready/countdown/error 상태 초기화
8. 카메라·완료된 calibration 유지 및 새 방 서버 상태 재전송
9. 활성 방 leave와 REMATCHING cancel 분기
10. REMATCHING·ERROR 최소 UI 추가
11. GameReadyPage 통합 테스트
12. 전체 test, type-check, lint 실행

## 11. 테스트 계획

### `useMatchSocket.spec.ts`

- `does not create another socket while the current connection is open`
- `keeps the socket open after MATCH_SUCCESS`
- `forwards MATCH_REQUEUED`
- `ignores events from a replaced socket`
- `distinguishes intentional close from unexpected close`
- `does not reconnect after an intentional close`

### `useWaitingRoomSocket.spec.ts`

- `ignores ROOM_STATE from the previous room after reconnect`
- `ignores close and error events from a stale socket`
- `exposes the connected room id and generation`
- `clears the previous room state when connecting a new room`
- `keeps only one active socket`

### `useRandomRematchLifecycle.spec.ts`

- 현재 방 CLOSED → REMATCHING
- 이전 방 CLOSED 무시
- 중복 CLOSED 멱등
- 다른 gameType의 `MATCH_REQUEUED` 무시
- LEAVING 중 매칭 이벤트 무시
- 동일 roomId의 `MATCH_SUCCESS` 무시
- 새 roomId → CONNECTING_NEW_ROOM
- A 연결 중 B 수신 시 A generation 무효화
- 새 방 ROOM_STATE 이후 WAITING_ROOM
- COUNTDOWN 전환과 CLOSED 시 취소
- 다른 roomId의 ROOM_STATE/GAME_START 무시

### `GameReadyPage.spec.ts`

- RANDOM 진입 시 Match/WaitingRoom WebSocket 모두 연결
- INVITE에서는 Match WebSocket 미연결
- CLOSED 후 REMATCHING 문구 표시
- 이전 상대 카드·ready·countdown 초기화
- REMATCHING 중 Match socket 유지
- 새 성공 시 route `replace`
- 새 roomId WaitingRoom 연결
- 첫 ROOM_STATE 이후 준비 UI 복귀
- 내부 roomId 교체 시 컴포넌트와 Match socket 유지
- 활성 방 나가기 시 WaitingRoom leave 호출
- REMATCHING 나가기 시 match cancel 호출
- 나가기 중 늦은 성공이 route를 되돌리지 않음
- 회원/게스트 identity
- RANDOM 상대를 participantKey로 식별
- 완료된 calibration 재전송 및 ready 미재전송

Mock 대상:

- 다중 인스턴스를 지원하는 `MockWebSocket`
- `fetch`
- localStorage/sessionStorage
- Vue Router
- 카메라/LiveKit composable
- countdown 및 reconnect는 fake timer 사용

현재 읽기 전용 검증 결과:

```text
npm.cmd test -- --run
→ 28 files, 207 tests 통과

npm.cmd run type-check
→ 통과

npm.cmd run lint
→ 통과
```

Canvas와 `scrollTo()` jsdom 미구현 경고는 있었지만 테스트 실패는 아니다.

## 12. 위험과 결정 필요사항

### 구현 전에 반드시 결정

- Match WebSocket이 REMATCHING 중 끊겼을 때 자동으로 `joinMatch()`을 1회 재호출할지, ERROR에서 사용자 재시도만 허용할지
  - 권장: 사용자 재시도
- 나가기 후 이동 위치
  - 현재 코드는 홈이 아니라 해당 게임 상세로 이동
- cancel과 새 매칭 성사가 동시에 발생했을 때 생성된 새 방을 어느 수준까지 best-effort 정리할지

### 현재 코드로 결정 가능

- route는 source of truth가 아니라 activeRoomId의 미러
- 새 roomId는 `router.replace()`
- 컴포넌트 강제 remount 없음
- 카메라와 완료된 calibration 유지
- ready는 새 방에서 false
- RANDOM 상대는 participantKey로 구분
- `MATCH_REQUEUED`는 REMATCHING 유지용으로만 처리
- INVITE 흐름은 분리하여 그대로 유지

### 후속 작업으로 연기 가능

- `MATCH_REQUEUED`에 `previousRoomId` 또는 `rematchToken` 추가
- `MATCH_SUCCESS`에 match attempt/generation 식별자 추가
- Match WebSocket `AUTH_OK` 이벤트 추가
- 현재 matchmaking 상태 조회 API 추가
- WebSocket 인증 실패에서 회원 토큰 만료와 게스트 세션 만료를 구분하는 계약

현재 payload만으로는 동일 gameType에서 과거와 현재의 `MATCH_REQUEUED`를 완벽히 구분할 수 없다. 프론트 상태·socket generation으로 대부분 차단할 수 있지만 완전한 상관관계는 백엔드 식별자가 있어야 한다.

## 13. 예상 변경 범위

수정 예상:

- `frontend/src/pages/GameReadyPage.vue`
- `frontend/src/composables/useMatchSocket.ts`
- `frontend/src/composables/useWaitingRoomSocket.ts`
- 각 대응 테스트 파일

신규 예상:

- `frontend/src/composables/useRandomRematchLifecycle.ts`
- `frontend/src/composables/useRandomRematchLifecycle.spec.ts`
- `frontend/src/api/identity.spec.ts`

수정하지 않을 파일:

- `frontend/src/components/games/GameRoomDialog.vue`
- `frontend/src/pages/GameDetailPage.vue`
- `frontend/src/api/match.ts`
- `frontend/src/api/waitingRoom.ts`
- router 및 기존 계약 type
- INVITE 초대코드·HOST 로직
- 게임 진행·결과 관련 파일
- `backend/**`

백엔드 변경은 이번 구현에 필수는 아니며, 이벤트 상관관계 강화만 후속 개선 대상이다.

