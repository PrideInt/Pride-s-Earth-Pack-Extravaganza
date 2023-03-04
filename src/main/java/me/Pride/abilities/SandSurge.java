package me.Pride.abilities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
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
	
	private final String path = "ExtraAbilities.Prride.SandSurge.";
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.SELECT_RANGE)
	private double select_range;
	@Attribute(Attribute.WIDTH)
	private int width;
	@Attribute(Attribute.HEIGHT)
	private double launch;
	@Attribute(Attribute.SPEED)
	private double launch_speed;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	private int blind_duration;
	private int blind_amplifier;
	@Attribute(Attribute.SPEED)
	private double throw_speed;
	@Attribute("CollisionRadius")
	private double collision_radius;
	
	private int throwCount;
	private boolean removeAfterTime;
	private long time;
	
	private Block target;
	private List<FallingBlock> fallingBlocks = new ArrayList<>();
	
	public SandSurge(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (!bPlayer.canSandbend()) {
			return;
		} else if (RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.select_range = ConfigManager.getConfig().getDouble(path + "SelectRange");
		this.width = ConfigManager.getConfig().getInt(path + "SurgeWidth");
		this.launch = ConfigManager.getConfig().getDouble(path + "Launch");
		this.launch_speed = ConfigManager.getConfig().getDouble(path + "LaunchSpeed");
		this.damage = ConfigManager.getConfig().getDouble(path + "Damage");
		this.blind_duration = ConfigManager.getConfig().getInt(path + "BlindDuration");
		this.blind_amplifier = ConfigManager.getConfig().getInt(path + "BlindAmplifier");
		this.throw_speed = ConfigManager.getConfig().getDouble(path + "ThrowSpeed");
		this.collision_radius = ConfigManager.getConfig().getDouble(path + "CollisionRadius");
		
		this.target = getEarthSourceBlock(this.select_range);
		
		if (this.target == null) {
			return;
		} else if (RegionProtection.isRegionProtected(player, this.target.getLocation(), this)) {
			return;
		} else if (!isSandbendable(target)) {
			return;
		}
		line(target, b -> surge(b, b.getLocation().add(0, 1, 0)));
		playEarthbendingSound(player.getLocation());
		
		start();
	}

	@Override
	public void progress() {
		for (Iterator<FallingBlock> itr = fallingBlocks.iterator(); itr.hasNext();) {
			FallingBlock fallingBlock = itr.next();

			if (fallingBlock.isOnGround()) {
				itr.remove();
			} else {
				if (ThreadLocalRandom.current().nextInt(3) == 0) {
					displaySandParticle(fallingBlock.getLocation(), 1, 0.25F, 0.25F, 0.25F, 0F, isRedSand(fallingBlock.getBlockData().getMaterial()));
				}
				List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(fallingBlock.getLocation(), 1);
				entities.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof LivingEntity).map(e -> (LivingEntity) e).forEach(e -> {
					DamageHandler.damageEntity(e, this.damage, this);

					if (!e.hasPotionEffect(PotionEffectType.BLINDNESS)) {
						e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, this.blind_duration, this.blind_amplifier));
					}
				});
			}
		}
		if (this.fallingBlocks.isEmpty()) {
			remove();
			return;
		}
		if (this.throwCount > 0) {
			if (!this.removeAfterTime) {
				this.time = System.currentTimeMillis();
			}
			this.removeAfterTime = true;
		}
		if (this.removeAfterTime) {
			if (System.currentTimeMillis() > this.time + 10000) {
				remove();
				return;
			}
		}
	}
	
	private void surge(Block block, Location location) {
		BlockData data = getMaterial(block.getType()).createBlockData();
		double y = this.launch;
		double speed = this.launch_speed;
		
		while (y > 0) {
			double x = ThreadLocalRandom.current().nextDouble();
			double z = ThreadLocalRandom.current().nextDouble();

			x = (ThreadLocalRandom.current().nextBoolean()) ? x : -x;
			z = (ThreadLocalRandom.current().nextBoolean()) ? z : -z;
			
			y--;
			speed -= 0.15;
			
			FallingBlock fb = player.getWorld().spawnFallingBlock(location, data);
			fb.setVelocity(new Vector((x >= 1 ? 0.25 : x), y, (z >= 1 ? 0.25 : z)).normalize().multiply(speed > 0 ? speed : 0.1));
			fb.setDropItem(false);

			fb.setMetadata(Loader.getFallingBlocksKey(), new FixedMetadataValue(ProjectKorra.plugin, 0));
			this.fallingBlocks.add(fb);
		}
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
		return material.name().contains("RED_SAND");
	}
	
	private void blockAbilities() {
		// main
		CoreAbility main = CoreAbility.getAbility(SandSurge.class);
		
		// small
		CoreAbility eblast = CoreAbility.getAbility(EarthBlast.class),
					fblast = CoreAbility.getAbility(FireBlast.class),
					ablast = CoreAbility.getAbility(AirBlast.class),
					aswipe = CoreAbility.getAbility(AirSwipe.class),
					wmanip = CoreAbility.getAbility(WaterManipulation.class);
		
		// large
		CoreAbility esmash = CoreAbility.getAbility(EarthSmash.class),
					lightning = CoreAbility.getAbility(Lightning.class),
					torrent = CoreAbility.getAbility(Torrent.class),
					surge_wave = CoreAbility.getAbility(SurgeWave.class),
					surge_wall = CoreAbility.getAbility(SurgeWall.class);
	
		CoreAbility[] smallAbilities = { eblast, fblast, ablast, aswipe, wmanip },
					  largeAbilities = { esmash, lightning, torrent, surge_wave, surge_wall };
		
		for (CoreAbility small : smallAbilities) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, small, false, true));
		}
		for (CoreAbility large : largeAbilities) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, large, false, false));
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
		
		for (int i = 0; i < this.width; i++) {
			final double adjustedI = i - this.width / 2.0;
			Block block = target.getWorld().getBlockAt(target.getLocation().clone().add(orth.clone().multiply(adjustedI)));
			Block top = GeneralMethods.getTopBlock(block.getLocation(), 3);
			
			if (isSandbendable(top) && !RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
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
	
	public void throwSurge() {
		if (this.throwCount < 1) {
			playEarthbendingSound(player.getLocation());

			for (FallingBlock fallingBlock : this.fallingBlocks) {
				fallingBlock.setVelocity(player.getEyeLocation().getDirection().multiply(this.throw_speed));
			}
		}
		this.throwCount++;
	}
	
	public static void throwSurge(Player player) {
		getAbility(player, SandSurge.class).throwSurge();
	}
	
	public List<FallingBlock> getFallingBlocks() {
		return this.fallingBlocks;
	}
	
	public int getThrowCount() {
		return this.throwCount;
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
		return this.fallingBlocks.stream().map(fb -> (Location) fb.getLocation()).collect(Collectors.toList());
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
		return true;
	}
	
	@Override
	public double getCollisionRadius() {
		return this.collision_radius;
	}
	
	@Override
	public void handleCollision(Collision collision) {
		super.handleCollision(collision);
		
		CoreAbility second = collision.getAbilitySecond();
		
		if (second instanceof EarthSmash || second instanceof Torrent || second instanceof Lightning || second instanceof SurgeWave || second instanceof SurgeWall) {
			if (second.getLocation() == null) {
				List<Location> locations = second.getLocations();
				if (locations != null) {
					if (locations.size() > 0) {
						for (Location location : locations) {
							for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, 1.25)) {
								if (!(entity instanceof FallingBlock)) continue;
								
								if (entity.hasMetadata(Loader.getFallingBlocksKey())) {
									this.fallingBlocks.remove(entity);
								}
							}
						}
					}
				}
			} else {
				List<Entity> fallingBlocks = GeneralMethods.getEntitiesAroundPoint(collision.getLocationSecond(), 2.5);
				fallingBlocks.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof FallingBlock).map(e -> (FallingBlock) e).forEach(e -> {
					this.fallingBlocks.remove(e);
				});
			}
		}
	}
	
	@Override
	public void remove() {
		bPlayer.addCooldown(this);
		
		super.remove();
	}
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.SandSurge.Enabled", true);
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
	public void load() { }

	@Override
	public void stop() { }
}
