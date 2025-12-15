package me.evisual.rlenv.env;

public final class Observation
{
    private final double[] features;

    public Observation(double[] features) {
        this.features = features;
    }

    public double[] getFeatures() {
        return features;
    }
}
