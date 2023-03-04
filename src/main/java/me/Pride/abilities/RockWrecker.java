package me.Pride.abilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
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
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.SurgeWall;
import com.projectkorra.projectkorra.waterbending.SurgeWave;
import com.projectkorra.projectkorra.waterbending.Torrent;
import com.projectkorra.projectkorra.waterbending.WaterManipulation;

import me.Pride.loader.Loader;

public class RockWrecker extends LavaAbility implements AddonAbility {
	
	private final String path = "ExtraAbilities.Prride.RockWrecker.";
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.CHARGE_DURATION)
	private long primeTime, fullTime;
	@Attribute(Attribute.SELECT_RANGE)
	private int selectRange;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	private boolean revert;
	@Attribute("RevertTime")
	private long revertTime;
	private boolean createLava;
	
	@Attribute(Attribute.DAMAGE)
	private static final double STARTING_DAMAGE = ConfigManager.getConfig().getDouble("ExtraAbilities.Prride.RockWrecker.Damage.StartingDamage");
	@Attribute(Attribute.DAMAGE)
	private static final double PRIME_DAMAGE = ConfigManager.getConfig().getDouble("ExtraAbilities.Prride.RockWrecker.Damage.PrimeDamage");
	@Attribute(Attribute.DAMAGE)
	private static final double FULL_DAMAGE = ConfigManager.getConfig().getDouble("ExtraAbilities.Prride.RockWrecker.Damage.FullDamage");
	
	private enum State {
		STARTING(1, STARTING_DAMAGE, true, false), PRIME(1.25, PRIME_DAMAGE, true, false), FULL(1.5, FULL_DAMAGE, false, true);
		
		private boolean removeSmall, removeLarge;
		private double radius;
		private double damage;
		
		State(double radius, double damage, boolean removeSmall, boolean removeLarge) {
			this.radius = radius;
			this.damage = damage;
			this.removeSmall = removeSmall;
			this.removeLarge = removeLarge;
		}
		public double getRadius() { return radius; }
		public double getDamage() { return damage; }
		public boolean removeSmall() { return removeSmall; }
		public boolean removeLarge() { return removeLarge; }
	}
	
	private int time;
	private boolean advanced;
	private int rockRange;
	private double formLavaTime;
	
	private Block target;
	private Location location;
	private Vector direction;
	
	private Set<TempBlock> tempBlocks = new HashSet<>();
	private List<Block> lavaBlocks = new ArrayList<>();
	
	private State state = State.STARTING;

	public RockWrecker(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (!bPlayer.canLavabend()) {
			return;
		} else if (RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.primeTime = ConfigManager.getConfig().getLong(path + "FormTime.Prime");
		this.fullTime = ConfigManager.getConfig().getLong(path + "FormTime.Full");
		this.selectRange = ConfigManager.getConfig().getInt(path + "SelectRange");
		this.speed = ConfigManager.getConfig().getDouble(path + "Speed");
		this.range = ConfigManager.getConfig().getDouble(path + "Range");
		this.knockback = ConfigManager.getConfig().getDouble(path + "Knockback");
		this.revert = ConfigManager.getConfig().getBoolean(path + "Revert.Revert");
		this.revertTime = ConfigManager.getConfig().getLong(path + "Revert.RevertTime");
		this.createLava = ConfigManager.getConfig().getBoolean(path + "CreateLavaPool");
		
		for (int i = 1; i <= this.selectRange; i++) {
			this.rockRange = i;
			this.target = getEarthSourceBlock(i);
			
			if (target != null && isEarthbendable(target)) {
				break;
			}
		}
		if (target == null) {
			return;
		} else if (RegionProtection.isRegionProtected(player, target.getLocation(), this)) {
			return;
		}
		if (createLava) {
			GeneralMethods.getBlocksAroundPoint(target.getLocation(), 1.425).stream().filter(b -> !isAir(b.getType()) && isEarthbendable(b)).forEach(b -> {
				Block block = GeneralMethods.getTopBlock(b.getLocation(), 1, 1);
				
				if (isEarthbendable(block) && isTransparent(block.getRelative(BlockFace.UP))) {
					lavaBlocks.add(block);
					if (isPlant(block.getRelative(BlockFace.UP)) && !WaterAbility.isLeaves(block.getRelative(BlockFace.UP))) {
						lavaBlocks.add(block.getRelative(BlockFace.UP));
					}
				}
			});
		}
		this.location = target.getLocation();
		
		playEarthbendingSound(target.getLocation());
		
		start();
	}

	@Override
	public void progress() {
		if (!bPlayer.canBendIgnoreBinds(this)) {
			remove();
			return;
		}
		if (createLava && lavaBlocks.size() > 0) {
			formLavaTime += 0.05;

			if (time > 0.25) {
				Block block = lavaBlocks.get(ThreadLocalRandom.current().nextInt(lavaBlocks.size()));

				if (isPlant(block)) {
					new PlantRegrowth(player, block);
					new TempBlock(block, Material.AIR.createBlockData(), 12000L);
				} else if (isEarthbendable(block)) {
					new TempBlock(block, sideIsAir(block) ? Material.MAGMA_BLOCK.createBlockData() : Material.LAVA.createBlockData(), 12000L);
				}
				lavaBlocks.remove(block);
				time = 0;
			}
		}
		if (time == time(primeTime)) {
			state = State.PRIME;
			playEffects(target.getLocation(), state, target);
			
		} else if (time == time(fullTime)) {
			state = State.FULL;
			playEffects(target.getLocation(), state, target);
		}
		if (player.isSneaking() && !advanced) {
			target = GeneralMethods.getTargetedLocation(player, rockRange, true, false).getBlock();
			location = target.getLocation().clone();
			direction = player.getEyeLocation().getDirection();
			
			time++;
			
			tempBlocks.forEach(tb -> tb.revertBlock());
			shape(state, target, b -> tempBlocks.add(new TempBlock(b, Material.MAGMA_BLOCK.createBlockData())));
		}
		if (!player.isSneaking() || advanced) {
			throwRock();
		}
		blockAbilities(state);
	}
	
	private void shape(State state, Block block, Consumer<Block> blocks) {
		List<Block> cube = GeneralMethods.getBlocksAroundPoint(block.getLocation(), state == State.STARTING ? 0.5 : state.getRadius());
		
		cube.stream().filter(b -> isAir(b.getType()) || isWater(b)).forEach(b -> blocks.accept(b));
	}
	
	private void throwRock() {
		if (time > 0) {
			playEffects(target.getLocation(), state, target);
			time = -1;
			--time;
		}
		advanced = true;
		
		location.add(direction.normalize().multiply(this.speed));
		
		tempBlocks.forEach(tb -> tb.revertBlock());
		shape(state, location.getBlock(), b -> tempBlocks.add(new TempBlock(b, Material.MAGMA_BLOCK.createBlockData())));
		
		if (!isTransparent(location.getBlock()) && !TempBlock.isTempBlock(location.getBlock())) {
			List<Block> blocks = GeneralMethods.getBlocksAroundPoint(location, state.getRadius() * 1.5);
			blocks.stream().filter(b -> !isIndestructible(b.getType())).forEach(b -> {
				if (revert) {
					new TempBlock(b, Material.AIR.createBlockData(), this.revertTime);
				} else {
					b.setType(Material.AIR);
				}
			});
			explosion(location);
			remove();
			return;
		}
		if (location.distanceSquared(target.getLocation()) > this.range * this.range) {
			remove();
			return;
		}
		if (state == State.FULL) {
			damage(location, state.getRadius(), state.getDamage(), true);
		} else {
			damage(location, state.getRadius(), state.getDamage(), false);
		}
	}
	
	private void damage(Location location, double radius, double damage, boolean multiple) {
		Stream<LivingEntity> entities = GeneralMethods.getEntitiesAroundPoint(location, radius).stream().filter(e -> e.getUniqueId() != player.getUniqueId() && e instanceof LivingEntity).map(e -> (LivingEntity) e);
		if (multiple) {
			entities.forEach(e -> {
				
			DamageHandler.damageEntity(e, damage, this);
			final Vector travelVec = GeneralMethods.getDirection(location, e.getLocation());
			e.setVelocity(travelVec.normalize().multiply(this.knockback));
			});
		} else {
			Optional<LivingEntity> entity = entities.findFirst();
			
			if (entity.isPresent()) {
				ParticleEffect.BLOCK_CRACK.display(location, 10, 0F, 0F, 0F, 0F, Material.MAGMA_BLOCK.createBlockData());
				DamageHandler.damageEntity(entity.orElse(null), damage, this);
				
				remove();
				return;
			}
		}
	}
	
	private void explosion(Location location) {
		int size = tempBlocks.size() < 1 ? 1 : tempBlocks.size() / 4;
		for (int i = 0; i < (tempBlocks.size() < 1 ? 1 : tempBlocks.size() / size); i++) {
			double x = ThreadLocalRandom.current().nextGaussian() * 3;
			double z = ThreadLocalRandom.current().nextGaussian() * 3;
			double y = ThreadLocalRandom.current().nextGaussian() * 3;

			x = (ThreadLocalRandom.current().nextBoolean()) ? x : -x;
			z = (ThreadLocalRandom.current().nextBoolean()) ? z : -z;
			y = (ThreadLocalRandom.current().nextBoolean()) ? y : -y;

			FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(location, Material.MAGMA_BLOCK.createBlockData());
			fallingBlock.setVelocity(new Vector(-x, -y, -z).normalize().multiply(-1));
			fallingBlock.canHurtEntities();
			fallingBlock.setDropItem(false);
			fallingBlock.setMetadata(Loader.getFallingBlocksKey(), new FixedMetadataValue(ProjectKorra.plugin, 0));
		}
	}
	
	private void playEffects(Location location, State state, Block block) {
		playEarthbendingSound(location);
		shape(state, block, b -> player.getWorld().spawnParticle(Particle.REDSTONE, b.getLocation(), 1, 1F, 1F, 1F, 0, new DustOptions(Color.fromRGB(209, 99, 36), 1)));
	}

	private int time(long time) {
		return ((int) (time / 1000)) * 20;
	}
	
	private boolean isIndestructible(Material material) {
		switch (material) {
			case END_PORTAL:
			case END_PORTAL_FRAME:
			case NETHER_PORTAL:
			case BEDROCK:
			case BARRIER:
			case COMMAND_BLOCK:
				return true;
		}
		return false;
	}

	private boolean sideIsAir(Block block) {
		BlockFace[] faces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_WEST };
		for (BlockFace face : faces) {
			if (isAir(block.getRelative(face).getType())) {
				return true;
			}
		}
		return false;
	}
	
	private void blockAbilities(State state) {
		// main
		CoreAbility main = CoreAbility.getAbility(RockWrecker.class);
		
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
		
		boolean removeSelf = false;
		
		if (state == State.FULL) {
			removeSelf = false;
		} else if (state == State.STARTING) {
			removeSelf = true;
		}
		for (CoreAbility small : smallAbilities) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, small, removeSelf, state.removeSmall()));
		}
		for (CoreAbility large : largeAbilities) {
			if (state == State.PRIME) {
				removeSelf = true;
			}
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, large, removeSelf, state.removeLarge()));
		}
	}
	
	@Override
	public long getCooldown() {
		return this.cooldown;
	}
	
	@Override
	public Location getLocation() {
		return location;
	}
	
	@Override
	public String getName() {
		return "RockWrecker";
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
		return state.getRadius();
	}
	
	@Override
	public void handleCollision(Collision collision) {
		super.handleCollision(collision);
		
		if (collision.isRemovingFirst()) {
			explosion(collision.getLocationFirst());
			remove();
			return;
		}
	}
	
	@Override
	public void remove() {
		tempBlocks.forEach(tb -> tb.revertBlock());
		bPlayer.addCooldown(this);
		super.remove();
	}
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.RockWrecker.Enabled", true);
	}

	@Override
	public String getAuthor() {
		return Loader.getAuthor(Element.LAVA);
	}

	@Override
	public String getVersion() {
		return Loader.getVersion(Element.LAVA);
	}
	
	@Override
	public String getDescription() {
		return "This technique of lavabending requires the user to use all the earth fragments around them to transform into a large "
				+ "ball of molten rock/magma in order to fire and cause a variety of destructive attacks.";
	}
	
	@Override
	public String getInstructions() {
		return "To use this ability, hold sneak while looking at an earth block to transform and grab a large fragment of magma from. "
				+ "After certain durations of time, the magma will compress and largen into larger blocks. Each form has a different amount "
				+ "of damage and collision radius.";
	}

	@Override
	public void load() { }

	@Override
	public void stop() { }
}
