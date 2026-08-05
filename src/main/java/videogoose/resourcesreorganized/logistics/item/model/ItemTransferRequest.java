package videogoose.resourcesreorganized.logistics.item.model;

import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

import java.util.Objects;
import java.util.UUID;

/**
 * A queued request to move a stack from one logistics node to another.
 * <p>
 * Note for anyone reading older code: this used to carry {@code allowVertical} and
 * {@code requirePump}. Both existed only for the tube family — tubes were how items moved vertically,
 * gated on an item pump. Conveyor belts carry items vertically themselves now
 * ({@code BeltShape.TURN_UP}), tubes and pumps were removed, and with them those two flags.
 */
public record ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType,
                                  int metaId, int count, long enqueueTick, boolean allowVanillaFallback,
                                  TransportFamily transportFamily, int channel,
                                  boolean allowDirectInventoryAdjacency,
                                  boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, TransportFamily.CONVEYOR, -1, true, false, false);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, true, false, false);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowDirectInventoryAdjacency, boolean requireInventoryPort) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowDirectInventoryAdjacency, requireInventoryPort, requireInventoryPort);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowDirectInventoryAdjacency, boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowDirectInventoryAdjacency, sourceRequiresInventoryPort, destinationRequiresInventoryPort);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this(requestId, sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, TransportFamily.CONVEYOR, -1, true, false, false);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowDirectInventoryAdjacency, boolean requireInventoryPort) {
		this(requestId, sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowDirectInventoryAdjacency, requireInventoryPort, requireInventoryPort);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowDirectInventoryAdjacency, boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
		this.destinationNodeId = Objects.requireNonNull(destinationNodeId, "destinationNodeId");
		this.itemType = itemType;
		this.metaId = metaId;
		this.count = Math.max(0, count);
		this.enqueueTick = enqueueTick;
		this.allowVanillaFallback = allowVanillaFallback;
		this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily");
		this.channel = channel;
		this.allowDirectInventoryAdjacency = allowDirectInventoryAdjacency;
		this.sourceRequiresInventoryPort = sourceRequiresInventoryPort;
		this.destinationRequiresInventoryPort = destinationRequiresInventoryPort;
	}

	public boolean isRequireInventoryPort() {
		return sourceRequiresInventoryPort || destinationRequiresInventoryPort;
	}

	public ItemTransferRequest withCount(int newCount) {
		return new ItemTransferRequest(requestId, sourceNodeId, destinationNodeId, itemType, metaId, newCount, enqueueTick, allowVanillaFallback, transportFamily, channel, allowDirectInventoryAdjacency, sourceRequiresInventoryPort, destinationRequiresInventoryPort);
	}
}
