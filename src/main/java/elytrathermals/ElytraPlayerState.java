package elytrathermals;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class ElytraPlayerState {

    /* configuration constants */

    final double max_velocity = 2.0;
    final double max_altitude = 275;

    /* instance tracking */

    protected Player _player;
    protected BossBar _altitudeBossBar;
    protected BossBar _speedBossBar;

    /** 
     * Constructor - create bossbars
     */
    public ElytraPlayerState(Player player) {
        _player = player;
        _altitudeBossBar = player.getServer().createBossBar("Altitude", BarColor.BLUE, BarStyle.SEGMENTED_20);
        _altitudeBossBar.addPlayer(_player);
        _altitudeBossBar.setProgress(0);
        _altitudeBossBar.setVisible(false);
        _speedBossBar = player.getServer().createBossBar("Velocity", BarColor.BLUE, BarStyle.SEGMENTED_20);
        _speedBossBar.addPlayer(_player);
        _speedBossBar.setProgress(0);
        _speedBossBar.setVisible(false);
    }

    /** 
     * public update method
     */
    public void update() {
        updateBossBars();
    }

    /** 
     * Update ElytraThermals BossBars
     */
    protected void updateBossBars() {
        if (_player.isGliding()) {
            // Altimeter
            double altitude = _player.getLocation().getY();
            BarColor altimeterColor = BarColor.BLUE;
            if (altitude < 65 || altitude > (max_altitude-25)) altimeterColor = BarColor.YELLOW;
            if (altitude >= max_altitude || altitude <= 25) altimeterColor = BarColor.RED;
            _altitudeBossBar.setColor(altimeterColor);
            _altitudeBossBar.setTitle(String.format("Altitude: %d", (int) altitude));
            _altitudeBossBar.setProgress(Math.min(1.0, Math.max(0.0, altitude / max_altitude)));
            _altitudeBossBar.setVisible(true);
            // Speedometer
            double speed = _player.getVelocity().length();
            _speedBossBar.setTitle(String.format("Speed: %3.1f", 20 * speed));
            _speedBossBar.setProgress(Math.min(1.0, Math.max(0.0, speed / max_velocity)));
            _speedBossBar.setVisible(true);

        } else {
            _altitudeBossBar.setProgress(0);
            _altitudeBossBar.setVisible(false);
            _speedBossBar.setProgress(0);
            _speedBossBar.setVisible(false);
        }
    } 
   
} 