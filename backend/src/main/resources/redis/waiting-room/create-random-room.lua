if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end
if ARGV[4] == ARGV[6] then return -1 end

local first_ok, first = pcall(cjson.decode, ARGV[5])
local second_ok, second = pcall(cjson.decode, ARGV[7])
if not first_ok or not second_ok
    or first.roomRole ~= 'PLAYER' or second.roomRole ~= 'PLAYER'
    or first.slotNo ~= 1 or second.slotNo ~= 2
    or first.isReady ~= false or second.isReady ~= false
    or first.calibrationStatus ~= 'PENDING'
    or second.calibrationStatus ~= 'PENDING' then
    return -1
end

redis.call(
    'HSET',
    KEYS[1],
    'roomId', ARGV[1],
    'roomType', 'RANDOM',
    'gameName', ARGV[2],
    'roomStatus', 'WAITING',
    'createdAt', ARGV[3]
)
redis.call('HSET', KEYS[2], ARGV[4], ARGV[5], ARGV[6], ARGV[7])
redis.call('PEXPIRE', KEYS[1], ARGV[8])
redis.call('PEXPIRE', KEYS[2], ARGV[8])
return 1
