package io.cosmospf.gatling.javaapi.profile;


public final class ProfileBuilderNew {

    public static YamlBuilder buildFromYaml(String path) {
        return new YamlBuilder(io.cosmospf.gatling.profile.ProfileBuilderNew.buildFromYamlJava(path));
    }
}