package org.galaxio.gatling.javaapi.profile;


public final class ProfileBuilderNew {

    public static YamlBuilder buildFromYaml(String path) {
        return new YamlBuilder(org.galaxio.gatling.profile.ProfileBuilderNew.buildFromYamlJava(path));
    }
}