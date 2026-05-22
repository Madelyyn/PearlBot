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
package org.pearlbot.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.Entity;
import com.zenith.event.client.ClientBotTick;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ProjectileData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.pearlbot.PearlBotConfig;

import java.util.List;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.BLOCK_DATA;
import static com.zenith.Globals.CACHE;
import static org.pearlbot.PearlBotPlugin.PLUGIN_CONFIG;

public class EnderPearlTrackerModule extends Module {
    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(of(ClientBotTick.class, e -> resolvePendingOwners()));
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("pearlbot-detect")
            .setPriority(1000)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                .inbound(ClientboundAddEntityPacket.class, (packet, session) -> {
                    onAddEntity(packet);
                    return packet;
                })
                .inbound(ClientboundBlockUpdatePacket.class, (packet, session) -> {
                    onBlockUpdate(packet);
                    return packet;
                })
                .inbound(ClientboundRemoveEntitiesPacket.class, (packet, session) -> {
                    onRemoveEntities(packet);
                    return packet;
                })
                .build())
            .build();
    }

    private void onAddEntity(ClientboundAddEntityPacket packet) {
        if (!PLUGIN_CONFIG.enabled) return;
        if (packet.getType() != EntityType.ENDER_PEARL) return;

        int ownerEntityId = 0;
        if (packet.getData() instanceof ProjectileData projectile) {
            ownerEntityId = projectile.getOwnerId();
        }

        debug("AddEntity ENDER_PEARL id={} uuid={} ownerEntityId={} pos=({}, {}, {})",
            packet.getEntityId(), packet.getUuid(), ownerEntityId,
            packet.getX(), packet.getY(), packet.getZ());

        tryRegister(packet.getUuid(), packet.getEntityId(), ownerEntityId,
            packet.getX(), packet.getY(), packet.getZ());
    }

    private void tryRegister(UUID pearlUuid, int entityId, int ownerEntityId,
                             double x, double y, double z) {
        UUID ownerUuid = resolveOwnerUuid(ownerEntityId);
        Integer pendingOwnerEntityId = (ownerUuid == null && ownerEntityId > 0) ? ownerEntityId : null;
        registerChamber(pearlUuid, entityId, ownerUuid, pendingOwnerEntityId, x, y, z);
    }

    private void resolvePendingOwners() {
        if (PLUGIN_CONFIG.chambers.isEmpty()) return;
        for (var entry : PLUGIN_CONFIG.chambers.entrySet()) {
            var chamber = entry.getValue();
            if (chamber.pendingOwnerEntityId == null) continue;
            UUID resolved = resolveOwnerUuid(chamber.pendingOwnerEntityId);
            if (resolved == null) continue;
            chamber.ownerUuid = resolved;
            chamber.pendingOwnerEntityId = null;
            info("Resolved chamber (pearl {}) owner to {} at ({}, {}, {})",
                entry.getKey(), resolved, chamber.x, chamber.y, chamber.z);
        }
    }

    private UUID resolveOwnerUuid(int ownerEntityId) {
        if (ownerEntityId <= 0) return null;
        var entityCache = CACHE.getEntityCache();
        if (entityCache == null) return null;
        Entity owner = entityCache.getEntities().get(ownerEntityId);
        return owner != null ? owner.getUuid() : null;
    }

    private void registerChamber(UUID pearlUuid, int entityId, UUID ownerUuid,
                                 Integer pendingOwnerEntityId, double x, double y, double z) {
        TrapdoorPos trapdoor = findTrapdoor(x, y, z);
        if (trapdoor == null) {
            debug("No trapdoor found within +/-{} blocks of pearl at ({}, {}, {})",
                PLUGIN_CONFIG.trapdoorScanRadius, x, y, z);
            return;
        }

        PearlBotConfig.StasisChamber chamber = new PearlBotConfig.StasisChamber();
        chamber.x = trapdoor.x;
        chamber.y = trapdoor.y;
        chamber.z = trapdoor.z;
        chamber.entityId = entityId;
        chamber.ownerUuid = ownerUuid;
        chamber.pendingOwnerEntityId = pendingOwnerEntityId;
        PearlBotConfig.StasisChamber prev = PLUGIN_CONFIG.chambers.put(pearlUuid, chamber);

        String ownerLabel = ownerUuid != null
            ? ownerUuid.toString()
            : (pendingOwnerEntityId != null
                ? "unknown (waiting on entity " + pendingOwnerEntityId + ")"
                : "unknown (no thrower)");
        if (prev == null) {
            info("Registered new chamber for owner {} (pearl {}) at trapdoor ({}, {}, {})",
                ownerLabel, pearlUuid, chamber.x, chamber.y, chamber.z);
        } else {
            debug("Updated chamber for owner {} (pearl {}) at trapdoor ({}, {}, {})",
                ownerLabel, pearlUuid, chamber.x, chamber.y, chamber.z);
        }
    }

    private TrapdoorPos findTrapdoor(double x, double y, double z) {
        var chunkCache = CACHE.getChunkCache();
        if (chunkCache == null) return null;

        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.round(y);
        int radius = PLUGIN_CONFIG.trapdoorScanRadius;

        for (int dy = -radius; dy <= radius; dy++) {
            int ty = by + dy;
            var section = chunkCache.getChunkSection(bx, ty, bz);
            if (section == null) continue;
            int stateId = section.getBlock(bx & 15, ty & 15, bz & 15);
            if (stateId == 0) continue;
            var block = BLOCK_DATA.getBlockDataFromBlockStateId(stateId);
            if (block == null) continue;
            if (block.name().endsWith("_trapdoor")) {
                return new TrapdoorPos(bx, ty, bz);
            }
        }
        return null;
    }

    private void onBlockUpdate(ClientboundBlockUpdatePacket packet) {
        if (PLUGIN_CONFIG.chambers.isEmpty()) return;
        var entry = packet.getEntry();
        int bx = entry.getX();
        int by = entry.getY();
        int bz = entry.getZ();
        var block = BLOCK_DATA.getBlockDataFromBlockStateId(entry.getBlock());
        boolean stillTrapdoor = block != null && block.name().endsWith("_trapdoor");
        if (stillTrapdoor) return;

        PLUGIN_CONFIG.chambers.entrySet().removeIf(e -> {
            var c = e.getValue();
            if (c.x != bx || c.y != by || c.z != bz) return false;
            info("Removing chamber for owner {} (pearl {}): trapdoor at ({}, {}, {}) replaced with {}",
                c.ownerUuid, e.getKey(), bx, by, bz, block == null ? "air/unknown" : block.name());
            return true;
        });
    }

    private void onRemoveEntities(ClientboundRemoveEntitiesPacket packet) {
        if (PLUGIN_CONFIG.chambers.isEmpty()) return;
        int[] ids = packet.getEntityIds();
        if (ids.length == 0) return;

        var player = CACHE.getPlayerCache() != null ? CACHE.getPlayerCache().getThePlayer() : null;
        if (player == null) return;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double viewSq = (double) PLUGIN_CONFIG.pearlViewDistance * PLUGIN_CONFIG.pearlViewDistance;

        PLUGIN_CONFIG.chambers.entrySet().removeIf(e -> {
            var c = e.getValue();
            for (int id : ids) {
                if (id != c.entityId) continue;
                double dx = c.x - px;
                double dy = c.y - py;
                double dz = c.z - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= viewSq) {
                    info("Removing chamber for owner {} (pearl {}): entity {} despawned within view ({} <= {} blocks sq)",
                        c.ownerUuid, e.getKey(), id, distSq, viewSq);
                    return true;
                }
                debug("Pearl entity {} despawned outside view distance ({} > {} blocks sq); keeping chamber",
                    id, distSq, viewSq);
            }
            return false;
        });
    }

    private record TrapdoorPos(int x, int y, int z) {}
}
