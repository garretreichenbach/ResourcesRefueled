package videogoose.resourcesreorganized.element.item;

import videogoose.resourcesreorganized.data.FluidMeta;

/**
 * Raw Heliogen Plasma — the unrefined output of the Heliogen Condenser.
 * Collected from the StellarFuelSupplier pool and processed in the Heliogen Refinery
 * to produce Heliogen Canisters (Filled).
 * <p>
 * Not placeable; purely an inventory item.
 */
public class HeliogenPlasma extends Item {

	public HeliogenPlasma() {
		super("Heliogen Plasma");
	}

	@Override
	public void initData() {
		super.initData();
		itemInfo.description = "Raw stellar plasma distilled from the photonic wind of nearby stars. Extremely energetic, but unstable in bulk quantities.";
	}

	@Override
	public void postInitData() {
		// Heliogen Plasma is energetically unstable — tanks containing it explode when destroyed.
		FluidMeta.registerVolatile(itemInfo.id);
	}

	@Override
	public void initResources() {
		// TODO: custom icon once art assets are available.
	}
}
