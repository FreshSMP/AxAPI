package com.artillexstudios.axapi.nms.v1_21_R3.wrapper;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class ServerPlayerWrapper implements com.artillexstudios.axapi.nms.wrapper.ServerPlayerWrapper {
    private Player wrapped;
    private ServerPlayer serverPlayer;

    public ServerPlayerWrapper(Player player) {
        this.wrapped = player;
    }

    public ServerPlayerWrapper(ServerPlayer player) {
        this.serverPlayer = player;
    }

    @Override
    public double getX() {
        this.update();
        return this.serverPlayer.getX();
    }

    @Override
    public double getZ() {
        this.update();
        return this.serverPlayer.getZ();
    }

    @Override
    public Player wrapped() {
        Player wrapped = this.wrapped;
        if (wrapped == null) {
            wrapped = this.serverPlayer.getBukkitEntity();
            this.wrapped = wrapped;
        }

        return wrapped;
    }

    @Override
    public void update(boolean force) {
        if (this.serverPlayer == null || force) {
            this.serverPlayer = ((CraftPlayer) this.wrapped).getHandle();
        }
    }

    @Override
    public Object asMinecraft() {
        this.update();
        return this.serverPlayer;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ServerPlayerWrapper that)) {
            return false;
        }

        this.update();
        that.update();
        if (Objects.equals(this.serverPlayer, that.serverPlayer)) {
            return true;
        }

        return this.wrapped().getUniqueId().equals(that.wrapped().getUniqueId());
    }

    @Override
    public int hashCode() {
        this.update();
        return Objects.hashCode(this.serverPlayer);
    }
}
