package videogoose.resourcesreorganized.logistics.item.belt;

/**
 * A single item stack physically riding a conveyor belt.
 * <p>
 * {@link #progress} is how far the stack has advanced through its current cell, in {@code [0, 1)};
 * at {@code >= 1} the simulator tries to hand it to the next cell or destination inventory. Mutable
 * by design &mdash; the server simulator advances these in place each tick, and the client renderer
 * interpolates {@link #progress} between syncs.
 */
public final class BeltItem {

	public short type;
	public int metaId;
	public int count;
	public float progress;

	public BeltItem(short type, int metaId, int count, float progress) {
		this.type = type;
		this.metaId = metaId;
		this.count = count;
		this.progress = progress;
	}
}
