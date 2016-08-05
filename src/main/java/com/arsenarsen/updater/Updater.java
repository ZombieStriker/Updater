package com.arsenarsen.updater;

import com.google.common.io.ByteStreams;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Checks and auto updates a plugin<br>
 * <br>
 * <b>You must have a option in your config to disable the updater!</b>
 *
 * @author Arsen
 */
public class Updater {

	private static final String HOST = "https://api.curseforge.com";
	private static final String QUERY = "/servermods/files?projectIds=";
	private static final String AGENT = "Updater by ArsenArsen";
	private static final File WORKING_DIR = new File("plugins" + File.separator + "AUpdater" + File.separator);
	private static final File BACKUP_DIR = new File(WORKING_DIR, "backups" + File.separator);
	private static final File LOG_FILE = new File(WORKING_DIR, "updater.log");
	private static final char[] HEX_CHAR_ARRAY = "0123456789abcdef".toCharArray();
	private static final Pattern NAME_MATCH = Pattern.compile(".*\\sv?[0-9.]*");
	private static final String VERSION_SPLIT = "\\sv?";

	private int id = -1;

	private Plugin p;
	private UpdateAvailability lastCheck = null;
	private UpdateResult lastUpdate = UpdateResult.NOT_UPDATED;
	private File pluginFile;
	private String downloadURL = null;
	private String futuremd5;
	private String downloadName;
	private List<Channel> allowedChannels = Arrays.asList(Channel.ALPHA, Channel.BETA, Channel.RELEASE);
	private List<UpdateCallback> callbacks = new ArrayList<>();
	private SyncCallbackCaller caller = new SyncCallbackCaller();
	private List<String> skipTags = new ArrayList<>();

