if redis.call('EXISTS', KEYS[1]) == 0 then
    return cjson.encode({ status = 'ROOM_NOT_FOUND' })
end
if redis.call('EXISTS', KEYS[2]) == 0 then
    return cjson.encode({ status = 'CORRUPTED' })
end

local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
local game_name = redis.call('HGET', KEYS[1], 'gameName')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
if room_type ~= 'RANDOM' or room_code ~= false or game_name == false then
    return cjson.encode({ status = 'CORRUPTED' })
end
if room_status == 'CLOSED' then
    return cjson.encode({ status = 'ALREADY_CLOSED' })
end
if room_status == 'IN_GAME' then
    return cjson.encode({ status = 'NOT_JOINABLE' })
end
if room_status ~= 'WAITING' and room_status ~= 'COUNTDOWN' then
    return cjson.encode({ status = 'CORRUPTED' })
end

local entries = redis.call('HGETALL', KEYS[2])
if #entries ~= 4 then return cjson.encode({ status = 'CORRUPTED' }) end
local remaining_key = nil
local quitter_found = false
local slots = {}
for index = 1, #entries, 2 do
    local participant_key = entries[index]
    local ok, participant = pcall(cjson.decode, entries[index + 1])
    if not ok or participant.roomRole ~= 'PLAYER'
        or (participant.slotNo ~= 1 and participant.slotNo ~= 2)
        or slots[participant.slotNo] then
        return cjson.encode({ status = 'CORRUPTED' })
    end
    slots[participant.slotNo] = true
    if participant_key == ARGV[2] then
        quitter_found = true
    else
        remaining_key = participant_key
    end
end
if not slots[1] or not slots[2] then
    return cjson.encode({ status = 'CORRUPTED' })
end
if not quitter_found then
    return cjson.encode({ status = 'PARTICIPANT_NOT_FOUND' })
end

redis.call('HSET', KEYS[1], 'roomStatus', 'CLOSED')
redis.call('HDEL', KEYS[1], 'countdownId', 'countdownEndsAt')
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('PEXPIRE', KEYS[2], ARGV[3])
return cjson.encode({
    status = 'CLOSED_NOW',
    roomId = ARGV[1],
    gameName = game_name,
    quitterParticipantKey = ARGV[2],
    remainingParticipantKey = remaining_key,
    previousRoomStatus = room_status
})
