package me.Pride.abilities;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
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
	private int armor_decrease;
	@Attribute(Attribute.SELECT_RANGE)
	private double select_range;
	@Attribute("RevertTime")
	private long revert_time;
	@Attribute(Attribute.RADIUS)
	private double magnetize_radius;
	@Attribute("Pull")
	private double magnetize_speed;
	
	private boolean traceEntity, traceBlock;
	
	private Entity targetEntity;
	private Location targetLocation;
	
	private Set<Strip> metalStrips;
	private List<Entity> strippedEntities;
	private List<Block> strippedBlocks;
	public static final Map<Entity, MetalArea[]> AREAS = new ConcurrentHashMap<>();
	
	public MetalStrips(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (!bPlayer.canMetalbend()) {
			return;
		} else if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.duration = ConfigManager.getConfig().getLong(path + "Duration");
		this.armor_decrease = ConfigManager.getConfig().getInt(path + "DecreaseArmor");
		this.select_range = ConfigManager.getConfig().getDouble(path + "Stripped.SelectRange");
		this.revert_time = ConfigManager.getConfig().getLong(path + "Stripped.Block.RevertTime");
		this.magnetize_radius = ConfigManager.getConfig().getDouble(path + "Stripped.Entity.MagnetizeRadius");
		this.magnetize_speed = ConfigManager.getConfig().getDouble(path + "Stripped.Entity.MagnetizePullSpeed");
		
		this.metalStrips = new HashSet<>();
		this.strippedEntities = new ArrayList<>();
		this.strippedBlocks = new ArrayList<>();
		
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
			metalStrip.handle();
			if (metalStrip.cleanup()) {
				metalStrip.getItem().remove();
				itr.remove();
			} else if (metalStrip.hasBlock()) {
				strippedBlocks.add(metalStrip.getLocation().getBlock());
				itr.remove();
			}
		}
		for (Block block : strippedBlocks) {
			player.spawnParticle(Particle.REDSTONE, block.getLocation().clone().add(0.5, 0.5, 0.5), 1, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(Color.fromRGB(176, 173, 172), 1));
		}
		for (Entity entity : AREAS.keySet()) {
			if (!isArmorableEntity(entity)) {
				double size = ((LivingEntity) entity).getEyeLocation().distance(entity.getLocation()) / 2 + 0.8;
				if (AREAS.get(entity).length > 0) {
					for (int i = 0; i < 3; i++) {
						if (AREAS.get(entity)[i] != null) {
							player.getWorld().spawnParticle(Particle.REDSTONE, entity.getLocation().clone().add(0, AREAS.get(entity)[i].getY(), 0), 3, size / 2, size / 2, size / 2, 0, new Particle.DustOptions(Color.fromRGB(176, 173, 172), 1));
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
		for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), this.magnetize_radius)) {
			if (entity.getUniqueId() == player.getUniqueId() || !AREAS.containsKey(entity)) continue;

			if (AREAS.containsKey(entity)) {
				if (traceEntity) {
					if (entity.getUniqueId() == targetEntity.getUniqueId()) continue;
				}
			}
			entity.setVelocity(GeneralMethods.getDirection(entity.getLocation(), targetLocation).normalize().multiply(this.magnetize_speed));
			entity.setFallDistance(0);
		}
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
					if ((armor.get().getType().getMaxDurability() - damageable.getDamage()) - this.armor_decrease <= 0) {
						player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
						armor.get().setAmount(0);
					}
					damageable.setDamage(damageable.getDamage() + this.armor_decrease);
					
					armor.get().setItemMeta(meta);
					
					playMetalbendingSound(player.getLocation());
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
		
		RayTraceResult trace = player.getWorld().rayTraceEntities(player.getLocation().clone().add(0, 1, 0), player.getEyeLocation().getDirection(), this.select_range, 1.15, e -> e.getUniqueId() != player.getUniqueId());
		if (trace == null) {
			trace = player.getWorld().rayTraceBlocks(player.getEyeLocation().clone().add(0, 1, 0), player.getEyeLocation().getDirection(), this.select_range, FluidCollisionMode.NEVER, false);
		}
		if (trace != null && trace.getHitEntity() != null) {
			if (AREAS.containsKey(trace.getHitEntity())) {
				traceEntity = true;
				targetEntity = trace.getHitEntity();
				targetLocation = trace.getHitEntity().getLocation().clone().add(0, 1, 0);
			}
		} else if (trace != null && trace.getHitBlock() != null) {
			if (strippedBlocks.contains(trace.getHitBlock())) {
				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1F, 1F);
				traceBlock = true;

				for (Block block : GeneralMethods.getBlocksAroundPoint(trace.getHitBlock().getLocation().clone().add(0.5, 0.5, 0.5), 1.25)) {
					if (isIndestructible(block.getType())) continue;

					new TempBlock(block, Material.AIR.createBlockData(), this.revert_time);
				}
				strippedBlocks.remove(trace.getHitBlock());
			}
		}
		if (!traceEntity && !traceBlock) {
			targetLocation = GeneralMethods.getTargetedLocation(player, this.select_range).clone();
		}
	}
	
	public static void traceEntityBlock(Player player) {
		getAbility(player, MetalStrips.class).traceEntityBlock();
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
	
	protected boolean isArmorableEntity(Entity entity) {
		switch (entity.getType()) {
			case PLAYER:
			case ZOMBIE:
			case ZOMBIE_VILLAGER:
			case SKELETON:
			case HUSK:
			case STRAY:
			case PIGLIN:
			case ZOMBIFIED_PIGLIN:
			case DROWNED:
			case WITHER_SKELETON:
			case PILLAGER:
			case EVOKER:
			case VINDICATOR:
			case GIANT:
				return true;
		}
		return false;
	}
	
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
		
		private boolean cleanup;
		private boolean hasBlock;
		private ItemDisplay item;
		
		Strip(Location origin, Vector direction, MetalStrips metalStrips) {
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
		public void handle() {
			this.location.add(this.direction.multiply(this.speed));
			this.item.teleport(this.location);

			if (this.location.distanceSquared(this.origin) > this.range * this.range) {
				this.cleanup = true;
			}
			if (GeneralMethods.isRegionProtectedFromBuild(this.metalStrips, this.location)) {
				this.cleanup = true;
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
				if (!this.cleanup) {
					this.cleanup = true;
				}
			}
			if (!GeneralMethods.isTransparent(this.location.getBlock())) {
				this.hasBlock = true;
			}
		}
		private void trackArea(Entity entity) {
			if (entity instanceof LivingEntity) {
				if (entity instanceof Player) {
					if (Commands.invincible.contains(entity.getName())) return;
				}
				if (isTallEntity(entity)) {
					if (entity instanceof Giant) {
						bindMetal(entity, 12, 7, 3.5, 2.25);
					} else if (entity instanceof WitherSkeleton) {
						bindMetal(entity, 2.4, 1.8, 0.8, 0.35);
					} else {
						bindMetal(entity, 2, 1.5, 0.65, 0.35);
					}
				} else {
					if (!AREAS.containsKey(entity)) {
						AREAS.put(entity, new MetalArea[0]);
					}
					new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW, 30, this.amplifier), this.metalStrips);
				}
			}
		}
		private void bindMetal(Entity entity, double head, double chest, double legs, double feet) {
			MetalArea area;
			if (this.location.getY() <= getEntityYVal(entity, head) && this.location.getY() >= getEntityYVal(entity, chest)) {
				area = MetalArea.HEAD;
			} else if (this.location.getY() < getEntityYVal(entity, chest) && this.location.getY() >= getEntityYVal(entity, legs)) {
				area = MetalArea.CHEST;
			} else if (this.location.getY() < getEntityYVal(entity, legs) && this.location.getY() >= getEntityYVal(entity, feet)) {
				area = MetalArea.LEGS;
			} else if (this.location.getY() < getEntityYVal(entity, feet) && this.location.getY() >= getEntityYVal(entity, 0)) {
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
					case 3: new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.BLINDNESS, 30, this.amplifier), this.metalStrips); break;
					case 2: new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW_DIGGING, 30, this.amplifier), this.metalStrips); break;
					case 1:
					case 0:
						new TempStrippedEffects(entity, metalStrips.duration, new PotionEffect(PotionEffectType.SLOW, 30, this.amplifier), this.metalStrips); break;
				}
			}
			if (AREAS.containsKey(entity)) {
				if (AREAS.get(entity).length > 0) {
					if (idx != -1) {
						if (AREAS.get(entity)[idx] == MetalArea.NONE) {
							AREAS.get(entity)[idx] = area;
						}
					}
				}
			} else {
				AREAS.put(entity, new MetalArea[4]);
			}
		}
		private void applyArmor(int idx, BiConsumer<ItemStack, PotionEffectType> bi) {
			switch (idx) {
				case 3: bi.accept(new ItemStack(Material.IRON_BLOCK, 1), PotionEffectType.BLINDNESS); break;
				case 2: bi.accept(new ItemStack(Material.IRON_CHESTPLATE, 1), PotionEffectType.SLOW_DIGGING); break;
				case 1: bi.accept(new ItemStack(Material.IRON_LEGGINGS, 1), PotionEffectType.SLOW); break;
				case 0: bi.accept(new ItemStack(Material.IRON_BOOTS, 1), PotionEffectType.SLOW); break;
			}
		}
		private double getEntityYVal(Entity entity, double offset) {
			return entity.getLocation().clone().add(0, offset, 0).getY();
		}
		public Location getLocation() { return this.location; }
		public ItemDisplay getItem() { return this.item; }
		public boolean hasBlock() { return this.hasBlock; }
		public boolean cleanup() { return this.cleanup; }
	}
}

