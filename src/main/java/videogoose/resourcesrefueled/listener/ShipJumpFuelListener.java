package videogoose.resourcesrefueled.listener;

import api.listener.Listener;
import api.listener.events.entity.ShipJumpEngageEvent;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.server.controller.SectorSwitch;
import org.schema.game.server.data.GameServerState;
import org.schema.schine.network.server.ServerMessage;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;
import videogoose.resourcesrefueled.utils.InventoryUtils;

/**
 * Handles Heliogen fuel consumption when a ship engages its FTL drive.
 * <p>
 * Priority order for fuel sources:
 *  1. Heliogen Tank multiblock (FluidTankSystemModule) — preferred, silent consumption.
 *  2. Heliogen Canister (Filled) items in any ship inventory — fallback with warning.
 *  3. Neither available — jump is still allowed but cooldown penalty is applied .
 */
public class ShipJumpFuelListener extends Listener<ShipJumpEngageEvent> {

	@Override
	public void onEvent(ShipJumpEngageEvent event) {
		if(!(event.getController() instanceof Ship)) return;
		if(!event.getController().isOnServer()) return;

		Vector3i currentSector = event.getOriginalSectorPos();
		Vector3i targetSector = event.getNewSector();
		double jumpDistance = Vector3i.getDistance(currentSector, targetSector);

		int canistersNeeded = (int) Math.ceil(jumpDistance * ConfigManager.getFtlFuelPerSector());
		if(canistersNeeded <= 0) return;

		ShipManagerContainer shipManagerContainer = ((Ship) event.getController()).getManagerContainer();
		FluidTankSystemModule tankModule = (FluidTankSystemModule) shipManagerContainer.getModMCModule(ElementRegistry.HELIOGEN_TANK.getId());

		if(tankModule == null) {
			// No tanks installed — fall back to inventory canisters with a warning.
			int available = countInventoryCanisters(shipManagerContainer);
			Vector3i redirect = calcShortJumpTarget(currentSector, targetSector, available, canistersNeeded);
			shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen System] No fuel tanks detected — attempting to use canisters from inventory.\nInstall Heliogen Tanks for better performance and reliability.", ServerMessage.MESSAGE_TYPE_WARNING);
			consumeCanisters(event, shipManagerContainer, canistersNeeded, redirect);
			return;
		}

		// Tanks present — check if they hold Heliogen and have enough fuel.
		boolean tankHoldsHeliogen = tankModule.getFluidId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		double fuelPerCanister = ConfigManager.getFuelPerCanister();
		double fuelRequired = canistersNeeded * fuelPerCanister;
		double fuelAvailable = tankHoldsHeliogen ? tankModule.getCurrentFluidLevel() : 0;

