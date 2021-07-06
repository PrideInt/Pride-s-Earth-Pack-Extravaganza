package me.Pride.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.airbending.AirBlast;
import com.projectkorra.projectkorra.airbending.AirSwipe;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.EarthBlast;
import com.projectkorra.projectkorra.earthbending.EarthSmash;
import com.projectkorra.projectkorra.firebending.FireBlast;
import com.projectkorra.projectkorra.firebending.lightning.Lightning;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.waterbending.SurgeWall;
import com.projectkorra.projectkorra.waterbending.SurgeWave;
import com.projectkorra.projectkorra.waterbending.Torrent;
import com.projectkorra.projectkorra.waterbending.WaterManipulation;

import me.Pride.loader.Loader;
import net.md_5.bungee.api.ChatColor;

public class SandSurge extends SandAbility implements AddonAbility {
	
	private static final String PATH = "ExtraAbilities.Prride.SandSurge.";
	private static final FileConfiguration CONFIG = ConfigManager.getConfig();
	
	@Attribute(Attribute.COOLDOWN)
	private static final long COOLDOWN = CONFIG.getLong(PATH + "Cooldown");
	@Attribute(Attribute.SELECT_RANGE)
	private static final double SELECT_RANGE = CONFIG.getDouble(PATH + "SelectRange");
	@Attribute(Attribute.WIDTH)
	private static final int WIDTH = CONFIG.getInt(PATH + "SurgeWidth");
	@Attribute(Attribute.HEIGHT)
	private static final double LAUNCH = CONFIG.getDouble(PATH + "Launch");
	@Attribute(Attribute.SPEED)
	private static final double LAUNCH_SPEED = CONFIG.getDouble(PATH + "LaunchSpeed");
	@Attribute(Attribute.DAMAGE)
	private static final double DAMAGE = CONFIG.getDouble(PATH + "Damage");
	private static final int BLIND_DURATION = CONFIG.getInt(PATH + "BlindDuration"),
							 BLIND_AMPLIFIER = CONFIG.getInt(PATH + "BlindAmplifier");
	@Attribute(Attribute.SPEED)
	private static final double THROW_SPEED = CONFIG.getDouble(PATH + "ThrowSpeed");
	@Attribute("CollisionRadius")
	private static final double COLLISION_RADIUS = CONFIG.getDouble(PATH + "CollisionRadius");
	
	private int ct = 0;
	private boolean largeAbil;
	private double time;
	
	private Block target;
	private Random random = new Random();
	
	private List<FallingBlock> fallBlocks = new CopyOnWriteArrayList<>();
	
