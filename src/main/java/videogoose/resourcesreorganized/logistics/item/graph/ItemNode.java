package videogoose.resourcesreorganized.logistics.item.graph;

import java.util.Objects;

public final class ItemNode {

	private final String id;
	private final ItemNodeType type;
	private final int throughputPerTick;

	public ItemNode(String id, ItemNodeType type, int throughputPerTick) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		this.throughputPerTick = Math.max(1, throughputPerTick);
	}

	public String getId() {
		return id;
	}

	public ItemNodeType getType() {
		return type;
	}

	public int getThroughputPerTick() {
		return throughputPerTick;
	}
}