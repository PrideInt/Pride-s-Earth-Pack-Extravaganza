package me.Pride.util;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.util.TempArmor;
import me.Pride.abilities.MetalStrips;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TempStrippedEffects {
	private static final Set<TempStrippedEffects> EFFECTS = new HashSet<>();

	private long duration;
	private MetalStrips.MetalArea area;
	private TempArmor armor;
	private Entity entity;
	private PotionEffect potionEffect;

	private long start;

	public TempStrippedEffects(Entity entity, long duration, ItemStack[] armor, PotionEffect effect, MetalStrips.MetalArea area, CoreAbility ability) {
		this.armor = new TempArmor((LivingEntity) entity, duration, ability, armor);
		this.entity = entity;
		this.potionEffect = effect;
		this.area = area;
		this.duration = duration;

		this.start = System.currentTimeMillis();
		EFFECTS.add(this);
	}

	public TempStrippedEffects(Entity entity, long duration, PotionEffect effect, CoreAbility ability) {
		this.entity = entity;
		this.potionEffect = effect;
		this.duration = duration;

		this.start = System.currentTimeMillis();
		EFFECTS.add(this);
	}

	public static void handle() {
		MetalStrips.AREAS.keySet().forEach(entity -> {
			if (MetalStrips.AREAS.get(entity).length != 0) {
				if (entity instanceof LivingEntity) {
					if (TempArmor.getTempArmorList((LivingEntity) entity).size() == 0) {
						MetalStrips.AREAS.remove(entity);
					}
				}
			}
		});

		for (Iterator<TempStrippedEffects> itr = EFFECTS.iterator(); itr.hasNext(); ) {
			TempStrippedEffects effect = itr.next();
			Map<Entity, MetalStrips.MetalArea[]> areas = MetalStrips.AREAS;

			if (areas.get(effect.getEntity()) != null && areas.get(effect.getEntity()).length == 0) {
				if (System.currentTimeMillis() > effect.getStartTime() + effect.getDuration()) {
					areas.remove(effect.getEntity());
					itr.remove();
				}
			} else {
				if (effect.getArmor() != null) {
					if (System.currentTimeMillis() > effect.getArmor().getStartTime() + effect.getArmor().getDuration()) {
						for (Entity entity : areas.keySet()) {
							for (int i = 0; i < 4; i++) {
								if (areas.get(entity)[i] == effect.getArea()) {
									areas.get(entity)[i] = MetalStrips.MetalArea.NONE;
								}
							}
						}
						itr.remove();
					}
				}
			}
			if (effect.getEntity() instanceof LivingEntity) {
				((LivingEntity) effect.getEntity()).addPotionEffect(effect.getPotionEffect());
			}
		}
	}

	public TempArmor getArmor() {
		return this.armor;
	}

	public Entity getEntity() {
		return this.entity;
	}

	public PotionEffect getPotionEffect() {
		return this.potionEffect;
	}

	public MetalStrips.MetalArea getArea() {
		return this.area;
	}

	public long getStartTime() {
		return this.start;
	}

	public long getDuration() {
		return this.duration;
	}
}
