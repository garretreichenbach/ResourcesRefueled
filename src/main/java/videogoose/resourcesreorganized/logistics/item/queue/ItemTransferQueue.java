package videogoose.resourcesreorganized.logistics.item.queue;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

import java.util.ArrayDeque;
import java.util.Optional;

public final class ItemTransferQueue {

	private final int capacity;
	private final ArrayDeque<ItemTransferRequest> queue = new ArrayDeque<ItemTransferRequest>();

	public ItemTransferQueue(int capacity) {
		this.capacity = Math.max(1, capacity);
	}

	public boolean offer(ItemTransferRequest request) {
		if(queue.size() >= capacity) {
			return false;
		}
		return queue.offer(request);
	}

	public Optional<ItemTransferRequest> poll() {
		return Optional.ofNullable(queue.poll());
	}

	public int size() {
		return queue.size();
	}
}

