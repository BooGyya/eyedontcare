if redis.call('EXISTS', KEYS[3]) == 1 then
    return 2
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

local currentRoomId = redis.call('HGET', KEYS[1], 'waitingRoomId')
local currentGameType = redis.call('HGET', KEYS[1], 'gameType')
local currentStatus = redis.call('HGET', KEYS[1], 'matchStatus')

if currentRoomId ~= ARGV[1] or currentGameType ~= ARGV[2] then
    return 0
end

if currentStatus ~= 'ENTERING_ROOM' and currentStatus ~= 'IN_WAITING_ROOM' then
    return 0
end

redis.call(
    'HSET',
    KEYS[1],
    'matchStatus', 'SEARCHING',
    'waitingRoomId', '',
    'queuedAt', ARGV[3],
    'statusChangedAt', ARGV[3],
    'matchAttemptId', ''
)
redis.call('PEXPIRE', KEYS[1], ARGV[4])
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[5])
redis.call('SET', KEYS[3], '1', 'PX', ARGV[4])

return 1
