package me.Pride.abilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.EarthSmash;
import com.projectkorra.projectkorra.util.TempArmor;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.TempPotionEffect;

import me.Pride.loader.Loader;
import net.md_5.bungee.api.ChatColor;

public class MetalStrips extends MetalAbility implements AddonAbility {
	
	private static final String PATH = "ExtraAbilities.Prride.MetalStrips.";
	private static final FileConfiguration CONFIG = ConfigManager.getConfig();
	
	private enum Usage {
		ENTITY, BLOCK
	}
	
	@Attribute(Attribute.COOLDOWN)
	private static final long COOLDOWN = CONFIG.getLong(PATH + "Cooldown");
	@Attribute(Attribute.RANGE)
	private static final double RANGE = CONFIG.getDouble(PATH + "Range");
	@Attribute(Attribute.SPEED)
	private static final double SPEED = CONFIG.getDouble(PATH + "Speed");
	@Attribute("RevertTime")
	private static final long STRIP_TIME = CONFIG.getLong(PATH + "StripTime");
	@Attribute(Attribute.RADIUS)
	private static final double RADIUS = CONFIG.getDouble(PATH + "MagnetizeRadius");
	@Attribute(Attribute.SELECT_RANGE)
	private static final double SELECT_RANGE = CONFIG.getDouble(PATH + "SelectRange.Target"),
								SELECT_ENTITY = CONFIG.getDouble(PATH + "SelectRange.Entity");
	@Attribute(Attribute.SPEED)
	private static final double MAGNETIZE_SPEED = CONFIG.getDouble(PATH + "MagnetizeSpeed");
	@Attribute("DecreaseArmor")
	private static final int ARMOR_DECREASE = CONFIG.getInt(PATH + "DecreaseArmor");
	@Attribute(Attribute.RADIUS)
	private static final double BLOCK_RADIUS = CONFIG.getDouble(PATH + "BlockDamageRadius");
	@Attribute("RevertTime")
	private static final long REVERT_TIME = CONFIG.getLong(PATH + "RevertTime");
	private static final boolean REVERT = CONFIG.getBoolean(PATH + "Revert");
	
	private boolean remove;
	private boolean removeWithCooldown;
	private boolean stripBlock;
	private boolean isEarthSmash;
	
	public double TIME;
	
	private List<Strip> strips = new ArrayList<>(); // manages strips
	private List<Block> blocks = new CopyOnWriteArrayList<>(); // manages regular blocks
	private List<EarthSmash> ess = new ArrayList<>(); // manages EarthSmashes
	
	private Set<TempBlock> tempBlocks = new HashSet<>(); // manages temp blocks
	
	private Map<Entity, Strip> stripents = new ConcurrentHashMap<>(); // manages strip to entity relationship
	public Map<LivingEntity, Set<Strip.Area>> areas = new HashMap<>(); // manages entities to a list of confirmed areas
	
	private Random random = new Random();
	private Usage usage;
	
	// magnetizing variables
	private boolean magnetize;
	private boolean useTarget;
	private boolean restricted;
	private Entity target;
	private Location targetLocation;
	private Vector direction;

