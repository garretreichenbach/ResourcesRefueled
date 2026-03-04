package videogoose.resourcesrefueled.manager;

import api.mod.config.FileConfiguration;
import videogoose.resourcesrefueled.ResourcesRefueled;

public final class ConfigManager {

	private static FileConfiguration mainConfig;
	private static final String[] defaultMainConfig = {
			"debug_mode: false # If true, enables debug logging and features.",
			"fuel_cost_per_strength_unit: 0.5 # Heliogen canisters consumed per unit of extractor strength per tick.",
			"unfueled_extraction_efficiency: 0.3 # Fraction of normal extraction rate when running without Heliogen fuel (0.0-1.0).",
			"ftl_fuel_per_sector: 1.0 # Heliogen canisters consumed per sector of FTL jump distance.",
			"fuel_per_canister: 100.0 # Fluid units represented by one filled Heliogen Canister when drawing from a tank.",
			"ftl_unfueled_cooldown_multiplier: 3.0 # Multiplier applied to FTL cooldown when jumping without fuel.",
			"star_damage_threshold: 0.85 # Star proximity (0-1) above which hull damage applies. 1.0 = star centre.",
			"star_damage_scale: 10.0 # Hull damage per second at maximum proximity (threshold + 1.0).",
			"tank_explosion_yield_per_unit: 5.0 # Explosion damage yield per unit of Heliogen stored in a destroyed tank.",
			"fluid_levels_per_explosion: 3000 # Amount of fluid in a tank that corresponds to one explosion when the tank is destroyed. Higher values = fewer explosions.",
			"max_fluid_explosion_radius: 15.0 # Maximum radius for explosions caused by fluid tanks. Actual explosion radius scales with fluid level, up to this maximum.",
			"fluid_tank_explosion_damage: 10000.0 # Damage dealt by explosions caused by fluid tanks.",
	};

	public static void initialize(ResourcesRefueled instance) {
		mainConfig = instance.getConfig("config");
		saveDefaultConfig(defaultMainConfig);
	}

	private static void saveDefaultConfig(String[] config) {
		for(String line : config) {
			String key = line.substring(0, line.indexOf(':')).trim();
			String value = line.substring(line.indexOf(':') + 1, line.indexOf('#')).trim();
			String comment = line.substring(line.indexOf('#') + 1).trim();
			if(!mainConfig.getKeys().contains(key)) {
				mainConfig.set(key, value);
				mainConfig.setComment(key, comment);
			}
		}
		mainConfig.saveConfig();
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static double getFuelCostPerStrengthUnit() {
		try {
			return Double.parseDouble(mainConfig.getString("fuel_cost_per_strength_unit"));
		} catch(Exception e) {
			return 0.5;
		}
	}

	public static double getUnfueledExtractionEfficiency() {
		try {
			return Double.parseDouble(mainConfig.getString("unfueled_extraction_efficiency"));
		} catch(Exception e) {
			return 0.3;
		}
	}

	public static double getFtlFuelPerSector() {
		try {
			return Double.parseDouble(mainConfig.getString("ftl_fuel_per_sector"));
		} catch(Exception e) {
			return 1.0;
		}
	}

	public static double getFtlUnfueledCooldownMultiplier() {
		try {
			return Double.parseDouble(mainConfig.getString("ftl_unfueled_cooldown_multiplier"));
		} catch(Exception e) {
			return 3.0;
		}
	}

	public static float getStarDamageThreshold() {
		try {
			return Float.parseFloat(mainConfig.getString("star_damage_threshold"));
		} catch(Exception e) {
			return 0.85f;
		}
	}

	public static float getStarDamageScale() {
		try {
			return Float.parseFloat(mainConfig.getString("star_damage_scale"));
		} catch(Exception e) {
			return 10.0f;
		}
	}

	public static float getTankExplosionYieldPerUnit() {
		try {
			return Float.parseFloat(mainConfig.getString("tank_explosion_yield_per_unit"));
		} catch(Exception e) {
			return 5.0f;
		}
	}

	public static double getFuelPerCanister() {
		try {
			return Double.parseDouble(mainConfig.getString("fuel_per_canister"));
		} catch(Exception e) {
			return 100.0;
		}
	}

	public static int getFluidLevelPerExplosion() {
		try {
			return Integer.parseInt(mainConfig.getString("fluid_levels_per_explosion"));
		} catch(Exception e) {
			return 3000;
		}
	}

	public static float getMaxFluidExplosionRadius() {
		try {
			return Float.parseFloat(mainConfig.getString("max_fluid_explosion_radius"));
		} catch(Exception e) {
			return 15.0f;
		}
	}

	public static float getFluidExplosionDamage() {
		try {
			return Float.parseFloat(mainConfig.getString("fluid_tank_explosion_damage"));
		} catch(Exception e) {
			return 5.0f;
		}
	}
}


