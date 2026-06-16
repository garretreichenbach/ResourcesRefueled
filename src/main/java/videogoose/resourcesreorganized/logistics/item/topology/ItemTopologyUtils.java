package videogoose.resourcesreorganized.logistics.item.topology;

import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.element.ElementCollection;

import java.util.HashSet;
import java.util.Set;

public final class ItemTopologyUtils {

	private ItemTopologyUtils() {
	}

	public static Set<Long> faceAdjacentIndices(long index) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		Set<Long> result = new HashSet<>(6);
		result.add(ElementCollection.getIndex(pos.x + 1, pos.y, pos.z));
		result.add(ElementCollection.getIndex(pos.x - 1, pos.y, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y + 1, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y - 1, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y, pos.z + 1));
		result.add(ElementCollection.getIndex(pos.x, pos.y, pos.z - 1));
		return result;
	}
}
