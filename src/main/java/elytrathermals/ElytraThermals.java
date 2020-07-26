package elytrathermals;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class ElytraThermals extends JavaPlugin {
    private static Listener thermalEventListener = new ThermalEventListener();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(thermalEventListener, this);
        getLogger().info("ElytraThermals Enabled - thanks to jmack2424");
    }

    @Override
    public void onDisable() {
        thermalEventListener = null;
        getLogger().info("ElytraThermals Disabled");
    }
    
}