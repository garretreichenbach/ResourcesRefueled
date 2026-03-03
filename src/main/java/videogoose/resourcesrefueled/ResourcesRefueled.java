package videogoose.resourcesrefueled;

import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.manager.EventManager;

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
		super.onEnable();
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerCommands();
		registerPackets();
	}

	@Override
	public void onClientCreated(ClientInitializeEvent event) {

	}

	private void registerCommands() {

	}

	private void registerPackets() {

	}
}
