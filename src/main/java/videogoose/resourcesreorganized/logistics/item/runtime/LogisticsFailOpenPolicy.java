package videogoose.resourcesreorganized.logistics.item.runtime;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class LogisticsFailOpenPolicy {

	private final BooleanSupplier failOpenSupplier;

	public LogisticsFailOpenPolicy(BooleanSupplier failOpenSupplier) {
		this.failOpenSupplier = failOpenSupplier;
	}

	public <T> T execute(Supplier<T> action, Supplier<T> fallback) {
		try {
			return action.get();
		} catch(RuntimeException exception) {
			if(failOpenSupplier != null && failOpenSupplier.getAsBoolean()) {
				return fallback.get();
			}
			throw exception;
		}
	}
}

