package io.cosmospf.gatling.javaapi.profile;

import io.cosmospf.gatling.profile.Yaml;

public record YamlBuilder(Yaml yaml) {

    public OneProfileBuilder selectProfile(String profileName) {
        return new OneProfileBuilder(yaml.selectProfile(profileName));
    }
}
