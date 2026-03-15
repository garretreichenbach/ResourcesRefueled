package videogoose.resourcesreorganized;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import org.schema.schine.resource.ResourceLoader;
import videogoose.resourcesreorganized.commands.GiveFluidContainerCommand;
import videogoose.resourcesreorganized.commands.ReloadConfigCommand;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.fuel.EntityFuelManager;
import videogoose.resourcesreorganized.fuel.StellarFuelManager;
import videogoose.resourcesreorganized.industry.RecipeManager;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.manager.EventManager;
import videogoose.resourcesreorganized.manager.ItemLogisticsManager;
import videogoose.resourcesreorganized.manager.ResourceManager;

public final class ResourcesReorganized extends StarMod {

	private static ResourcesReorganized instance;

	public ResourcesReorganized() {
		instance = this;
	}

	public static ResourcesReorganized getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		ConfigManager.initialize(this);
		ItemLogisticsManager.initialize();
		EventManager.initialize(this);
		registerCommands();
	}

	@Override
	public void onDisable() {
		StellarFuelManager.saveFuelData();
		EntityFuelManager.saveCacheData();
		ItemLogisticsManager.shutdown();
	}

	@Override
	public void onClientCreated(ClientInitializeEvent event) {

	}

	@Override
	public void onBlockConfigLoad(BlockConfig config) {
		logInfo("Registering elements...");
		try {
			ElementRegistry.registerRRSBlocks();
			ElementRegistry.registerElements();
			ElementRegistry.doOverwrites();
			RecipeManager.registerRecipes();
			logInfo("Elements registered successfully");
		} catch(Exception e) {
			logException("[ResourcesReorganized] Failed to register elements — mod may not function correctly", e);
		}
	}

	@Override
	public void onResourceLoad(ResourceLoader loader) {
		ResourceManager.loadResources();
	}

	@Override
	public void logInfo(String message) {
		super.logInfo("[ResourcesReorganized] " + message);
	}

	@Override
	public void logWarning(String message) {
		super.logWarning("[ResourcesReorganized] " + message);
	}

	@Override
	public void logException(String message, Exception exception) {
		super.logException("[ResourcesReorganized] " + message, exception);
	}

	@Override
	public void logFatal(String message, Exception exception) {
		onDisable(); //Attempt to save fuel data and cache data before crashing, since a fatal error likely means the mod is in a broken state and continuing to run could cause further issues
		super.logFatal("[ResourcesReorganized] " + message, exception);
	}

	public void logDebug(String message) {
		if(ConfigManager.isDebugMode()) {
			logMessage("[DEBUG]: [ResourcesReorganized] " + message);
		}
	}

	private void registerCommands() {
		StarLoader.registerCommand(new GiveFluidContainerCommand());
		StarLoader.registerCommand(new ReloadConfigCommand());
	}
}
