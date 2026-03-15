package videogoose.resourcesreorganized.logistics.fluid.model;

public class FluidPortSegment {

	public final long blockIndex;
	public double bufferLevel;
	public short bufferFluidId;

	public FluidPortSegment(long blockIndex) {
		this.blockIndex = blockIndex;
	}
}

