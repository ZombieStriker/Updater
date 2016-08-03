# Updater
This is my own special updater, made for sake of having it.
## Usage
Just copy this class into your plugin, and then
~~~~java
Updater updater = new Updater(plugin, id);
if(updater.checkForUpdates() == Update.UPDATE_AVAILABLE){
    updater.update();
}
~~~~
You can be more advanced with this, for example rechecking for updates in a command using ``updater.checkForUpdates(true)``, and updating in commands and listeners aswell using ``updater.update()``. Feel free to explore the methods.
## Maven
For those who prefer Maven, use this snippet:
```
...
<repository>
	<id>arsens-repo</id>
	<url>http://repo-arsenarsen.rhcloud.com/content/repositories/snapshots/</url>
</repository>
...
<dependency>
	<groupId>com.arsenarsen</groupId>
	<artifactId>updater</artifactId>
	<version>1.0.0-R0.2-SNAPSHOT</version>
</dependency>'
```

Good luck and happy updating!