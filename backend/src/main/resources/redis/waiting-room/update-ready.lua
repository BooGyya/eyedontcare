local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
if room_status == false then return 'ROOM_NOT_FOUND' end
if (room_type == 'INVITE' and room_code ~= ARGV[2])
    or (room_type == 'RANDOM' and room_code ~= false)
    or (room_type ~= 'INVITE' and room_type ~= 'RANDOM') then
    return cjson.encode({ status = 'CORRUPTED' })
end
if room_status == 'COUNTDOWN' and room_type == 'RANDOM' then
    return cjson.encode({
        status = 'ALREADY_COUNTDOWN',
        countdownId = redis.call('HGET', KEYS[1], 'countdownId'),
        countdownEndsAt = redis.call('HGET', KEYS[1], 'countdownEndsAt')
    })
end
if room_status ~= 'WAITING' then
    return room_type == 'RANDOM'
        and cjson.encode({ status = 'STATE_CHANGE_NOT_ALLOWED' })
        or 'STATE_CHANGE_NOT_ALLOWED'
end
if room_type == 'INVITE' and redis.call('GET', KEYS[3]) ~= ARGV[1] then
    return 'CORRUPTED'
end

local raw = redis.call('HGET', KEYS[2], ARGV[3])
if raw == false then
    return room_type == 'RANDOM'
        and cjson.encode({ status = 'PARTICIPANT_NOT_FOUND' })
        or 'PARTICIPANT_NOT_FOUND'
end
local ok, participant = pcall(cjson.decode, raw)
if not ok or type(participant) ~= 'table'
    or type(participant.isReady) ~= 'boolean'
    or type(participant.calibrationStatus) ~= 'string' then
    return room_type == 'RANDOM'
        and cjson.encode({ status = 'CORRUPTED' })
        or 'CORRUPTED'
end
if participant.roomRole ~= 'PLAYER' then
    return room_type == 'RANDOM'
        and cjson.encode({ status = 'STATE_CHANGE_NOT_ALLOWED' })
        or 'STATE_CHANGE_NOT_ALLOWED'
end

local target = ARGV[4] == 'true'
local changed = participant.isReady ~= target
if target and participant.calibrationStatus ~= 'COMPLETED' then
    return room_type == 'RANDOM'
        and cjson.encode({ status = 'CALIBRATION_REQUIRED' })
        or 'CALIBRATION_REQUIRED'
end
if participant.isReady == target then
    redis.call('PEXPIRE', KEYS[1], ARGV[5])
    redis.call('PEXPIRE', KEYS[2], ARGV[5])
    if room_type == 'INVITE' then
        redis.call('PEXPIRE', KEYS[3], ARGV[5])
        return 'UNCHANGED'
    end
end

participant.isReady = target
redis.call('HSET', KEYS[2], ARGV[3], cjson.encode(participant))
redis.call('PEXPIRE', KEYS[1], ARGV[5])
redis.call('PEXPIRE', KEYS[2], ARGV[5])
if room_type == 'INVITE' then
    redis.call('PEXPIRE', KEYS[3], ARGV[5])
    return 'UPDATED'
end
if not target then return cjson.encode({ status = 'UPDATED' }) end

local entries = redis.call('HGETALL', KEYS[2])
if #entries ~= 4 then return cjson.encode({ status = 'CORRUPTED' }) end
local slots = {}
local ready_count = 0
for index = 1, #entries, 2 do
    local ok_entry, entry = pcall(cjson.decode, entries[index + 1])
    if not ok_entry or entry.roomRole ~= 'PLAYER'
        or (entry.slotNo ~= 1 and entry.slotNo ~= 2)
        or slots[entry.slotNo] then
        return cjson.encode({ status = 'CORRUPTED' })
    end
    slots[entry.slotNo] = true
    if entry.calibrationStatus == 'COMPLETED' and entry.isReady == true then
        ready_count = ready_count + 1
    end
end
if ready_count ~= 2 then
    return cjson.encode({ status = changed and 'UPDATED' or 'UNCHANGED' })
end
redis.call(
    'HSET',
    KEYS[1],
    'roomStatus', 'COUNTDOWN',
    'countdownId', ARGV[7],
    'countdownEndsAt', ARGV[8]
)
return cjson.encode({
    status = 'COUNTDOWN_STARTED',
    countdownId = ARGV[7],
    countdownEndsAt = ARGV[8]
})
