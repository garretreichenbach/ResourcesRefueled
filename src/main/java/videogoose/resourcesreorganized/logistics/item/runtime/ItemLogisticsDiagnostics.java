package videogoose.resourcesreorganized.logistics.item.runtime;

import java.util.concurrent.atomic.AtomicLong;

public final class ItemLogisticsDiagnostics {

	private final AtomicLong queued = new AtomicLong(0);
	private final AtomicLong completed = new AtomicLong(0);
	private final AtomicLong partial = new AtomicLong(0);
	private final AtomicLong failed = new AtomicLong(0);
	private final AtomicLong deferred = new AtomicLong(0);
	private final AtomicLong noRoute = new AtomicLong(0);

	public void recordQueued() {
		queued.incrementAndGet();
	}

	public void recordCompleted() {
		completed.incrementAndGet();
	}

	public void recordPartial() {
		partial.incrementAndGet();
	}

	public void recordFailed() {
		failed.incrementAndGet();
	}

	public void recordDeferred() {
		deferred.incrementAndGet();
	}

	public void recordNoRoute() {
		noRoute.incrementAndGet();
	}

	public String snapshot() {
		return "queued=" + queued.get() + " completed=" + completed.get() + " partial=" + partial.get() + " deferred=" + deferred.get() + " noRoute=" + noRoute.get() + " failed=" + failed.get();
	}
}

