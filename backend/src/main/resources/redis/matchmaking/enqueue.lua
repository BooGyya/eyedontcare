-- entry Hash와 게임별 Sorted Set 등록을 하나의 원자 연산으로 수행한다.
-- entry가 이미 있으면 기존 상태와 큐를 건드리지 않는다.
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- entry 없이 남은 ghost member는 새 신청을 막지 않도록 모든 게임 큐에서 복구한다.
for index = 2, #KEYS do
    redis.call('ZREM', KEYS[index], ARGV[1])
end

redis.call(
    'HSET',
    KEYS[1],
    'participantKey', ARGV[1],
    'gameType', ARGV[2],
    'matchStatus', ARGV[3],
    'waitingRoomId', ARGV[4],
    'queuedAt', ARGV[5],
    'statusChangedAt', ARGV[6],
    'matchAttemptId', ARGV[7]
)
redis.call('PEXPIRE', KEYS[1], ARGV[8])
redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1])

return 1
