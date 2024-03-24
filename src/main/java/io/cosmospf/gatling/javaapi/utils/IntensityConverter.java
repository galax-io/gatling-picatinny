package io.cosmospf.gatling.javaapi.utils;

public final class IntensityConverter {
    private IntensityConverter() {
    }

    public static double getIntensityFromString(String intensity) {
        return io.cosmospf.gatling.utils.IntensityConverter.getIntensityFromString(intensity);
    }

    public static double rph(double count) {
        return new io.cosmospf.gatling.utils.IntensityConverter.toRps(count).rph();
    }

    public static double rpm(double count) {
        return new io.cosmospf.gatling.utils.IntensityConverter.toRps(count).rpm();
    }

    public static double rps(double count) {
        return new io.cosmospf.gatling.utils.IntensityConverter.toRps(count).rps();
    }
}
