package videogoose.resourcesrefueled.manager;

import api.listener.Listener;
import api.listener.events.controller.ServerInitializeEvent;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.world.WorldSaveEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import org.ithirahad.resourcesresourced.events.HarvesterStrengthUpdateEvent;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.schine.network.server.ServerMessage;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.fuel.StellarFuelManager;
import videogoose.resourcesrefueled.listener.HarvesterFuelEfficiencyListener;
import videogoose.resourcesrefueled.listener.SegmentPieceKillEvent;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;
import videogoose.resourcesrefueled.utils.InventoryUtils;

public class EventManager {

	public static SegmentPieceKillEvent killEvent;

	public static void initialize(ResourcesRefueled instance) {
		// Block kill listener (tank explosion handled here)
		FastListenerCommon.segmentPieceKilledListeners.add(killEvent = new SegmentPieceKillEvent());

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
		StarLoader.registerListener(ShipJumpEngageEvent.class, new Listener<ShipJumpEngageEvent>() {
			@Override
			public void onEvent(ShipJumpEngageEvent event) {
				if(!(event.getController() instanceof Ship)) {
					// Not a ship - should never happen but just in case, don't do anything.
					return;
				}

				if(!event.getController().isOnServer()) {
					//Client-side event, ignore. Fuel consumption will be handled on the server and synced to the client, so we don't want to run this logic on the client at all.
					return;
				}

				Vector3i currentSector = event.getOriginalSectorPos();
				Vector3i targetSector = event.getNewSector();
				double jumpDistance = Vector3i.getDistance(currentSector, targetSector);
				double fuelPerCanister = ConfigManager.getFuelPerCanister();
				int canistersNeeded = (int) Math.ceil(jumpDistance * ConfigManager.getFtlFuelPerSector());
				if(canistersNeeded <= 0) {
					return;
				}

				ShipManagerContainer shipManagerContainer = ((Ship) event.getController()).getManagerContainer();
				if(shipManagerContainer.getModMCModule(ElementRegistry.HELIOGEN_TANK.getId()) == null) {
					//Attempt to use inventory canisters if no tanks are present, but warn the player that they should install tanks for better performance and reliability.
					consumeCanisters(shipManagerContainer, canistersNeeded);
				} else {
					FluidTankSystemModule module = (FluidTankSystemModule) shipManagerContainer.getModMCModule(ElementRegistry.HELIOGEN_TANK.getId());
					double fuelAvailable = module.getFluidTypeId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId() ? module.getCurrentFluidLevel() : 0;
					if(fuelAvailable >= canistersNeeded * fuelPerCanister) {
						module.setCurrentFluidLevel(fuelAvailable - canistersNeeded * fuelPerCanister);
						shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen] Consumed " + canistersNeeded + " canisters worth of fuel from Heliogen Tanks for jump.", ServerMessage.MESSAGE_TYPE_INFO);
					} else {
						shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen] Insufficient fuel in tanks for jump — attempting to use canisters from ship inventory. For better performance and reliability, ensure tanks are filled with Heliogen fuel.", ServerMessage.MESSAGE_TYPE_WARNING);
						consumeCanisters(shipManagerContainer, canistersNeeded);
					}
				}
			}

			private void consumeCanisters(ShipManagerContainer shipManagerContainer, int canistersNeeded) {
				shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen] No fuel tanks detected — attempting to use canisters from ship inventory. For better performance and reliability, install Heliogen Tanks to store fuel for FTL jumps.", ServerMessage.MESSAGE_TYPE_WARNING);
				int availableCanisters = 0;
				for(Inventory inventory : shipManagerContainer.getInventories().values()) {
					availableCanisters += inventory.getOverallQuantity(ElementRegistry.HELIOGEN_CANISTER_FILLED.getId());
				}
				if(availableCanisters < canistersNeeded) {
					shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen] Insufficient fuel canisters in ship inventory for jump — drive running on reserves. Cooldown extended.", ServerMessage.MESSAGE_TYPE_WARNING);
					//Todo: Apply cooldown
				} else {
					int canistersUsed = 0;
					for(Inventory inventory : shipManagerContainer.getInventories().values()) {
						int toUse = Math.min(inventory.getOverallQuantity(ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()), canistersNeeded - canistersUsed);
						if(toUse > 0) {
							InventoryUtils.removeItems(inventory, ElementRegistry.HELIOGEN_CANISTER_FILLED.getId(), toUse);
							canistersUsed += toUse;
							if(canistersUsed >= canistersNeeded) {
								break;
							}
						}
						//Note: This will consume canisters from inventories in an arbitrary order, which may not be ideal for player experience. A more complex implementation could prioritize certain inventories or allow the player to configure fuel consumption preferences.
						//However, implementing such a system would require additional UI and is beyond the scope of this initial implementation, so for now it just consumes from inventories in the order they are returned by getInventories().values(), which is effectively arbitrary.
						//In practice, players will likely just have one inventory (the main ship inventory) that contains canisters, so this should be sufficient for most cases.
					}
					shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen] Consumed " + canistersUsed + " fuel canisters from ship inventory for jump.", ServerMessage.MESSAGE_TYPE_INFO);
				}
			}
		}, instance);
	}
}

