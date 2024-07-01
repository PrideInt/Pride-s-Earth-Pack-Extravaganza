package me.Pride.abilities;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.TempBlock;
import me.Pride.loader.Loader;
import me.Pride.util.TempStrippedEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * I would not use MetalStrips right now
 * - Pride
 */

public class MetalStrips extends MetalAbility implements AddonAbility {
	
	protected final String path = "ExtraAbilities.Prride.MetalStrips.";
	
	public enum MetalArea {
		HEAD(2), CHEST(1), LEGS(0.5), FEET(0), NONE(0);
		private double y;
		MetalArea(double y) {
			this.y = y;
		}
		public double getY() { return this.y; }
	}
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	@Attribute("DecreaseArmor")
	private int armorDecrease;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute("RevertTime")
	private long revertTime;
	@Attribute(Attribute.RADIUS)
	private double magnetizeRadius;
	@Attribute("Pull")
	private double magnetizeSpeed;
	
	private boolean traceEntity, traceBlock;
	
	private Entity targetEntity;
	private Optional<Location> targetLocation;
	
	private Set<Strip> metalStrips;
	private Set<Block> strippedBlocks;
	public static final Map<Entity, MetalArea[]> METAL_CAPTURES = new ConcurrentHashMap<>();
	
	public MetalStrips(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (!bPlayer.canMetalbend()) {
			return;
		} else if (RegionProtection.isRegionProtected(player, player.getLocation(), this)) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.duration = ConfigManager.getConfig().getLong(path + "Duration");
		this.armorDecrease = ConfigManager.getConfig().getInt(path + "DecreaseArmor");
		this.selectRange = ConfigManager.getConfig().getDouble(path + "Stripped.SelectRange");
		this.revertTime = ConfigManager.getConfig().getLong(path + "Stripped.Block.RevertTime");
		this.magnetizeRadius = ConfigManager.getConfig().getDouble(path + "Stripped.Entity.MagnetizeRadius");
		this.magnetizeSpeed = ConfigManager.getConfig().getDouble(path + "Stripped.Entity.MagnetizePullSpeed");
		
		this.metalStrips = new HashSet<>();
		this.strippedBlocks = new HashSet<>();
		
		start();
		shootStrips();
	}
	
	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead()) {
			remove();
			return;
		}
		for (Iterator<Strip> itr = metalStrips.iterator(); itr.hasNext();) {
			Strip metalStrip = itr.next();

			if (!metalStrip.handle()) {
				if (metalStrip.getBlock() != null) {
					strippedBlocks.add(metalStrip.getBlock());
				}
				metalStrip.getItem().remove();
				itr.remove();
			}
		}
		for (Iterator<Block> itr = strippedBlocks.iterator(); itr.hasNext();) {
			Block block = itr.next();

			player.spawnParticle(Particle.REDSTONE, block.getLocation().clone().add(0.5, 0.5, 0.5), 8, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.fromRGB(176, 173, 172), 1));

			if (block.getType() == Material.AIR) {
				itr.remove();
			}
		}
		for (Entity entity : METAL_CAPTURES.keySet()) {
			if (!isArmorableEntity(entity)) {
				double size = Math.sqrt(((LivingEntity) entity).getEyeLocation().distanceSquared(entity.getLocation())) / 2 + 0.8;

				if (METAL_CAPTURES.get(entity).length > 0) {
					for (int i = 0; i < 3; i++) {
						if (METAL_CAPTURES.get(entity)[i] != null) {
							player.getWorld().spawnParticle(Particle.REDSTONE, entity.getLocation().clone().add(0, METAL_CAPTURES.get(entity)[i].getY(), 0), 3, size / 2, size / 2, size / 2, 0, new Particle.DustOptions(Color.fromRGB(176, 173, 172), 1));
						}
					}
				} else {
					player.getWorld().spawnParticle(Particle.REDSTONE, entity.getLocation(), 3, size / 4, size / 4, size / 4, 0, new Particle.DustOptions(Color.fromRGB(176, 173, 172), 1));
				}
			}
		}
		if (player.isSneaking()) {
			magnetize();
		}
	}
	
	private void magnetize() {
		targetLocation.ifPresent(location -> {
			Location target = location;

			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), magnetizeRadius)) {
				if (entity.getUniqueId() == player.getUniqueId() || !METAL_CAPTURES.containsKey(entity)) continue;

				if (METAL_CAPTURES.containsKey(entity)) {
					if (traceEntity) {
						if (entity.getUniqueId() == targetEntity.getUniqueId()) continue;

						target = targetEntity.getLocation().clone().add(0, 0.75, 0);
					}
				}
				entity.setVelocity(GeneralMethods.getDirection(entity.getLocation(), target).normalize().multiply(magnetizeSpeed));
				entity.setFallDistance(0);
			}
		});
	}
	
	public void shootStrips() {
		boolean ingot = false, i_armor = false;
		for (ItemStack iron : getIronMaterial()) {
			if (player.getInventory().containsAtLeast(iron, 1)) {
				ingot = true;
				break;
			}
		}
		if (!ingot) {
			Optional<ItemStack> armor = Arrays.stream(player.getInventory().getArmorContents())
					.filter(i -> {
						if (i != null) {
							switch (i.getType()) {
								case IRON_HELMET:
								case IRON_CHESTPLATE:
								case IRON_LEGGINGS:
								case IRON_BOOTS:
									return true;
							}
						}
						return false;
					})
					.findFirst();
			
			if (armor.isPresent()) {
				i_armor = true;
				
				ItemMeta meta = armor.get().getItemMeta();
				if (meta instanceof Damageable) {
					Damageable damageable = (Damageable) meta;
					if ((armor.get().getType().getMaxDurability() - damageable.getDamage()) - this.armorDecrease <= 0) {
						player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
						armor.get().setAmount(0);
					}
					damageable.setDamage(damageable.getDamage() + this.armorDecrease);
					
					armor.get().setItemMeta(meta);
				}
			}
		} else {
			for (ItemStack item : player.getInventory().getContents()) {
				if (item != null && (item.getType() == Material.IRON_INGOT || item.getType() == Material.IRON_NUGGET) && item.getAmount() > 0) {
					item.setAmount(item.getAmount() - 1);
					break;
				}
			}
		}
		if (ingot || i_armor) {
			// player.sendMessage("has ingot or armor");
			playMetalbendingSound(player.getLocation());
			metalStrips.add(new Strip(player.getLocation().add(0, 1, 0), player.getEyeLocation().getDirection(), this));
		}
	}
	
	public static void shootStrips(Player player) {
		getAbility(player, MetalStrips.class).shootStrips();
	}
	
	public void traceEntityBlock() {
		playMetalbendingSound(player.getLocation());
		traceEntity = false; traceBlock = false;
		
		RayTraceResult trace = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), this.selectRange, 1.15, e -> e.getUniqueId() != player.getUniqueId());
		if (trace == null) {
			trace = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), this.selectRange, FluidCollisionMode.NEVER, true);
		}
		if (trace != null && trace.getHitEntity() != null) {
			if (METAL_CAPTURES.containsKey(trace.getHitEntity())) {
				traceEntity = true;
				targetEntity = trace.getHitEntity();
				targetLocation = Optional.of(trace.getHitEntity().getLocation().clone().add(0, 1, 0));
			}
		} else if (trace != null && trace.getHitBlock() != null) {
			if (strippedBlocks.contains(trace.getHitBlock())) {
				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1F, 1F);
				traceBlock = true;

				for (Block block : GeneralMethods.getBlocksAroundPoint(trace.getHitBlock().getLocation().clone().add(0.5, 0.5, 0.5), 1.25)) {
					if (isIndestructible(block.getType())) continue;

					new TempBlock(block, Material.AIR.createBlockData(), this.revertTime);
				}
				strippedBlocks.remove(trace.getHitBlock());
				targetLocation = Optional.empty();
			}
		}
		if (!traceEntity && !traceBlock) {
			targetLocation = Optional.of(GeneralMethods.getTargetedLocation(player, this.selectRange).clone());
		}
	}
	
	public static void traceEntityBlock(Player player) {
		getAbility(player, MetalStrips.class).traceEntityBlock();
	}

	private void spawnFallingBlock(Location location, Block block) {
		double x = ThreadLocalRandom.current().nextGaussian() * 3;
		double z = ThreadLocalRandom.current().nextGaussian() * 3;
		double y = ThreadLocalRandom.current().nextGaussian() * 3;

		x = (ThreadLocalRandom.current().nextBoolean()) ? x : -x;
		z = (ThreadLocalRandom.current().nextBoolean()) ? z : -z;
		y = (ThreadLocalRandom.current().nextBoolean()) ? y : -y;

		FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(location, block.getBlockData());
		fallingBlock.setVelocity(new Vector(-x, -y, -z).normalize().multiply(-1));
		fallingBlock.setDropItem(false);
		fallingBlock.setMetadata(Loader.getFallingBlocksKey(), new FixedMetadataValue(ProjectKorra.plugin, 0));
	}
	
	private List<ItemStack> getIronMaterial() {
		List<ItemStack> iron = new ArrayList<>();
		for (String material : ConfigManager.getConfig().getStringList(path + "MetalMaterials")) {
			if (Material.valueOf(material) != null) {
				iron.add(new ItemStack(Material.valueOf(material)));
			}
		}
		return iron;
	}
	
	public long getCurrentAbilityTime() {
		return (getStartTime() + this.duration) - System.currentTimeMillis();
	}

	public long getAddedTime() {
		return System.currentTimeMillis() + this.duration;
	}

	/** These mobs can have armor on them AND they are rendered/visible
	 *
	 * @param entity
	 * @return Entity able to wear armor
	 */
	private boolean isArmorableEntity(Entity entity) {
		switch (entity.getType()) {
			case DROWNED:
			case EVOKER:
			case GIANT:
			case HUSK:
			case PIGLIN:
			case PIGLIN_BRUTE:
			case PILLAGER:
			case PLAYER:
			case SKELETON:
			case STRAY:
			case VINDICATOR:
			case WITHER_SKELETON:
			case ZOMBIE:
			case ZOMBIE_VILLAGER:
			case ZOMBIFIED_PIGLIN:
				return true;
		}
		return false;
	}

	/** Entities that are tall (2ish blocks tall/as tall as a player)
	 *
	 * @param entity
	 * @return "tall" entities
	 */
	protected boolean isTallEntity(Entity entity) {
		if (isArmorableEntity(entity)) return true;
		
		switch (entity.getType()) {
			case VILLAGER:
			case CREEPER:
			case WITCH:
			case WANDERING_TRADER:
			case ILLUSIONER:
				return true;
		}
		return false;
	}
	
	public static boolean isIndestructible(Material type) {
		switch (type) {
			case COMMAND_BLOCK:
			case BEDROCK:
			case BARRIER:
			case END_PORTAL:
			case END_PORTAL_FRAME:
			case NETHER_PORTAL:
			case STRUCTURE_BLOCK:
				return true;
		}
		return false;
	}
	
	@Override
	public boolean isSneakAbility() {
		return true;
	}
	
	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}
	
	@Override
	public String getName() {
		return "MetalStrips";
	}
	
	@Override
	public Location getLocation() {
		return null;
	}
	
	@Override
	public void load() { }
	
	@Override
	public void stop() { }
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("ExtraAbilities.Prride.MetalStrips.Enabled", true);
	}
	
	@Override
	public String getAuthor() {
		return Loader.getAuthor(this.getElement());
	}
	
	@Override
	public String getVersion() {
		return Loader.getVersion(this.getElement());
	}
	
	class Strip {
		private Location origin, location;
		private Vector direction;
		private MetalStrips metalStrips;
		
		@Attribute(Attribute.SPEED)
		private double speed;
		@Attribute(Attribute.RANGE)
		private double range;
		@Attribute("EffectAmplifier")
		private int amplifier;

		private ItemDisplay item;
		private Block block;
		
		public Strip(Location origin, Vector direction, MetalStrips metalStrips) {
			this.origin = origin.clone();
			this.direction = direction;
			this.metalStrips = metalStrips;
			
			this.speed = ConfigManager.getConfig().getDouble(path + "Speed");
			this.range = ConfigManager.getConfig().getDouble(path + "Range");
			this.amplifier = 1;
			
			this.location = this.origin.clone();

			this.item = player.getWorld().spawn(this.location, ItemDisplay.class);
			this.item.setItemStack(new ItemStack(Material.RAW_IRON_BLOCK));

			Transformation transformation = this.item.getTransformation();
			transformation.getScale().set(0.25);

			this.item.setTransformation(transformation);
			this.item.setMetadata("metalstrip", new FixedMetadataValue(ProjectKorra.plugin, 0));
		}
		public boolean handle() {
			this.location.add(this.direction.multiply(this.speed));
			this.item.teleport(this.location);

			if (this.location.distanceSquared(this.origin) > this.range * this.range) {
				return false;
			}
			if (RegionProtection.isRegionProtected(player, this.location, this.metalStrips)) {
				return false;
			}
			Optional<Entity> entity = GeneralMethods.getEntitiesAroundPoint(this.location, 1)
					.stream()
					.filter(e -> e.getUniqueId() != player.getUniqueId() && !e.hasMetadata("metalstrip"))
					.findFirst();

			if (entity.isPresent()) {
				if (entity.get() instanceof Item) {
					if (!entity.get().hasMetadata(Loader.getItemKey())) {
						entity.get().setMetadata(Loader.getItemKey(), new FixedMetadataValue(ProjectKorra.plugin, 0));
					}
				}
				trackArea(entity.get());
				return false;
			}
			if (!isAir(this.location.getBlock().getType()) && !this.location.getBlock().isLiquid() && !isIndestructible(this.location.getBlock().getType())) {
				this.block = this.location.getBlock();
				return false;
			}
			return true;
		}
		private void trackArea(Entity entity) {
			if (entity instanceof LivingEntity) {
				if (entity instanceof Player) {
					if (Commands.invincible.contains(entity.getName())) return;
				}
				if (isArmorableEntity(entity)) {
					if (isTallEntity(entity)) {
						if (entity instanceof Giant) {
							bindMetal(entity, 12, 7, 3.5, 2.25);
						} else if (entity instanceof WitherSkeleton) {
							bindMetal(entity, 2.4, 1.8, 0.8, 0.35);
						} else {
							bindMetal(entity, 2, 1.5, 0.65, 0.35);
						}
					}
				} else {
					if (!METAL_CAPTURES.containsKey(entity)) {
						METAL_CAPTURES.put(entity, new MetalArea[0]);
					}
					new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW, 30, this.amplifier), this.metalStrips);
				}
			}
		}
		private void bindMetal(Entity entity, double head, double chest, double legs, double feet) {
			MetalArea area;
			if (this.location.getY() <= getEntityY(entity, head) && this.location.getY() >= getEntityY(entity, chest)) {
				area = MetalArea.HEAD;
			} else if (this.location.getY() < getEntityY(entity, chest) && this.location.getY() >= getEntityY(entity, legs)) {
				area = MetalArea.CHEST;
			} else if (this.location.getY() < getEntityY(entity, legs) && this.location.getY() >= getEntityY(entity, feet)) {
				area = MetalArea.LEGS;
			} else if (this.location.getY() < getEntityY(entity, feet) && this.location.getY() >= getEntityY(entity, 0)) {
				area = MetalArea.FEET;
			} else {
				area = MetalArea.NONE;
			}
			int idx = -1;
			switch (area) {
				case NONE: break;
				case HEAD: idx = 3; break;
				case CHEST: idx = 2; break;
				case LEGS: idx = 1; break;
				case FEET: idx = 0; break;
			}
			if (isArmorableEntity(entity)) {
				ItemStack[] armor = new ItemStack[4];

				final int finalIdx = idx;
				applyArmor(idx, (i, p) -> {
					armor[finalIdx] = i;
					new TempStrippedEffects(entity, metalStrips.duration, armor, new PotionEffect(p, 30, this.amplifier), area, this.metalStrips);
				});
			} else {
				switch (idx) {
					case 3: new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.BLINDNESS, 30, this.amplifier), this.metalStrips);
						break;
					case 2: new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW_DIGGING, 30, this.amplifier), this.metalStrips);
						break;
					case 1:
					case 0: new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW, 30, this.amplifier), this.metalStrips);
						break;
				}
			}
			if (METAL_CAPTURES.containsKey(entity)) {
				if (METAL_CAPTURES.get(entity).length > 0) {
					if (idx != -1) {
						if (METAL_CAPTURES.get(entity)[idx] == MetalArea.NONE) {
							METAL_CAPTURES.get(entity)[idx] = area;
						}
					}
				}
			} else {
				METAL_CAPTURES.put(entity, new MetalArea[4]);
			}
		}
		private void applyArmor(int idx, BiConsumer<ItemStack, PotionEffectType> bi) {
			switch (idx) {
				case 3: bi.accept(new ItemStack(Material.IRON_BLOCK, 1), PotionEffectType.BLINDNESS);
					break;
				case 2: bi.accept(new ItemStack(Material.IRON_CHESTPLATE, 1), PotionEffectType.SLOW_DIGGING);
					break;
				case 1: bi.accept(new ItemStack(Material.IRON_LEGGINGS, 1), PotionEffectType.SLOW);
					break;
				case 0: bi.accept(new ItemStack(Material.IRON_BOOTS, 1), PotionEffectType.SLOW);
					break;
			}
		}
		private double getEntityY(Entity entity, double offset) {
			return entity.getLocation().clone().add(0, offset, 0).getY();
		}
		public Location getLocation() {
			return this.location;
		}
		public Block getBlock() {
			return this.block;
		}
		public ItemDisplay getItem() {
			return this.item;
		}
	}
}

