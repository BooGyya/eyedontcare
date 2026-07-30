if redis.call('EXISTS', KEYS[1]) == 0 then
    return 'ROOM_NOT_FOUND'
end
if redis.call('EXISTS', KEYS[2]) == 0 then
    return 'CORRUPTED'
end

local room_type = redis.call('HGET', KEYS[1], 'roomType')
local game_name = redis.call('HGET', KEYS[1], 'gameName')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
local created_at = redis.call('HGET', KEYS[1], 'createdAt')
if room_type == false or game_name == false or room_code == false
    or room_status == false or created_at == false then
    return 'CORRUPTED'
end
if room_type ~= 'INVITE'
    or room_code ~= ARGV[5]
    or string.match(room_code, '^%d%d%d%d$') == nil then
    return 'CORRUPTED'
end
if room_status ~= 'WAITING'
    and room_status ~= 'COUNTDOWN'
    and room_status ~= 'CLOSED' then
    return 'CORRUPTED'
end

local participant_json = redis.call('HGET', KEYS[2], ARGV[2])
if participant_json == false then
    return 'PARTICIPANT_NOT_FOUND'
end
local decode_ok, participant = pcall(cjson.decode, participant_json)
local max_participants = tonumber(ARGV[6])
if not decode_ok or type(participant) ~= 'table'
    or (participant.roomRole ~= 'HOST' and participant.roomRole ~= 'PLAYER')
    or type(participant.slotNo) ~= 'number'
    or participant.slotNo % 1 ~= 0
    or max_participants == nil
    or participant.slotNo < 1
    or participant.slotNo > max_participants then
    return 'CORRUPTED'
end

if room_status == 'CLOSED' then
    return 'ALREADY_CLOSED'
end

local indexed_room_id = redis.call('GET', KEYS[3])
if indexed_room_id == false or indexed_room_id ~= ARGV[1] then
    return 'CORRUPTED'
end

local active_ttl = tonumber(ARGV[3])
local closed_ttl = tonumber(ARGV[4])
if active_ttl == nil or active_ttl < 1
    or closed_ttl == nil or closed_ttl < 1 then
    return 'CORRUPTED'
end

if room_status == 'WAITING' and participant.roomRole == 'PLAYER' then
    redis.call('HDEL', KEYS[2], ARGV[2])
    redis.call('PEXPIRE', KEYS[1], active_ttl)
    redis.call('PEXPIRE', KEYS[2], active_ttl)
    redis.call('PEXPIRE', KEYS[3], active_ttl)
    return 'LEFT'
end

redis.call('DEL', KEYS[3])
redis.call('HSET', KEYS[1], 'roomStatus', 'CLOSED')
redis.call('PEXPIRE', KEYS[1], closed_ttl)
redis.call('PEXPIRE', KEYS[2], closed_ttl)
return 'ROOM_CLOSED'
