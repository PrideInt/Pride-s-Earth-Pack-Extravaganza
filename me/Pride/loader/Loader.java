package me.Pride.loader;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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

public class Loader implements Listener {
	
	public static List<FallingBlock> fallingBlocks = new ArrayList<>();
	
	@EventHandler
	public void onSneak(final PlayerToggleSneakEvent event) {
		if (event.isCancelled()) {
			return;
		}
		if (!event.isSneaking()) {
			return;
		}
		
		Player player = event.getPlayer();
		BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

		if (bPlayer == null) {
			return;
		}
		CoreAbility coreAbil = bPlayer.getBoundAbility();
		String abil = bPlayer.getBoundAbilityName();

		if (coreAbil == null) {
			return;	
		}

		if (bPlayer.canBendIgnoreCooldowns(coreAbil)) {
			if (bPlayer.canBend(CoreAbility.getAbility("Bulldoze")) && CoreAbility.getAbility(player, Bulldoze.class) == null) {
				new Bulldoze(player);
				
			} else if (bPlayer.canBend(CoreAbility.getAbility("RockWrecker")) && CoreAbility.getAbility(player, RockWrecker.class) == null) {
				new RockWrecker(player);
				
			} else if (bPlayer.canBend(CoreAbility.getAbility("SandSurge")) && CoreAbility.getAbility(player, SandSurge.class) == null) {
				new SandSurge(player);
				
			} else if (bPlayer.canBend(CoreAbility.getAbility("MetalStrips")) && abil.equalsIgnoreCase("MetalStrips")) {
				MetalStrips strips = CoreAbility.getAbility(player, MetalStrips.class);
				
				if (strips == null) return;
				
				strips.setMagnetizing(true);
				strips.setUsage(MetalStrips.Usage.ENTITY);
				EarthAbility.playMetalbendingSound(player.getLocation());
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onSwing(final PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) {
			return;
		}
		if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.isCancelled()) {
			return;
		}
		
		Player player = event.getPlayer();
		BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		
		if (bPlayer == null) {
			return;
		}
		CoreAbility coreAbil = bPlayer.getBoundAbility();
		String abil = bPlayer.getBoundAbilityName();

		if (coreAbil == null) {
			return;		
		}
		
		if (bPlayer.canBendIgnoreCooldowns(coreAbil)) {
			if (abil.equalsIgnoreCase("MetalStrips") && bPlayer.canBend(CoreAbility.getAbility("MetalStrips"))) {
				MetalStrips strips = CoreAbility.getAbility(player, MetalStrips.class);
				
				if (!CoreAbility.hasAbility(player, MetalStrips.class)) {
					new MetalStrips(player);
				} else {
					if (strips != null) {
						strips.shootStrips();
					}
				}
			} else if (abil.equalsIgnoreCase("SandSurge") && bPlayer.canBend(CoreAbility.getAbility("SandSurge"))) {
				SandSurge ss = CoreAbility.getAbility(player, SandSurge.class);
				
				if (ss == null) return;
				
				ss.throwSurge();
			}
		}
	}
	
	@EventHandler
	public void onPickup(EntityPickupItemEvent event) {
		if (event.getItem().hasMetadata("strippeditem")) {
			event.getItem().removeMetadata("strippeditem", ProjectKorra.plugin);
		}
	}
	
	@EventHandler
	public void removeBlocks(EntityChangeBlockEvent event) {
		if (event.getEntityType() == EntityType.FALLING_BLOCK) {
			if (fallingBlocks.remove(event.getEntity())) {
				event.setCancelled(true);
			}
		}
	}
	
	public static String getAuthor(Element element) {
		return element.getColor() + "" + ChatColor.UNDERLINE + "Prride";
	}
	
	public static String getVersion(Element element) {
		return element.getColor() + "" + ChatColor.UNDERLINE + "VERSION 1";
	}

}
