package com.artillexstudios.axapi.scheduler.impl;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.ClassUtils;
import org.bukkit.plugin.java.JavaPlugin;

public class SchedulerHandler {
    private Scheduler scheduler;

    public void init(JavaPlugin plugin) {
        if (ClassUtils.INSTANCE.classExists("io.papermc.paper.threadedregions.RegionizedServer")) {
            try {
                scheduler = (Scheduler) Class.forName("com.artillexstudios.axapi.scheduler.impl.FoliaScheduler").getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
            } catch (Exception exception) {
                scheduler = new BukkitScheduler(plugin);
            }
        } else {
            scheduler = new BukkitScheduler(plugin);
        }
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}
