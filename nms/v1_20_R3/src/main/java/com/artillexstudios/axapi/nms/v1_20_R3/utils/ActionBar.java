package com.artillexstudios.axapi.nms.v1_20_R3.utils;

import com.artillexstudios.axapi.utils.ComponentSerializer;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class ActionBar implements com.artillexstudios.axapi.utils.ActionBar {
    private ClientboundSetActionBarTextPacket actionBarTextPacket;
    private Component content;

    public ActionBar(Component content) {
        this.content = content;
        updatePacket();
    }

    @Override
    public void setContent(Component component) {
        this.content = component;
        updatePacket();
    }

    @Override
    public void send(Player player) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        serverPlayer.connection.send(actionBarTextPacket);
    }

    private void updatePacket() {
        actionBarTextPacket = new ClientboundSetActionBarTextPacket(ComponentSerializer.instance().<net.minecraft.network.chat.Component>toVanilla(content));
    }
}
