if redis.call('GET', KEYS[3]) == ARGV[1] then
    redis.call('DEL', KEYS[3])
end

redis.call('DEL', KEYS[1], KEYS[2])
return 1
