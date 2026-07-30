if redis.call('EXISTS', KEYS[1]) == 0 then return 'STALE' end
if redis.call('EXISTS', KEYS[2]) == 0 then return 'CORRUPTED' end
local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
if (room_type == 'INVITE' and room_code ~= ARGV[2])
    or (room_type == 'RANDOM' and room_code ~= false) then return 'CORRUPTED' end
if room_status == 'CLOSED' then return 'ROOM_CLOSED' end
if room_status ~= 'COUNTDOWN' then return 'INVALID_STATE' end
if redis.call('HGET', KEYS[1], 'countdownId') ~= ARGV[3]
    or redis.call('HGET', KEYS[1], 'countdownEndsAt') ~= ARGV[4] then
    return 'STALE'
end
if room_type == 'INVITE' and redis.call('GET', KEYS[3]) ~= ARGV[1] then
    return 'CORRUPTED'
end

local entries = redis.call('HGETALL', KEYS[2])
local host_count = 0
local player_count = 0
local used_slots = {}
for index = 1, #entries, 2 do
    local ok, participant = pcall(cjson.decode, entries[index + 1])
    if not ok or type(participant) ~= 'table'
        or type(participant.slotNo) ~= 'number'
        or participant.slotNo % 1 ~= 0
        or participant.slotNo < 1
        or participant.slotNo > tonumber(ARGV[5])
        or used_slots[participant.slotNo] then
        return 'CORRUPTED'
    end
    used_slots[participant.slotNo] = true
    if participant.roomRole == 'HOST' then
        host_count = host_count + 1
        if participant.calibrationStatus ~= 'COMPLETED' then return 'INVALID_STATE' end
    elseif participant.roomRole == 'PLAYER' then
        player_count = player_count + 1
        if participant.calibrationStatus ~= 'COMPLETED'
            or participant.isReady ~= true then return 'INVALID_STATE' end
    else
        return 'CORRUPTED'
    end
end
if (room_type == 'INVITE' and (host_count ~= 1 or player_count ~= 1))
    or (room_type == 'RANDOM' and (host_count ~= 0 or player_count ~= 2
        or not used_slots[1] or not used_slots[2])) then
    return 'INVALID_STATE'
end

redis.call('HSET', KEYS[1], 'roomStatus', 'IN_GAME')
redis.call('HDEL', KEYS[1], 'countdownId', 'countdownEndsAt')
if room_type == 'INVITE' then redis.call('DEL', KEYS[3]) end
redis.call('PEXPIRE', KEYS[1], ARGV[6])
redis.call('PEXPIRE', KEYS[2], ARGV[6])
return 'STARTED'
