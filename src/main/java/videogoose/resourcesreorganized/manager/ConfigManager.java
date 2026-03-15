package videogoose.resourcesreorganized.manager;

import api.utils.simpleconfig.SimpleConfigBool;
import api.utils.simpleconfig.SimpleConfigContainer;
import api.utils.simpleconfig.SimpleConfigDouble;
import api.utils.simpleconfig.SimpleConfigInt;
import videogoose.resourcesreorganized.ResourcesReorganized;

public final class ConfigManager {

	private static SimpleConfigContainer config;

	private static SimpleConfigBool debugMode;
	private static SimpleConfigBool logisticsProbeEnabled;
	private static SimpleConfigBool logisticsInterceptEnabled;
	private static SimpleConfigBool logisticsFailOpen;
	private static SimpleConfigBool itemConveyorRequirePortForAdvanced;

	private static SimpleConfigInt itemLogisticsTransfersPerTick;
	private static SimpleConfigInt itemLogisticsQueueSize;
	private static SimpleConfigInt itemLogisticsRetryDelayTicks;
	private static SimpleConfigInt itemLogisticsMaxAttempts;
	private static SimpleConfigInt fluidLevelsPerExplosion;

	private static SimpleConfigDouble fuelCostPerStrengthUnit;
	private static SimpleConfigDouble fueledExtractionBonus;
	private static SimpleConfigDouble ftlFuelPerSector;
	private static SimpleConfigDouble capacityPerCanister;
	private static SimpleConfigDouble capacityPerTank;
	private static SimpleConfigDouble maxFluidExplosionRadius;
	private static SimpleConfigDouble fluidTankExplosionDamage;
	private static SimpleConfigDouble pumpTransferPerTick;
	private static SimpleConfigDouble capacityPerPort;

	private static SimpleConfigInt condenserBaseOutput;
	private static SimpleConfigBool condenserProximityScale;

	private ConfigManager() {
	}

	public static void initialize(ResourcesReorganized instance) {
		config = new SimpleConfigContainer(instance, "config", false);

		debugMode = new SimpleConfigBool(config, "debug_mode", false, "If true, enables debug logging and features.");
		logisticsProbeEnabled = new SimpleConfigBool(config, "logistics_probe_enabled", false, "If true, enables temporary inventory mutation probe logging mixins.");
		logisticsInterceptEnabled = new SimpleConfigBool(config, "logistics_intercept_enabled", true, "If true, logistics mixins may intercept inventory mutations instead of allowing vanilla behavior.");
		logisticsFailOpen = new SimpleConfigBool(config, "logistics_fail_open", false, "If true, logistics hooks fail open and allow vanilla inventory behavior on errors.");
		itemConveyorRequirePortForAdvanced = new SimpleConfigBool(config, "item_conveyor_require_port_for_advanced", true, "If true, conveyor ingress requests require inventory-port endpoints (useful once advanced port filtering/splitting is enabled).");

		itemLogisticsTransfersPerTick = new SimpleConfigInt(config, "item_logistics_transfers_per_tick", 24, "Max queued item transfer operations the logistics runtime processes per tick.");
		itemLogisticsQueueSize = new SimpleConfigInt(config, "item_logistics_max_queue_size", 2048, "Max queued item transfer requests before new requests are rejected.");
		itemLogisticsRetryDelayTicks = new SimpleConfigInt(config, "item_logistics_retry_delay_ticks", 10, "Delay (in ticks) before retrying transfers that had no route or temporary failure.");
		itemLogisticsMaxAttempts = new SimpleConfigInt(config, "item_logistics_max_attempts", 6, "Maximum attempts for a transfer request before it is marked failed.");

		fuelCostPerStrengthUnit = new SimpleConfigDouble(config, "fuel_cost_per_strength_unit", 0.5, "Heliogen canisters consumed per unit of extractor strength per tick.");
		fueledExtractionBonus = new SimpleConfigDouble(config, "fueled_extraction_bonus", 0.5, "Fraction of additional output added per consumed canister when extractors are fueled with Heliogen (e.g. 0.5 = +50% resources per canister used).");
		condenserBaseOutput = new SimpleConfigInt(config, "condenser_base_output", 4, "Bonus Heliogen Plasma units produced per cycle at proximity 1.0 next to a normal star.");
		condenserProximityScale = new SimpleConfigBool(config, "condenser_proximity_scale", true, "If false, condenser output is flat regardless of star proximity (base recipe only + star class bonus).");
		ftlFuelPerSector = new SimpleConfigDouble(config, "ftl_fuel_per_sector", 0.0, "Heliogen canisters consumed per sector of FTL jump distance.");
		capacityPerCanister = new SimpleConfigDouble(config, "capacity_per_canister", 100.0, "Capacity in fluid units of a single canister.");
		capacityPerTank = new SimpleConfigDouble(config, "capacity_per_tank", 500.0, "Fluid units of capacity contributed by each placed Fluid Tank block.");
		fluidLevelsPerExplosion = new SimpleConfigInt(config, "fluid_levels_per_explosion", 3000, "Amount of fluid in a tank that corresponds to one explosion when the tank is destroyed. Higher values = fewer explosions.");
		maxFluidExplosionRadius = new SimpleConfigDouble(config, "max_fluid_explosion_radius", 15.0, "Maximum radius for explosions caused by fluid tanks. Actual explosion radius scales with fluid level, up to this maximum.");
		fluidTankExplosionDamage = new SimpleConfigDouble(config, "fluid_tank_explosion_damage", 10000.0, "Damage dealt by explosions caused by fluid tanks.");
		pumpTransferPerTick = new SimpleConfigDouble(config, "pump_transfer_per_tick", 20.0, "Fluid units a pump attempts to move per tick.");
		capacityPerPort = new SimpleConfigDouble(config, "capacity_per_port", 500.0, "Fluid units of internal buffer capacity for each Fluid Port block.");

		config.readWriteFields();
		if(isDebugMode()) {
			String mode = config.isServer() ? "server" : (config.local ? "client-local" : "client-synced");
			instance.logInfo("Config initialized via SimpleConfigContainer (mode=" + mode + ")");
		}
	}

