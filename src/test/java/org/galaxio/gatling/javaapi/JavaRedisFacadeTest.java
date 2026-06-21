package org.galaxio.gatling.javaapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava;
import org.galaxio.gatling.javaapi.redis.RedisGenericActionBuilder;
import org.galaxio.gatling.redis.RedisActionBuilder.GenericRedisActionBuilder;
import org.junit.jupiter.api.Test;

/**
 * Facade delegation/parity for the Redis Java facade (FR-016). The facade must forward inputs to the Scala core
 * unchanged and add no facade-only logic. We build action builders through {@link RedisClientPoolJava} and inspect the
 * wrapped Scala {@code GenericRedisActionBuilder} reached via {@code asScala()} — its {@code saveAsVar}/{@code reqName}
 * must equal exactly what was passed, with no facade-injected defaults. (Building DSL builders opens no Redis connection.)
 */
class JavaRedisFacadeTest {

    private final RedisClientPoolJava pool = new RedisClientPoolJava("localhost", 6379);

    private static GenericRedisActionBuilder scalaOf(RedisGenericActionBuilder javaBuilder) {
        return (GenericRedisActionBuilder) javaBuilder.asScala();
    }

    @Test
    void getDelegatesToScalaGenericBuilderWithNoInjectedDefaults() {
        GenericRedisActionBuilder scala = scalaOf(pool.GET("key"));
        // negative/boundary: the facade must NOT inject a saveAs or requestName of its own
        assertThat(scala.saveAsVar().isDefined()).isFalse();
        assertThat(scala.reqName().isDefined()).isFalse();
    }

    @Test
    void saveAsIsForwardedUnchanged() {
        GenericRedisActionBuilder scala = scalaOf(pool.GET("key").saveAs("result"));
        assertThat(scala.saveAsVar().isDefined()).isTrue();
        assertThat(scala.saveAsVar().get()).isEqualTo("result");
    }

    @Test
    void requestNameIsForwardedUnchanged() {
        GenericRedisActionBuilder scala = scalaOf(pool.SET("key", "value").requestName("redis-set"));
        assertThat(scala.reqName().isDefined()).isTrue();
        assertThat(scala.reqName().get()).isEqualTo("redis-set");
    }

    @Test
    void saveAsAndRequestNameComposeLikeTheScalaCore() {
        GenericRedisActionBuilder scala = scalaOf(pool.GET("key").saveAs("r").requestName("n"));
        assertThat(scala.saveAsVar().get()).isEqualTo("r");
        assertThat(scala.reqName().get()).isEqualTo("n");
    }
}
