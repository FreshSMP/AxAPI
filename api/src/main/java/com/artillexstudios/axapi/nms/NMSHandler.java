package com.artillexstudios.axapi.nms;

import com.artillexstudios.axapi.selection.BlockSetter;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface NMSHandler {

    byte[] serializeItemStack(ItemStack itemStack);

    ItemStack deserializeItemStack(byte[] bytes);

    void injectPlayer(Player player);

    void uninjectPlayer(Player player);

    int getProtocolVersionId(Player player);

    void setItemStackTexture(ItemStack item, String texture);

    BlockSetter newSetter(World world);
}