		if(fuelAvailable >= fuelRequired) {
			tankModule.setCurrentFluidLevel(fuelAvailable - fuelRequired);
			shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen System] Consumed " + String.format("%.1f", fuelRequired) + " units from Heliogen Tanks for jump.", ServerMessage.MESSAGE_TYPE_INFO);
			handleJump(event, true, targetSector);
		} else {
			// Tanks don't have enough — drain what's there, calculate remaining need, top up from inventory.
			int canistersFromTank = 0;
			if(tankHoldsHeliogen && fuelAvailable > 0) {
				tankModule.setCurrentFluidLevel(0);
				canistersFromTank = (int) Math.floor(fuelAvailable / fuelPerCanister);
				canistersNeeded -= canistersFromTank;
			}
			int inventoryAvailable = countInventoryCanisters(shipManagerContainer);
			// Total canisters we can actually provide = what came from the tank + what's in inventory (capped at remaining need)
			int totalCanistersAvailable = canistersFromTank + Math.min(inventoryAvailable, canistersNeeded);
			int originalCanistersNeeded = (int) Math.ceil(jumpDistance * ConfigManager.getFtlFuelPerSector());
			Vector3i redirect = calcShortJumpTarget(currentSector, targetSector, totalCanistersAvailable, originalCanistersNeeded);
			shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen System] Insufficient fuel in tanks — supplementing from ship inventory.", ServerMessage.MESSAGE_TYPE_WARNING);
			consumeCanisters(event, shipManagerContainer, canistersNeeded, redirect);
		}
	}

	/**
	 * Calculates the furthest sector we can jump to toward {@code target} given
	 * {@code availableCanisters} out of {@code neededCanisters} for the full distance.
	 * <p>
	 * Steps along the integer direction vector from {@code origin} toward {@code target},
	 * travelling {@code floor(jumpDistance * (available / needed))} sectors.
	 * Always returns at least one sector of progress so the ship isn't left stranded,
	 * and never overshoots the target.
	 */
	private static Vector3i calcShortJumpTarget(Vector3i origin, Vector3i target, int availableCanisters, int neededCanisters) {
		if(availableCanisters >= neededCanisters) {
			return new Vector3i(target);
		}

		// Direction vector from origin to target
		double dx = target.x - origin.x;
		double dy = target.y - origin.y;
		double dz = target.z - origin.z;
		double fullDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if(fullDist == 0) return new Vector3i(origin);

		// Scale the fraction: how far can we actually travel?
		double fraction = (neededCanisters > 0) ? (double) availableCanisters / neededCanisters : 0;
		// Guarantee at least 1 sector of movement so the ship always makes some progress
		double reachableDist = Math.max(1.0, fraction * fullDist);
		// Never overshoot
		reachableDist = Math.min(reachableDist, fullDist);

		int rx = origin.x + (int) Math.round((dx / fullDist) * reachableDist);
		int ry = origin.y + (int) Math.round((dy / fullDist) * reachableDist);
		int rz = origin.z + (int) Math.round((dz / fullDist) * reachableDist);

		return new Vector3i(rx, ry, rz);
	}

	/** Counts total filled Heliogen Canisters across all ship inventories. */
	private static int countInventoryCanisters(ShipManagerContainer shipManagerContainer) {
		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		int total = 0;
		for(Inventory inventory : shipManagerContainer.getInventories().values()) {
			total += inventory.getOverallQuantity(filledId);
		}
		return total;
	}

	/**
	 * Consumes up to {@code canistersNeeded} filled Heliogen Canisters from ship
	 * inventories, returning empties. Then executes the jump to {@code redirect}.
	 * {@code redirect} is pre-calculated by the caller — if there wasn't enough
	 * fuel, it points to the furthest reachable sector toward the original target.
	 */
	private void consumeCanisters(ShipJumpEngageEvent event, ShipManagerContainer shipManagerContainer, int canistersNeeded, Vector3i redirect) {
		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();

		int remaining = canistersNeeded;
		for(Inventory inventory : shipManagerContainer.getInventories().values()) {
			if(remaining <= 0) {
				break;
			}
			int toUse = Math.min(inventory.getOverallQuantity(filledId), remaining);
			if(toUse <= 0) {
				continue;
			}
			InventoryUtils.removeItems(inventory, filledId, toUse);
			inventory.incExistingOrNextFreeSlotWithoutException(emptyId, toUse);
			inventory.sendInventoryModification(toUse);
			remaining -= toUse;
		}

		boolean hadEnough = remaining == 0;
		int consumed = canistersNeeded - remaining;
		if(consumed > 0) {
			shipManagerContainer.getSegmentController().sendServerMessage("[Heliogen System] Consumed " + consumed + " fuel canister(s) from inventory for jump.", ServerMessage.MESSAGE_TYPE_INFO);
		}
		handleJump(event, hadEnough, redirect);
	}

	/**
	 * Executes the jump to {@code redirect} with appropriate messaging.
	 * */
	private void handleJump(ShipJumpEngageEvent event, boolean hadEnough, Vector3i redirect) {
		event.setCanceled(true); //We have to cancel the event and do our own custom version
		event.getController().getNetworkObject().graphicsEffectModifier.add((byte) 1);
		Ship ship = (Ship) event.getController();
		SectorSwitch queueSectorSwitch = ((GameServerState) ship.getState()).getController().queueSectorSwitch(ship, redirect, SectorSwitch.TRANS_JUMP, false, true, true);
		if(queueSectorSwitch != null) {
			queueSectorSwitch.delay = System.currentTimeMillis() + 4000;
			queueSectorSwitch.jumpSpawnPos = ship.getWorldTransform().origin;
			queueSectorSwitch.executionGraphicsEffect = 2;
			queueSectorSwitch.keepJumpBasisWithJumpPos = true;
		}
		String jumpTypeMsg = hadEnough ? "Jumping to target sector -> %s" : "Jumping on reserves to target sector -> %s";
		ship.sendServerMessage(String.format(jumpTypeMsg, redirect.toStringPure()), ServerMessage.MESSAGE_TYPE_INFO);
	}
}

