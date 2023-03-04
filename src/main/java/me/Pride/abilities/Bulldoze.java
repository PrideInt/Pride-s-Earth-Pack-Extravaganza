package me.Pride.abilities;

import java.util.List;
import java.util.Random;

import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ActionBar;
import com.projectkorra.projectkorra.util.TempBlock;

import me.Pride.loader.Loader;
import net.md_5.bungee.api.ChatColor;

public class Bulldoze extends EarthAbility implements AddonAbility {
	
	private final String path = "ExtraAbilities.Prride.Bulldoze.";
	private final FileConfiguration config = ConfigManager.getConfig();
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute(Attribute.RANGE)
	private double maxRange;
	@Attribute(Attribute.RADIUS)
	private double radius;
	private boolean revert;
	@Attribute("RevertTime")
	private long revertTime;
	private boolean changeable;

	private double increment;
	private double range;
	private boolean advanced;
	
	private Block target;
	private Location location;
	private Vector direction;
	private Random rand = new Random();
	
	private Listener listener;

	public Bulldoze(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
			return;
		}
		this.cooldown = config.getLong(path + "Cooldown");
		this.speed = config.getDouble(path + "BulldozeSpeed");
		this.selectRange = config.getDouble(path + "SelectRange");
		this.maxRange = config.getDouble(path + "MaxRange");
		this.radius = config.getDouble(path + "BulldozeRadius");
		this.revertTime = config.getLong(path + "RevertTime");
		this.changeable = config.getBoolean(path + "Changeable");
		this.increment = config.getDouble(path + "RangeIncrement");
		this.revert = config.getBoolean(path + "RevertBlocks");

		this.target = getEarthSourceBlock(range);
		
		if (target == null) {
			return;
		}
		this.location = target.getLocation();
		
		playEarthbendingSound(location);
		
		start();
	}

	@Override
	public long getCooldown() {
		return cooldown;
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
			target = getEarthSourceBlock(selectRange);
			
			if (target == null || !isEarthbendable(target)) {
				remove();
				return;
			} else if (target != null && RegionProtection.isRegionProtected(player, target.getLocation(), this)) {
				remove();
				return;
			}
			location = target.getLocation();
			direction = player.getEyeLocation().getDirection();
			
			if (range < maxRange) range += increment;
			
			ActionBar.sendActionBar(Element.EARTH.getColor() + "RANGE: " + (int) range, player);
		}
		
		if (!player.isSneaking() || advanced) {
			bulldoze();
		}
	}
	
	private void bulldoze() {
		advanced = true;
		
		if (changeable) {
			direction = player.getEyeLocation().getDirection();
		}
		location.add(direction.normalize().multiply(speed));
		
		if (RegionProtection.isRegionProtected(player, location, this)) {
			remove();
			return;
		}
		if (rand.nextInt(4) == 0) {
			player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 0F);
			player.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 0F);
		}
		
		List<Block> blocks = GeneralMethods.getBlocksAroundPoint(location, radius);
		
		blocks.stream().filter(b -> isEarthbendable(b) || isEarth(b) && !RegionProtection.isRegionProtected(player, b.getLocation(), this)).forEach(b -> {
			if (revert) {
				new TempBlock(b, Material.AIR.createBlockData(), revertTime);
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
