package videogoose.resourcesreorganized.element.item;

import api.utils.element.Blocks;
import videogoose.resourcesreorganized.manager.ConfigManager;

/**
 * A portable fluid canister.
 * <p>
 * A single item type represents both empty and filled states. The actual fluid stored,
 * the current amount, and the capacity are encoded as per-slot metadata via
 * {@link videogoose.resourcesreorganized.data.FluidMeta}. The engine's built-in
 * {@code "name"} and {@code "description"} metadata keys override the tooltip text
 * automatically, so no separate filled/empty item type is needed.
 * <p>
 * An empty canister has no metadata. A filled canister carries:
 * <ul>
 *   <li>{@code fluid_id}     — element ID of the stored fluid</li>
 *   <li>{@code fluid_amount} — current units stored</li>
 *   <li>{@code capacity}     — maximum units this canister holds</li>
 *   <li>{@code name}         — "Fluid Canister (&lt;fluid name&gt;)"</li>
 *   <li>{@code description}  — "X.X / Y.Y L of &lt;fluid name&gt;"</li>
 * </ul>
 */
public class FluidCanister extends Item {

	public FluidCanister() {
		super("Fluid Canister");
	}

	@Override
	public void initData() {
		super.initData();
		itemInfo.type = Blocks.PIPE.getInfo().type;
		itemInfo.description = "A sealed portable canister for storing fluids.\nHolds up to " + getCapacity() + " mL.";
		itemInfo.volume = 0.3f;
		itemInfo.mass = 0.05f;
		itemInfo.price = 10;
		itemInfo.shoppable = true;
	}

	@Override
	public void postInitData() {
	}

	@Override
	public void initResources() {
		// TODO: custom icon (glowing when filled)
	}

	public static double getCapacity() {
		return ConfigManager.getCapacityPerCanister();
	}
}


