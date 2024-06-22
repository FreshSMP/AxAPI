package com.artillexstudios.axapi.nms.v1_20_R4.packet;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.collections.ThreadSafeList;
import com.artillexstudios.axapi.events.PacketEntityInteractEvent;
import com.artillexstudios.axapi.gui.SignInput;
import com.artillexstudios.axapi.hologram.HologramLine;
import com.artillexstudios.axapi.hologram.Holograms;
import com.artillexstudios.axapi.items.PacketItemModifier;
import com.artillexstudios.axapi.nms.v1_20_R4.items.WrappedItemStack;
import com.artillexstudios.axapi.packetentity.PacketEntity;
import com.artillexstudios.axapi.reflection.FastMethodInvoker;
import com.artillexstudios.axapi.utils.ComponentSerializer;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axapi.utils.placeholder.Placeholder;
import com.artillexstudios.axapi.utils.placeholder.StaticPlaceholder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class PacketListener extends ChannelDuplexHandler {
    private static final Cache<Component, String> legacyCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(Duration.ofSeconds(20))
            .scheduler(Scheduler.systemScheduler())
            .build();
    private static final Cache<String, Component> componentCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(Duration.ofSeconds(20))
            .scheduler(Scheduler.systemScheduler())
            .build();
    private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Logger log = LoggerFactory.getLogger(PacketListener.class);
    private static final FastMethodInvoker methodInvoker = FastMethodInvoker.create("net.minecraft.network.protocol.game.PacketPlayInUseEntity", "a", FriendlyByteBuf.class);

    private final Player player;

    public PacketListener(Player player) {
        this.player = player;
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        switch (msg) {
            case ServerboundInteractPacket packet -> {
                if (AxPlugin.tracker == null) {
                    super.channelRead(ctx, msg);
                    return;
                }

                FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
                methodInvoker.invoke(packet, byteBuf);
                int entityId = byteBuf.readVarInt();
                int actionType = byteBuf.readVarInt();
                InteractionHand hand = null;
                Vector vector = null;
                boolean attack = false;
                if (actionType == 0) {
                    // Interact
                    int interactionHand = byteBuf.readVarInt();
                    hand = interactionHand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                } else if (actionType == 1) {
                    // Attack
                    attack = true;
                } else {
                    // Interact at
                    float x = byteBuf.readFloat();
                    float y = byteBuf.readFloat();
                    float z = byteBuf.readFloat();
                    vector = new Vector(x, y, z);
                    int interactionHand = byteBuf.readVarInt();
                    hand = interactionHand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                }

                byteBuf.release();

                PacketEntity entity = AxPlugin.tracker.getById(entityId);
                if (entity != null) {
                    PacketEntityInteractEvent event = new PacketEntityInteractEvent(player, entity, attack, vector, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
                    // TODO: Call interact on entity
                    Bukkit.getPluginManager().callEvent(event);
                }
            }
            case ServerboundSignUpdatePacket updatePacket -> {
                SignInput signInput = SignInput.remove(player);
                if (signInput == null) {
                    super.channelRead(ctx, msg);
                    return;
                }

                signInput.getListener().accept(player, ComponentSerializer.INSTANCE.asAdventureFromJson(Arrays.asList(updatePacket.getLines())).toArray(new net.kyori.adventure.text.Component[0]));
                com.artillexstudios.axapi.scheduler.Scheduler.get().runAt(signInput.getLocation(), task -> {
                    CraftBlockData data = (CraftBlockData) signInput.getLocation().getBlock().getType().createBlockData();
                    BlockPos pos = CraftLocation.toBlockPosition(signInput.getLocation());
                    ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                    serverPlayer.connection.send(new ClientboundBlockUpdatePacket(pos, data.getState()));
                });
                return;
            }
            case ServerboundSetCreativeModeSlotPacket packet -> {
                var item = packet.itemStack();
                if (PacketItemModifier.isListening()) {
                    PacketItemModifier.restore(new WrappedItemStack(item));
                }
            }
            default -> {}
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        switch (msg) {
            case ClientboundSetEntityDataPacket dataPacket -> {
                HologramLine line = Holograms.byId(dataPacket.id());

                if (line == null || (line.type() != HologramLine.Type.TEXT || !line.hasPlaceholders())) {
                    // The entity is not a packet entity, skip!
                    super.write(ctx, msg, promise);
                    return;
                }

                List<SynchedEntityData.DataValue<?>> dataValues = new ArrayList<>(dataPacket.packedItems());
                Iterator<SynchedEntityData.DataValue<?>> iterator = dataValues.iterator();

                SynchedEntityData.DataValue<?> value = null;
                while (iterator.hasNext()) {
                    SynchedEntityData.DataValue<?> next = iterator.next();
                    if (next.id() != 2) continue;

                    Optional<Component> content = (Optional<Component>) next.value();
                    if (content.isEmpty()) {
                        super.write(ctx, msg, promise);
                        return;
                    }

                    String legacy = legacyCache.get(content.get(), (minecraftComponent) -> {
                        String gsonText = Component.Serializer.toJson(minecraftComponent, MinecraftServer.getServer().registryAccess());
                        net.kyori.adventure.text.Component gsonComponent = GsonComponentSerializer.gson().deserialize(gsonText);
                        return LEGACY_COMPONENT_SERIALIZER.serialize(gsonComponent);
                    });

                    if (legacy == null) {
                        super.write(ctx, msg, promise);
                        return;
                    }

                    ThreadSafeList<Placeholder> placeholders = line.placeholders();
                    for (int i = 0; i < placeholders.size(); i++) {
                        Placeholder placeholder = placeholders.get(i);
                        if (placeholder instanceof StaticPlaceholder) continue;
                        legacy = placeholder.parse(player, legacy);
                    }

                    Component component = componentCache.get(legacy, (legacyText) -> {
                        net.kyori.adventure.text.Component formatted = StringUtils.format(legacyText);
                        String gson = GsonComponentSerializer.gson().serialize(formatted);
                        return Component.Serializer.fromJson(gson, MinecraftServer.getServer().registryAccess());
                    });

                    value = new SynchedEntityData.DataValue<>(next.id(), EntityDataSerializers.OPTIONAL_COMPONENT, Optional.ofNullable(component));
                    iterator.remove();
                    break;
                }

                if (value != null) {
                    dataValues.add(value);
                }

                ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(dataPacket.id(), dataValues);

                super.write(ctx, packet, promise);
            }
            case ClientboundContainerSetSlotPacket packet -> {
                if (PacketItemModifier.isListening()) {
                    PacketItemModifier.callModify(new WrappedItemStack(packet.getItem()), player, PacketItemModifier.Context.SET_SLOT);
                }

                super.write(ctx, packet, promise);
            }
            case ClientboundContainerSetContentPacket packet -> {
                if (PacketItemModifier.isListening()) {
                    PacketItemModifier.callModify(new WrappedItemStack(packet.getCarriedItem()), player, PacketItemModifier.Context.SET_CONTENTS);

                    for (ItemStack item : packet.getItems()) {
                        PacketItemModifier.callModify(new WrappedItemStack(item), player, PacketItemModifier.Context.SET_CONTENTS);
                    }
                }

                super.write(ctx, packet, promise);
            }
            case ClientboundSetEquipmentPacket packet -> {
                if (PacketItemModifier.isListening()) {
                    List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> items = new ArrayList<>();

                    for (Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack> slot : packet.getSlots()) {
                        ItemStack second = slot.getSecond();
                        if (second == null) {
                            items.add(Pair.of(slot.getFirst(), ItemStack.EMPTY));
                            continue;
                        }

                        ItemStack itemStack = second.copy();
                        PacketItemModifier.callModify(new WrappedItemStack(itemStack), player, PacketItemModifier.Context.EQUIPMENT);
                        items.add(Pair.of(slot.getFirst(), itemStack));
                    }

                    ClientboundSetEquipmentPacket newEquipmentPacket = new ClientboundSetEquipmentPacket(packet.getEntity(), items);
                    super.write(ctx, newEquipmentPacket, promise);
                } else {
                    super.write(ctx, msg, promise);
                }
            }
//            case ClientboundBundlePacket bundlePacket when PacketItemModifier.isListening() -> {
//                for (Packet<?> packet : bundlePacket.subPackets()) {
//                    if (packet instanceof ClientboundSetEntityDataPacket dataPacket) {
//                        for (SynchedEntityData.DataValue<?> packedItem : dataPacket.packedItems()) {
//                            if (packedItem.serializer().equals(EntityDataSerializers.ITEM_STACK)) {
//                                ItemStack value = (ItemStack) packedItem.value();
//                                PacketItemModifier.callModify(new WrappedItemStack(value), player, PacketItemModifier.Context.DROPPED_ITEM);
//
//                                super.write(ctx, msg, promise);
//                                return;
//                            }
//                        }
//                    }
//                }
//
//                super.write(ctx, msg, promise);
//            }
            case null, default -> super.write(ctx, msg, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (com.artillexstudios.axapi.utils.FeatureFlags.DEBUG.get()) {
            log.error("An unhandled exception occurred on ctx {}!", ctx, cause);
        }

        super.exceptionCaught(ctx, cause);
    }
}
