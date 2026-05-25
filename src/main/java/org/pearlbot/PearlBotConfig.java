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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PearlBotConfig {
    public boolean enabled = true;
    public String triggerWord = "warp";
    public int pearlViewDistance = 64;
    public int trapdoorScanRadius = 5;
    public int pullTimeoutSeconds = 30;
    public int waitForOwnerSeconds = 180;
    public int maxChambersPerPlayer = 0;

    public final IdleGoal idleGoal = new IdleGoal();
    public static class IdleGoal {
        public boolean enabled = false;
        public int x = 0;
        public int y = 0;
        public int z = 0;
        public int radius = 3;
    }

    public final Whitelist whitelist = new Whitelist();
    public static class Whitelist {
        public boolean enabled = true;
        public Map<UUID, WhitelistedPlayer> players = new LinkedHashMap<>();
    }

    public static class WhitelistedPlayer {
        public String username;
        public UUID uuid;

        public WhitelistedPlayer() {}
        public WhitelistedPlayer(String username, UUID uuid) {
            this.username = username;
            this.uuid = uuid;
        }
    }

    public Map<UUID, StasisChamber> chambers = new LinkedHashMap<>();
    public static class StasisChamber {
        public int x;
        public int y;
        public int z;
        public int entityId;
        public UUID ownerUuid;
        public Integer pendingOwnerEntityId;
    }

    public final DiscordTrigger discordTrigger = new DiscordTrigger();
    public static class DiscordTrigger {
        public boolean enabled = true;
        public String channelId = "";
        public int authCodeTtlMinutes = 5;
    }

    public Map<UUID, LinkedAccount> linkedAccounts = new LinkedHashMap<>();
    public static class LinkedAccount {
        public String discordUserId;
        public String discordUsername;
        public String mcUsername;
        public long linkedAtMs;

        public LinkedAccount() {}
        public LinkedAccount(String discordUserId, String discordUsername, String mcUsername, long linkedAtMs) {
            this.discordUserId = discordUserId;
            this.discordUsername = discordUsername;
            this.mcUsername = mcUsername;
            this.linkedAtMs = linkedAtMs;
        }
    }

    public List<PendingPull> pendingPulls = new ArrayList<>();
    public static class PendingPull {
        public UUID ownerUuid;
        public String ownerName;
        public int blockX;
        public int blockY;
        public int blockZ;
        public long queuedAtMs;

        public PendingPull() {}
        public PendingPull(UUID ownerUuid, String ownerName, int x, int y, int z, long queuedAtMs) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.blockX = x;
            this.blockY = y;
            this.blockZ = z;
            this.queuedAtMs = queuedAtMs;
        }
    }
}
