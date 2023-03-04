package me.Pride.loader;

import java.util.ArrayList;
import java.util.List;

import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;

import me.Pride.abilities.Bulldoze;
import me.Pride.abilities.MetalStrips;
import me.Pride.abilities.RockWrecker;
import me.Pride.abilities.SandSurge;
import net.md_5.bungee.api.ChatColor;

public class Loader extends ElementalAbility implements AddonAbility, Listener {
	
	@EventHandler
	public void onSneak(final PlayerToggleSneakEvent event) {
		if (event.isCancelled() || !event.isSneaking()) {
			return;
		}
		Player player = event.getPlayer();
		BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

		if (bPlayer == null) return;
		
		CoreAbility coreAbil = bPlayer.getBoundAbility();
		String abil = bPlayer.getBoundAbilityName();

		if (coreAbil == null) return;

		if (bPlayer.canBendIgnoreCooldowns(coreAbil)) {
			if (abil.equalsIgnoreCase("Bulldoze")) {
				new Bulldoze(player);
			} else if (abil.equalsIgnoreCase("RockWrecker")) {
				new RockWrecker(player);
			} else if (abil.equalsIgnoreCase("SandSurge")) {
				new SandSurge(player);
			} else if (abil.equalsIgnoreCase("MetalStrips")) {
				if (CoreAbility.hasAbility(player, MetalStrips.class)) {
					MetalStrips.traceEntityBlock(player);
				}
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onSwing(final PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) return;
		if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.isCancelled()) return;
		
		Player player = event.getPlayer();
		BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		
		if (bPlayer == null) return;
		
		CoreAbility coreAbil = bPlayer.getBoundAbility();
		String abil = bPlayer.getBoundAbilityName();

		if (coreAbil == null) return;
		
		if (bPlayer.canBendIgnoreCooldowns(coreAbil)) {
			if (abil.equalsIgnoreCase("MetalStrips") && bPlayer.canBend(CoreAbility.getAbility("MetalStrips"))) {
				if (!CoreAbility.hasAbility(player, MetalStrips.class)) {
					new MetalStrips(player);
				} else {
					MetalStrips.shootStrips(player);
				}
			} else if (abil.equalsIgnoreCase("SandSurge")) {
				if (CoreAbility.hasAbility(player, SandSurge.class)) {
					SandSurge.throwSurge(player);
				}
			}
		}
	}
	
	@EventHandler
	public void onPickup(EntityPickupItemEvent event) {
		if (event.getItem().hasMetadata(getItemKey())) {
			event.getItem().removeMetadata(getItemKey(), ProjectKorra.plugin);
		}
	}
	
	@EventHandler
	public void removeBlocks(EntityChangeBlockEvent event) {
		if (event.getEntityType() == EntityType.FALLING_BLOCK) {
			if (event.getEntity().hasMetadata(getFallingBlocksKey())) {
				event.setCancelled(true);
			}
		}
	}
	
	public static String getAuthor(Element element) {
		return element.getSubColor().getColor() + "" + ChatColor.UNDERLINE + "Prride";
	}
	
	public static String getVersion(Element element) {
		return element.getSubColor().getColor() + "" + ChatColor.UNDERLINE + "VERSION 3";
	}
	
	public static String getFallingBlocksKey() {
		return "es:fallblocks";
	}
	public static String getItemKey() {
		return "es:items";
	}
	
	public Loader(Player player) { super(player); }
	
	@Override
	public void progress() { }
	@Override
	public boolean isSneakAbility() { return false; }
	@Override
	public boolean isHarmlessAbility() { return false; }
	@Override
	public boolean isIgniteAbility() { return false; }
	@Override
	public boolean isExplosiveAbility() { return false; }
	@Override
	public long getCooldown() { return 0; }
	@Override
	public String getName() { return "Loader"; }
	@Override
	public Element getElement() { return null; }
	@Override
	public Location getLocation() { return null; }
	@Override
	public String getAuthor() { return null; }
	@Override
	public String getVersion() { return null; }
	
	@Override
	public void load() {
		ProjectKorra.log.info(ChatColor.GREEN + "Earth abilities by Prride are loaded in!");
		ProjectKorra.plugin.getServer().getPluginManager().registerEvents(this, ProjectKorra.plugin);
		
		FileConfiguration config = ConfigManager.getConfig();
		
		// Bulldoze
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Enabled", true);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.BulldozeSpeed", 1.1);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.BulldozeRadius", 2.35);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.SelectRange", 10);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.MaxRange", 20);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.RangeIncrement", 0.2);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.Changeable", false);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.RevertBlocks", true);
		config.addDefault("ExtraAbilities.Prride.Bulldoze.RevertTime", 30000);
		
		// RockWrecker
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Enabled", true);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Cooldown", 6000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.FormTime.Prime", 3000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.FormTime.Full", 6500);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.SelectRange", 18);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Range", 20);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Speed", 1.1);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.StartingDamage", 2);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.PrimeDamage", 3);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Damage.FullDamage", 4);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Knockback", 3.5);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Revert.Revert", true);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.Revert.RevertTime", 12000);
		config.addDefault("ExtraAbilities.Prride.RockWrecker.CreateLavaPool", true);
		
		// Stalagmites
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Enabled", true);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Speed", 1.0);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Range", 18);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.RevertTime", 7000);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.Knockback", 1.2);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MinWidth", 4);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MaxWidth", 4);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MinHeight", 4);
		config.addDefault("ExtraAbilities.Prride.Stalagmites.MaxHeight", 6);
		
		// MetalStrips
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Enabled", true);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Cooldown", 12000);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Duration", 20000);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Range", 18);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Speed", 1.2);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.DecreaseArmor", 10);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Stripped.SelectRange", 12);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Stripped.Block.RevertTime", 20000);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Stripped.Entity.MagnetizeRadius", 6);
		config.addDefault("ExtraAbilities.Prride.MetalStrips.Stripped.Entity.MagnetizePullSpeed", 0.65);
		
		List<String> metalMaterials = new ArrayList<>();
		metalMaterials.add(Material.IRON_INGOT.toString());
		metalMaterials.add(Material.IRON_NUGGET.toString());
		
		config.addDefault("ExtraAbilities.Prride.MetalStrips.MetalMaterials", metalMaterials);
		
		// SandSurge
		config.addDefault("ExtraAbilities.Prride.SandSurge.Enabled", true);
		config.addDefault("ExtraAbilities.Prride.SandSurge.Cooldown", 5000);
		config.addDefault("ExtraAbilities.Prride.SandSurge.SelectRange", 12);
		config.addDefault("ExtraAbilities.Prride.SandSurge.SurgeWidth", 6);
		config.addDefault("ExtraAbilities.Prride.SandSurge.Launch", 8);
		config.addDefault("ExtraAbilities.Prride.SandSurge.LaunchSpeed", 1.2);
		config.addDefault("ExtraAbilities.Prride.SandSurge.Damage", 1.5);
		config.addDefault("ExtraAbilities.Prride.SandSurge.BlindDuration", 75);
		config.addDefault("ExtraAbilities.Prride.SandSurge.BlindAmplifier", 1);
		config.addDefault("ExtraAbilities.Prride.SandSurge.ThrowSpeed", 1.1);
		config.addDefault("ExtraAbilities.Prride.SandSurge.CollisionRadius", 1.3);
		
		ConfigManager.defaultConfig.save();
	}
	
	@Override
	public void stop() {
		HandlerList.unregisterAll(this);
		
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (entity.hasMetadata("stripfallblock")) {
					entity.remove();
				}
			}
		}
	}
}
