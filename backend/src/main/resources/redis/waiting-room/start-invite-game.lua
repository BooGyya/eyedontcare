local function result(status, countdown_id, countdown_ends_at)
    return cjson.encode({
        status = status,
        countdownId = countdown_id,
        countdownEndsAt = countdown_ends_at
    })
end

if redis.call('EXISTS', KEYS[1]) == 0 then return result('ROOM_NOT_FOUND') end
if redis.call('EXISTS', KEYS[2]) == 0 then return result('CORRUPTED') end
local room_type = redis.call('HGET', KEYS[1], 'roomType')
local room_code = redis.call('HGET', KEYS[1], 'roomCode')
local room_status = redis.call('HGET', KEYS[1], 'roomStatus')
if room_type ~= 'INVITE' or room_code ~= ARGV[2] then return result('CORRUPTED') end
if redis.call('GET', KEYS[3]) ~= ARGV[1] then return result('CORRUPTED') end

local requester_raw = redis.call('HGET', KEYS[2], ARGV[3])
if requester_raw == false then return result('PARTICIPANT_NOT_FOUND') end
local requester_ok, requester = pcall(cjson.decode, requester_raw)
if not requester_ok or type(requester) ~= 'table' then return result('CORRUPTED') end
if requester.roomRole ~= 'HOST' then return result('GAME_START_FORBIDDEN') end

if room_status == 'COUNTDOWN' then
    local existing_id = redis.call('HGET', KEYS[1], 'countdownId')
    local existing_ends_at = redis.call('HGET', KEYS[1], 'countdownEndsAt')
    if existing_id == false or existing_ends_at == false then return result('CORRUPTED') end
    redis.call('PEXPIRE', KEYS[1], ARGV[7])
    redis.call('PEXPIRE', KEYS[2], ARGV[7])
    redis.call('PEXPIRE', KEYS[3], ARGV[7])
    return result('ALREADY_COUNTDOWN', existing_id, existing_ends_at)
end
if room_status ~= 'WAITING' then return result('STATE_CHANGE_NOT_ALLOWED') end

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
        or participant.slotNo > tonumber(ARGV[6])
        or used_slots[participant.slotNo] then
        return result('CORRUPTED')
    end
    used_slots[participant.slotNo] = true
    if participant.roomRole == 'HOST' then
        host_count = host_count + 1
        if participant.calibrationStatus ~= 'COMPLETED' then
            return result('CALIBRATION_REQUIRED')
        end
    elseif participant.roomRole == 'PLAYER' then
        player_count = player_count + 1
        if participant.calibrationStatus ~= 'COMPLETED'
            or participant.isReady ~= true then
            return result('PARTICIPANTS_NOT_READY')
        end
    else
        return result('CORRUPTED')
    end
end
if host_count ~= 1 then return result('CORRUPTED') end
if player_count ~= 1 then return result('PARTICIPANTS_NOT_READY') end

redis.call(
    'HSET', KEYS[1],
    'roomStatus', 'COUNTDOWN',
    'countdownId', ARGV[4],
    'countdownEndsAt', ARGV[5]
)
redis.call('PEXPIRE', KEYS[1], ARGV[7])
redis.call('PEXPIRE', KEYS[2], ARGV[7])
redis.call('PEXPIRE', KEYS[3], ARGV[7])
return result('STARTED', ARGV[4], ARGV[5])
