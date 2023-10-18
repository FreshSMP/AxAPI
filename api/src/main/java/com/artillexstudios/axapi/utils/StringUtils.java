package com.artillexstudios.axapi.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    public static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER;

    public static final MiniMessage MINI_MESSAGE;
    private static Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    static {
        if (Version.getServerVersion().protocolId >= Version.v1_16_5.protocolId) {
            MINI_MESSAGE = MiniMessage.builder()
                    .tags(StandardTags.defaults())
                    .build();

            LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
                    .character('\u00a7')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
        } else {
            MINI_MESSAGE = MiniMessage.builder()
                    .tags(StandardTags.defaults())
                    .build();

            LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
                    .character('\u00a7')
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
        }
    }

    @NotNull
    public static Component format(@NotNull String input, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(input, resolvers).applyFallbackStyle(TextDecoration.ITALIC.withState(false));
    }

    @NotNull
    public static String formatToString(@NotNull String input, TagResolver... resolvers) {
        return ChatColor.translateAlternateColorCodes('&', legacyHexFormat(LEGACY_COMPONENT_SERIALIZER.serialize(format(input, resolvers))));
    }

    @NotNull
    public static List<Component> formatList(@NotNull List<String> list, TagResolver... resolvers) {
        List<Component> newList = new ArrayList<>(list.size());
        for (String line : list) {
            newList.add(format(line, resolvers));
        }

        return newList;
    }

    // Thanks! https://www.spigotmc.org/threads/hex-color-code-translate.449748/
    public static String legacyHexFormat(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        var builder = new StringBuilder(message.length() + 4 * 8);

        while (matcher.find()) {
            var group = matcher.group(1);
            matcher.appendReplacement(builder, ChatColor.COLOR_CHAR + "x"
                    + ChatColor.COLOR_CHAR + group.charAt(0) + ChatColor.COLOR_CHAR + group.charAt(1)
                    + ChatColor.COLOR_CHAR + group.charAt(2) + ChatColor.COLOR_CHAR + group.charAt(3)
                    + ChatColor.COLOR_CHAR + group.charAt(4) + ChatColor.COLOR_CHAR + group.charAt(5)
            );
        }

        return matcher.appendTail(builder).toString();
    }

    @NotNull
    public static List<String> formatListToString(@NotNull List<String> list, TagResolver... resolvers) {
        List<String> newList = new ArrayList<>(list.size());
        for (String line : list) {
            newList.add(formatToString(line, resolvers));
        }

        return newList;
    }

    public static String formatNumber(String pattern, double number) {
        DecimalFormat formatter = new DecimalFormat(pattern);
        return formatter.format(number);
    }

    @NotNull
    public static String formatTime(long time) {
        Duration remainingTime = Duration.ofMillis(time);
        long total = remainingTime.getSeconds();
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
