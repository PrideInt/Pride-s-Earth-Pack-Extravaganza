package me.Pride.abilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
	
	private static final String PATH = "ExtraAbilities.Prride.Stalagmites.";
	private static final FileConfiguration CONFIG = ConfigManager.getConfig();
	
	@Attribute(Attribute.COOLDOWN)
	private static final long COOLDOWN = CONFIG.getLong(PATH + "Cooldown");
	@Attribute(Attribute.SPEED)
	private static final double SPEED = CONFIG.getDouble(PATH + "Speed");
	@Attribute(Attribute.RANGE)
	private static final double RANGE = CONFIG.getDouble(PATH + "Range");
	@Attribute("RevertTime")
	private static final long REVERT_TIME = CONFIG.getLong(PATH + "RevertTime");
	@Attribute(Attribute.KNOCKBACK)
	private static final double KNOCKBACK = CONFIG.getDouble(PATH + "Knockback");
	@Attribute(Attribute.WIDTH)
	private static final int MIN_WIDTH = CONFIG.getInt(PATH + "MinWidth"),
							 MAX_WIDTH = CONFIG.getInt(PATH + "MaxWidth");
	@Attribute(Attribute.HEIGHT)
	private static final int MIN_HEIGHT = CONFIG.getInt(PATH + "MinHeight"), 
							 MAX_HEIGHT = CONFIG.getInt(PATH + "MaxHeight");
	
	private Location origin;
	private Location location;
	private Vector direction;
	private Random rand = new Random();
	private Permission perm;
	
	private Set<Spike> spikes = new HashSet<>();

	public Stalagmites(Player player) {
		super(player);
		
		if (!bPlayer.canBendIgnoreBindsCooldowns(this)) return;
		
		if (bPlayer.isOnCooldown(this)) return;
		
		origin = player.getLocation();
		location = origin.clone();
		direction = player.getLocation().getDirection();
		
		bPlayer.addCooldown(this);
		
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
	public void progress() {
		if (!player.isOnline() || player.isDead()) {
			remove();
			return;
		}
		
		for (Iterator<Spike> itr = spikes.iterator(); itr.hasNext();) {
			Spike spike = itr.next();
			
			spike.progress();
			
			if (spike.outOfRange()) {
				itr.remove();
			}
		}
		progressStalagmites();
	}
	
	private void progressStalagmites() {
		location.add(direction.multiply(SPEED));
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

		if (!TempBlock.isTempBlock(top)) location.setY(top.getY() + 1);
		
		if (location.distanceSquared(origin) > RANGE * RANGE) {
			remove();
			return;
		}
		
		trackEntities(location, e -> damage(e, 1, this));
		
		if (rand.nextInt(8) == 0) player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5F, 1F);
		
		Material ground = location.getBlock().getRelative(BlockFace.DOWN).getType();
		
		if (isLavabendable(location.getBlock())) {
			ground = Material.LAVA;
		}
		
		spikes.add(new Spike(this, location, getMaterial(ground) == null ? ground : getMaterial(ground)));
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
	
	@Override
	public boolean isHiddenAbility() {
		return !ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.Stalagmites.Enabled");
	}
	
	public class Spike {
		
		private double maxLength;
		private boolean aroundEntities;
		
		private CoreAbility ability;
		private Location location, origin, destination;
		private Vector direction;
		private Random rand = new Random();
		private Material material;
		
		public Spike(CoreAbility ability, Location location, Material material) {
			this.ability = ability;
			this.material = material;
			this.origin = location;
			this.location = origin.clone();
			this.destination = generateRandomDest(origin);
			this.direction = GeneralMethods.getDirection(origin, destination);
		}
		
		public void progress() {
			location.add(direction.normalize().multiply(1));
			if (isTransparent(location.getBlock()) && !TempBlock.isTempBlock(location.getBlock())) {
				new TempBlock(location.getBlock(), material.createBlockData(), REVERT_TIME);
			}
			ParticleEffect.BLOCK_CRACK.display(location, 10, 0.8F, 0.8F, 0.8F, 0F, material.createBlockData());
			
			if (trackEntities(location, e -> {
				if (isLavabendable(origin.getBlock()) || isLava(material) || material == Material.MAGMA_BLOCK) {
					e.setFireTicks(50);
				}
				damage(e, 2, ability, true, destination, KNOCKBACK);
			})) {
				aroundEntities = true;
			}
			
			if (rand.nextInt(12) == 0) playEarthbendingSound(location);
			
			blockAbilities();
		}
		
		private Location generateRandomDest(Location origin) {
			double x = (double) rand.nextInt((MAX_WIDTH + MIN_WIDTH) + 1) - MIN_WIDTH;
			double y = (double) rand.nextInt((MAX_HEIGHT - MIN_HEIGHT) + 1) + MIN_HEIGHT;
			double z = (double) rand.nextInt((MAX_WIDTH + MIN_WIDTH) + 1) - MIN_WIDTH;
			
			Location point = origin.clone();
			
			double[] xyz = {x, y, z};
			
			double max = Double.MIN_VALUE;
			
			for (double n : xyz) {
				if (n > max) {
					max = n;
				}
			}
			
			maxLength = max;
			
			return point.add(x, y, z);
		}
		
		public boolean outOfRange() {
			return location.distance(origin) > maxLength;
		}
		
		public boolean aroundEntities() {
			return aroundEntities;
		}
		
		public Location getLocation() {
			return location;
		}
	}
	
	public boolean trackEntities(Location location, Consumer<LivingEntity> consumer) {
		if (location == null) return false;
		
		List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, 1.1);
		
		Optional<LivingEntity> entity = entities.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof LivingEntity).map(e -> (LivingEntity) e).findFirst();
		
		if (entity.isPresent()) {
			consumer.accept(entity.orElse(null));
			return true;
		}
		return false;
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
		CoreAbility stal = CoreAbility.getAbility(Stalagmites.class);
		
		// small
		CoreAbility eb = CoreAbility.getAbility(EarthBlast.class),
					fb = CoreAbility.getAbility(FireBlast.class),
					ab = CoreAbility.getAbility(AirBlast.class),
					as = CoreAbility.getAbility(AirSwipe.class),
					wm = CoreAbility.getAbility(WaterManipulation.class);
		
		CoreAbility[] small = {eb, fb, ab, as, wm};
		
		for (CoreAbility smal : small) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(stal, smal, false, true));
		}
	}
	
	@Override
	public double getCollisionRadius() {
		return 1.25;
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
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.Stalagmites.Enabled", true);
	}

}
