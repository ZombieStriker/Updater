# Updater
This is my own special updater, made for sake of having it.
## Usage
Just copy this class into your plugin, and then
~~~~java
Updater updater = new Updater(plugin, id, download, callbacks);
~~~~
Here is what that means:
* plugin - The main class
* id - Your plugin ID
* download - Should we download and update straight away? 
* Callbacks, if needed, completly optional

You can be more advanced with this, for example rechecking for updates in a command using ``updater.checkForUpdates(true)``, and updating in commands and listeners aswell using ``updater.update()``. Feel free to explore the methods.
## Maven
For those who prefer Maven, use this snippet:
```
...
<repository>
	<id>arsens-repo</id>
	<url>https://repo.arsenarsen.com/content/repositories/snapshots/</url>
</repository>
...
<dependency>
	<groupId>com.arsenarsen</groupId>
	<artifactId>updater</artifactId>
	<version>1.1.5-R0.1-SNAPSHOT</version>
</dependency>
```

## Policy
If this updater is correctly used, it is fully policy compliant. What you need to do to make sure your project gets approved is provide a way to turn the updater off, and state that you use this updater. In the constructor you can replace download with ``getConfig().getBoolean("PaTh", dEfAuLt)``

## License
Here is a quick summary of my license: https://tldrlegal.com/license/gnu-general-public-license-v3-(gpl-3)

Good luck and happy updating!

Related bukkit post found here: https://bukkit.org/threads/updater-by-arsenarsen.427824/
