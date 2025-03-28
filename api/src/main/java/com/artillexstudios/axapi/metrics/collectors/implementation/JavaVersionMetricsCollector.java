package com.artillexstudios.axapi.metrics.collectors.implementation;

import com.artillexstudios.axapi.metrics.collectors.MetricsCollector;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class JavaVersionMetricsCollector implements MetricsCollector {
    private final String version = Runtime.version().toString() + "|" +  Runtime.version().feature();

    @Override
    public void collect(JsonArray data) {
        JsonObject object = new JsonObject();
        object.addProperty("@type", "java-version");
        object.addProperty("java-version", this.version);
        data.add(object);
    }
}
