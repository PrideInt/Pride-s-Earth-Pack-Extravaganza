package me.Pride.abilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.airbending.AirBlast;
import com.projectkorra.projectkorra.airbending.AirSwipe;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.EarthBlast;
import com.projectkorra.projectkorra.firebending.FireBlast;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.WaterManipulation;

import me.Pride.loader.Loader;
import net.md_5.bungee.api.ChatColor;

public class Stalagmites extends EarthAbility implements AddonAbility, ComboAbility {
	
	private final String path = "ExtraAbilities.Prride.Stalagmites.";
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.RANGE)
	private double range;
	
	private Location origin;
	private Location location;
	private Vector direction;
	private Permission perm;
	
	private Set<Spike> spikes = new HashSet<>();

	public Stalagmites(Player player) {
		super(player);
		
		if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
			return;
		} else if (bPlayer.isOnCooldown(this)) {
			return;
		} else if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) {
			return;
		}
		origin = player.getLocation().clone();
		location = origin.clone();
		direction = player.getLocation().getDirection();
		
		bPlayer.addCooldown(this);
		
		blockAbilities();
		start();
	}

	@Override
	public void progress() {
		for (Iterator<Spike> itr = spikes.iterator(); itr.hasNext();) {
			Spike spike = itr.next();
			
			spike.progress();
			
			if (spike.cleanup()) {
				itr.remove();
			}
		}
		progressStalagmites();
	}
	
	private void progressStalagmites() {
		location.add(direction.multiply(this.speed));
		direction.setY(0);
		
		ParticleEffect.BLOCK_CRACK.display(location, 10, 0F, 0F, 0F, 0F, location.getBlock().getRelative(BlockFace.DOWN).getType().createBlockData());
		
		// from EarthGrab
		Block top = GeneralMethods.getTopBlock(location, 2);

		if (!isTransparent(top.getRelative(BlockFace.UP))) {
			remove();
			return;
		}
		if (!isEarthbendable(top)) {
			Block under = top.getRelative(BlockFace.DOWN);

			if (isTransparent(top) && isEarthbendable(under)) {
				if (!TempBlock.isTempBlock(under)) {
					top = under;
				}
			} else {
				remove();
				return;
			}
		}
		if (!TempBlock.isTempBlock(top)) {
			location.setY(top.getY() + 1);
		}
		
		if (location.distanceSquared(origin) > this.range * this.range) {
			remove();
			return;
		}
		trackEntities(location, e -> damage(e, 1, this));
		
		if (ThreadLocalRandom.current().nextInt(8) == 0) {
			player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5F, 1F);
		}
		Material ground = location.getBlock().getRelative(BlockFace.DOWN).getType();
		if (isLavabendable(location.getBlock())) {
			ground = Material.LAVA;
		}
		spikes.add(new Spike(this, location, getMaterial(ground) == null ? ground : getMaterial(ground)));
	}
	
	@Override
	public long getCooldown() {
		return this.cooldown;
	}
	
	@Override
	public Location getLocation() {
		return null;
	}
	
	@Override
	public List<Location> getLocations() {
		List<Location> locations = new ArrayList<>();
		for (Spike spike : spikes) {
			locations.add(spike.getLocation());
		}
		return locations;
	}
	
	@Override
	public String getName() {
		return "Stalagmites";
	}
	
	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
	
	@Override
	public boolean isSneakAbility() {
		return false;
	}
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.Stalagmites.Enabled", true);
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
		return "This ability involves the practice of creating irregular formations of earth in the form of spikes that shoot out from the ground "
				+ "to unpredictably attack any nearby victims. Any person or entity caught in this line, or, lines of attacks will be damaged and "
				+ "launched in that respective direction.";
	}
	
	@Override
	public String getInstructions() {
		return ChatColor.GOLD + "EarthSmash (Tap Shift) > EarthBlast (Left Click Twice)";
	}
	
	public class Spike {
		private CoreAbility ability;
		private Location location, origin, destination;
		private Vector direction;
		private Material material;
		
		@Attribute(Attribute.DAMAGE)
		private double damage;
		@Attribute(Attribute.SPEED)
		private double speed;
		@Attribute("RevertTime")
		private long revert_time;
		@Attribute(Attribute.KNOCKBACK)
		private double knockback;
		@Attribute(Attribute.WIDTH)
		private int min_width, max_width;
		@Attribute(Attribute.HEIGHT)
		private int min_height, max_height;
		
		private double maxLength;
		private boolean cleanup;
		
		public Spike(CoreAbility ability, Location location, Material material) {
			this.ability = ability;
			this.origin = location.clone();
			this.material = material;
			
			this.location = this.origin.clone();
			this.destination = generateRandomDest(origin);
			this.direction = GeneralMethods.getDirection(origin, destination);
			
			this.damage = ConfigManager.getConfig().getDouble(path + "Damage");
			this.speed = ConfigManager.getConfig().getDouble(path + "Speed");
			this.revert_time = ConfigManager.getConfig().getLong(path + "RevertTime");
			this.knockback = ConfigManager.getConfig().getDouble(path + "Knockback");
			this.min_width = ConfigManager.getConfig().getInt(path + "MinWidth");
			this.max_width = ConfigManager.getConfig().getInt(path + "MaxWidth");
			this.min_height = ConfigManager.getConfig().getInt(path + "MinHeight");
			this.max_height = ConfigManager.getConfig().getInt(path + "MaxHeight");
			
			playEarthbendingSound(location);
		}
		
		public void progress() {
			this.location.add(this.direction.normalize().multiply(this.speed));
			
			if (this.location.distanceSquared(this.origin) > this.maxLength * this.maxLength) {
				this.cleanup = true;
			}
			if (isTransparent(this.location.getBlock()) && !TempBlock.isTempBlock(this.location.getBlock())) {
				new TempBlock(this.location.getBlock(), this.material.createBlockData(), this.revert_time);
			}
			ParticleEffect.BLOCK_CRACK.display(this.location, 10, 0.8F, 0.8F, 0.8F, 0F, this.material.createBlockData());
			
			trackEntities(this.location, e -> {
				if (isLavabendable(this.origin.getBlock()) || isLava(this.material) || this.material == Material.MAGMA_BLOCK) {
					e.setFireTicks(50);
				}
				damage(e, this.damage, this.ability, true, this.destination, this.knockback);
				this.cleanup = true;
			});
		}
		
		private Location generateRandomDest(Location origin) {
			double x = ThreadLocalRandom.current().nextInt(this.min_width, this.max_width);
			double y = ThreadLocalRandom.current().nextInt(this.min_height, this.max_height);
			double z = ThreadLocalRandom.current().nextInt(this.min_width, this.max_width);
			x = ThreadLocalRandom.current().nextBoolean() ? x : -x;
			z = ThreadLocalRandom.current().nextBoolean() ? z : -z;
			
			Location point = origin.clone();
			
			double[] xyz = {x, y, z};
			double max = 0;
			
			for (double n : xyz) {
				if (Math.abs(n) > max) {
					max = Math.abs(n);
				}
			}
			this.maxLength = max;
			
			return point.add(x, y, z);
		}
		public Location getLocation() { return this.location; }
		public boolean cleanup() { return this.cleanup; }
	}
	
	public void trackEntities(Location location, Consumer<LivingEntity> consumer) {
		List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, 1.1);
		Optional<LivingEntity> entity = entities.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof LivingEntity).map(e -> (LivingEntity) e).findFirst();
		
		consumer.accept(entity.get());
	}
	
	public void damage(LivingEntity entity, double damage, CoreAbility ability, boolean applyVelocity, Location destination, double knockback) {
		DamageHandler.damageEntity(entity, damage, ability);
		
		if (applyVelocity) {
			final Vector travelVec = GeneralMethods.getDirection(location, destination);
			entity.setVelocity(travelVec.normalize().multiply(knockback));
		}
	}
	
	public void damage(LivingEntity entity, double damage, CoreAbility ability) {
		damage(entity, damage, ability, false, null, 0);
	}
	
	public Material getMaterial(Material material) {
		switch (material) {
			case SAND:
				return Material.SANDSTONE;
			case RED_SAND:
				return Material.RED_SANDSTONE;
			case GRASS_BLOCK:
				return Material.DIRT;
			case PODZOL:
				return Material.DIRT;
			case GRAVEL:
				return Material.STONE;
			case LAVA:
				return Material.MAGMA_BLOCK;
		}
		return null;
	}
	
	private void blockAbilities() {
		// main
		CoreAbility main = CoreAbility.getAbility(Stalagmites.class);
		
		// small
		CoreAbility eblast = CoreAbility.getAbility(EarthBlast.class),
					fblast = CoreAbility.getAbility(FireBlast.class),
					ablast = CoreAbility.getAbility(AirBlast.class),
					aswipe = CoreAbility.getAbility(AirSwipe.class),
					wmanip = CoreAbility.getAbility(WaterManipulation.class);
		
		CoreAbility[] smallAbilities = { eblast, fblast, ablast, aswipe, wmanip };
		
		for (CoreAbility small : smallAbilities) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, small, false, true));
		}
	}
	
	@Override
	public double getCollisionRadius() {
		return 1.15;
	}
	
	@Override
	public void handleCollision(Collision collision) {
		super.handleCollision(collision);
	}
	
	@Override
	public Object createNewComboInstance(Player player) {
		return new Stalagmites(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> combo = new ArrayList<>();
		combo.add(new AbilityInformation("EarthSmash", ClickType.SHIFT_DOWN));
		combo.add(new AbilityInformation("EarthSmash", ClickType.SHIFT_UP));
		combo.add(new AbilityInformation("EarthBlast", ClickType.LEFT_CLICK));
		combo.add(new AbilityInformation("EarthBlast", ClickType.LEFT_CLICK));
		return combo;
	}

	@Override
	public void load() {
		perm = new Permission("bending.combo.stalagmites");
		perm.setDefault(PermissionDefault.OP);
		ProjectKorra.plugin.getServer().getPluginManager().addPermission(perm);
	}

	@Override
	public void stop() {
		ProjectKorra.plugin.getServer().getPluginManager().removePermission(perm);
	}
}
