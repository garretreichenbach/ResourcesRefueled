package videogoose.resourcesreorganized.logistics.fluid.model;

import java.util.HashSet;
import java.util.Set;

public class FluidNetwork {

	public final Set<Long> memberIndices = new HashSet<Long>();
	public double fluidLevel;
	public double tankCapacity;
	public short fluidId;

	public boolean isEmpty() {
		return memberIndices.isEmpty();
	}
}

