package com.artillexstudios.axapi.nms.v1_21_R3;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.gui.AnvilInput;
import com.artillexstudios.axapi.gui.SignInput;
import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.items.component.DataComponentImpl;
import com.artillexstudios.axapi.items.nbt.CompoundTag;
import com.artillexstudios.axapi.loot.LootTable;
import com.artillexstudios.axapi.nms.v1_21_R3.packet.PacketListener;
import com.artillexstudios.axapi.nms.wrapper.ServerPlayerWrapper;
import com.artillexstudios.axapi.packetentity.PacketEntity;
import com.artillexstudios.axapi.reflection.FastFieldAccessor;
import com.artillexstudios.axapi.selection.BlockSetter;
import com.artillexstudios.axapi.selection.ParallelBlockSetter;
import com.artillexstudios.axapi.serializers.Serializer;
import com.artillexstudios.axapi.utils.ActionBar;
import com.artillexstudios.axapi.utils.BossBar;
import com.artillexstudios.axapi.utils.ComponentSerializer;
import com.artillexstudios.axapi.utils.DebugMarker;
import com.artillexstudios.axapi.utils.Title;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.attribute.CraftAttribute;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftContainer;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSHandler implements com.artillexstudios.axapi.nms.NMSHandler {
    private static final FastFieldAccessor ELEMENT_DATA = FastFieldAccessor.forClassField(ArrayList.class, "elementData");
    private final String AXAPI_HANDLER;
    private Field channelField;
    private Field connectionField;
    private AtomicInteger entityCounter;
    private FastFieldAccessor attributeSupplierAccessor;
    private final FeatureFlags flags;

    public NMSHandler(AxPlugin plugin) {
        AXAPI_HANDLER = "axapi_handler_" + plugin.getName().toLowerCase(Locale.ENGLISH);
        this.flags = plugin.flags();

        try {
            connectionField = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl").getDeclaredField("e");
            connectionField.setAccessible(true);
            channelField = Class.forName("net.minecraft.network.NetworkManager").getDeclaredField("n");
            channelField.setAccessible(true);
            Field entityIdField = Entity.class.getDeclaredField("c");
            entityIdField.setAccessible(true);
            entityCounter = (AtomicInteger) entityIdField.get(null);
            attributeSupplierAccessor = FastFieldAccessor.forClassField(AttributeMap.class, "e");
        } catch (Exception exception) {
            log.error("An exception occurred while initializing NMSHandler!", exception);
        }
    }

    @Override
    public Serializer<Object, Component> componentSerializer() {
        return new Serializer<>() {
            @Override
            public Component serialize(Object object) {
                if (!(object instanceof net.minecraft.network.chat.Component component)) {
                    throw new IllegalStateException("Can only serialize component!");
                }

                String gsonText = net.minecraft.network.chat.Component.Serializer.toJson(component, MinecraftServer.getServer().registryAccess());
                return GsonComponentSerializer.gson().deserialize(gsonText);
            }

            @Override
            public Object deserialize(Component value) {
                return net.minecraft.network.chat.Component.Serializer.fromJson(GsonComponentSerializer.gson().serializer().toJsonTree(value), MinecraftServer.getServer().registryAccess());
            }
        };
    }

    @Override
    public void injectPlayer(Player player) {
        var serverPlayer = ((CraftPlayer) player).getHandle();

        var channel = getChannel(getConnection(serverPlayer.connection));

        if (!channel.pipeline().names().contains(PACKET_HANDLER)) {
            return;
        }

        if (channel.pipeline().names().contains(AXAPI_HANDLER)) {
            return;
        }

        channel.eventLoop().submit(() -> {
            channel.pipeline().addBefore(PACKET_HANDLER, AXAPI_HANDLER, new PacketListener(this.flags, player));
        });
    }

    @Override
    public void uninjectPlayer(Player player) {
        var serverPlayer = ((CraftPlayer) player).getHandle();

        var channel = getChannel(getConnection(serverPlayer.connection));

        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(AXAPI_HANDLER) != null) {
                channel.pipeline().remove(AXAPI_HANDLER);
            }
        });
    }

    @Override
    public int getProtocolVersionId(Player player) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        return serverPlayer.connection.connection.protocolVersion;
    }

    @Override
    public PacketEntity createEntity(EntityType entityType, Location location) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.entity.PacketEntity(entityType, location);
    }

    @Override
    public BlockSetter newSetter(World world) {
        return new BlockSetterImpl(world);
    }

    @Override
    public ActionBar newActionBar(Component content) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.utils.ActionBar(content);
    }

    @Override
    public Title newTitle(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.utils.Title(title, subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public BossBar newBossBar(Component title, float progress, BossBar.Color color, BossBar.Style style, BossBar.Flag... flags) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.utils.BossBar(title, progress, color, style, flags);
    }

    @Override
    public CompoundTag newTag() {
        return new com.artillexstudios.axapi.nms.v1_21_R3.items.nbt.CompoundTag(new net.minecraft.nbt.CompoundTag());
    }

    @Override
    public WrappedItemStack wrapItem(ItemStack itemStack) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.items.WrappedItemStack(itemStack);
    }

    @Override
    public WrappedItemStack wrapItem(String snbt) {
        try {
            net.minecraft.nbt.CompoundTag tag = TagParser.parseTag(snbt);
            int dataVersion = tag.getInt("DataVersion");
            net.minecraft.nbt.CompoundTag converted = (net.minecraft.nbt.CompoundTag) MinecraftServer.getServer().fixerUpper.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), dataVersion, SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
            net.minecraft.world.item.ItemStack item = net.minecraft.world.item.ItemStack.parse(MinecraftServer.getServer().registryAccess(), converted).orElseThrow();
            return new com.artillexstudios.axapi.nms.v1_21_R3.items.WrappedItemStack(item);
        } catch (CommandSyntaxException exception) {
            log.error("An error occurred while parsing item from SNBT!", exception);
            return null;
        }
    }

    @Override
    public WrappedItemStack wrapItem(byte[] bytes) {
        return wrapItem(ItemStackSerializer.INSTANCE.deserializeFromBytes(bytes));
    }

    @Override
    public void openSignInput(SignInput signInput) {
        ServerPlayer player = ((CraftPlayer) signInput.getPlayer()).getHandle();
        BlockPos pos = CraftLocation.toBlockPosition(signInput.getLocation());
        player.connection.send(new ClientboundBlockUpdatePacket(pos, ((CraftBlockData) Material.OAK_SIGN.createBlockData()).getState()));

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("id", "minecraft:oak_sign");

        if (!tag.contains("front_text")) {
            tag.put("front_text", new net.minecraft.nbt.CompoundTag());
        }

        net.minecraft.nbt.CompoundTag sideTag = tag.getCompound("front_text");
        if (!tag.contains("messages")) {
            sideTag.put("messages", new ListTag());
        }

        ListTag messagesNbt = sideTag.getList("messages", Tag.TAG_STRING);

        for (int i = 0; i < 4; i++) {
            String gson = ComponentSerializer.instance().toGson(i > signInput.getLines().length ? Component.empty() : signInput.getLines()[i]);

            messagesNbt.add(i, net.minecraft.nbt.StringTag.valueOf(gson));
        }

        ClientboundBlockEntityDataPacket clientboundBlockEntityDataPacket = new ClientboundBlockEntityDataPacket(pos, BlockEntityType.SIGN, tag);
        ClientboundOpenSignEditorPacket openSignEditorPacket = new ClientboundOpenSignEditorPacket(pos, true);
        player.connection.send(clientboundBlockEntityDataPacket);
        player.connection.send(openSignEditorPacket);
    }

    @Override
    public void setTitle(Inventory inventory, Component title) {
        net.minecraft.network.chat.Component nmsTitle = ComponentSerializer.instance().toVanilla(title);
        for (HumanEntity viewer : inventory.getViewers()) {
            CraftPlayer craftPlayer = (CraftPlayer) viewer;
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            int containerId = serverPlayer.containerMenu.containerId;
            MenuType<?> windowType = CraftContainer.getNotchInventoryType(inventory);
            serverPlayer.connection.send(new ClientboundOpenScreenPacket(containerId, windowType, nmsTitle));
            craftPlayer.updateInventory();
        }
    }

    @Override
    public DataComponentImpl dataComponents() {
        return new com.artillexstudios.axapi.nms.v1_21_R3.items.data.DataComponentImpl();
    }

    @Override
    public OfflinePlayer getCachedOfflinePlayer(String name) {
        OfflinePlayer result = Bukkit.getPlayerExact(name);
        if (result == null) {
            GameProfile profile = MinecraftServer.getServer().getProfileCache().getProfileIfCached(name);
            if (profile != null) {
                result = ((CraftServer) Bukkit.getServer()).getOfflinePlayer(profile);
            }
        }

        return result;
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        serverPlayer.connection.send((Packet<?>) packet);
    }

    @Override
    public void sendPacket(ServerPlayerWrapper player, Object packet) {
        ServerPlayer serverPlayer = ((ServerPlayer) player.asMinecraft());
        serverPlayer.connection.send((Packet<?>) packet);
    }

    @Override
    public ParallelBlockSetter newParallelSetter(World world) {
        return new ParallelBlockSetterImpl(world);
    }

    @Override
    public int nextEntityId() {
        return entityCounter.incrementAndGet();
    }

    @Override
    public void sendMessage(Player player, Component message) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        serverPlayer.connection.send(new ClientboundSystemChatPacket((net.minecraft.network.chat.Component) ComponentSerializer.instance().toVanilla(message), false));
    }

    @Override
    public DebugMarker marker(Color color, String message, int duration, int transparency, Location location) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.utils.DebugMarker(color, message, duration, transparency, location);
    }

    @Override
    public double getBase(Player player, Attribute attribute) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        AttributeMap map = serverPlayer.getAttributes();
        AttributeSupplier supplier = attributeSupplierAccessor.get(map);
        return supplier.getBaseValue(CraftAttribute.bukkitToMinecraftHolder(attribute));
    }

    @Override
    public Player dummyPlayer() {
        return new ServerPlayer(MinecraftServer.getServer(), ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle(), new GameProfile(UUID.randomUUID(), "dummy"), ClientInformation.createDefault()).getBukkitEntity();
    }

    @Override
    public LootTable lootTable(Key key) {
        return new com.artillexstudios.axapi.nms.v1_21_R3.loot.LootTable(key);
    }

    @Override
    public List<ServerPlayerWrapper> players(World world) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel level = craftWorld.getHandle();
        List<ServerPlayer> players = level.players();
        int size = players.size();
        List<ServerPlayerWrapper> playerList = new ObjectArrayList<>(size);

        if (players instanceof ArrayList<ServerPlayer> arrayList) {
            Object[] serverPlayers = ELEMENT_DATA.get(arrayList);

            for (int i = 0; i < size; i++) {
                ServerPlayer serverPlayer = (ServerPlayer) serverPlayers[i];
                if (serverPlayer == null) {
                    continue;
                }

                playerList.add(new com.artillexstudios.axapi.nms.v1_21_R3.wrapper.ServerPlayerWrapper(serverPlayer));
            }
        } else if (players instanceof LinkedList<ServerPlayer> linkedList) {
            for (ServerPlayer serverPlayer : linkedList.toArray(new ServerPlayer[0])) {
                playerList.add(new com.artillexstudios.axapi.nms.v1_21_R3.wrapper.ServerPlayerWrapper(serverPlayer));
            }
        }

        return playerList;
    }

    @Override
    public void openAnvilInput(AnvilInput anvilInput) {
        Player player = anvilInput.player();
        player.closeInventory();
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        Location location = anvilInput.location();
        AnvilMenu anvilMenu = new AnvilMenu(serverPlayer.nextContainerCounter(), serverPlayer.getInventory(), ContainerLevelAccess.create(((CraftWorld) location.getWorld()).getHandle(), new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ())));
        anvilMenu.checkReachable = false;
        anvilMenu.setTitle(ComponentSerializer.instance().toVanilla(anvilInput.title()));

        Inventory inventory = ((AbstractContainerMenu) anvilMenu).getBukkitView().getTopInventory();
        inventory.setItem(0, anvilInput.itemStack().toBukkit());

        sendPacket(player, new ClientboundOpenScreenPacket(anvilMenu.containerId, anvilMenu.getType(), anvilMenu.getTitle()));
        serverPlayer.containerMenu = anvilMenu;
        serverPlayer.initMenu(anvilMenu);
    }

    @Override
    public ServerPlayerWrapper wrapper(Object player) {
        if (player instanceof ServerPlayer sp) {
            return new com.artillexstudios.axapi.nms.v1_21_R3.wrapper.ServerPlayerWrapper(sp);
        } else if (player instanceof Player pl) {
            return new com.artillexstudios.axapi.nms.v1_21_R3.wrapper.ServerPlayerWrapper(pl);
        }

        return null;
    }

    private Channel getChannel(Connection connection) {
        try {
            return (Channel) channelField.get(connection);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Connection getConnection(ServerCommonPacketListenerImpl serverGamePacketListener) {
        try {
            return (Connection) connectionField.get(serverGamePacketListener);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
