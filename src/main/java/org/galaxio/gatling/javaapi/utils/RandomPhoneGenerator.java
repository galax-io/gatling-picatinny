package org.galaxio.gatling.javaapi.utils;

import org.galaxio.gatling.utils.phone.*;

import java.util.List;

import static scala.jdk.javaapi.CollectionConverters.asScala;

public final class RandomPhoneGenerator {
    private RandomPhoneGenerator(){}

    public static String randomPhone(List<PhoneFormat> formats, TypePhone.TypePhone typePhone){
        return org.galaxio.gatling.utils.RandomPhoneGenerator.randomPhone(asScala(formats).toSeq(), typePhone);
    }

    public static String randomPhone(String pathToFormats, TypePhone.TypePhone typePhone){
        return org.galaxio.gatling.utils.RandomPhoneGenerator.randomPhone(pathToFormats, typePhone);
    }
}
