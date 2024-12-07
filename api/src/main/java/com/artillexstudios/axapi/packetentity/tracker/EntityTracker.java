package com.artillexstudios.axapi.packetentity.tracker;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.nms.NMSHandlers;
import com.artillexstudios.axapi.nms.wrapper.ServerPlayerWrapper;
import com.artillexstudios.axapi.packetentity.PacketEntity;
import com.artillexstudios.axapi.reflection.FastFieldAccessor;
import com.artillexstudios.axapi.utils.ExceptionReportingScheduledThreadPool;
import com.artillexstudios.axapi.utils.LogUtils;
import com.artillexstudios.axapi.utils.PaperUtils;
import com.artillexstudios.axapi.utils.Version;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EntityTracker {
    private static final boolean folia = PaperUtils.isFolia();
    private final ConcurrentHashMap<Integer, TrackedEntity> entityMap = new ConcurrentHashMap<>();
    private final FastFieldAccessor accessor = FastFieldAccessor.forClassField(String.format("com.artillexstudios.axapi.nms.%s.entity.PacketEntity", Version.getServerVersion().nmsVersion), "tracker");
    private final JavaPlugin instance = AxPlugin.getPlugin(AxPlugin.class);
    private ScheduledExecutorService service;

    public void startTicking() {
        this.shutdown();
        this.service = new ExceptionReportingScheduledThreadPool(FeatureFlags.PACKET_ENTITY_TRACKER_THREADS.get(), new ThreadFactoryBuilder().setNameFormat(this.instance.getName() + "-EntityTracker-%s").build());
        this.service.scheduleAtFixedRate(() -> {
            try {
                this.process();
            } catch (Exception exception) {
                if (exception instanceof ConcurrentModificationException) {
                    // There's something weird with the entity tracker after the server starts up.
                    // Nothing blew up yet, so I guess the error is safe to ignore...
                    // If something blows up, I'm not the person to blame!
                    // (But people don't like seeing errors, so this is the solution until I find out what causes the tracker to throw a CME)
                    //
                    // Please don't hunt me for this, I didn't want to do it.
                    if (FeatureFlags.DEBUG.get()) {
                        LogUtils.error("Caught ConcurrentModificationException when processing packet entities!", exception);
                    }
                    return;
                }

                LogUtils.error("An unexpected error occurred while processing packet entities via the tracker!", exception);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (this.service == null) {
            return;
        }

        this.service.shutdown();
        try {
            this.service.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            LogUtils.error("Failed to shut down EntityTracker service!", exception);
        }
    }

    public PacketEntity getById(int id) {
        TrackedEntity entity = this.entityMap.get(id);
        return entity == null ? null : entity.entity;
    }

    public void addEntity(PacketEntity entity) {
        TrackedEntity trackedEntity = new TrackedEntity(entity);
        this.accessor.set(entity, trackedEntity);

        this.entityMap.put(entity.id(), trackedEntity);

        trackedEntity.updateTracking(trackedEntity.getPlayersInTrackingRange());
    }

    public void removeEntity(PacketEntity entity) {
        TrackedEntity trackedEntity = this.entityMap.remove(entity.id());

        if (trackedEntity != null) {
            trackedEntity.broadcastRemove();
        }

        this.accessor.set(entity, null);
    }

    public void untrackFor(ServerPlayerWrapper player) {
        for (EntityTracker.TrackedEntity tracker : this.entityMap.values()) {
            tracker.untrack(player);
        }
    }

    public void process() {
        for (TrackedEntity entity : this.entityMap.values()) {
            entity.updateTracking(entity.getPlayersInTrackingRange());
            if (entity.hasViewers()) {
                entity.entity.sendChanges();
            }
        }
    }

    public static class TrackedEntity {
        public final Set<ServerPlayerWrapper> seenBy = ConcurrentHashMap.newKeySet();
        private final PacketEntity entity;
        private final World world;
        private List<ServerPlayerWrapper> lastTrackerCandidates;

        public TrackedEntity(PacketEntity entity) {
            this.entity = entity;
            this.world = this.entity.location().getWorld();
        }

        public void updateTracking(@NotNull List<ServerPlayerWrapper> newTrackerCandidates) {
            List<ServerPlayerWrapper> oldTrackerCandidates = this.lastTrackerCandidates;
            this.lastTrackerCandidates = newTrackerCandidates;

            for (ServerPlayerWrapper raw : newTrackerCandidates) {
                this.updatePlayer(raw);
            }

            if (oldTrackerCandidates != null && oldTrackerCandidates.size() == newTrackerCandidates.size() && oldTrackerCandidates.equals(newTrackerCandidates)) {
                return;
            }

            for (ServerPlayerWrapper player : this.seenBy.toArray(new ServerPlayerWrapper[0])) {
                if (newTrackerCandidates.isEmpty() || !newTrackerCandidates.contains(player)) {
                    this.updatePlayer(player);
                }
            }
        }

        public void updatePlayer(ServerPlayerWrapper player) {
            float dx = (float) player.getX() - (float) this.entity.location().getX();
            float dz = (float) player.getZ() - (float) this.entity.location().getZ();
            float d1 = dx * dx + dz * dz;
            boolean flag = (int) d1 <= this.entity.viewDistanceSquared();

            if (flag && !this.entity.canSee(player.wrapped())) {
                flag = false;
            }

            if (flag) {
                if (this.seenBy.add(player)) {
                    this.entity.addPairing(player.wrapped());
                }
            } else if (this.seenBy.remove(player)) {
                this.entity.removePairing(player.wrapped());
            }
        }

        public void untrack(ServerPlayerWrapper player) {
            if (!this.seenBy.remove(player)) {
                return;
            }

            this.entity.removePairing(player.wrapped());
        }

        public List<ServerPlayerWrapper> getPlayersInTrackingRange() {
            if (folia) {
                List<Player> players = this.world.getPlayers();
                List<ServerPlayerWrapper> wrapper = new ArrayList<>(players.size());
                for (Player player : players) {
                    wrapper.add(ServerPlayerWrapper.wrap(player));
                }

                return wrapper;
            }

            return NMSHandlers.getNmsHandler().players(this.world);
        }

        public void broadcast(Object packet) {
            for (ServerPlayerWrapper player : this.seenBy) {
                NMSHandlers.getNmsHandler().sendPacket(player, packet);
            }
        }

        public void broadcastRemove() {
            for (ServerPlayerWrapper player : this.seenBy) {
                this.entity.removePairing(player.wrapped());
            }
        }

        public boolean hasViewers() {
            return !this.seenBy.isEmpty();
        }
    }
}
