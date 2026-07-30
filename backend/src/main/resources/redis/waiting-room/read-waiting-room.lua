if redis.call('EXISTS', KEYS[1]) == 0 then
    return cjson.encode({ status = 'ROOM_NOT_FOUND' })
end
if redis.call('EXISTS', KEYS[2]) == 0 then
    return cjson.encode({ status = 'CORRUPTED' })
end

local room_values = redis.call(
    'HMGET',
    KEYS[1],
    'roomType',
    'gameName',
    'roomCode',
    'roomStatus',
    'createdAt',
    'countdownId',
    'countdownEndsAt'
)
for _, index in ipairs({1, 2, 4, 5}) do
    if room_values[index] == false then
        return cjson.encode({ status = 'CORRUPTED' })
    end
end
if (room_values[1] == 'INVITE' and room_values[3] == false)
    or (room_values[1] == 'RANDOM' and room_values[3] ~= false) then
    return cjson.encode({ status = 'CORRUPTED' })
end
if room_values[4] == 'COUNTDOWN'
    and (room_values[6] == false or room_values[7] == false) then
    return cjson.encode({ status = 'CORRUPTED' })
end

local participant_values = redis.call('HGETALL', KEYS[2])
if #participant_values == 0 or #participant_values % 2 ~= 0 then
    return cjson.encode({ status = 'CORRUPTED' })
end

local participants = {}
for index = 1, #participant_values, 2 do
    local decode_ok, participant = pcall(cjson.decode, participant_values[index + 1])
    if not decode_ok or type(participant) ~= 'table' then
        return cjson.encode({ status = 'CORRUPTED' })
    end
    table.insert(participants, {
        participantKey = participant_values[index],
        participant = participant
    })
end

return cjson.encode({
    status = 'FOUND',
    room = {
        roomId = ARGV[1],
        roomType = room_values[1],
        gameName = room_values[2],
        roomCode = room_values[3] == false and cjson.null or room_values[3],
        roomStatus = room_values[4],
        createdAt = room_values[5],
        countdownId = room_values[6] == false and cjson.null or room_values[6],
        countdownEndsAt = room_values[7] == false and cjson.null or room_values[7]
    },
    participants = participants
})
