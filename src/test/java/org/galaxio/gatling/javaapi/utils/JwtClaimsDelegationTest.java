package org.galaxio.gatling.javaapi.utils;

import org.galaxio.gatling.utils.jwt.ClaimsBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facade delegation (Constitution I): {@code Jwt.claims()} must return the real Scala
 * {@link ClaimsBuilder} with the new 6-field constructor wired correctly, and the typed
 * session-claim methods must delegate to the Scala core (no facade-only logic).
 */
class JwtClaimsDelegationTest {

    @Test
    void claimsFactoryProducesAnEmptyBuilderWithForcedTypesWired() {
        ClaimsBuilder cb = Jwt.claims();
        assertTrue(cb.staticClaims().isEmpty());
        assertTrue(cb.elClaims().isEmpty());
        assertTrue(cb.forcedTypes().isEmpty(), "6th constructor arg (forcedTypes) must be wired as an empty map");
        assertFalse(cb.setIat());
        assertFalse(cb.setNbf());
        assertTrue(cb.ttl().isEmpty());
    }

    @Test
    void typedSessionClaimTerminalsRegisterAForcedType() {
        // The as* terminals are the Java/Kotlin-friendly equivalents of Scala's .as[T].
        ClaimsBuilder cb = Jwt.claims()
                .claimFromSession("uid", "#{uid}").asLong()
                .claimFromSession("name", "#{name}").asString()
                .claimFromSession("ratio", "#{ratio}").asDouble()
                .claimFromSession("admin", "#{admin}").asBoolean()
                .claimFromSession("roles", "#{roles}").asJson();
        assertTrue(cb.elClaims().contains("uid"));
        assertTrue(cb.elClaims().contains("roles"));
        assertEquals(5, cb.forcedTypes().size());
    }

    @Test
    void autoDetectClaimDoesNotRegisterAForcedType() {
        ClaimsBuilder cb = Jwt.claims().claimFromSession("uid", "#{uid}").autoDetect();
        assertTrue(cb.elClaims().contains("uid"));
        assertTrue(cb.forcedTypes().isEmpty());
    }
}
