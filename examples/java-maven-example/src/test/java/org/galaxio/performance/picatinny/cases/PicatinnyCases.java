package org.galaxio.performance.picatinny.cases;

import io.gatling.javaapi.core.ChainBuilder;
import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.Transactions;
import org.galaxio.gatling.javaapi.utils.IntensityConverter;
import org.galaxio.gatling.javaapi.utils.Jwt;
import org.galaxio.gatling.utils.jwt.JwtGeneratorBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.pause;

public final class PicatinnyCases {
    private static final JwtGeneratorBuilder JWT_GENERATOR = Jwt.jwt("HS256", "performance-secret")
            .defaultHeader()
            .payloadFromResource("jwtTemplates/payload.json");

    private PicatinnyCases() {
    }

    public static ChainBuilder businessOperation(String transactionName) {
        return exec(Transactions.startTransaction(transactionName))
                .exec(scenarioOperation())
                .exec(Transactions.endTransaction(transactionName));
    }

    public static ChainBuilder scenarioOperation() {
        return exec(Jwt.setJwt(JWT_GENERATOR, "jwt"))
                .pause(1)
                .exec(session -> {
                    require(SimulationConfig.baseUrl().equals("http://localhost"), "baseUrl");
                    require(IntensityConverter.rpm(60.0) == SimulationConfig.intensity(), "intensity");
                    require(session.getString("uuid").length() == 36, "uuid");
                    require(session.getString("jwt").split("\\.").length == 3, "jwt");
                    require(!session.getString("formattedPhone").isBlank(), "formattedPhone");
                    require(session.getString("pan").length() >= 16, "pan");

                    require(!session.getString("randomDate").isBlank(), "randomDate");
                    require(!session.getString("rangeFrom").isBlank(), "rangeFrom");
                    require(!session.getString("customValue").isBlank(), "customValue");
                    require(!session.getString("phone").isBlank(), "phone");
                    require(!session.getString("rangeString").isBlank(), "rangeString");
                    require(!session.getString("regex").isBlank(), "regex");
                    require(!session.getString("natItn").isBlank(), "natItn");
                    require(!session.getString("passport").isBlank(), "passport");

                    require(!session.getString("alphabeticStr").isBlank(), "alphabeticStr");
                    require(!session.getString("firstName").isBlank(), "firstName");
                    require(!session.getString("username").isBlank(), "username");
                    require(session.getString("accountNumber").length() == 20, "accountNumber");
                    require(!session.getString("productName").isBlank(), "productName");
                    require(!session.getString("usSSN").isBlank(), "usSSN");
                    require(!session.getString("phoneTollFree").isBlank(), "phoneTollFree");
                    require(!session.getString("loremWord").isBlank(), "loremWord");
                    require(!session.getString("createdAt").isBlank(), "createdAt");

                    return session;
                });
    }

    private static void require(boolean condition, String feature) {
        if (!condition) {
            throw new IllegalStateException("Picatinny Java project check failed for " + feature);
        }
    }
}