	public SandSurge(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this) || !bPlayer.canSandbend()) return;
		
		target = getEarthSourceBlock(SELECT_RANGE);
		
		if (target == null) return;
		
		if (!isSandbendable(target)) return;
		
		line(target, b -> surge(b, b.getLocation().add(0, 1, 0)));
		playEarthbendingSound(player.getLocation());
		
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
		for (FallingBlock fb : fallBlocks) {
			locations.add(fb.getLocation());
		}
		return locations;
	}

	@Override
	public String getName() {
		return "SandSurge";
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
		
		for (FallingBlock fb : fallBlocks) {
			if (random.nextInt(3) == 0) {
				displaySandParticle(fb.getLocation(), 1, 0.25F, 0.25F, 0.25F, 0F, isRedSand(fb.getBlockData().getMaterial()));
			}
			
			if (fb.isOnGround()) {
				fb.remove();
				fallBlocks.remove(fb);
			}
			
			List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(fb.getLocation(), 1);
			entities.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof LivingEntity).map(e -> (LivingEntity) e).forEach(e -> {
				DamageHandler.damageEntity(e, DAMAGE, this);
				
				if (!e.hasPotionEffect(PotionEffectType.BLINDNESS)) {
					e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_DURATION, BLIND_AMPLIFIER));
				}
			});
		}
		
		blockAbilities();
		
		if (fallBlocks.isEmpty()) {
			remove();
			return;
		}
		
		if (ct > 0) {
			time += 0.05;
			
			if (time >= 10) {
				remove();
				return;
			}
		}
	}
	
	// from RaiseEarthWall
	private void line(Block target, Consumer<Block> blocks) {
		final Vector direction = player.getEyeLocation().getDirection().normalize();
		double ox, oy, oz;
		direction.setY(0);
		ox = -direction.getZ();
		oy = 0;
		oz = direction.getX();

		Vector orth = new Vector(ox, oy, oz);
		orth = orth.normalize();
		orth = getDegreeRoundedVector(orth, 0.25);
		
		for (int i = 0; i < WIDTH; i++) {
			final double adjustedI = i - WIDTH / 2.0;
			Block block = target.getWorld().getBlockAt(target.getLocation().clone().add(orth.clone().multiply(adjustedI)));
			Block top = GeneralMethods.getTopBlock(block.getLocation(), 3);
			
			if (isSandbendable(top) && !GeneralMethods.isRegionProtectedFromBuild(player, "SandSurge", top.getLocation())) {
				blocks.accept(top);
			}
		}
	}
	
	// from RaiseEarthWall
	private Vector getDegreeRoundedVector(Vector vec, final double degreeIncrement) {
		if (vec == null) {
			return null;
		}
		vec = vec.normalize();
		final double[] dims = { vec.getX(), vec.getY(), vec.getZ() };

		for (int i = 0; i < dims.length; i++) {
			final double dim = dims[i];
			final int sign = dim >= 0 ? 1 : -1;
			final int dimDivIncr = (int) (dim / degreeIncrement);

			final double lowerBound = dimDivIncr * degreeIncrement;
			final double upperBound = (dimDivIncr + (1 * sign)) * degreeIncrement;

			if (Math.abs(dim - lowerBound) < Math.abs(dim - upperBound)) {
				dims[i] = lowerBound;
			} else {
				dims[i] = upperBound;
			}
		}
		return new Vector(dims[0], dims[1], dims[2]);
	}
	
	private void surge(Block block, Location location) {
		BlockData data = getMaterial(block.getType()).createBlockData();
		double y = LAUNCH;
		double speed = LAUNCH_SPEED;
		
		while (y > 0) {
			double x = random.nextDouble();
			double z = random.nextDouble();

			x = (random.nextBoolean()) ? x : -x;
			z = (random.nextBoolean()) ? z : -z;
			
			y--;
			speed -= 0.15;
			
			FallingBlock fb = player.getWorld().spawnFallingBlock(location, data);
			fb.setVelocity(new Vector((x >= 1 ? 0.25 : x), y, (z >= 1 ? 0.25 : z)).normalize().multiply(speed > 0 ? speed : 0.1));
			fb.setDropItem(false);
			
			fallBlocks.add(fb);
			Loader.fallingBlocks.add(fb);
		}
	}
	
	public void throwSurge() {
		if (ct < 1) {
			playEarthbendingSound(player.getLocation());
			
			for (FallingBlock fb : fallBlocks) {
				fb.setVelocity(player.getEyeLocation().getDirection().multiply(THROW_SPEED));
			}
		}
		ct++;
	}
	
	private Material getMaterial(Material material) {
		switch (material) {
			case SAND:
				return Material.SANDSTONE;
			case RED_SAND:
				return Material.RED_SANDSTONE;
			default:
				if (material.toString().contains("RED")) {
					return Material.RED_SAND;
				} else {
					return Material.SAND;
				}
		}
	}
	
	private boolean isRedSand(Material material) {
		switch (material) {
			case RED_SAND:
				return true;
			case CHISELED_RED_SANDSTONE:
				return true;
			case CUT_RED_SANDSTONE:
				return true;
			case CUT_RED_SANDSTONE_SLAB:
				return true;
			case RED_SANDSTONE:
				return true;
			case SMOOTH_RED_SANDSTONE:
				return true;
		}
		return false;
	}
	
	private void blockAbilities() {
		// main
		CoreAbility main = CoreAbility.getAbility(SandSurge.class);
		
		// small
		CoreAbility eb = CoreAbility.getAbility(EarthBlast.class),
					fb = CoreAbility.getAbility(FireBlast.class),
					ab = CoreAbility.getAbility(AirBlast.class),
					as = CoreAbility.getAbility(AirSwipe.class),
					wm = CoreAbility.getAbility(WaterManipulation.class);
		
		// large
		CoreAbility esmash = CoreAbility.getAbility(EarthSmash.class),
					li = CoreAbility.getAbility(Lightning.class),
					tor = CoreAbility.getAbility(Torrent.class),
					surwave = CoreAbility.getAbility(SurgeWave.class),
					surwall = CoreAbility.getAbility(SurgeWall.class);
	
		CoreAbility[] small = {eb, fb, ab, as, wm},
					  large = {esmash, li, tor, surwave, surwall};
		
		for (CoreAbility smal : small) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, smal, false, true));
		}
		for (CoreAbility lar : large) {
			largeAbil = true;
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, lar, false, false));
		}
	}
	
	public List<FallingBlock> getFallingBlocks() {
		return fallBlocks;
	}
	
	public int getThrow() {
		return ct;
	}
	
	@Override
	public double getCollisionRadius() {
		return COLLISION_RADIUS;
	}
	
	@Override
	public void handleCollision(Collision collision) {
		super.handleCollision(collision);
		
		Location large = collision.getLocationSecond();
		
		if (largeAbil) {
			List<Entity> fallingBlocks = GeneralMethods.getEntitiesAroundPoint(large, 2.5);
			fallingBlocks.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof FallingBlock).map(e -> (FallingBlock) e).forEach(e -> {
				fallBlocks.remove(e);
			});
		}
	}
	
	@Override
	public void remove() {
		bPlayer.addCooldown(this);
		
		super.remove();
	}

	@Override
	public String getAuthor() {
		return Loader.getAuthor(Element.SAND);
	}

	@Override
	public String getVersion() {
		return Loader.getVersion(Element.SAND);
	}
	
	@Override
	public String getDescription() {
		return "Both a defensive and offensive ability, this attack involves creating a surge of sand from any nearby sand source. In its initial stage, "
				+ "it acts as a barrier that can be used to defend against basic attacks. In its secondary stage, it acts as a large wave of sand that "
				+ "can damage and blind an opponent when launched.";
	}
	
	@Override
	public String getInstructions() {
		return ChatColor.GOLD + "Tap shift at a sand source to form a sand barrier. Left click at any given time before the sand falls to launch "
				+ "the sand at any given location.";
	}

	@Override
	public void load() {
		
	}

	@Override
	public void stop() {
		
	}

}
