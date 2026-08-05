-- roomId와 participantKey가 현재 entry와 일치할 때만 삭제한다.
-- KEYS[1]은 entry Hash, KEYS[2..]는 모든 게임별 큐다.
if redis.call('EXISTS', KEYS[1]) == 0 then
    for index = 2, #KEYS do
        redis.call('ZREM', KEYS[index], ARGV[2])
    end
    return 0
end

if redis.call('HGET', KEYS[1], 'participantKey') ~= ARGV[2]
    or redis.call('HGET', KEYS[1], 'waitingRoomId') ~= ARGV[1] then
    return 2
end

if ARGV[3] == 'true' then
    local status = redis.call('HGET', KEYS[1], 'matchStatus')
    if status ~= 'ENTERING_ROOM' and status ~= 'IN_WAITING_ROOM' then
        return 2
    end
end

for index = 2, #KEYS do
    redis.call('ZREM', KEYS[index], ARGV[2])
end
redis.call('DEL', KEYS[1])

return 1
