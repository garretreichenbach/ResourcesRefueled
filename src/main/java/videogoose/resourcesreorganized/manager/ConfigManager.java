package videogoose.resourcesreorganized.manager;

import api.mod.config.FileConfiguration;
import videogoose.resourcesreorganized.ResourcesReorganized;

public final class ConfigManager {

	private static FileConfiguration mainConfig;

	private static final String[] defaultMainConfig = {
			"debug_mode: false # If true, enables debug logging and features.",
			"logistics_probe_enabled: false # If true, enables temporary inventory mutation probe logging mixins.",
			"logistics_intercept_enabled: false # If true, logistics mixins may intercept inventory mutations instead of allowing vanilla behavior.",
			"logistics_fail_open: true # If true, logistics hooks fail open and allow vanilla inventory behavior on errors.",
			"item_conveyor_require_port_for_advanced: false # If true, conveyor ingress requests require inventory-port endpoints (useful once advanced port filtering/splitting is enabled).",
			"item_logistics_transfers_per_tick: 24 # Max queued item transfer operations the logistics runtime processes per tick.",
			"item_logistics_max_queue_size: 2048 # Max queued item transfer requests before new requests are rejected.",
			"item_logistics_retry_delay_ticks: 10 # Delay (in ticks) before retrying transfers that had no route or temporary failure.",
			"item_logistics_max_attempts: 6 # Maximum attempts for a transfer request before it is marked failed.",
			"fuel_cost_per_strength_unit: 0.5 # Heliogen canisters consumed per unit of extractor strength per tick.",
			"fueled_extraction_bonus: 0.5 # Fraction of additional output added per consumed canister when extractors are fueled with Heliogen (e.g. 0.5 = +50% resources per canister used).",
			"condenser_base_output: 4 # Bonus Heliogen Plasma units produced per cycle at proximity 1.0 next to a normal star.",
			"condenser_proximity_scale: true # If false, condenser output is flat regardless of star proximity (base recipe only + star class bonus).",
			"ftl_fuel_per_sector: 0.0 # Heliogen canisters consumed per sector of FTL jump distance.",
			"capacity_per_canister: 100.0 # Capacity in fluid units of a single canister.",
			"capacity_per_tank: 500.0 # Fluid units of capacity contributed by each placed Fluid Tank block.",
			"fluid_levels_per_explosion: 3000 # Amount of fluid in a tank that corresponds to one explosion when the tank is destroyed. Higher values = fewer explosions.",
			"max_fluid_explosion_radius: 15.0 # Maximum radius for explosions caused by fluid tanks. Actual explosion radius scales with fluid level, up to this maximum.",
			"fluid_tank_explosion_damage: 10000.0 # Damage dealt by explosions caused by fluid tanks.",
			"pump_transfer_per_tick: 20.0 # Fluid units a pump attempts to move per tick.",
			"capacity_per_port: 500.0 # Fluid units of internal buffer capacity for each Fluid Port block.",
	};

	public static void initialize(ResourcesReorganized instance) {
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

	public static boolean isDebugMode() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("debug_mode"));
		} catch(Exception e) {
			return false;
		}
	}

	public static boolean isLogisticsProbeEnabled() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("logistics_probe_enabled"));
		} catch(Exception e) {
			return false;
		}
	}

	public static boolean isLogisticsInterceptEnabled() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("logistics_intercept_enabled"));
		} catch(Exception e) {
			return false;
		}
	}

	public static boolean isLogisticsFailOpen() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("logistics_fail_open"));
		} catch(Exception e) {
			return true;
		}
	}

	public static boolean isItemConveyorRequirePortForAdvanced() {
		try {
			return Boolean.parseBoolean(mainConfig.getString("item_conveyor_require_port_for_advanced"));
		} catch(Exception e) {
			return false;
		}
	}

	public static int getItemLogisticsTransfersPerTick() {
		try {
			return Integer.parseInt(mainConfig.getString("item_logistics_transfers_per_tick"));
		} catch(Exception e) {
			return 24;
		}
	}

	public static int getItemLogisticsQueueCapacity() {
		try {
			return Integer.parseInt(mainConfig.getString("item_logistics_max_queue_size"));
		} catch(Exception e) {
			return 2048;
		}
	}

	public static int getItemLogisticsRetryDelayTicks() {
		try {
			return Integer.parseInt(mainConfig.getString("item_logistics_retry_delay_ticks"));
		} catch(Exception e) {
			return 10;
		}
	}

	public static int getItemLogisticsMaxAttempts() {
		try {
			return Integer.parseInt(mainConfig.getString("item_logistics_max_attempts"));
		} catch(Exception e) {
			return 6;
		}
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

	public static double getCapacityPerCanister() {
		try {
			return Double.parseDouble(mainConfig.getString("capacity_per_canister"));
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

	public static double getCapacityPerTank() {
		try {
			return Double.parseDouble(mainConfig.getString("capacity_per_tank"));
		} catch(Exception e) {
			return 500.0;
		}
	}

	public static double getPumpTransferPerTick() {
		try {
			return Double.parseDouble(mainConfig.getString("pump_transfer_per_tick"));
		} catch(Exception e) {
			return 50.0;
		}
	}

	public static double getCapacityPerPort() {
		try {
			return Double.parseDouble(mainConfig.getString("capacity_per_port"));
		} catch(Exception e) {
			return 500.0;
		}
	}
}

