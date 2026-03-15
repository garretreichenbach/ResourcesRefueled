package videogoose.resourcesreorganized.logistics.item.graph;

import java.util.Objects;

public final class ItemNode {

	private final String id;
	private final ItemNodeType type;
	private final int throughputPerTick;
	private final TransportFamily transportFamily;
	private final boolean extractionCapable;
	private final int channelMask;

	public ItemNode(String id, ItemNodeType type, int throughputPerTick) {
		this(id, type, throughputPerTick, inferFamily(type), type == ItemNodeType.INVENTORY_PORT, -1);
	}

	public ItemNode(String id, ItemNodeType type, int throughputPerTick, TransportFamily transportFamily, boolean extractionCapable, int channelMask) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		this.throughputPerTick = Math.max(1, throughputPerTick);
		this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily");
		this.extractionCapable = extractionCapable;
		this.channelMask = channelMask;
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

	public TransportFamily getTransportFamily() {
		return transportFamily;
	}

	public boolean isExtractionCapable() {
		return extractionCapable;
	}

	public boolean supportsChannel(int channel) {
		if(channel < 0 || channelMask == -1) {
			return true;
		}
		return (channelMask & (1 << channel)) != 0;
	}

	private static TransportFamily inferFamily(ItemNodeType type) {
		if(type == ItemNodeType.CONVEYOR) {
			return TransportFamily.CONVEYOR;
		}
		if(type == ItemNodeType.TUBE || type == ItemNodeType.PUMP) {
			return TransportFamily.TUBE;
		}
		return TransportFamily.NEUTRAL;
	}
}