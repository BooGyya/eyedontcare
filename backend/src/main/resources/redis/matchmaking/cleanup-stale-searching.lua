-- SEARCHING entry와 queue member를 원자적으로 확인한다.
-- Hash는 SEARCHING이고 해당 queue member가 없을 때만 stale entry를 삭제한다.
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

if redis.call('HGET', KEYS[1], 'participantKey') ~= ARGV[1]
    or redis.call('HGET', KEYS[1], 'gameType') ~= ARGV[2]
    or redis.call('HGET', KEYS[1], 'matchStatus') ~= 'SEARCHING' then
    return 0
end

if redis.call('ZSCORE', KEYS[2], ARGV[1]) ~= false then
    return 0
end

redis.call('DEL', KEYS[1])
return 1
