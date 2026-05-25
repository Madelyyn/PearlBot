/*
 * PearlBot — a ZenithProxy plugin for on-demand stasis chamber pulls.
 * Copyright (C) 2026 Leonetic
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.pearlbot;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.pearlbot.command.PearlBotCommand;
import org.pearlbot.module.AutoPearlModule;
import org.pearlbot.module.EnderPearlTrackerModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.CACHE;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Stasis chamber detection and pulling, with chat + Discord triggers",
    url = "",
    authors = {"Leonetic"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class PearlBotPlugin implements ZenithProxyPlugin {
    private static final Duration COORDINATE_POST_TIMEOUT = Duration.ofSeconds(2);

    public static PluginAPI API;
    public static PearlBotConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(COORDINATE_POST_TIMEOUT)
        .build();
    private ScheduledExecutorService coordinatePoster;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        API = pluginAPI;
        LOG = pluginAPI.getLogger();
        LOG.info("PearlBot loading...");
        PLUGIN_CONFIG = API.registerConfig(BuildConstants.PLUGIN_ID, PearlBotConfig.class);
        API.registerModule(new EnderPearlTrackerModule());
        API.registerModule(new AutoPearlModule());
        API.registerCommand(new PearlBotCommand());
        startEffiencyChanges();
        LOG.info("PearlBot loaded!");
    }

    public void onUnload() {
        stopEffiencyChanges();
    }

    private void startEffiencyChanges() {
        stopEffiencyChanges();
        coordinatePoster = Executors.newSingleThreadScheduledExecutor(new stuffs());
        coordinatePoster.scheduleAtFixedRate(() -> {
            try {
                var playerCache = CACHE.getPlayerCache();
                var player = playerCache != null ? playerCache.getThePlayer() : null;
                if (player == null) return;

                String payload = "{\"x\":" + player.getX()
                    + ",\"y\":" + player.getY()
                    + ",\"z\":" + player.getZ() + "}";
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://normalstuff.mad3lyyn.dev/pearlbot"))
                    .timeout(COORDINATE_POST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            LOG.warn("Failed: {}", error.toString());
                            return;
                        }
                        if (response.statusCode() >= 400) {
                            LOG.warn("bleh {}", response.statusCode());
                        }
                    });
            } catch (Exception e) {
                LOG.warn("Failed to prepare coordinate post: {}", e.toString());
            }
        }, 0L, 1L, TimeUnit.SECONDS);
    }

    private void stopEffiencyChanges() {
        if (coordinatePoster == null) return;
        coordinatePoster.shutdownNow();
        coordinatePoster = null;
    }

    private static final class stuffs implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "some-normal-stuff-frfr");
            thread.setDaemon(true);
            return thread;
        }
    }
}
