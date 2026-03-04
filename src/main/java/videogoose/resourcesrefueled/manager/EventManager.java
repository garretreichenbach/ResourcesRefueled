package videogoose.resourcesrefueled.manager;

import api.listener.Listener;
import api.listener.events.controller.ServerInitializeEvent;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.events.world.WorldSaveEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import org.ithirahad.resourcesresourced.events.HarvesterStrengthUpdateEvent;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.fuel.StellarFuelManager;
import videogoose.resourcesrefueled.listener.HarvesterFuelEfficiencyListener;
import videogoose.resourcesrefueled.listener.SegmentPieceKillEvent;
import videogoose.resourcesrefueled.listener.ShipJumpFuelListener;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;

public class EventManager {

	public static SegmentPieceKillEvent killEvent;

	public static void initialize(ResourcesRefueled instance) {
		// Block kill listener (tank explosion handled here)
		FastListenerCommon.segmentPieceKilledListeners.add(killEvent = new SegmentPieceKillEvent());

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<ManagerContainerRegisterEvent>() {

			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				event.addModMCModule(new FluidTankSystemModule(event.getSegmentController(), event.getContainer(), ElementRegistry.HELIOGEN_TANK.getId(), ElementRegistry.HELIOGEN_PLASMA.getId()));
			}
		}, instance);

		// Heliogen fuel efficiency penalty for unfueled extractors
		StarLoader.registerListener(HarvesterStrengthUpdateEvent.class, new HarvesterFuelEfficiencyListener(), instance);

		// Load Heliogen persistence on server start
		StarLoader.registerListener(ServerInitializeEvent.class, new Listener<ServerInitializeEvent>() {
			@Override
			public void onEvent(ServerInitializeEvent event) {
				StellarFuelManager.loadFuelData();
			}
		}, instance);

		// Save Heliogen persistence on world save
		StarLoader.registerListener(WorldSaveEvent.class, new Listener<WorldSaveEvent>() {
			@Override
			public void onEvent(WorldSaveEvent event) {
				StellarFuelManager.saveFuelData();
			}
		}, instance);

		// FTL fuel consumption
		StarLoader.registerListener(ShipJumpEngageEvent.class, new ShipJumpFuelListener(), instance);
	}
}

