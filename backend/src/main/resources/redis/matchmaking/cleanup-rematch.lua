-- 이전 RANDOM 방에서 생성된 자동 재매칭 entry만 token 비교 후 원자적으로 취소한다.
-- KEYS[1]은 entry Hash, KEYS[2]는 previousRoomId별 marker, KEYS[3..]는 모든 게임 큐다.
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

if redis.call('HGET', KEYS[1], 'participantKey') ~= ARGV[1] then
    return 2
end

local status = redis.call('HGET', KEYS[1], 'matchStatus')
local waitingRoomId = redis.call('HGET', KEYS[1], 'waitingRoomId')
if status ~= 'SEARCHING' or waitingRoomId ~= '' then
    return 3
end

local entryToken = redis.call('HGET', KEYS[1], 'rematchToken')
local markerToken = redis.call('GET', KEYS[2])
if entryToken == false or entryToken == '' or markerToken == false then
    return 2
end
if entryToken ~= markerToken then
    return 4
end

local queued = false
for index = 3, #KEYS do
    if redis.call('ZSCORE', KEYS[index], ARGV[1]) ~= false then
        queued = true
        break
    end
end
if not queued then
    return 3
end

for index = 3, #KEYS do
    redis.call('ZREM', KEYS[index], ARGV[1])
end
redis.call('DEL', KEYS[1], KEYS[2])

return 1
