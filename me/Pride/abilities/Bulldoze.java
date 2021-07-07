package me.Pride.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ActionBar;
import com.projectkorra.projectkorra.util.TempBlock;

import me.Pride.loader.Loader;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Bulldoze extends EarthAbility implements AddonAbility {
	
	private static final String PATH = "ExtraAbilities.Prride.Bulldoze.";
	private static final FileConfiguration CONFIG = ConfigManager.getConfig();
	
	@Attribute(Attribute.COOLDOWN)
	private static final long COOLDOWN = CONFIG.getLong(PATH + "Cooldown");
	@Attribute(Attribute.SPEED)
	private static final double SPEED = CONFIG.getDouble(PATH + "BulldozeSpeed");
	@Attribute(Attribute.SELECT_RANGE)
	private static final double SELECT_RANGE = CONFIG.getDouble(PATH + "SelectRange");
	@Attribute(Attribute.RANGE)
	private static final double MAX_RANGE = CONFIG.getDouble(PATH + "MaxRange");
	@Attribute(Attribute.RADIUS)
	private static final double RADIUS = CONFIG.getDouble(PATH + "BulldozeRadius");
	private static final boolean REVERT = CONFIG.getBoolean(PATH + "Revert");
	@Attribute("RevertTime")
	private static final long REVERT_TIME = CONFIG.getLong(PATH + "RevertTime");
	private static final boolean CHANGEABLE = CONFIG.getBoolean(PATH + "Changeable");

	private double INCREMENT = CONFIG.getDouble(PATH + "RangeIncrement");
	private double range;
	private boolean advanced;
	
	private Block target;
	private Location location;
	private Vector direction;
	private Random rand = new Random();
	
	private Listener listener;

	public Bulldoze(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) return;
		
		target = getEarthSourceBlock(SELECT_RANGE);
		
		if (target == null) return;
		
		if (GeneralMethods.isRegionProtectedFromBuild(player, "Bulldoze", player.getLocation())) return;
		
		location = target.getLocation();
		
		playEarthbendingSound(location);
		
		start();
	}

	@Override
	public long getCooldown() {
		return COOLDOWN;
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public String getName() {
		return "Bulldoze";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead()) {
			remove();
			return;
		}
		
		if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
			remove();
			return;
		}
		
		if (player.isSneaking() && !advanced) {
			target = getEarthSourceBlock(SELECT_RANGE);
			
			if (target == null || !isEarthbendable(target)) {
				remove();
				return;
			}
			
			if (target != null) {
				if (GeneralMethods.isRegionProtectedFromBuild(player, "Bulldoze", target.getLocation())) {
					remove();
					return;
				}
			}
			
			location = target.getLocation();
			direction = player.getEyeLocation().getDirection();
			
			if (range < MAX_RANGE) range += INCREMENT;
			
			ActionBar.sendActionBar(Element.EARTH.getColor() + "RANGE: " + (int) range, player);
		}
		
		if (!player.isSneaking() || advanced) {
			bulldoze();
		}
	}
	
	private void bulldoze() {
		advanced = true;
		
		if (CHANGEABLE) {
			direction = player.getEyeLocation().getDirection();
		}
		
		location.add(direction.normalize().multiply(SPEED));
		
		if (GeneralMethods.isRegionProtectedFromBuild(player, "Bulldoze", location)) {
			remove();
			return;
		}
		
		if (rand.nextInt(4) == 0) {
			player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 0F);
			player.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 0F);
		}
		
		List<Block> blocks = GeneralMethods.getBlocksAroundPoint(location, RADIUS);
		
		blocks.stream().filter(b -> isEarthbendable(b) || isEarth(b) && !GeneralMethods.isRegionProtectedFromBuild(player, "Bulldoze", b.getLocation())).forEach(b -> { 
			if (REVERT) { 
				new TempBlock(b, Material.AIR.createBlockData(), REVERT_TIME);
			} else { 
				b.setType(Material.AIR); 
			}});
		
		if (target.getLocation().distanceSquared(location) > range * range) {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
	}

	@Override
	public String getAuthor() {
		return Loader.getAuthor(Element.EARTH);
	}

	@Override
	public String getVersion() {
		return Loader.getVersion(Element.EARTH);
	}
	
	@Override
	public String getDescription() {
		return "A simple earth ability that allows the earthbender to quickly and rapidly push all earth in order to tunnel through the ground. "
				+ "Used when Iroh and Aang were travelling through the crystal catacombs, this ability allows for quick transportation underground.";
	}
	
	@Override
	public String getInstructions() {
		return ChatColor.GOLD + "Hold sneak to select a tunnel range. Release to form the tunnel.";
	}

	@Override
	public void load() {
		ProjectKorra.log.info(ChatColor.GREEN + "Earth abilities by Prride are loaded in!");
		listener = new Loader();
		ProjectKorra.plugin.getServer().getPluginManager().registerEvents(listener, ProjectKorra.plugin);
		
		FileConfiguration config = ConfigManager.getConfig();
		
		// Bulldoze
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.BulldozeSpeed", 1.1);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.BulldozeRadius", 2.35);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.SelectRange", 10);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.MaxRange", 20);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.RangeIncrement", 0.2);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Changeable", false);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Revert", true);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.RevertTime", 30000);
		
		// RockWrecker
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Cooldown", 6000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.FormTime.Prime", 3000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.FormTime.Full", 6500);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.SelectRange", 18);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Speed", 1.1);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.StartingDamage", 2);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.PrimeDamage", 3);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.FullDamage", 4);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Knockback", 3.5);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Revert.Revert", true);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Revert.RevertTime", 12000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.CreateLavaPool", true);
		
		// Stalagmites
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Speed", 1.1);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.RevertTime", 7000);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Knockback", 1.2);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MinWidth", 5);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MaxWidth", 5);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MinHeight", 0);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MaxHeight", 6);
		
		// MetalStrips
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Cooldown", 12000);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Range", 18);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Speed", 1.2);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.StripTime", 20000);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.MagnetizeRadius", 6);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.SelectRange.Target", 12);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.SelectRange.Entity", 6);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.MagnetizeSpeed", 0.65);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.DecreaseArmor", 10);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.BlockDamageRadius", 1.35);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Revert", true);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.RevertTime", 20000);
		
		List<String> metalMaterials = new ArrayList<>();
		metalMaterials.add(Material.IRON_INGOT.toString());
		metalMaterials.add(Material.IRON_NUGGET.toString());
		
		config.addDefault("ExtraAbilities.Prride.MetalStrips.MetalMaterials", metalMaterials);
		
		// SandSurge
		config.addDefault("ExtraAbilities.Prride.SandSurge.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.SandSurge.SelectRange", 12);
		config.addDefault("ExtraAbilities.Prride.SandSurge.SurgeWidth", 6);
		config.addDefault("ExtraAbilities.Prride.SandSurge.Launch", 8);
		config.addDefault("ExtraAbilities.Prride.SandSurge.LaunchSpeed", 1.2);
		config.addDefault("ExtraAbilities.Prride.SandSurge.Damage", 1.5);
		config.addDefault("ExtraAbilities.Prride.SandSurge.BlindDuration", 75);
		config.addDefault("ExtraAbilities.Prride.SandSurge.BlindAmplifier", 1);
		config.addDefault("ExtraAbilities.Prride.SandSurge.ThrowSpeed", 1.1);
		config.addDefault("ExtraAbilities.Prride.SandSurge.CollisionRadius", 1.3);
		
		ConfigManager.defaultConfig.save();
	}

	@Override
	public void stop() {
		HandlerList.unregisterAll(listener);
		
		for (FallingBlock fb : Loader.fallingBlocks) {
			fb.remove();
		}
		Loader.fallingBlocks.clear();
	}

}
