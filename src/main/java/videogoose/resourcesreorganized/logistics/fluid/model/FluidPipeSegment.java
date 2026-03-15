package videogoose.resourcesreorganized.logistics.fluid.model;

public class FluidPipeSegment {

	public final long blockIndex;
	public final short blockType;
	public float flowRate;

	public FluidPipeSegment(long blockIndex, short blockType) {
		this.blockIndex = blockIndex;
		this.blockType = blockType;
		flowRate = 0;
	}
}

