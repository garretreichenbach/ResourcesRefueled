package videogoose.resourcesrefueled.element.item;

/**
 * An empty portable canister for Heliogen fuel.
 * Fill it at a Heliogen Refinery to produce a Heliogen Canister (Filled).
 * Can be purchased from shops as a starting point.
 */
public class HeliogenCanisterEmpty extends Item {

	/** Volume per canister — small enough to carry many, big enough to feel meaningful. */
	public static final float VOLUME = 0.1f;

	public HeliogenCanisterEmpty() {
		super("Heliogen Canister (Empty)");
	}

	@Override
	public void initData() {
		super.initData();
		itemInfo.description = "A sealed portable canister designed to store condensed Heliogen fuel. " +
				"Currently empty. Fill it at a Heliogen Refinery to make it usable as extractor or FTL fuel.";
		itemInfo.volume = VOLUME;
		itemInfo.mass = 0.005f; // Light when empty.
		itemInfo.price = 10;
		itemInfo.shoppable = true;
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {
		// TODO: custom icon
	}
}

