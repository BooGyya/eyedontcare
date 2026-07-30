local function result(status)
    return cjson.encode({status = status})
end

local indexed_room_id = redis.call('GET', KEYS[1])
if indexed_room_id == false then
    return result('INVALID_INVITE_CODE')
end
if indexed_room_id ~= ARGV[1] then
    return result('CORRUPTED')
end
if redis.call('EXISTS', KEYS[2]) == 0 then
    return result('INVALID_INVITE_CODE')
end
if redis.call('EXISTS', KEYS[3]) == 0 then
    return result('CORRUPTED')
end

local room_type = redis.call('HGET', KEYS[2], 'roomType')
local game_name = redis.call('HGET', KEYS[2], 'gameName')
local room_code = redis.call('HGET', KEYS[2], 'roomCode')
local room_status = redis.call('HGET', KEYS[2], 'roomStatus')
local created_at = redis.call('HGET', KEYS[2], 'createdAt')
if room_type == false or game_name == false or room_code == false
    or room_status == false or created_at == false then
    return result('CORRUPTED')
end
if room_type ~= 'INVITE' or room_code ~= ARGV[4] then
    return result('CORRUPTED')
end
if room_status == 'COUNTDOWN' or room_status == 'CLOSED' then
    return result('NOT_JOINABLE')
end
if room_status ~= 'WAITING' then
    return result('CORRUPTED')
end
if redis.call('HEXISTS', KEYS[3], ARGV[2]) == 1 then
    return result('ALREADY_JOINED')
end

local max_participants = tonumber(ARGV[5])
local ttl_millis = tonumber(ARGV[6])
if max_participants == nil or max_participants < 1
    or ttl_millis == nil or ttl_millis < 1 then
    return result('CORRUPTED')
end
if redis.call('HLEN', KEYS[3]) >= max_participants then
    return result('FULL')
end

local participant_entries = redis.call('HGETALL', KEYS[3])
local used_slots = {}
local snapshot_participants = {}
for index = 1, #participant_entries, 2 do
    local participant_key = participant_entries[index]
    local decode_ok, participant = pcall(cjson.decode, participant_entries[index + 1])
    if not decode_ok or type(participant) ~= 'table'
        or type(participant.slotNo) ~= 'number'
        or participant.slotNo % 1 ~= 0
        or participant.slotNo < 1
        or participant.slotNo > max_participants
        or used_slots[participant.slotNo]
        or type(participant.displayName) ~= 'string'
        or type(participant.roomRole) ~= 'string'
        or type(participant.isReady) ~= 'boolean'
        or type(participant.calibrationStatus) ~= 'string'
        or type(participant.joinedAt) ~= 'string' then
        return result('CORRUPTED')
    end
    used_slots[participant.slotNo] = true
    snapshot_participants[#snapshot_participants + 1] = {
        participantKey = participant_key,
        participant = participant
    }
end

local available_slot = nil
for slot = 1, max_participants do
    if not used_slots[slot] then
        available_slot = slot
        break
    end
end
if available_slot == nil then
    return result('CORRUPTED')
end

local new_decode_ok, new_participant = pcall(cjson.decode, ARGV[3])
if not new_decode_ok or type(new_participant) ~= 'table' then
    return result('CORRUPTED')
end
new_participant.slotNo = available_slot
local stored_participant = cjson.encode(new_participant)

redis.call('HSET', KEYS[3], ARGV[2], stored_participant)
redis.call('PEXPIRE', KEYS[1], ttl_millis)
redis.call('PEXPIRE', KEYS[2], ttl_millis)
redis.call('PEXPIRE', KEYS[3], ttl_millis)

snapshot_participants[#snapshot_participants + 1] = {
    participantKey = ARGV[2],
    participant = new_participant
}

return cjson.encode({
    status = 'JOINED',
    room = {
        roomId = ARGV[1],
        roomType = room_type,
        gameName = game_name,
        roomCode = room_code,
        roomStatus = room_status,
        createdAt = created_at
    },
    participants = snapshot_participants
})
