package org.galaxio.gatling.javaapi.redis;

import io.gatling.javaapi.redis.RedisClientPool;
import org.galaxio.gatling.redis.RedisActionBuilder.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static io.gatling.javaapi.core.internal.Expressions.toAnyExpression;
import static org.galaxio.gatling.javaapi.internal.Expression.toListExpression;

public final class RedisClientPoolJava {

    private final RedisClientPool redisClientPool;
    private final com.redis.RedisClientPool directPool;

    public RedisClientPoolJava(String host, int port) {
        this.redisClientPool = new RedisClientPool(host, port);
        this.directPool = null;
    }

    public RedisClientPoolJava(RedisClientPool redisClientPool) {
        this.redisClientPool = redisClientPool;
        this.directPool = null;
    }

    public RedisClientPoolJava(com.redis.RedisClientPool scalaPool) {
        this.redisClientPool = null;
        this.directPool = scalaPool;
    }

    com.redis.RedisClientPool redisClientPoolAsScala() {
        if (directPool != null) {
            return directPool;
        }

        Method m;
        com.redis.RedisClientPool invoke;

        try {
            m = RedisClientPool.class.getDeclaredMethod("asScala");
            m.setAccessible(true);
            invoke = (com.redis.RedisClientPool) m.invoke(redisClientPool);
        } catch (NoSuchMethodException e) {
            throw new RedisClientPoolJavaException(
                    "The " + RedisClientPool.class.getName() + "class does not have a asScala method", e);
        } catch (InvocationTargetException e) {
            throw new RedisClientPoolJavaException(
                    "asScala method of " + RedisClientPool.class.getName() + "class failed", e);
        } catch (IllegalAccessException e) {
            throw new RedisClientPoolJavaException(
                    "asScala method of " + RedisClientPool.class.getName() + "class not available", e);
        } catch (Exception e) {
            throw new RedisClientPoolJavaException("Unknown error ", e);
        }

        return invoke;
    }

    private RedisClientPoolOps ops() {
        return new RedisClientPoolOps(redisClientPoolAsScala());
    }

    // Legacy DEL/SREM/SADD methods (backward compatible)

    public RedisActionBuilder<RedisDelActionBuilder> DEL(String key, String... keys) {
        return new RedisActionBuilder<>(
                ops().DEL(toAnyExpression(key), toListExpression(keys))
        );
    }

    public RedisActionBuilder<RedisDelActionBuilder> DEL(String key, List<String> keys) {
        return DEL(key, keys.toArray(new String[0]));
    }

    public RedisActionBuilder<RedisDelActionBuilder> DEL(String key) {
        return DEL(key, Collections.emptyList());
    }

    public RedisActionBuilder<RedisSremActionBuilder> SREM(String key, String value, String... values) {
        return new RedisActionBuilder<>(
                ops().SREM(toAnyExpression(key), toAnyExpression(value), toListExpression(values))
        );
    }

    public RedisActionBuilder<RedisSremActionBuilder> SREM(String key, String value, List<String> values) {
        return SREM(key, value, values.toArray(new String[0]));
    }

    public RedisActionBuilder<RedisSremActionBuilder> SREM(String key, String value) {
        return SREM(key, value, Collections.emptyList());
    }

    public RedisActionBuilder<RedisSaddActionBuilder> SADD(String key, String value, String... values) {
        return new RedisActionBuilder<>(
                ops().SADD(toAnyExpression(key), toAnyExpression(value), toListExpression(values))
        );
    }

    public RedisActionBuilder<RedisSaddActionBuilder> SADD(String key, String value, List<String> values) {
        return SADD(key, value, values.toArray(new String[0]));
    }

    public RedisActionBuilder<RedisSaddActionBuilder> SADD(String key, String value) {
        return SADD(key, value, Collections.emptyList());
    }

    // New generic commands returning RedisGenericActionBuilder (supports saveAs/requestName)

    // String operations

