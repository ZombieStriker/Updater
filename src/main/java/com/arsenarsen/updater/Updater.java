package com.arsenarsen.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.io.ByteStreams;

/**
 * Checks and auto updates a plugin<br>
 * <br>
 * <b>You must have a option in your config to disable the updater!</b>
 * 
 * @author Arsen
 *
 */
public class Updater {

	private static final String HOST = "https://api.curseforge.com";
	private static final String QUERY = "/servermods/files?projectIds=";
	private static final String AGENT = "Updater by ArsenArsen";
	private static final File BACKUP_DIR = new File("backup" + File.separator);
	public final static char[] HEX_CHAR_ARRAY = "0123456789ABCDEF".toCharArray();

	private int id = -1;

	private Plugin p;
	private Update lastCheck = null;
	private File pluginFile;
	private String downloadURL = null;
	private Status lastUpdate = Status.NOT_UPDATED;
	private String futuremd5;
	private List<Channel> alloweledChannels = Arrays.asList(Channel.values());

	/**
	 * Makes the updater for a plugin
	 * 
	 * @param p
	 *            Plugin to update
	 */
	public Updater(Plugin p) {
		this.p = p;
		pluginFile = new File(p.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
	}

	/**
	 * Makes the updater for a plugin with an ID
	 * 
	 * @param p
	 *            The plugin
	 * @param id
	 *            Plugin ID
	 */
	public Updater(Plugin p, int id) {
		this(p);
		setID(id);

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
	 * @param id
	 *            The plugin ID
	 */
	public void setID(int id) {
		this.id = id;
	}

	/**
	 * Attempts a update
	 * 
	 * @throws IllegalStateException
	 *             if the ID was not set
	 */
	public void update() {
		if (id == -1) {
			throw new IllegalStateException("Plugin ID is not set!");
		}
		
		if(lastCheck == null) {
			checkForUpdates();
		}
		
		if (!BACKUP_DIR.exists() || !BACKUP_DIR.isDirectory()) {
			BACKUP_DIR.mkdir();
		}

		if(lastCheck == Update.UPDATE_AVAILABLE) {
			new BukkitRunnable() {
				@Override
				public void run() {
					p.getLogger().info("Starting update of " + p.getName());
					lastUpdate = download() ? Status.UPDATE_SUCCEEDED : Status.UPDATE_FAILED;
					p.getLogger().log(Level.INFO, "Success!");
				}
			}.runTaskAsynchronously(p);
		}
	}

	private boolean download() {
		try {
			Files.copy(pluginFile.toPath(),
					new File(BACKUP_DIR, "backup-" + System.currentTimeMillis() + "-" + p.getName() + ".jar").toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			pluginFile.delete();
			final File downloadTo = new File(pluginFile.getAbsolutePath().replace("plugins",
					"plugins" + File.separator + "ALib" + File.separator + "Updater"));
			downloadTo.getParentFile().mkdirs();
			downloadTo.delete();
			Files.copy(new URL(downloadURL).openStream(), downloadTo.toPath());
			String content = "";
			if (!fileHash(downloadTo).equalsIgnoreCase(futuremd5)) {
				try {
					List<String> lines = Files.readAllLines(downloadTo.toPath());
					if (lines.get(0) != null
							& lines.get(0).equals("<html><head><title>Object moved</title></head><body>")) {
						for (String line : lines) {
							content += line;
						}
						content = content.replace("\">here</a>.</h2></body></html>", "").replace(
								"<html><head><title>Object moved</title></head><body><h2>Object moved to <a href=\"",
								"");
						downloadURL = content;
						return download();

					}
				} catch (MalformedInputException e) {
					return false;
				}
			}
			pluginFile.setWritable(true, false);
			pluginFile.delete();
			InputStream in = new FileInputStream(downloadTo);
			OutputStream out = new FileOutputStream(pluginFile);
			ByteStreams.copy(in, out);
			in.close();
			out.close();
			return true;
		} catch (IOException e) {
			p.getLogger().log(Level.SEVERE, "Couldn't download update for " + p.getName(), e);
			return false;
		}
	}

	/**
	 * Checks for new updates
	 * 
	 * @param force
	 *            Discards the cached state in order to get a new one
	 * @return Is there any updates
	 * @throws IllegalStateException
	 *             If the plugin ID is not set
	 */
	public Update checkForUpdates(boolean force) {
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
				StringBuffer responseBuffer = new StringBuffer();
				String line;
				while ((line = responseReader.readLine()) != null) {
					responseBuffer.append(line);
				}
				responseReader.close();
				String response = responseBuffer.toString();
				int counter = 1;
				if (connection.getResponseCode() == 200) {

					try {
						boolean done = false;
						String currentmd5 = fileHash(pluginFile);

						while (!done) {
							JSONParser parser = new JSONParser();
							JSONArray json = (JSONArray) parser.parse(response);
							if(json.size() - counter < 0) {
								done = true;
								lastCheck = Update.NO_UPDATE;
								done = true;
								break;
							}
							JSONObject latest = (JSONObject) json.get(json.size() - counter);
							futuremd5 = (String) latest.get("md5");
							String channel = (String) latest.get("releaseType");
							if (alloweledChannels.contains(Channel.matchChannel(channel.toUpperCase()))) {
								if (futuremd5.equalsIgnoreCase(currentmd5)) {
									lastCheck = Update.NO_UPDATE;
								} else {
									lastCheck = Update.UPDATE_AVAILABLE;
									downloadURL = (String) latest.get("downloadUrl");
								}
								done = true;
							} else counter++;
						}
					} catch (ParseException e) {
						p.getLogger().log(Level.SEVERE, "Could not parse API Response for " + target, e);
						lastCheck = Update.CANT_UNDERSTAND;
					}
				} else
					lastCheck = Update.SM_UNREACHABLE;
			} catch (IOException e) {
				p.getLogger().log(Level.SEVERE, "Could not check for updates for plugin " + p.getName(), e);
				lastCheck = Update.SM_UNREACHABLE;
			}
		}

		return lastCheck;

	}

	/**
	 * Checks for new updates, non forcing cache override
	 * 
	 * @return Is there any updates
	 * @throws IllegalStateException
	 *             If the plugin ID is not set
	 */
	public Update checkForUpdates() {
		return checkForUpdates(false);
	}

	/**
	 * Checks did the update run successfully
	 * 
	 * @return The update state
	 */
	public Status isUpdated() {
		return lastUpdate;
	}

	/**
	 * Sets alloweled channels, AKA release types
	 * @param channels The alloweled channels
	 */
	public void setChannels(Channel... channels) {
		alloweledChannels.clear();
		alloweledChannels.addAll(Arrays.asList(channels));
	}

	/**
	 * Shows the outcome of an update
	 * 
	 * @author Arsen
	 *
	 */
	public static enum Status {
		UPDATE_SUCCEEDED, UPDATE_FAILED, NOT_UPDATED;
	}

	/**
	 * Shows the outcome of an update check
	 * 
	 * @author Arsen
	 *
	 */
	public static enum Update {
		UPDATE_AVAILABLE, NO_UPDATE, SM_UNREACHABLE, CANT_UNDERSTAND;
	}

	public static enum Channel {
		// @formatter:off
		
		RELEASE("release"),
		BETA("beta"),
		ALPHA("alpha");
		
		// @formatter:on

		private String channel;

		private Channel(String channel) {
			this.channel = channel;
		}

		/**
		 * Gets the channel value
		 * @return the channel value
		 */
		public String getChannel() {
			return channel;
		}
		
		/**
		 * Returns channel whose channel value matches the given string
		 * @param channel The channel value
		 * @return The Channel constant
		 */
		public static Channel matchChannel(String channel) {
			for(Channel c : values()) {
				if(c.channel.equalsIgnoreCase(channel)) {
					return c;
				}
			}
			return null;
		}
	}
	
	/**
	 * Calculates files MD5 hash
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
		    for ( int j = 0; j < digest.length; j++ ) {
		        int v = digest[j] & 0xFF;
		        hexChars[j * 2] = HEX_CHAR_ARRAY[v >>> 4];
		        hexChars[j * 2 + 1] = HEX_CHAR_ARRAY[v & 0x0F];
		    }
		    is.close();
		    return new String(hexChars);
		} catch(IOException | NoSuchAlgorithmException e) {
			p.getLogger().log(Level.SEVERE, "Could not digest " + file.getPath(), e);
			return null;
		}
	}
}