	public static void reload() {
		if(config != null) {
			config.readFields();
		}
	}

	public static boolean isDebugMode() {
		return boolOrDefault(debugMode, false);
	}

	public static boolean isLogisticsProbeEnabled() {
		return boolOrDefault(logisticsProbeEnabled, false);
	}

	public static boolean isLogisticsInterceptEnabled() {
		return boolOrDefault(logisticsInterceptEnabled, true);
	}

	public static boolean isLogisticsFailOpen() {
		return boolOrDefault(logisticsFailOpen, false);
	}

	public static boolean isItemConveyorRequirePortForAdvanced() {
		return boolOrDefault(itemConveyorRequirePortForAdvanced, true);
	}

	public static int getItemLogisticsTransfersPerTick() {
		return clampInt(intOrDefault(itemLogisticsTransfersPerTick, 24), 1, 1000000);
	}

	public static int getItemLogisticsQueueCapacity() {
		return clampInt(intOrDefault(itemLogisticsQueueSize, 2048), 1, 10000000);
	}

	public static int getItemLogisticsRetryDelayTicks() {
		return clampInt(intOrDefault(itemLogisticsRetryDelayTicks, 10), 1, 1000000);
	}

	public static int getItemLogisticsMaxAttempts() {
		return clampInt(intOrDefault(itemLogisticsMaxAttempts, 6), 1, 1000000);
	}

	public static double getFuelCostPerStrengthUnit() {
		return Math.max(0.0, doubleOrDefault(fuelCostPerStrengthUnit, 0.5));
	}

	public static double getFueledExtractionBonus() {
		return Math.max(0.0, doubleOrDefault(fueledExtractionBonus, 0.5));
	}

	public static int getCondenserBaseOutput() {
		return clampInt(intOrDefault(condenserBaseOutput, 4), 0, 1000000);
	}

	public static boolean isCondenserProximityScaled() {
		return boolOrDefault(condenserProximityScale, true);
	}

	public static double getFtlFuelPerSector() {
		return Math.max(0.0, doubleOrDefault(ftlFuelPerSector, 0.0));
	}

	public static double getCapacityPerCanister() {
		return Math.max(0.0, doubleOrDefault(capacityPerCanister, 100.0));
	}

	public static int getFluidLevelPerExplosion() {
		return clampInt(intOrDefault(fluidLevelsPerExplosion, 3000), 1, 1000000000);
	}

	public static float getMaxFluidExplosionRadius() {
		return (float) Math.max(0.0, doubleOrDefault(maxFluidExplosionRadius, 15.0));
	}

	public static float getFluidExplosionDamage() {
		return (float) Math.max(0.0, doubleOrDefault(fluidTankExplosionDamage, 5.0));
	}

	public static double getCapacityPerTank() {
		return Math.max(0.0, doubleOrDefault(capacityPerTank, 500.0));
	}

	public static double getPumpTransferPerTick() {
		return Math.max(0.0, doubleOrDefault(pumpTransferPerTick, 50.0));
	}

	public static double getCapacityPerPort() {
		return Math.max(0.0, doubleOrDefault(capacityPerPort, 500.0));
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean boolOrDefault(SimpleConfigBool entry, boolean defaultValue) {
		if(entry == null || entry.getValue() == null) {
			return defaultValue;
		}
		return entry.getValue();
	}

	private static int intOrDefault(SimpleConfigInt entry, int defaultValue) {
		if(entry == null || entry.getValue() == null) {
			return defaultValue;
		}
		return entry.getValue();
	}

	private static double doubleOrDefault(SimpleConfigDouble entry, double defaultValue) {
		if(entry == null || entry.getValue() == null) {
			return defaultValue;
		}
		return entry.getValue();
	}
}
