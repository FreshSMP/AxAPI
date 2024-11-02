package com.artillexstudios.axapi.utils.updatechecker.sources;

import com.artillexstudios.axapi.utils.updatechecker.ArtifactVersion;
import com.artillexstudios.axapi.utils.updatechecker.Changelog;
import com.artillexstudios.axapi.utils.updatechecker.UpdateCheck;
import com.artillexstudios.axapi.utils.updatechecker.UpdateCheckResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class ModrinthUpdateCheckSource implements UpdateCheckSource {
    private final String id;

    public ModrinthUpdateCheckSource(String id) {
        this.id = id;
    }

    @Override
    public UpdateCheck check(ArtifactVersion current) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.modrinth.com/v2/project/" + this.id + "/version"))
                    .timeout(Duration.of(10, ChronoUnit.SECONDS))
                    .GET()
                    .build();

            HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new UpdateCheck(UpdateCheckResult.FAILED, current, List.of());
            }

            String body = response.body().toString();
            List<Changelog> changelogs = new ArrayList<>();
            JsonArray obj = new Gson().fromJson(body, JsonArray.class);
            ArtifactVersion latest = new ArtifactVersion(obj.get(0).getAsJsonObject().get("version_number").getAsString());

            for (JsonElement jsonElement : obj) {
                JsonObject object = jsonElement.getAsJsonObject();
                ArtifactVersion version = new ArtifactVersion(object.get("version_number").getAsString());
                if (version.version() > current.version()) {
                    changelogs.add(new Changelog(version, object.get("changelog").getAsString()));
                } else if (version.version() == current.version()) {
                    break;
                }
            }

            return new UpdateCheck(latest.version() > current.version() ? UpdateCheckResult.UPDATE_AVAILABLE : latest.version() == current.version() ? UpdateCheckResult.UP_TO_DATE : UpdateCheckResult.DEV_VERSION, latest, changelogs);
        } catch (Exception e) {
            return new UpdateCheck(UpdateCheckResult.FAILED, current, List.of(), e);
        }
    }
}
