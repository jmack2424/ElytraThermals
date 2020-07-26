package elytrathermals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class ThermalEventListener implements Listener {

      /* These variables may fit well in a config file */

      // airbrake feature 
      private final boolean airbrakeEnabled = true;
      private final double airbrakeFactor = 0.10;
      // suffocation feature 
      private final boolean suffocateEnabled = true;
      private final double suffocationHeight = 270;
      private final double suffocationDamage = 0.5;
      // thermal feature
      private final boolean thermalLiftEnabled = true;
      private final boolean thermalDragEnabled = true;
      private final Sound thermalSound = Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE;
      private final double thermalInit = 0.2; // initial bubble
      private final double thermalLift = 1.2; // thermal lift factor
      private final double thermalDrag = -1.0; // thermal drag factor
      private final double thermalDistance = 12.0; // max distance over block
      private final double thermalVelocity = 0.7; // min horizontal gliding velocity
      private final int thermalTickDelay = 15; // tick delay between thermals
      private final int thermalParticleTicks = 20; // particle ticks
      private final List<Material> thermalLiftMaterials = new ArrayList<>(Arrays.asList(Material.MAGMA_BLOCK,Material.LAVA,Material.CAMPFIRE,Material.FIRE));
      private final List<Material> thermalDragMaterials = new ArrayList<>(Arrays.asList(Material.ICE,Material.PACKED_ICE,Material.BLUE_ICE,Material.SNOW_BLOCK));
      
      /* instance tracking */

      // active player thermal tracking - prevent lag
      private final List<String> activeThermals = new ArrayList<String>();
      // player state tracking 
      private final HashMap<String, ElytraPlayerState> playerStates = new HashMap<>();

      /** 
       * Listens to PlayerJoinEvent - creates ElytraPlayerState for joined player
       * @param event
       */
      @EventHandler(ignoreCancelled = true)
      public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            playerStates.put(player.getName(), new ElytraPlayerState(player));
      }

      /** 
       * Listens to PlayerQuitEvent - removes ElytraPlayerState for quit player
       * @param event
       */
      @EventHandler(ignoreCancelled = true)
      public void onPlayerQuit(PlayerQuitEvent event) {
            ElytraPlayerState _deletedState = playerStates.remove(event.getPlayer().getName());
            // just in case your JVM supports forced garbage collection
            _deletedState = null;
      }

      /** 
       * Listens to PlayerMoveEvent - implements ElytraThermal features
       * @param event
       */
      @EventHandler
      public void PlayerGlideEvent(final PlayerMoveEvent event) {
            final Player player = event.getPlayer();
            final String playerName = player.getName();
            if (activeThermals.contains(playerName)) return; // exit if player is currently in a thermal 
            if (player.isGliding()){
                  // update to toggle bars
                  updateElytraPlayerState(playerName);
                  
                  // handle suffocation
                  if (suffocateEnabled && player.getLocation().getY() >= suffocationHeight) {
                        player.sendMessage(ChatColor.RED + "Low Oxygen, decrease altitude!"); 
                        player.damage(suffocationDamage);
                  }
                  // handle airbrake
                  if (airbrakeEnabled && player.isSneaking()) {
                        handleAirbrakePhysics(player);
                  }
                  
                  // handle thermals
                  final boolean lift = thermalLiftEnabled && isThermalNearPlayer(player, true);
                  // prioritize lift and short circuit to reduce lag
                  if (lift || (thermalDragEnabled && isThermalNearPlayer(player, false))) {
                        if (!isAboveThreshold(player)) {
                              player.sendMessage(ChatColor.RED + "Thermal missed, increase speed!"); 
                              return;
                        }
                        activeThermals.add(playerName); // otherwise add to thermal check
                        // begin thermal
                        player.sendMessage((lift?ChatColor.GOLD:ChatColor.AQUA)+"Thermal " + (lift?"lift":"drag") + " activated!");
                        // play a sound
                        player.getWorld().playSound(player.getLocation(), thermalSound, SoundCategory.PLAYERS, 1.0F, 1.0F); 
                        // handle physics
                        handleThermalPhysics(player, lift);
                        // spawn particles
                        handleThermalParticles(player, lift);
                  }
                  
            } else {
                  // toggle in or out of glide state - update to toggle bars
                  updateElytraPlayerState(playerName);
            }
      }

      /** 
       * Handle player physics for thermal interaction
       * @param player
       * @param lift
       */
      private void handleThermalPhysics(final Player player, final boolean lift){
            // slight pull towards thermal source before push
            player.setVelocity(player.getVelocity().setY((lift ? -1 : 1)*thermalInit)); 

            new BukkitRunnable() {
                  int ticks = 5;                // spread physics over 5 ticks
                  Vector v = new Vector(0, (lift ? thermalLift : thermalDrag) / ticks, 0);
                  
                  @Override
                  public void run() {
                        player.setVelocity(player.getVelocity().add(v)); 
                        updateElytraPlayerState(player.getName());
                        if (ticks < 0) {
                              this.cancel();
                              return;
                        }
                        ticks--; // decrement ticks
                  }
            }.runTaskTimer(ElytraThermals.getPlugin(ElytraThermals.class), 1, 1);
      }

      /** 
       * Handle player physics for thermal interaction
       * @param player
       * @param lift
       */
      private void handleThermalParticles(final Player player, final boolean lift){
            new BukkitRunnable() {
                  int ticks = 0; //track ticks
                  double arc = 0; // track current arc
                  Location playerLocation, particleLocation1, particleLocation2; // location variables

                  @Override
                  public void run() {
                        playerLocation = player.getLocation(); // track player movement
                        // calculate dual helix
                        arc += Math.PI / 10;
                        // calc sin once to reduce lag
                        double sinarc = Math.sin(arc);
                        particleLocation1 = playerLocation.clone().add(Math.cos(arc), sinarc + 1, sinarc);
                        particleLocation2 = playerLocation.clone().add(Math.cos(arc + Math.PI), sinarc + 1, Math.sin(arc + Math.PI));
                        // spawn particles to world
                        Particle particleToCreate = lift ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
                        player.getWorld().spawnParticle(particleToCreate, particleLocation1, 0);
                        player.getWorld().spawnParticle(particleToCreate, particleLocation2, 0);
                        // remove player from array
                        if (ticks == thermalTickDelay || ticks > thermalParticleTicks) {
                              activeThermals.remove(player.getName());
                        }
                        if (ticks > thermalParticleTicks) {
                              this.cancel();
                              return;
                        }
                        ticks++; // increment ticks
                  }
            }.runTaskTimer(ElytraThermals.getPlugin(ElytraThermals.class), 0, 1);
      }

      /** 
       * Aerodynamic brake tick for player
       * @param playerName
       */
      private void handleAirbrakePhysics(Player player){
            // get player speed scalar
            double speed = player.getVelocity().length();
            // calculate horizontal brake coefficient
            double brake = 1-Math.max(0.0,(speed/2)*airbrakeFactor);
            // calculate vertical brake coefficient
            double brakeVert = Math.max(1.0,(brake+(airbrakeFactor/2)));
            // update player physics
            player.setVelocity(new Vector(player.getVelocity().getX()*brake, player.getVelocity().getY()*brakeVert, player.getVelocity().getZ()*brake));
      }

      /** 
       * Update ElytraPlayerState for passed playerName
       * @param playerName
       */
      private void updateElytraPlayerState(String playerName){
            // get state object
            ElytraPlayerState state = playerStates.get(playerName);
            // if exists, update
            if (state != null) state.update();
      }

      /** 
       * Determines if player glide velocity is above threshold
       * @param player
       * @return boolean
       */
      private boolean isAboveThreshold(final Player player) {
            // get player horizontal speed scalar
            double v = player.getVelocity().setY(0).length();
            // check threshold
            return v > thermalVelocity;
      }

      /** 
       * Determines if passed block is a thermal lift block
       * Ideal for a config array
       * @param block
       * @return boolean
       */
      private boolean isThermalBlock(final Block block, final boolean lift) {
            if ( (lift && thermalLiftMaterials.contains(block.getType())) || 
                 (!lift && thermalDragMaterials.contains(block.getType())) )
                  return true;
            return false;
      }

      /** 
       * Determines if the player is above or below a thermal block within distance threshold
       * @param player
       * @param lift
       * @return boolean
       */
      private boolean isThermalNearPlayer(final Player player, final boolean lift) {
            Block block;                                                      // block check variables
            Double dist = 0.0;                                                // distance check variable
            // offset player height
            Location loc = player.getLocation().subtract(0, (lift ? 0.5 : -0.5), 0); 
             // may need additional check here for void
            while((lift && loc.getY() >= 0) || (!lift && loc.getY() <= 256)) { 
                  block = loc.getBlock();
                  if (isThermalBlock(block, lift)) return true;               // short circuit main test
                  if (dist > 0) {                                             // if height is one block, include nearby blocks
                        if (isThermalBlock(loc.getBlock().getRelative(BlockFace.NORTH), lift) ||
                            isThermalBlock(loc.getBlock().getRelative(BlockFace.SOUTH), lift) ||
                            isThermalBlock(loc.getBlock().getRelative(BlockFace.EAST), lift) ||
                            isThermalBlock(loc.getBlock().getRelative(BlockFace.WEST), lift)
                        ) return true;                
                  }
                  if(block.getType() != Material.AIR) return false;           // if not air, no thermal here
                  if (lift) loc.subtract(0, 1, 0);                            // move down
                  else loc.add(0, 1, 0);                                      // move up
                  dist += 1;                                                  // add distance check
                  if (dist >= thermalDistance) return false;                  // exit if distance exceeded
            }
            return false;                                                     // default false
      }

      // /** 
      //  * Determines if the player is above a thermal lift block within distance threshold
      //  * @param player
      //  * @return boolean
      //  */
      // private boolean isThermalLiftUnderPlayer(final Player player) {
      //       if (!thermalLiftEnabled) return false;
      //       Location loc = player.getLocation().subtract(0, 0.5, 0);          // start 0.5 dist below player
      //       Block block;                                                      // block check variables
      //       Double dist = 0.0;                                                // distance check variable
      //       while(loc.getY() >= 0) {                                          // may need additional check here for void
      //             block = loc.getBlock();
      //             if (isThermalBlock(block, true)) return true;                 // short circuit main test
      //             if (dist > 0) {                                             // if height is one block, include nearby blocks
      //                   if (isThermalBlock(loc.getBlock().getRelative(BlockFace.NORTH), true) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.SOUTH), true) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.EAST), true) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.WEST), true)
      //                   ) return true;                
      //             }
      //             if(block.getType() != Material.AIR) return false;           // if not air, no thermal here
      //             loc.subtract(0, 1, 0);                                      // move down
      //             dist += 1;                                                  // add distance check
      //             if (dist >= thermalDistance) return false;                  // exit if distance exceeded
      //       }
      //       return false;                                                     // default false
      // }

      // /** 
      //  * Determines if the player is below a thermal drag block within distance threshold
      //  * @param player
      //  * @return boolean
      //  */
      // private boolean isThermalDragAbovePlayer(final Player player) {
      //       if (!thermalDragEnabled) return false;
      //       Location loc = player.getLocation().add(0, 0.5, 0);               // start 0.5 dist below player
      //       Block block;                                                      // block check variables
      //       Double dist = 0.0;                                                // distance check variable
      //       while(loc.getY() <= 256) {                                        // may need additional check here for void
      //             block = loc.getBlock();
      //             if (isThermalBlock(block, false)) return true;                 // short circuit main test
      //             if (dist > 0) {                                             // if height is one block, include nearby blocks
      //                   if (isThermalBlock(loc.getBlock().getRelative(BlockFace.NORTH), false) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.SOUTH), false) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.EAST), false) ||
      //                       isThermalBlock(loc.getBlock().getRelative(BlockFace.WEST), false)
      //                   ) return true;                
      //             }
      //             if(block.getType() != Material.AIR) return false;           // if not air, no thermal here
      //             loc.add(0, 1, 0);                                           // move down
      //             dist += 1;                                                  // add distance check
      //             if (dist >= thermalDistance) return false;                  // exit if distance exceeded
      //       }
      //       return false;                                                     // default false
      // }

}