package org.galaxio.gatling.javaapi.redis;

import io.gatling.javaapi.core.ActionBuilder;
import org.galaxio.gatling.redis.RedisActionBuilder.GenericRedisActionBuilder$;

public final class RedisGenericActionBuilder implements ActionBuilder {

    private final org.galaxio.gatling.redis.RedisActionBuilder.GenericRedisActionBuilder wrapped;

    RedisGenericActionBuilder(org.galaxio.gatling.redis.RedisActionBuilder.GenericRedisActionBuilder wrapped) {
        this.wrapped = wrapped;
    }

    public RedisGenericActionBuilder saveAs(String variable) {
        return new RedisGenericActionBuilder(wrapped.saveAs(variable));
    }

    public RedisGenericActionBuilder requestName(String name) {
        return new RedisGenericActionBuilder(wrapped.requestName(name));
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        return wrapped;
    }
}
