package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Fake bukkit scheduler
 * <br>
 * Created by Arsen on 26.8.2016.
 */
public abstract class BukkitRunnable implements Runnable {
    public synchronized BukkitTask runTaskAsynchronously(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        new Thread(this).start();
        System.out.println("RTA");
        return null;
    }

    public synchronized BukkitTask runTask(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        System.out.println("RT");
        run();
        return null;
    }
}
