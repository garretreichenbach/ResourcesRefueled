package videogoose.resourcesrefueled;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import org.schema.schine.resource.ResourceLoader;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.fuel.StellarFuelManager;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.manager.EventManager;
import videogoose.resourcesrefueled.manager.ResourceManager;

public final class ResourcesRefueled extends StarMod {

	private static ResourcesRefueled instance;
	public ResourcesRefueled() {
		instance = this;
	}
	public static ResourcesRefueled getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerCommands();
		registerPackets();
	}

	@Override
	public void onDisable() {
		StellarFuelManager.saveFuelData();
	}

	@Override
	public void onClientCreated(ClientInitializeEvent event) {

	}

	@Override
	public void onBlockConfigLoad(BlockConfig config) {
		logInfo("Registering elements...");
		try {
			ElementRegistry.registerElements();
			ElementRegistry.doOverwrites();
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

	private void registerPackets() {

	}
}
