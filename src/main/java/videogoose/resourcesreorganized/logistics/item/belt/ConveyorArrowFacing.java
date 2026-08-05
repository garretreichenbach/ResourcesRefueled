package videogoose.resourcesreorganized.logistics.item.belt;

import api.utils.game.BlockFacingArrowAPI;

/**
 * Tells the game's build-mode orientation arrow which face a belt shape outputs through.
 * <p>
 * The vanilla arrow points along the held block's forward face. That is the output for a straight
 * belt and for the in-plane turns, but {@link BeltShape#TURN_UP} outputs through its <i>top</i> face,
 * so without this the arrow would point along its input.
 * <p>
 * {@link BlockFacingArrowAPI} exists only on a patched StarMade.jar, so this reference is kept in a
 * class of its own: it loads the first time {@link #register} is called, which lets the caller treat
 * a missing API as a skipped override rather than a broken mod. See
 * {@code ConveyorBeltBlock.postInitData}.
 */
public final class ConveyorArrowFacing {

	private ConveyorArrowFacing() {
	}

	/** Registers {@code shape}'s output face as the arrow direction for {@code blockType}. */
	public static void register(short blockType, BeltShape shape) {
		BlockFacingArrowAPI.register(blockType, (type, orientation) -> shape.exitSide((byte) orientation));
	}
}
