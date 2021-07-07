package me.Pride.abilities;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
	
	private static final String PATH = "ExtraAbilities.Prride.RockWrecker.";
	private static final FileConfiguration CONFIG = ConfigManager.getConfig();
	
	@Attribute(Attribute.COOLDOWN)
	private static final long COOLDOWN = CONFIG.getLong(PATH + "Cooldown");
	@Attribute(Attribute.CHARGE_DURATION)
	private static final long PRIME_TIME = CONFIG.getLong(PATH + "FormTime.Prime"),
							  FULL_TIME = CONFIG.getLong(PATH + "FormTime.Full");
	@Attribute(Attribute.SELECT_RANGE)
	private static final int SELECT_RANGE = CONFIG.getInt(PATH + "SelectRange");
	@Attribute(Attribute.SPEED)
	private static final double SPEED = CONFIG.getDouble(PATH + "Speed");
	@Attribute(Attribute.DAMAGE)
	private static final double STARTING_DAMAGE = CONFIG.getDouble(PATH + "Damage.StartingDamage"),
								PRIME_DAMAGE = CONFIG.getDouble(PATH + "Damage.PrimeDamage"),
								FULL_DAMAGE = CONFIG.getDouble(PATH + "Damage.FullDamage");
	@Attribute(Attribute.KNOCKBACK)
	private static final double KNOCKBACK = CONFIG.getDouble(PATH + "Knockback");
	private static final boolean REVERT = CONFIG.getBoolean(PATH + "Revert.Revert");
	@Attribute("RevertTime")
	private static final long REVERT_TIME = CONFIG.getLong(PATH + "Revert.RevertTime");
	private static final boolean CREATE_LAVA = CONFIG.getBoolean(PATH + "CreateLavaPool");
	
	private enum State {
		STARTING(1, STARTING_DAMAGE, true, false), PRIME(1.25, PRIME_DAMAGE, true, false), FULL(1.5, FULL_DAMAGE, false, true);
		
		private boolean removeSmall, removeLarge;
		private double radius;
		private double damage;
		
		private State(double radius, double damage, boolean removeSmall, boolean removeLarge) {
			this.radius = radius;
			this.damage = damage;
			this.removeSmall = removeSmall;
			this.removeLarge = removeLarge;
		}
		
		public double getRadius() {
			return radius;
		}
		
		public double getDamage() {
			return damage;
		}
		
		public boolean removeSmall() {
			return removeSmall;
		}
		
		public boolean removeLarge() {
			return removeLarge;
		}
	}
	
	private double time;
	private boolean advanced;
	private int range;
	private double step, step_, step__;
	
	private Block target;
	private Location location;
	private Vector direction;
	private Random rand = new Random();
	
	private Set<TempBlock> tempBlocks = new HashSet<>();
	
	private State state = State.STARTING;

	public RockWrecker(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this) || !bPlayer.canLavabend()) return;
		
		for (int i = 1; i <= SELECT_RANGE; i++) {
			range = i;
			target = getEarthSourceBlock((double) i);
			
			if (target != null && isEarthbendable(target)) {
				break;
			}
		}
		
		if (target == null) return;
		
		if (GeneralMethods.isRegionProtectedFromBuild(player, "RockWrecker", player.getLocation()) || GeneralMethods.isRegionProtectedFromBuild(player, "RockWrecker", target.getLocation())) return;
		
		if (CREATE_LAVA) {
			GeneralMethods.getBlocksAroundPoint(target.getLocation(), 1.425).stream().filter(b -> !isAir(b.getType()) && isEarthbendable(b)).forEach(b -> {
				Block block = GeneralMethods.getTopBlock(b.getLocation(), 1, 1);
				
				if (isEarthbendable(block) && isTransparent(block.getRelative(BlockFace.UP))) {
					ParticleEffect.LAVA.display(b.getLocation(), 2);
					new TempBlock(block, Material.LAVA.createBlockData(), 12000L);
					
					if (isPlant(block.getRelative(BlockFace.UP)) && !WaterAbility.isLeaves(block.getRelative(BlockFace.UP))) {
						new TempBlock(block.getRelative(BlockFace.UP), Material.AIR.createBlockData(), 12000L);
					}
				}
			});
		}
		
		playEarthbendingSound(target.getLocation());
		
		start();
	}

	@Override
	public long getCooldown() {
		return COOLDOWN;
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
	public void progress() {
		if (!player.isOnline() || player.isDead() || !bPlayer.canBendIgnoreBindsCooldowns(this)) {
			remove();
			return;
		}
		
		if (location != null) {
			if (GeneralMethods.isRegionProtectedFromBuild(player, "RockWrecker", location)) {
				remove();
				return;
			}
		}
		
		if (player.isSneaking() && !advanced) {
			target = GeneralMethods.getTargetedLocation(player, range, true, false).getBlock();
			location = target.getLocation().clone();
			direction = player.getEyeLocation().getDirection();
			
			time += 0.05;
			
			if (time >= ((double) PRIME_TIME / 1000.0) && time <= ((double) FULL_TIME / 1000.0)) {
				state = State.PRIME;

				step += 0.05; playEffects(target.getLocation(), state, target, step);
				
			} else if (time > ((double) FULL_TIME / 1000.0)) {
				state = State.FULL;
				
				step_ += 0.05; playEffects(target.getLocation(), state, target, step_);
			}
			
			tempBlocks.forEach(tb -> tb.revertBlock());
			shape(state, target, b -> formRock(b));
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
	
	private void formRock(Block block) {
		tempBlocks.add(new TempBlock(block, Material.MAGMA_BLOCK.createBlockData()));
	}
	
	private void throwRock() {
		advanced = true;
		
		step__ += 0.05; playEffects(target.getLocation(), state, target, step__);
		
		location.add(direction.normalize().multiply(SPEED));
		
		tempBlocks.forEach(tb -> tb.revertBlock());
		shape(state, location.getBlock(), b -> formRock(b));
		
		if (!isTransparent(location.getBlock()) && !TempBlock.isTempBlock(location.getBlock())) {
			List<Block> blocks = GeneralMethods.getBlocksAroundPoint(location, state.getRadius());
			if (REVERT) {
				for (Block b : blocks) {
					new TempBlock(b, Material.AIR.createBlockData(), REVERT_TIME);
				}
			} else {
				for (Block b : blocks) {
					b.setType(Material.AIR);
				}
			}
			explosion(location);
			remove();
			return;
		}
		
		if (location.distanceSquared(target.getLocation()) > 20 * 20) {
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
			e.setVelocity(travelVec.normalize().multiply(KNOCKBACK));
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
			double x = rand.nextDouble() * 3;
			double z = rand.nextDouble() * 3;
			double y = rand.nextDouble() * 3;

			x = (rand.nextBoolean()) ? x : -x;
			z = (rand.nextBoolean()) ? z : -z;
			y = (rand.nextBoolean()) ? y : -y;

			FallingBlock fb = player.getWorld().spawnFallingBlock(location, Material.MAGMA_BLOCK.createBlockData());
			fb.setVelocity(new Vector(-x, -y, -z).normalize().multiply(-1));
			fb.canHurtEntities();
			fb.setDropItem(false);
			
			Loader.fallingBlocks.add(fb);
		}
	}
	
	private void playEffects(Location location, State state, Block block, double step) {
		if (step < 0.06) {
			playEarthbendingSound(location);
			shape(state, block, b -> player.getWorld().spawnParticle(Particle.REDSTONE, b.getLocation(), 1, 1F, 1F, 1F, 0, new DustOptions(Color.fromRGB(209, 99, 36), 1)));
		}
	}
	
	private void blockAbilities(State state) {
		// main
		CoreAbility main = CoreAbility.getAbility(RockWrecker.class);
		
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
		
		boolean removeSelf = false;
		
		if (state == State.FULL) {
			removeSelf = false;
		} else if (state == State.STARTING) {
			removeSelf = true;
		}
		
		for (CoreAbility smal : small) {
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, smal, removeSelf, state.removeSmall()));
		}
		for (CoreAbility lar : large) {
			if (state == State.PRIME) {
				removeSelf = true;
			}
			ProjectKorra.getCollisionManager().addCollision(new Collision(main, lar, removeSelf, state.removeLarge()));
		}
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
	public void load() {
		
	}

	@Override
	public void stop() {
		
	}

}
