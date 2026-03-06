package videogoose.resourcesrefueled.element.item;

/**
 * A filled portable canister of Heliogen fuel.
 * Used as the primary portable fuel source for extractor operations and FTL jumps.
 * Produced at the Heliogen Refinery from raw Heliogen Plasma.
 * <p>
 * Heavier than an empty canister due to the compressed plasma inside.
 * The fuel consumption logic in ExtractorFuelListener and the ShipJumpEngageEvent
 * listener checks for this item in cargo by ID.
 */
public class HeliogenCanisterFilled extends Item {

	public static final float VOLUME = 0.3f;

	public HeliogenCanisterFilled() {
		super("Heliogen Canister (Filled)");
	}

	@Override
	public void initData() {
		super.initData();
		itemInfo.description = "A sealed portable canister containing condensed Heliogen fuel.\nUse as fuel for resource extractors (improves efficiency) or FTL drives (extends range and reduces cooldown).\nProduced at a Heliogen Refinery.";
		itemInfo.volume = VOLUME;
		itemInfo.mass = 0.05f; // Noticeably heavier than an empty canister.
		itemInfo.price = 200;
		itemInfo.shoppable = false; // Must be produced, not bought.
	}

	@Override
	public void postInitData() {
	}

	@Override
	public void initResources() {
		// TODO: custom icon (glowing canister)
	}
}


