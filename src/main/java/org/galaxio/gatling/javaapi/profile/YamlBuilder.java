package org.galaxio.gatling.javaapi.profile;

import org.galaxio.gatling.profile.Yaml;

public record YamlBuilder(Yaml yaml) {

    public OneProfileBuilder selectProfile(String profileName) {
        return new OneProfileBuilder(yaml.selectProfile(profileName));
    }
}
