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
import videogoose.resourcesrefueled.fuel.EntityFuelManager;
import videogoose.resourcesrefueled.fuel.StellarFuelManager;
import videogoose.resourcesrefueled.listener.HarvesterEnhancerOverrideListener;
import videogoose.resourcesrefueled.listener.SegmentPieceEventHandler;
import videogoose.resourcesrefueled.listener.ShipJumpFuelListener;
import videogoose.resourcesrefueled.listener.SolarCondenserTickListener;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;

public class EventManager {

	private static final SegmentPieceEventHandler segmentPieceEventHandler = new SegmentPieceEventHandler();

	public static void initialize(ResourcesRefueled instance) {
		FastListenerCommon.segmentPieceAddListeners.add(segmentPieceEventHandler);
		FastListenerCommon.segmentPieceRemoveListeners.add(segmentPieceEventHandler);
		FastListenerCommon.segmentPieceKilledListeners.add(segmentPieceEventHandler);
		FastListenerCommon.factoryManufactureListeners.add(new SolarCondenserTickListener());

		// Replace vanilla enhancer bonus on extractors with Heliogen-fuel-based boost
		StarLoader.registerListener(HarvesterStrengthUpdateEvent.class, new HarvesterEnhancerOverrideListener(), instance);

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<ManagerContainerRegisterEvent>() {

			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				FluidTankSystemModule tankModule = new FluidTankSystemModule(event.getContainer());
				event.addModMCModule(tankModule);

				// Sync the virtualised fuel cache from the live entity state now that it's loaded.
				// Canister count defaults to 0 at registration time (inventory may not be fully
				// populated yet); onPreManufacture will call syncFromLive again with the real count
				// on the first extractor tick.
				String uid = event.getSegmentController().getUniqueIdentifier();
				EntityFuelManager.syncFromLive(uid, tankModule, 0);
			}
		}, instance);

		// Load Heliogen persistence on server start
		StarLoader.registerListener(ServerInitializeEvent.class, new Listener<ServerInitializeEvent>() {
			@Override
			public void onEvent(ServerInitializeEvent event) {
				StellarFuelManager.loadFuelData();
				EntityFuelManager.loadCacheData();
			}
		}, instance);

		// Save Heliogen persistence on world save
		StarLoader.registerListener(WorldSaveEvent.class, new Listener<WorldSaveEvent>() {
			@Override
			public void onEvent(WorldSaveEvent event) {
				StellarFuelManager.saveFuelData();
				EntityFuelManager.saveCacheData();
			}
		}, instance);

		// FTL fuel consumption
		StarLoader.registerListener(ShipJumpEngageEvent.class, new ShipJumpFuelListener(), instance);
	}
}