	public MetalStrips(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this) || !bPlayer.canMetalbend()) return;
		
		for (ItemStack i : player.getInventory().getContents()) {
			if (i != null) {
				if (hasIronMaterial(i.getType()) || hasIronArmor(i.getType())) {
					if (i.getAmount() >= 1) {
						shootStrips();
						start();
					}
				}
			}
		}
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
		for (Strip strip : strips) {
			locations.add(strip.getLocation());
		}
		return locations;
	}

	@Override
	public String getName() {
		return "MetalStrips";
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
		
		boolean stripped = false;
		
		for (Block block : blocks) {
			if (block == null || isAir(block.getType())) {
				blocks.remove(block);
			}
			Location centre = block.getLocation().clone();
			centre.add(0.5, 0.5, 0.5);
			spawnMetalParticles(centre, 1, 0.25F);
		}
		
		for (Iterator<Strip> itr = strips.iterator(); itr.hasNext();) {
			Strip strip = itr.next();
			
			strip.progress(e -> stripents.put(e, strip));
			
			if (strip.outOfRange() && !strip.isStripped()) {
				itr.remove();
				
			} else if (!isTransparent(strip.getLocation().getBlock())) {
				if (stripBlock) {
					List<Block> blocks = getBlocks(strip.getBlock().getLocation(), BLOCK_RADIUS),
							    tblocks = getBlocks(strip.getBlock().getLocation(), BLOCK_RADIUS * 1.75);
					
					for (Block b : blocks) {
						this.blocks.add(b);
					}
					for (Block tbs : tblocks) {
						if (TempBlock.isTempBlock(tbs)) {
							tempBlocks.add(TempBlock.get(tbs));
						}
					}
					// from Hiro's TerraSense
					for (EarthSmash es : CoreAbility.getAbilities(EarthSmash.class)) {
						if (es.getLocations() != null && !es.getLocations().isEmpty()
							&& es.getLocations().get(0).getWorld().equals(player.getWorld())
							&& es.getLocations().get(0).distance(player.getLocation()) <= RANGE) {
							
							for (Location esl : es.getLocations()) {
								if (esl == null) continue;
								
								TempBlock estb = TempBlock.get(esl.getBlock());
								
								if (tempBlocks.contains(estb)) {
									isEarthSmash = true;
									ess.add(es);
								}
							}
						}
					}
				}
				itr.remove();
			}
			
			if (strip.isStripped()) {
				stripped = true;
			}
		}
		
		stripents.keySet().stream().filter(e -> validEntities(e) && !armoredEntities(e)).forEach(e -> {
			float radius = 0;
			if (e instanceof Item) {
				radius = 0.15F;
			} else if (e instanceof FallingBlock) {
				radius = 0.5F;
			}
			if (e != null) {
				if (e.hasMetadata("strippeditem")) {
					spawnMetalParticles(e.getLocation(), 3, radius);
					
				} else if (e instanceof FallingBlock) {
					spawnMetalParticles(e.getLocation(), 3, radius);
				}
			}
		});
		
		for (LivingEntity e : areas.keySet()) {
			double size = (e.getEyeLocation().distance(e.getLocation()) / 2 + 0.8D);
			for (Strip.Area a : areas.get(e)) {
				spawnMetalParticles(e.getLocation().add(0, a.getY(), 0), 3, (float) (size / 4F));
			}
		}
		
		stripBlock();
		
		magnetize();
		
		if (stripped) {
			TIME += 0.05;
			
			if (TIME >= (double) (STRIP_TIME / 1000.0)) {
				removeWithCooldown = true;
				remove();
				return;
			}
		}
	}
	
	public void shootStrips() {
		if (player.isSneaking()) {
			usage = Usage.BLOCK;
		} else {
			usage = Usage.ENTITY;
		}
		boolean hasIngots = false;
		for (ItemStack i : player.getInventory().getContents()) {
			if (i != null) {
				if (hasIronMaterial(i.getType()) && i.getAmount() >= 1) {
					hasIngots = true;
					int amount = i.getAmount();
					amount--;
					i.setAmount(amount);
					
					playMetalbendingSound(player.getLocation());
					strips.add(new Strip(player, this));
					break;
				}
			}
		}
		if (!hasIngots) {
			for (ItemStack i : player.getInventory().getArmorContents()) {
				if (i != null) {
					if (hasIronArmor(i.getType())) {
						ItemMeta meta = i.getItemMeta();
						if (meta instanceof Damageable) {
							Damageable damage = (Damageable) meta;
							
							if ((i.getType().getMaxDurability() - damage.getDamage()) - ARMOR_DECREASE <= 0) {
								player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
								i.setAmount(0);
							}
							
							damage.setDamage(damage.getDamage() + ARMOR_DECREASE);
							
							i.setItemMeta(meta);
							
							playMetalbendingSound(player.getLocation());
							strips.add(new Strip(player, this));
							break;
						}
					}
				}
			}
		}
	}
	
	private void stripBlock() {
		if (player.isSneaking()) {
			if (usage == Usage.BLOCK) {
				stripBlock = true;
			}
		} else {
			if (stripBlock) {
				if (!tempBlocks.isEmpty()) {
					if (isEarthSmash) {
						player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1F, 1F);
						ess.forEach(es -> {
							for (Location esl : es.getLocations()) {
								if (esl == null) continue;
								
								TempBlock estb = TempBlock.get(esl.getBlock());
								
								spawnFallingBlock(esl, estb.getBlock());
								estb.revertBlock();
							}
							es.remove();
						});
					} else {
						List<Block> blocks = GeneralMethods.getBlocksAroundPoint(GeneralMethods.getTargetedLocation(player, RANGE), 3.5);
						player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1F, 1F);
						
						for (Block b : blocks) {
							if (tempBlocks.contains(TempBlock.get(b))) {
								TempBlock tb = TempBlock.get(b);
								
								if (tb == null) continue;
								
								spawnFallingBlock(tb.getLocation(), tb.getBlock());
								tb.revertBlock();
								TempBlock.removeBlock(b);
							}
						}
					}
					tempBlocks.clear();
				}
				if (!this.blocks.isEmpty()) {
					player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1F, 1F);
					
					for (Block b : this.blocks) {
						spawnFallingBlock(b.getLocation(), b);
						if (REVERT) {
							new TempBlock(b, Material.AIR.createBlockData(), REVERT_TIME);
						} else {
							b.setType(Material.AIR);
						}
					}
					this.blocks.clear();
				}
			}
			stripBlock = false;
		}
	}
	
	public void magnetize() {
		if (!stripents.keySet().isEmpty()) {
			playMetalbendingSound(player.getLocation());
			
			if (magnetize) {
				if (player.isSneaking()) {
					for (double i = 0; i <= SELECT_ENTITY; i++) {
						target = GeneralMethods.getTargetedEntity(player, i);
						
						if (target != null) {
							Strip s = stripents.get(target);
							
							if (s == null) continue;
							
							if (s.isStripped()) {
								useTarget = true;
								break;
							}
						}
					}
					for (Entity e : stripents.keySet()) {
						direction = GeneralMethods.getDirection(e.getLocation(), targetLocation);
						
						Predicate<Entity> filter = ent -> ent.getUniqueId() != player.getUniqueId() && validEntities(ent);
						
						if (useTarget) {
							if (target == null) {
								continue;
							}
							filter = ent -> ent.getUniqueId() != target.getUniqueId() && ent.getUniqueId() != player.getUniqueId() && validEntities(ent);
						}
						
						Stream<Entity> entities = getEntities(player.getLocation(), RADIUS * 2, filter);
						
						entities.forEach(ent -> { 
							if (useTarget) {
								direction = GeneralMethods.getDirection(ent.getLocation(), target.getLocation());
							}
							restricted = false; 
						});
						
						if (!restricted) {
							e.setVelocity(direction.normalize().multiply(MAGNETIZE_SPEED));
							e.setFallDistance(0);
						}
					}
				} else {
					magnetize = false;
				}
				if (remove || usage == Usage.BLOCK) {
					magnetize = false;
				}
			}
		}
	}
	
	public void setMagnetizing(boolean magnetize) {
		useTarget = false;
		restricted = true;
		targetLocation = GeneralMethods.getTargetedLocation(player, SELECT_RANGE);
		this.magnetize = magnetize;
	}

	@Override
	public String getAuthor() {
		return Loader.getAuthor(Element.METAL);
	}

	@Override
	public String getVersion() {
		return Loader.getVersion(Element.METAL);
	}
	
	@Override
	public String getDescription() {
		return "An advanced metalbending technique that involves firing rapid strikes of metal at a target in order to perform attacks that render "
				+ "them onto the user's control. As shown through Kuvira's swift and graceful metal attacks used to disorient and manipulate her targets, "
				+ "this ability allows the user to launch as many metal materials as they have on hand to help out in battle or to be used as utility.";
	}
	
	@Override
	public String getInstructions() {
		return ChatColor.GOLD + "To use this ability, left click to shoot out metal strips. Additionally, if you do not have iron ingots on hand, donning iron armor "
				+ "while using this ability will draw out metal from your armor to be used; with the durability decreasing every strip."
				+ "\n(" + ChatColor.UNDERLINE + "Entities" + ChatColor.RESET + "" + ChatColor.GOLD + ") "
				+ "To place entities under your control, simply left click to shoot out a strip while targeting an entity, and whatever "
				+ "area you target will place iron armor (strips) onto that area. Binding the head area will cause blindness to the target, "
				+ "binding the chest area will cause slow digging, and binding the feet area will cause slowness. Additionally, you can also shoot "
				+ "strips out towards falling blocks and items to manipulate them as well. While these entities are binded by metal, you can sneak "
				+ "to magnetize them. Sneaking while looking at a binded entity will magnetize every bound entity in the area towards that entity, while "
				+ "sneaking at a desired target location will magnetize them towards that location."
				+ "\n(" + ChatColor.UNDERLINE + "Blocks" + ChatColor.RESET + "" + ChatColor.GOLD + ") "
				+ "To place blocks under your control, sneak while left clicking to shoot out a strip towards any block "
				+ "Release sneak as soon as it is bound to destroy the blocks. Currently, the only bending blocks this applies to are EarthSmash blocks.";
	}
	
	public static class Strip {
		
		private boolean stripped;
		
		private Player player;
		private MetalStrips ability;
		
		private Location origin, location;
		private Vector direction;
		
		private enum Area {
			HEAD(2), CHEST(1), LEGS(0.5), FEET(0);
			
			private double y;
			
			private Area(double y) {
				this.y = y;
			}
			
			public double getY() {
				return y;
			}
		}
		
		private Area area;
		
		private List<TempArmor> armorStrips = new ArrayList<>();
		
		public Strip(Player player, MetalStrips ability) {
			this.player = player;
			this.ability = ability;
			this.origin = player.getLocation().add(0, 1, 0);
			this.location = origin.clone();
			this.direction = player.getLocation().getDirection();
		}
		
		public void progress(Consumer<Entity> ent) {
			if (!stripped) {
				location.add(direction.normalize().multiply(SPEED));
				
				Item strip = player.getWorld().dropItem(location, new ItemStack(Material.IRON_INGOT, 1));
				strip.setPickupDelay(61);
				strip.remove();
				
				List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, 1.1);
				Optional<Entity> entity = entities.stream().filter(e -> e.getUniqueId() != player.getUniqueId() && validEntities(e)).findFirst();
				
				if (entity.isPresent()) {
					Entity en = entity.orElse(null);
					if (en instanceof LivingEntity) {
						LivingEntity e = (LivingEntity) en;
						if (trackArea(location.getY(), e)) {
							bindMetal(area, e);
							ent.accept(e);
							stripped = true;
						}
					} else if (validEntities(en) && !(en instanceof LivingEntity)) {
						if (en instanceof Item) {
							en.setMetadata("strippeditem", new FixedMetadataValue(ProjectKorra.plugin, 0));
						}
						ent.accept(en);
						stripped = true;
					}
				} 
			}
		}
		
		private boolean trackArea(double y, LivingEntity entity) {
			if (y <= trackY(entity, 2) && y >= trackY(entity, 1.5)) {
				area = Area.HEAD;
				
			} else if (y >= trackY(entity, 0.65) && y < trackY(entity, 1.5)) {
				area = Area.CHEST;
				
			} else if (y >= trackY(entity, 0.35) && y < trackY(entity, 0.65)) {
				area = Area.LEGS;
				
			} else if (y >= trackY(entity, 0)) {
				area = Area.FEET;
			}
			if (area != null) {
				if (!ability.areas.containsKey(entity)) {
					ability.areas.put(entity, new HashSet<>());
				}
				ability.areas.get(entity).add(area);
				return true;
			}
			return false;
		}
		
		private double trackY(Entity entity, double y) {
			return entity.getLocation().add(0, y, 0).getY();
		}
		
		private void bindMetal(Area area, LivingEntity entity) {
			ItemStack[] pos = new ItemStack[4];
			
			final int duration = (int) ((((double) STRIP_TIME / (double) 1000) - ability.TIME) / 0.05);
			
			switch (area) {
			
			case HEAD:
				pos[3] = new ItemStack(Material.IRON_HELMET);
				new TempPotionEffect(entity, new PotionEffect(PotionEffectType.BLINDNESS, duration, Integer.MAX_VALUE));
				break;
			case CHEST:
				pos[2] = new ItemStack(Material.IRON_CHESTPLATE);
				new TempPotionEffect(entity, new PotionEffect(PotionEffectType.SLOW_DIGGING, duration, 2));
				break;
			case LEGS:
				pos[1] = new ItemStack(Material.IRON_LEGGINGS);
				break;
			case FEET:
				pos[0] = new ItemStack(Material.IRON_BOOTS);
				new TempPotionEffect(entity, new PotionEffect(PotionEffectType.SLOW, duration, 1));
				break;
			}
			armorStrips.add(new TempArmor(entity, 250000L, ability, pos));
		}
		
		public boolean outOfRange() {
			return origin.distance(location) > RANGE;
		}
		
		public boolean isStripped() {
			return stripped;
		}
		
		public Location getLocation() {
			return location;
		}
		
		public Area getArea() {
			return area;
		}
		
		public Block getBlock() {
			return location.getBlock();
		}
		
		public List<Block> getNearBlocks() {
			return GeneralMethods.getBlocksAroundPoint(location, 1.25);
		}
		
		public List<TempArmor> getArmorStrips() {
			return armorStrips;
		}
	}
	
	private void spawnFallingBlock(Location location, Block block) {
		double x = random.nextDouble() * 3;
		double z = random.nextDouble() * 3;
		double y = random.nextDouble() * 3;

		x = (random.nextBoolean()) ? x : -x;
		z = (random.nextBoolean()) ? z : -z;
		y = (random.nextBoolean()) ? y : -y;

		FallingBlock fb = player.getWorld().spawnFallingBlock(location, block.getType().createBlockData());
		fb.setVelocity(new Vector(-x, -y, -z).normalize().multiply(-1));
		fb.setDropItem(false);
		
		Loader.fallingBlocks.add(fb);
	}
	
	private void spawnMetalParticles(Location location, int amount, float radius) {
		player.getWorld().spawnParticle(Particle.REDSTONE, location, amount, radius, radius, radius, 0, new DustOptions(Color.fromRGB(176, 173, 172), 1));
	}
	
	private Stream<Entity> getEntities(Location location, double radius, Predicate<Entity> predicate) {
		return GeneralMethods.getEntitiesAroundPoint(location, radius).stream().filter(predicate);
	}
	
	private List<Block> getBlocks(Location location, double radius) {
		return GeneralMethods.getBlocksAroundPoint(location, radius);
	}
	
	private static boolean validEntities(Entity entity) {
		return entity instanceof LivingEntity || entity instanceof FallingBlock || entity instanceof Item || entity instanceof ArmorStand;
	}
	
	private static boolean armoredEntities(Entity entity) {
		return entity instanceof Zombie || entity instanceof Skeleton || entity instanceof Player || entity instanceof Piglin;
	}
	
	private boolean hasIronArmor(Material material) {
		switch (material) {
			case IRON_CHESTPLATE:
				return true;
			case IRON_LEGGINGS:
				return true;
			case IRON_HELMET:
				return true;
			case IRON_BOOTS:
				return true;
		}
		return false;
	}
	
	private boolean hasIronMaterial(Material material) {
		final List<String> MATERIALS = CONFIG.getStringList(PATH + "MetalMaterials");
		if (MATERIALS.contains(material.toString())) {
			return true;
		}
		return false;
	}
	
	@Override
	public void remove() {
		remove = true;
		
		if (removeWithCooldown) bPlayer.addCooldown(this);
		
		stripents.keySet().stream().filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e).forEach(e -> {
			Strip strip = stripents.get(e);
			
			if (TempArmor.getTempArmorList(e).contains(strip.getArmorStrips())) {
				strip.getArmorStrips().forEach(a -> a.revert());
			}
			TempArmor.getTempArmorList(e).forEach(a -> a.revert());
		});
		
		super.remove();
	}

	@Override
	public void load() {
		
	}

	@Override
	public void stop() {
		
	}

}
