package videogoose.resourcesrefueled.element.item;

/**
 * Raw Heliogen Plasma — the unrefined output of the Heliogen Condenser.
 * Collected from the StellarFuelSupplier pool and processed in the Heliogen Refinery
 * to produce Heliogen Canisters (Filled).
 * <p>
 * Not placeable; purely an inventory item.
 */
public class HeliogenPlasma extends Item {

	/** Volume per unit — deliberately bulky to incentivise refining into canisters. */
	public static final float VOLUME = 0.002f;

	public HeliogenPlasma() {
		super("Heliogen Plasma");
	}

	@Override
	public void initData() {
		super.initData();
		itemInfo.description = "Raw stellar plasma distilled from the photonic wind of nearby stars. Extremely energetic, but unstable in bulk quantities.\nRefine in a Heliogen Refinery to produce portable Heliogen Canisters suitable for use as fuel.";
		itemInfo.volume = VOLUME;
		itemInfo.shoppable = false; // Cannot be bought in shops — must be produced.
		itemInfo.blockResourceType = 0; // Treated as an ore/raw material.
	}

	@Override
	public void postInitData() {
		// No cross-element wiring needed.
	}

	@Override
	public void initResources() {
		// TODO: custom icon once art assets are available.
	}
}

