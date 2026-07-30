local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
if room_status == false then return 'ROOM_NOT_FOUND' end
if room_type ~= 'INVITE' or room_code ~= ARGV[2] then return 'CORRUPTED' end
if room_status ~= 'WAITING' then return 'STATE_CHANGE_NOT_ALLOWED' end
if redis.call('GET', KEYS[3]) ~= ARGV[1] then return 'CORRUPTED' end

local raw = redis.call('HGET', KEYS[2], ARGV[3])
if raw == false then return 'PARTICIPANT_NOT_FOUND' end
local ok, participant = pcall(cjson.decode, raw)
if not ok or type(participant) ~= 'table'
    or type(participant.isReady) ~= 'boolean'
    or type(participant.calibrationStatus) ~= 'string' then
    return 'CORRUPTED'
end
if participant.roomRole ~= 'PLAYER' then return 'STATE_CHANGE_NOT_ALLOWED' end

local target = ARGV[4] == 'true'
if target and participant.calibrationStatus ~= 'COMPLETED' then
    return 'CALIBRATION_REQUIRED'
end
if participant.isReady == target then
    redis.call('PEXPIRE', KEYS[1], ARGV[5])
    redis.call('PEXPIRE', KEYS[2], ARGV[5])
    redis.call('PEXPIRE', KEYS[3], ARGV[5])
    return 'UNCHANGED'
end

participant.isReady = target
redis.call('HSET', KEYS[2], ARGV[3], cjson.encode(participant))
redis.call('PEXPIRE', KEYS[1], ARGV[5])
redis.call('PEXPIRE', KEYS[2], ARGV[5])
redis.call('PEXPIRE', KEYS[3], ARGV[5])
return 'UPDATED'