    public RedisGenericActionBuilder GET(String key) {
        return new RedisGenericActionBuilder(ops().GET(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder SET(String key, String value) {
        return new RedisGenericActionBuilder(ops().SET(toAnyExpression(key), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder GETSET(String key, String value) {
        return new RedisGenericActionBuilder(ops().GETSET(toAnyExpression(key), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder SETNX(String key, String value) {
        return new RedisGenericActionBuilder(ops().SETNX(toAnyExpression(key), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder SETEX(String key, long expiry, String value) {
        return new RedisGenericActionBuilder(
                ops().SETEX(toAnyExpression(key), toAnyExpression(String.valueOf(expiry)), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder MGET(String key, String... keys) {
        return new RedisGenericActionBuilder(ops().MGET(toAnyExpression(key), toListExpression(keys)));
    }

    // Counter operations

    public RedisGenericActionBuilder INCR(String key) {
        return new RedisGenericActionBuilder(ops().INCR(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder INCRBY(String key, long increment) {
        return new RedisGenericActionBuilder(
                ops().INCRBY(toAnyExpression(key), toAnyExpression(String.valueOf(increment))));
    }

    public RedisGenericActionBuilder DECR(String key) {
        return new RedisGenericActionBuilder(ops().DECR(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder DECRBY(String key, long decrement) {
        return new RedisGenericActionBuilder(
                ops().DECRBY(toAnyExpression(key), toAnyExpression(String.valueOf(decrement))));
    }

    // Hash operations

    public RedisGenericActionBuilder HGET(String key, String field) {
        return new RedisGenericActionBuilder(ops().HGET(toAnyExpression(key), toAnyExpression(field)));
    }

    public RedisGenericActionBuilder HSET(String key, String field, String value) {
        return new RedisGenericActionBuilder(
                ops().HSET(toAnyExpression(key), toAnyExpression(field), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder HDEL(String key, String field, String... fields) {
        return new RedisGenericActionBuilder(
                ops().HDEL(toAnyExpression(key), toAnyExpression(field), toListExpression(fields)));
    }

    public RedisGenericActionBuilder HGETALL(String key) {
        return new RedisGenericActionBuilder(ops().HGETALL(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder HMGET(String key, String... fields) {
        return new RedisGenericActionBuilder(ops().HMGET(toAnyExpression(key), toListExpression(fields)));
    }

    // List operations

    public RedisGenericActionBuilder LPUSH(String key, String value, String... values) {
        return new RedisGenericActionBuilder(
                ops().LPUSH(toAnyExpression(key), toAnyExpression(value), toListExpression(values)));
    }

    public RedisGenericActionBuilder RPUSH(String key, String value, String... values) {
        return new RedisGenericActionBuilder(
                ops().RPUSH(toAnyExpression(key), toAnyExpression(value), toListExpression(values)));
    }

    public RedisGenericActionBuilder LPOP(String key) {
        return new RedisGenericActionBuilder(ops().LPOP(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder RPOP(String key) {
        return new RedisGenericActionBuilder(ops().RPOP(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder LRANGE(String key, int start, int end) {
        return new RedisGenericActionBuilder(
                ops().LRANGE(toAnyExpression(key), toAnyExpression(String.valueOf(start)), toAnyExpression(String.valueOf(end))));
    }

    public RedisGenericActionBuilder LLEN(String key) {
        return new RedisGenericActionBuilder(ops().LLEN(toAnyExpression(key)));
    }

    // Key operations

    public RedisGenericActionBuilder EXISTS(String key) {
        return new RedisGenericActionBuilder(ops().EXISTS(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder EXPIRE(String key, int ttl) {
        return new RedisGenericActionBuilder(
                ops().EXPIRE(toAnyExpression(key), toAnyExpression(String.valueOf(ttl))));
    }

    public RedisGenericActionBuilder TTL(String key) {
        return new RedisGenericActionBuilder(ops().TTL(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder KEYS(String pattern) {
        return new RedisGenericActionBuilder(ops().KEYS(toAnyExpression(pattern)));
    }

    // Set operations

    public RedisGenericActionBuilder SMEMBERS(String key) {
        return new RedisGenericActionBuilder(ops().SMEMBERS(toAnyExpression(key)));
    }

    public RedisGenericActionBuilder SISMEMBER(String key, String value) {
        return new RedisGenericActionBuilder(ops().SISMEMBER(toAnyExpression(key), toAnyExpression(value)));
    }

    public RedisGenericActionBuilder SCARD(String key) {
        return new RedisGenericActionBuilder(ops().SCARD(toAnyExpression(key)));
    }
}
