package tests;

import com.arsenarsen.updater.Updater;
import com.avaje.ebean.EbeanServer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Basic updater test. Meh
 * <br>
 * Created by Arsen on 25.8.2016.
 */
public class Tester {
    public static StringBuilder version = new StringBuilder();
    private static Thread mainThread = Thread.currentThread();

    // RIP!
    static Plugin testPlugin = new Plugin(){


        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return null;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return false;
        }

        @Override
        public File getDataFolder() {
            return null;
        }

        @Override
        public PluginDescriptionFile getDescription() {
            return new PluginDescriptionFile(getName(), version.toString(), "Tester$1");
        }

        @Override
        public FileConfiguration getConfig() {
            return null;
        }

        @Override
        public InputStream getResource(String filename) {
            return null;
        }

        @Override
        public void saveConfig() {

        }

        @Override
        public void saveDefaultConfig() {

        }

        @Override
        public void saveResource(String resourcePath, boolean replace) {

        }

        @Override
        public void reloadConfig() {

        }

        @Override
        public PluginLoader getPluginLoader() {
            return null;
        }

        @Override
        public Server getServer() {
            return null;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void onDisable() {

        }

        @Override
        public void onLoad() {

        }

        @Override
        public void onEnable() {

        }

        @Override
        public boolean isNaggable() {
            return false;
        }

        @Override
        public void setNaggable(boolean canNag) {

        }

        @Override
        public EbeanServer getDatabase() {
            return null;
        }

        @Override
        public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
            return null;
        }

        @Override
        public Logger getLogger() {
            return Logger.getLogger("Test");
        }

        @Override
        public String getName() {
            return "Test";
        }
    };

    private static AtomicBoolean onHold = new AtomicBoolean(true);

    public static Thread getMainThread() {
        return mainThread;
    }

    @Test
    /*
     * This will download SimpleAutoBroadcaster v1.0 as a test. Afterwards another test will be ran to see if it dowloads again.
     */
    public void testUpdaterWithNewVer() throws NoSuchFieldException, IllegalAccessException, InterruptedException, IOException {
        version.setLength(0);
        version.append("0.0.1");
        Updater u = new Updater(testPlugin, 100736, false);
        Field file = u.getClass().getDeclaredField("pluginFile");
        file.setAccessible(true);
        File pfile = new File("plugins" + File.separatorChar + "testTarget.jar");
        pfile.delete();
        pfile.createNewFile();
        file.set(u, pfile);
        Field debug = u.getClass().getDeclaredField("debug");
        debug.setAccessible(true);
        debug.set(u, true);
        u.registerCallback(new Updater.UpdateCallback() {
            @Override
            public void updated(Updater.UpdateResult updateResult, Updater updater) {
                onHold.set(false);
                Assert.assertEquals(Updater.UpdateResult.UPDATE_SUCCEEDED, updateResult);
            }
        });
        u.update();
        while(onHold.get()) {
            Thread.sleep(1);
        }
        Assert.assertEquals("2ee9ad1bda49345dda147442763fdcda", u.fileHash(pfile));
    }

    @Test
    /*
     * This will try to see if the file downloads
     */
    public void testUpdaterWithOldVer() throws NoSuchFieldException, IllegalAccessException, InterruptedException, IOException {
        version.setLength(0);
        version.append("2.0.0");
        Updater u = new Updater(testPlugin, 100736, false);
        Field file = u.getClass().getDeclaredField("pluginFile");
        file.setAccessible(true);
        File pfile = new File("plugins" + File.separatorChar + "testTarget.jar");
        pfile.delete();
        pfile.createNewFile();
        file.set(u, pfile);
        Field debug = u.getClass().getDeclaredField("debug");
        debug.setAccessible(true);
        debug.set(u, true);
        System.out.println(u.checkForUpdates());
        Assert.assertEquals(Updater.UpdateAvailability.NO_UPDATE, u.checkForUpdates());
    }
}