	/**
	 * Makes the updater for a plugin
	 *
	 * @param p Plugin to update
	 */
	public Updater(Plugin p) {
		this.p = p;
		pluginFile = new File(p.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		if (!LOG_FILE.exists()) {
			try {
				LOG_FILE.getParentFile().mkdirs();
				LOG_FILE.createNewFile();
				log("Created log file!");
			} catch (IOException e) {
				p.getLogger().log(Level.SEVERE, "Could not create " + LOG_FILE.getName() + "!", e);
			}

		}
	}

	/**
	 * Makes the updater for a plugin with an ID
	 *
	 * @param p  The plugin
	 * @param id Plugin ID
	 */
	public Updater(Plugin p, int id) {
		this(p);
		setID(id);

	}

	/**
	 * Makes the updater for a plugin with an ID
	 *
	 * @param p        The plugin
	 * @param id       Plugin ID
	 * @param download Set to true if your plugin needs to be immediately downloaded
	 * @param skipTags Tags, endings of a filename, that updater will ignore, must begin with a dash ('-')
	 */
	public Updater(Plugin p, int id, boolean download, String... skipTags) {
		this(p);
		setID(id);
		for(String tag : skipTags)
			if(tag.startsWith("-"))
				this.skipTags.add(tag);
		if (download && (checkForUpdates() == UpdateAvailability.UPDATE_AVAILABLE)) {
			update();
		}
	}

	/**
	 * Makes the updater for a plugin with an ID
	 *
	 * @param p         The plugin
	 * @param id        Plugin ID
	 * @param download  Set to true if your plugin needs to be immediately downloaded
	 * @param skipTags  Tags, endings of a filename, that updater will ignore, or null for none
	 * @param callbacks All update callbacks you need
	 */
	public Updater(Plugin p, int id, boolean download, String[] skipTags, UpdateCallback... callbacks) {
		this(p);
		setID(id);
		this.callbacks.addAll(Arrays.asList(callbacks));
		if (skipTags != null) {
			for(String tag : skipTags)
				if(tag.startsWith("-"))
					this.skipTags.add(tag);
		}
		if (download && (checkForUpdates() == UpdateAvailability.UPDATE_AVAILABLE)) {
			update();
		}
	}

	/**
	 * Gets the plugin ID
	 *
	 * @return the plugin ID
	 */
	public int getID() {
		return id;
	}

	/**
	 * Sets the plugin ID
	 *
	 * @param id The plugin ID
	 */
	public void setID(int id) {
		this.id = id;
	}

	/**
	 * Adds a new callback
	 *
	 * @param callback Callback to register
	 */
	public void registerCallback(UpdateCallback callback) {
		callbacks.add(callback);
	}

	/**
	 * Attempts a update
	 *
	 * @throws IllegalStateException if the ID was not set
	 */
	public void update() {
		if (id == -1) {
			throw new IllegalStateException("Plugin ID is not set!");
		}

		if (lastCheck == null) {
			checkForUpdates();
		}

		if (!BACKUP_DIR.exists() || !BACKUP_DIR.isDirectory()) {
			BACKUP_DIR.mkdir();
		}
		final Updater updater = this;
		if (lastCheck == UpdateAvailability.UPDATE_AVAILABLE) {
			new BukkitRunnable() {

				@Override public void run() {
					p.getLogger().info("Starting update of " + p.getName());
					log("Updating " + p.getName() + "!");
					lastUpdate = download();
					p.getLogger().log(Level.INFO, "Update done! Result: " + lastUpdate);
					caller.call(callbacks, lastUpdate, updater);
				}
			}.runTaskAsynchronously(p);
		}
	}

	private UpdateResult download() {
		try {
			Files.copy(pluginFile.toPath(),
					new File(BACKUP_DIR, "backup-" + System.currentTimeMillis() + "-" + p.getName() + ".jar").toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			final File downloadTo = new File(pluginFile.getParentFile().getAbsolutePath() + "Updater" + File.separator,
					downloadName);
			downloadTo.getParentFile().mkdirs();
			downloadTo.delete();
			Files.copy(new URL(downloadURL).openStream(), downloadTo.toPath());
			String content = "";
			if (!fileHash(downloadTo).equalsIgnoreCase(futuremd5)) {
				try {
					List<String> lines = Files.readAllLines(downloadTo.toPath(), StandardCharsets.UTF_8);
					if (lines.get(0) != null && lines.get(0)
							.equals("<html><head><title>Object moved</title></head><body>")) {
						for (String line : lines) {
							content += line;
						}
						content = content.replace("\">here</a>.</h2></body></html>", "").replace(
								"<html><head><title>Object moved</title></head><body><h2>Object moved to <a href=\"",
								"");
						downloadURL = content;
						return download();

					}
				} catch (MalformedInputException ignored) {
				}
			}
			if (downloadTo.getName().endsWith(".jar")) {
				pluginFile.setWritable(true, false);
				pluginFile.delete();
				InputStream in = new FileInputStream(downloadTo);
				OutputStream out = new FileOutputStream(pluginFile);
				long bytes;
				p.getLogger().info("Update done! Downloaded " + (bytes = ByteStreams.copy(in, out)) + " bytes!");
				log("Updated plugin " + p.getName() + " with " + bytes + "bytes!");
				in.close();
				out.close();
				return UpdateResult.UPDATE_SUCCEEDED;
			} else
				return unzip(downloadTo);
		} catch (IOException e) {
			p.getLogger().log(Level.SEVERE, "Couldn't download update for " + p.getName(), e);
			log("Failed to update " + p.getName() + "!", e);
			return UpdateResult.IOERROR;
		}
	}

	private void log(String message, Exception exception) {
		StringWriter string = new StringWriter();
		PrintWriter print = new PrintWriter(string);
		exception.printStackTrace(print);
		log(message + " " + string.toString());
		try {
			string.close();
		} catch (IOException ignored) {
		}
		print.close();
	}

	private UpdateResult unzip(File download) {
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(download);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			ZipEntry entry;
			while ((entry = entries.nextElement()) != null) {
				pluginFile.setWritable(true, false);
				pluginFile.delete();
				File target = new File(pluginFile.getParentFile(), entry.getName());
				if (!entry.isDirectory()) {
					target.getParentFile().mkdirs();
					InputStream zipStream = zipFile.getInputStream(entry);
					OutputStream fileStream = new FileOutputStream(target);
					ByteStreams.copy(zipStream, fileStream);
					try {
						zipStream.close();
						fileStream.close();
					} catch (IOException ignored) {
					}
				}
			}
			return UpdateResult.UPDATE_SUCCEEDED;
		} catch (IOException e) {
			if (e instanceof ZipException) {
				p.getLogger().log(Level.SEVERE, "Could not unzip downloaded file!", e);
				log("Update for " + p.getName() + "was an unknown filetype! ", e);
				return UpdateResult.UNKNOWN_FILE_TYPE;
			} else {
				p.getLogger().log(Level.SEVERE,
						"An IOException occured while trying to update %s!".replace("%s", p.getName()), e);
				log("Update for " + p.getName() + "was an unknown filetype! ", e);
				return UpdateResult.IOERROR;
			}
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	/**
	 * Checks for new updates
	 *
	 * @param force Discards the cached state in order to get a new one, ignored if update check didn't run
	 * @return Is there any updates
	 * @throws IllegalStateException If the plugin ID is not set
	 */
	public UpdateAvailability checkForUpdates(boolean force) {
		if (id == -1) {
			throw new IllegalStateException("Plugin ID is not set!");
		}

		if (force || lastCheck == null) {
			String target = HOST + QUERY + id;

			try {
				URL url = new URL(target);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.addRequestProperty("User-Agent", AGENT);
				connection.connect();
				BufferedReader responseReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder responseBuffer = new StringBuilder();
				String line;
				while ((line = responseReader.readLine()) != null) {
					responseBuffer.append(line);
				}
				responseReader.close();
				String response = responseBuffer.toString();
				int counter = 1;
				if (connection.getResponseCode() == 200) {

					try {

						while (true) {
							JSONParser parser = new JSONParser();
							JSONArray json = (JSONArray) parser.parse(response);
							if (json.size() - counter < 0) {
								lastCheck = UpdateAvailability.NO_UPDATE;
								break;
							}
							JSONObject latest = (JSONObject) json.get(json.size() - counter);
							futuremd5 = (String) latest.get("md5");
							String channel = (String) latest.get("releaseType");
							String name = (String) latest.get("name");
							if (allowedChannels.contains(Channel.matchChannel(channel.toUpperCase())) && !hasTag(
									name)) {
								String noTagName = name;
								String oldVersion = p.getDescription().getVersion().replaceAll("-.*", "");
								for(String tag : skipTags){
									noTagName = noTagName.replace(tag, "");
									oldVersion = oldVersion.replace(tag, "");
								}
								if(!NAME_MATCH.matcher(noTagName).matches()) {
									lastCheck = UpdateAvailability.CANT_PARSE_NAME;
									return lastCheck;
								}
								String[] splitName = noTagName.split(VERSION_SPLIT);
								String version = splitName[splitName.length - 1];
								if(oldVersion.length() > version.length()){
									while(oldVersion.length() > version.length()){
										version += ".0";
									}
								} else if(oldVersion.length() < version.length()){
									while(oldVersion.length() < version.length()){
										oldVersion += ".0";
									}
								}
								String[] splitOldVersion = oldVersion.split("\\.");
								String[] splitVersion = version.split("\\.");

								Integer[] parsedOldVersion = new Integer[splitOldVersion.length];
								Integer[] parsedVersion = new Integer[splitVersion.length];

								for(int i = 0; i < parsedOldVersion.length; i++){
									parsedOldVersion[i] = Integer.parseInt(splitOldVersion[i]);
								}
								for(int i = 0; i < parsedVersion.length; i++){
									parsedVersion[i] = Integer.parseInt(splitVersion[i]);
								}
								boolean update = false;
								for(int i = 0; i < parsedOldVersion.length; i++){
									if(parsedOldVersion[i] < parsedVersion[i]) {
										update = true;
										break;
									}
								}
								if (!update) {
									lastCheck = UpdateAvailability.NO_UPDATE;
								} else {
									lastCheck = UpdateAvailability.UPDATE_AVAILABLE;
									downloadURL = (String) latest.get("downloadUrl");
									downloadName = (String) latest.get("fileName");
								}
								break;
							} else
								counter++;
						}
					} catch (ParseException e) {
						p.getLogger().log(Level.SEVERE, "Could not parse API Response for " + target, e);
						log("Could not parse API Response for " + target + " while updating " + p.getName(), e);
						lastCheck = UpdateAvailability.CANT_UNDERSTAND;
					}
				} else {
					log("Could not reach API for " + target + " while updating " + p.getName());
					lastCheck = UpdateAvailability.SM_UNREACHABLE;
				}
			} catch (IOException e) {
				p.getLogger().log(Level.SEVERE, "Could not check for updates for plugin " + p.getName(), e);
				log("Could not reach API for " + target + " while updating " + p.getName());
				lastCheck = UpdateAvailability.SM_UNREACHABLE;
			}
		}
		log("Update check ran for " + p.getName() + "! Check resulted in " + lastCheck);
		return lastCheck;
	}

	private void log(String message) {
		try {
			Files.write(LOG_FILE.toPath(), Collections.singletonList(
					"[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " + message),
					StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			p.getLogger().log(Level.SEVERE, "Could not log to " + LOG_FILE.getPath() + "!", e);
		}
	}

	private boolean hasTag(String name) {
		for (String tag : skipTags) {
			if (name.toLowerCase().endsWith(tag.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks for new updates, non forcing cache override
	 *
	 * @return Is there any updates
	 * @throws IllegalStateException If the plugin ID is not set
	 */
	public UpdateAvailability checkForUpdates() {
		return checkForUpdates(false);
	}

	/**
	 * Checks did the update run successfully
	 *
	 * @return The update state
	 */
	public UpdateResult isUpdated() {
		return lastUpdate;
	}

	/**
	 * Sets allowed channels, AKA release types
	 *
	 * @param channels The allowed channels
	 */
	public void setChannels(Channel... channels) {
		allowedChannels.clear();
		allowedChannels.addAll(Arrays.asList(channels));
	}

	/**
	 * Shows the outcome of an update
	 *
	 * @author Arsen
	 */
	public enum UpdateResult {
		/**
		 * Update was successful
		 */
		UPDATE_SUCCEEDED,

		/**
		 * Update was not attempted yet
		 */
		NOT_UPDATED,

		/**
		 * Could not unpack the update
		 */
		UNKNOWN_FILE_TYPE,

		/**
		 * An unknown IO error occurred
		 */
		IOERROR
	}

	/**
	 * Shows the outcome of an update check
	 *
	 * @author Arsen
	 */
	public enum UpdateAvailability {
		/**
		 * There is an update
		 */
		UPDATE_AVAILABLE,

		/**
		 * You have the latest version
		 */
		NO_UPDATE,

		/**
		 * Could not reach server mods API
		 */
		SM_UNREACHABLE,

		/**
		 * Update name cannot be parsed, meaning the version cannot be compared
		 */
		CANT_PARSE_NAME,

		/**
		 * Could not parse response from server mods API
		 */
		CANT_UNDERSTAND
	}

	public enum Channel {
		/**
		 * Normal release
		 */
		RELEASE("release"),

		/**
		 * Beta release
		 */
		BETA("beta"),

		/**
		 * Alpha release
		 */
		ALPHA("alpha");

		private String channel;

		Channel(String channel) {
			this.channel = channel;
		}

		/**
		 * Gets the channel value
		 *
		 * @return the channel value
		 */
		public String getChannel() {
			return channel;
		}

		/**
		 * Returns channel whose channel value matches the given string
		 *
		 * @param channel The channel value
		 * @return The Channel constant
		 */
		public static Channel matchChannel(String channel) {
			for (Channel c : values()) {
				if (c.channel.equalsIgnoreCase(channel)) {
					return c;
				}
			}
			return null;
		}
	}

	/**
	 * Calculates files MD5 hash
	 *
	 * @param file The file to digest
	 * @return The MD5 hex or null, if the operation failed
	 */
	public String fileHash(File file) {
		FileInputStream is;
		try {
			is = new FileInputStream(file);
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] bytes = new byte[2048];
			int numBytes;
			while ((numBytes = is.read(bytes)) != -1) {
				md.update(bytes, 0, numBytes);
			}
			byte[] digest = md.digest();
			char[] hexChars = new char[digest.length * 2];
			for (int j = 0; j < digest.length; j++) {
				int v = digest[j] & 0xFF;
				hexChars[j * 2] = HEX_CHAR_ARRAY[v >>> 4];
				hexChars[j * 2 + 1] = HEX_CHAR_ARRAY[v & 0x0F];
			}
			is.close();
			return new String(hexChars);
		} catch (IOException | NoSuchAlgorithmException e) {
			p.getLogger().log(Level.SEVERE, "Could not digest " + file.getPath(), e);
			return null;
		}
	}

	/**
	 * Called right after update is done
	 *
	 * @author Arsen
	 */
	public interface UpdateCallback {

		void updated(UpdateResult updateResult, Updater updater);
	}

	private class SyncCallbackCaller extends BukkitRunnable {
		private List<UpdateCallback> callbacks;
		private UpdateResult updateResult;
		private Updater updater;

		public void run() {
			for (UpdateCallback callback : callbacks) {
				callback.updated(updateResult, updater);
			}
		}

		void call(List<UpdateCallback> callbacks, UpdateResult updateResult, Updater updater) {
			this.callbacks = callbacks;
			this.updateResult = updateResult;
			this.updater = updater;
			runTask(updater.p);
		}

	}
}
