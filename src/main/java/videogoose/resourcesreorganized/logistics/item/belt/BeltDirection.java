package videogoose.resourcesreorganized.logistics.item.belt;

import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.element.ElementCollection;

/**
 * Maps a conveyor belt block's orientation byte to its flow direction.
 * <p>
 * The forward face is where items exit the belt; the opposite (backward) face is where items are
 * pulled in. The orientation decode mirrors {@code FluidNetworkDrawer}'s pump mapping &mdash; if items
 * flow the wrong way relative to the belt model, adjust {@link #offset(byte)} to match the model's
 * actual forward face.
 */
public final class BeltDirection {

	private BeltDirection() {
	}

	/** Block index of the cell directly ahead (where items exit). */
	public static long forwardIndex(long index, byte orientation) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		Vector3i d = offset(orientation);
		return ElementCollection.getIndex(pos.x + d.x, pos.y + d.y, pos.z + d.z);
	}

	/** Block index of the cell directly behind (where items are pulled in). */
	public static long backwardIndex(long index, byte orientation) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		Vector3i d = offset(orientation);
		return ElementCollection.getIndex(pos.x - d.x, pos.y - d.y, pos.z - d.z);
	}

	/** Unit offset of the forward face for the given orientation byte. */
	public static Vector3i offset(byte orientation) {
		int dir = orientation & 0x07;
		return switch(dir) {
			case 0 -> new Vector3i(0, 0, 1);
			case 1 -> new Vector3i(0, 0, -1);
			case 2 -> new Vector3i(0, 1, 0);
			case 3 -> new Vector3i(0, -1, 0);
			case 4 -> new Vector3i(-1, 0, 0);
			case 5 -> new Vector3i(1, 0, 0);
			default -> new Vector3i(0, 0, 1);
		};
	}
}
