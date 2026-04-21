package videogoose.resourcesreorganized.logistics.fluid;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.schine.graphicsengine.forms.BoundingBox;

import java.util.HashSet;
import java.util.Set;

public final class FluidTopologyUtils {

	private FluidTopologyUtils() {
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

	public static BoundingBox buildBoundingBox(LongArrayList tankIndices) {
		BoundingBox bb = new BoundingBox();
		if(tankIndices.isEmpty()) {
			return bb;
		}
		Vector3i min = new Vector3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		Vector3i max = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		Vector3i pos = new Vector3i();
		for(int i = 0; i < tankIndices.size(); i++) {
			ElementCollection.getPosFromIndex(tankIndices.getLong(i), pos);
			min.set(Math.min(min.x, pos.x), Math.min(min.y, pos.y), Math.min(min.z, pos.z));
			max.set(Math.max(max.x, pos.x), Math.max(max.y, pos.y), Math.max(max.z, pos.z));
		}
		bb.set(min.toVector3f(), max.toVector3f());
		return bb;
	}
}

