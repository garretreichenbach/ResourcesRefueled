package videogoose.resourcesreorganized.logistics.item.topology;

import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

public record ItemTransportSegment(long blockIndex, short blockType, byte orientation, TransportFamily family) {
}
