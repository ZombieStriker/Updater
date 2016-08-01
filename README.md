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
