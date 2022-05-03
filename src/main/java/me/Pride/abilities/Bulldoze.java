package me.Pride.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
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
		
		target = getEarthSourceBlock(range);
		
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
	public void load() { }

	@Override
	public void stop() { }
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.Bulldoze.Enabled", true);
	}

}
