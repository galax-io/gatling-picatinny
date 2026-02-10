package org.galaxio.performance.example;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.redis.RedisClientPool;
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava;

import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;

public class SimpleSimulation extends Simulation {

    {
        RedisClientPool redisClientPool =
                new RedisClientPool("localhost", 6379)
                        .withDatabase(1)
                        .withBatchMode(true);

        RedisClientPoolJava redisClientPoolJava = new RedisClientPoolJava(redisClientPool);

        setUp(
                scenario("Java Example")
                        .exec(redisClientPoolJava.SADD("key", "values", "values", "values1"))
                        .exec(redisClientPoolJava.SADD("key", "values", List.of("values", "values1")))
                        .exec(redisClientPoolJava.DEL("key", "keys"))
                        .exec(redisClientPoolJava.SREM("key", "values", "values"))
                        .injectOpen(atOnceUsers(1))
        ).protocols();
    }
}
