package io.cosmospf.gatling.javaapi.utils;

import io.cosmospf.gatling.utils.phone.*;

import java.util.List;

import static scala.jdk.javaapi.CollectionConverters.asScala;

public final class RandomPhoneGenerator {
    private RandomPhoneGenerator(){}

    public static String randomPhone(List<PhoneFormat> formats, TypePhone.TypePhone typePhone){
        return io.cosmospf.gatling.utils.RandomPhoneGenerator.randomPhone(asScala(formats).toSeq(), typePhone);
    }

    public static String randomPhone(String pathToFormats, TypePhone.TypePhone typePhone){
        return io.cosmospf.gatling.utils.RandomPhoneGenerator.randomPhone(pathToFormats, typePhone);
    }
}
