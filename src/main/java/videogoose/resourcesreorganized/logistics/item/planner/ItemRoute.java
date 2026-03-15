package videogoose.resourcesreorganized.logistics.item.planner;

import java.util.Collections;
import java.util.List;

public final class ItemRoute {

	private final List<String> nodePath;
	private final int maxItemsPerTick;

	public ItemRoute(List<String> nodePath, int maxItemsPerTick) {
		this.nodePath = Collections.unmodifiableList(nodePath);
		this.maxItemsPerTick = Math.max(1, maxItemsPerTick);
	}

	public List<String> getNodePath() {
		return nodePath;
	}

	public int getMaxItemsPerTick() {
		return maxItemsPerTick;
	}
}

