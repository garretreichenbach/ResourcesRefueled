package videogoose.resourcesrefueled.listener;

import api.listener.Listener;
import org.ithirahad.resourcesresourced.events.HarvesterStrengthUpdateEvent;
import videogoose.resourcesrefueled.fuel.FuelTickState;
import videogoose.resourcesrefueled.manager.ConfigManager;

/**
 * Listens to RRS's HarvesterStrengthUpdateEvent and scales down extraction power
 * when the extractor is unfueled this tick.
 * <p>
 * RRS fires this event after the resource collection pass, setting the harvester's
 * strength for the NEXT tick. By scaling it here, we ensure unfueled extractors
 * permanently operate at UNFUELED_EXTRACTION_EFFICIENCY until fuel is restored.
 * <p>
 * Registered in EventManager via StarLoader.registerListener.
 */
public class HarvesterFuelEfficiencyListener extends Listener<HarvesterStrengthUpdateEvent> {

	@Override
	public void onEvent(HarvesterStrengthUpdateEvent event) {
		String uid = event.getExtractingEntity().getUniqueIdentifier();
		Boolean unfueled = FuelTickState.unfueledThisTick.get(uid);
		if(unfueled) {
			float efficiency = (float) ConfigManager.getUnfueledExtractionEfficiency();
			event.setPower(event.getPower() * efficiency);
		}
	}
}

