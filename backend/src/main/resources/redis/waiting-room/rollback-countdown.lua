if redis.call('EXISTS', KEYS[1]) == 0 then return 'STALE' end
local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
if (room_type == 'INVITE' and room_code ~= ARGV[2])
    or (room_type == 'RANDOM' and room_code ~= false) then return 'CORRUPTED' end
if redis.call('HGET', KEYS[1], 'roomStatus') ~= 'COUNTDOWN'
    or redis.call('HGET', KEYS[1], 'countdownId') ~= ARGV[3] then
    return 'STALE'
end
if redis.call('EXISTS', KEYS[2]) == 0
    or (room_type == 'INVITE' and redis.call('GET', KEYS[3]) ~= ARGV[1]) then
    return 'CORRUPTED'
end

redis.call('HSET', KEYS[1], 'roomStatus', 'WAITING')
redis.call('HDEL', KEYS[1], 'countdownId', 'countdownEndsAt')
redis.call('PEXPIRE', KEYS[1], ARGV[4])
redis.call('PEXPIRE', KEYS[2], ARGV[4])
if room_type == 'INVITE' then redis.call('PEXPIRE', KEYS[3], ARGV[4]) end
return 'ROLLED_BACK'
