package org.galaxio.performance.example

import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.redis.RedisClientPool
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava

class Debug : Simulation() {
    init {
        before {
            println("Some action")
        }

        val redisClientPool = RedisClientPool("localhost", 6379)
        val redisClientPoolJava = RedisClientPoolJava(redisClientPool)

        setUp(
            scenario("Kotlin Example")
                .exec(redisClientPoolJava.SADD("key", "values", "values", "values1"))
                .exec(redisClientPoolJava.SADD("key", "values", listOf("values", "values1")))
                .exec(redisClientPoolJava.DEL("key", "keys"))
                .exec(redisClientPoolJava.SREM("key", "values", "values"))
                .injectOpen(atOnceUsers(1))
        )

        after {
            println("Some action")
        }
    }
}

