if redis.call('SET', KEYS[3], ARGV[1], 'NX', 'PX', ARGV[9]) == false then
    return 0
end

redis.call(
    'HSET',
    KEYS[1],
    'roomType', ARGV[2],
    'gameName', ARGV[3],
    'roomCode', ARGV[4],
    'roomStatus', ARGV[5],
    'createdAt', ARGV[6]
)
redis.call('PEXPIRE', KEYS[1], ARGV[9])
redis.call('HSET', KEYS[2], ARGV[7], ARGV[8])
redis.call('PEXPIRE', KEYS[2], ARGV[9])

return 1
