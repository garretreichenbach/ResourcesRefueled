package videogoose.resourcesreorganized;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import org.schema.schine.resource.ResourceLoader;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.fuel.EntityFuelManager;
import videogoose.resourcesreorganized.fuel.StellarFuelManager;
import videogoose.resourcesreorganized.industry.RecipeManager;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.manager.EventManager;
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
		EventManager.initialize(this);
		registerCommands();
	}

	@Override
	public void onDisable() {
		StellarFuelManager.saveFuelData();
		EntityFuelManager.saveCacheData();
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
			logException("[ResourcesRefueled] Failed to register elements — mod may not function correctly", e);
		}
	}

	@Override
	public void onResourceLoad(ResourceLoader loader) {
		ResourceManager.loadResources();
	}

	private void registerCommands() {

	}

	public void logDebug(String message) {
		if(ConfigManager.isDebugMode()) {
			logInfo("[DEBUG] " + message);
		}
	}
}
