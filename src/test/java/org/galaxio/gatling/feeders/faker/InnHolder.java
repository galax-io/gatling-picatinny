package org.galaxio.gatling.feeders.faker;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.constraints.ru.INN;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Test-only holder used to validate generated Russian INN values against Hibernate Validator's official
 * {@code @INN} constraint (10/12-digit FTS checksum) from the Scala feeder specs.
 */
public class InnHolder {

    @INN(type = INN.Type.INDIVIDUAL)
    public final String individual;

    @INN(type = INN.Type.JURIDICAL)
    public final String juridical;

    public InnHolder(String individual, String juridical) {
        this.individual = individual;
        this.juridical = juridical;
    }

    /**
     * A Validator built with {@link ParameterMessageInterpolator} so no Jakarta EL implementation is
     * required on the test classpath. Built in Java because the bounded generics of
     * {@code Validation.byProvider} do not infer from Scala.
     */
    public static Validator validator() {
        return Validation
                .byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator();
    }
}
