package videogoose.resourcesrefueled.manager;

import api.mod.config.FileConfiguration;
import videogoose.resourcesrefueled.ResourcesRefueled;

public final class ConfigManager {

	private static FileConfiguration mainConfig;
	private static final String[] defaultMainConfig = {
			"debug_mode: false # If true, enables debug logging and features.",
			"fuel_cost_per_strength_unit: 0.5 # Heliogen canisters consumed per unit of extractor strength per tick.",
			"fueled_extraction_bonus: 0.5 # Fraction of additional output added per consumed canister when extractors are fueled with Heliogen (e.g. 0.5 = +50% resources per canister used).",
			"condenser_base_output: 4 # Bonus Heliogen Plasma units produced per cycle at proximity 1.0 next to a normal star.",
			"condenser_proximity_scale: true # If false, condenser output is flat regardless of star proximity (base recipe only + star class bonus).",
			"ftl_fuel_per_sector: 0.0 # Heliogen canisters consumed per sector of FTL jump distance.",
			"fuel_per_canister: 100.0 # Fluid units represented by one filled Heliogen Canister when drawing from a tank.",
			"fluid_levels_per_explosion: 3000 # Amount of fluid in a tank that corresponds to one explosion when the tank is destroyed. Higher values = fewer explosions.",
			"max_fluid_explosion_radius: 15.0 # Maximum radius for explosions caused by fluid tanks. Actual explosion radius scales with fluid level, up to this maximum.",
			"fluid_tank_explosion_damage: 10000.0 # Damage dealt by explosions caused by fluid tanks.",
			"fluid_tank_capacity_per_block: 500.0 # Fluid units of capacity contributed by each placed Fluid Tank block.",
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

	public static double getFuelCostPerStrengthUnit() {
		try {
			return Double.parseDouble(mainConfig.getString("fuel_cost_per_strength_unit"));
		} catch(Exception e) {
			return 0.5;
		}
	}

	public static double getFueledExtractionBonus() {
		try {
			return Double.parseDouble(mainConfig.getString("fueled_extraction_bonus"));
		} catch(Exception e) {
			return 0.5;
		}
	}

	public static int getCondenserBaseOutput() {
		try {
			return Integer.parseInt(mainConfig.getString("condenser_base_output"));
		} catch(Exception e) {
			return 4;
		}
	}

	public static boolean isCondenserProximityScaled() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("condenser_proximity_scale"));
		} catch(Exception e) {
			return true;
		}
	}

	public static double getFtlFuelPerSector() {
		try {
			return Double.parseDouble(mainConfig.getString("ftl_fuel_per_sector"));
		} catch(Exception e) {
			return 0.0;
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

	public static double getFluidTankCapacityPerBlock() {
		try {
			return Double.parseDouble(mainConfig.getString("fluid_tank_capacity_per_block"));
		} catch(Exception e) {
			return 500.0;
		}
	}
}


