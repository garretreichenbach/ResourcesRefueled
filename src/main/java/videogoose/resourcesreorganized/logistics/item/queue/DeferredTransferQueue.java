package videogoose.resourcesreorganized.logistics.item.queue;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;

public final class DeferredTransferQueue {

	private static final class DeferredItem {
		private final long readyAtTick;
		private final ItemTransferRequest request;

		private DeferredItem(long readyAtTick, ItemTransferRequest request) {
			this.readyAtTick = readyAtTick;
			this.request = request;
		}
	}

	private final PriorityQueue<DeferredItem> queue = new PriorityQueue<DeferredItem>(Comparator.comparingLong(item -> item.readyAtTick));

	public void defer(long readyAtTick, ItemTransferRequest request) {
		queue.offer(new DeferredItem(readyAtTick, request));
	}

	public Optional<ItemTransferRequest> pollReady(long currentTick) {
		DeferredItem first = queue.peek();
		if(first == null || first.readyAtTick > currentTick) {
			return Optional.empty();
		}
		return Optional.ofNullable(queue.poll()).map(item -> item.request);
	}
}

