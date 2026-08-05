package videogoose.resourcesreorganized.graphics;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.world.Segment;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.network.NetUtil;
import org.schema.schine.resource.ResourceLoader;
import videogoose.resourcesreorganized.logistics.item.belt.BeltItem;
import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.systems.ItemTransportSystemModule;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the item stacks currently riding conveyor belts, reusing the exact engine path that draws
 * items floating in space ({@link org.schema.game.client.view.effects.ItemDrawer} &rarr;
 * {@link Sprite#draw3D}). Each {@link BeltItem} on a client {@link ItemTransportSystemModule} becomes a
 * billboarded {@link BeltItemSprite}, positioned along its belt cell from the synced travel progress.
 * <p>
 * The server is authoritative and syncs item progress only every few ticks
 * ({@code conveyor_sync_interval_ticks}); between keyframes this drawer extrapolates each stack's
 * progress at the configured belt speed so motion looks continuous, snapping back to the authoritative
 * value whenever fresh data arrives.
 */
public class ConveyorItemDrawer extends ModWorldDrawer {

	/** On-screen size of an item billboard, mirroring {@code FreeItem}'s scale magnitude. Tunable. */
	private static final float ITEM_SCALE = 0.01f;
	/** How far above the belt cell centre (along the surface normal) a stack rides. Tunable. */
	private static final float SURFACE_LIFT = 0.02f;
	/** Server simulation step, in seconds &mdash; used to convert per-tick belt speed to per-second. */
	private static final float SERVER_TICK_SECONDS = NetUtil.UPDATE_RATE_SERVER / 1000.0f;
	/** Hard cap on how many cells one stack may be carried forward in a single frame (loop guard). */
	private static final int MAX_WALK = 64;

	private final int halfDim = Segment.HALF_DIM;
	private final Vector3i scratchPos = new Vector3i();
	private final Vector3f scratchLocal = new Vector3f();

	// Per-module client-side interpolation state: cell index -> the keyframe a stack arrived with.
	private final WeakHashMap<ItemTransportSystemModule, Map<Long, CellKeyframe>> animState = new WeakHashMap<>();

	/** The last authoritative progress synced for a belt cell, plus when it arrived, for extrapolation. */
	private static final class CellKeyframe {
		float syncedProgress;
		long arrivedNanos;
	}

	// Pooled sprites reused across frames to avoid per-item allocation churn.
	private final ArrayList<BeltItemSprite> pool = new ArrayList<>();

	private Sprite[] iconSheets;

	@Override
	public void onInit() {
	}

	@Override
	public void update(Timer timer) {
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public boolean isInvisible() {
		return false;
	}

	@Override
	public void draw() {
		GameClientState state = GameClient.getClientState();
		if(state == null) {
			return;
		}
		if(iconSheets == null && !initSheets()) {
			return;
		}

		long now = System.nanoTime();

		// Collect every in-flight stack across all client entities into the pooled sprite list.
		int count = 0;
		for(ItemTransportSystemModule module : ItemTransportSystemModule.snapshotInstances()) {
			SegmentController controller = module.getSegmentController();
			if(controller == null || controller.isOnServer()) {
				continue; // only render client-side copies; the server has no camera
			}
			Map<Long, BeltItem> cells = module.getCellItems();
			if(cells.isEmpty()) {
				continue;
			}
			count = appendModuleSprites(module, controller, cells, now, count);
		}
		if(count == 0) {
			return;
		}

		List<BeltItemSprite> live = pool.subList(0, count);
		// Mirror ItemDrawer.draw(): billboard each atlas page over the whole stack list.
		for(Sprite sheet : iconSheets) {
			sheet.setScale(0.01f, 0.01f, 0.01f);
			sheet.setFlip(true);
			sheet.setBillboard(true);

			Sprite.draw3D(sheet, live, Controller.getCamera());

			sheet.setBillboard(false);
			sheet.setFlip(false);
			sheet.setScale(1f, 1f, 1f);
		}
	}

	/**
	 * Appends one module's stacks to the pool starting at {@code count}, returning the new size.
	 * Also refreshes/prunes this module's keyframe state.
	 */
	private int appendModuleSprites(ItemTransportSystemModule module, SegmentController controller,
									Map<Long, BeltItem> cells, long now, int count) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		Transform worldTransform = controller.getWorldTransformOnClient();
		Map<Long, CellKeyframe> anim = animState.computeIfAbsent(module, m -> new java.util.HashMap<>());

		float perSecond = ConfigManager.getConveyorBeltSpeed() / SERVER_TICK_SECONDS;

		// The server/network thread can clear & repopulate cellItems (onTagDeserialize) while this render
		// thread iterates it. Guard against the resulting ConcurrentModificationException by holding the
		// map's monitor for the iteration; the writer side synchronizes on the same map.
		synchronized(cells) {
			for(Map.Entry<Long, BeltItem> entry : cells.entrySet()) {
				long cellIndex = entry.getKey();
				BeltItem item = entry.getValue();
				if(item == null || item.type <= 0) {
					continue;
				}
				ElementCollection.getPosFromIndex(cellIndex, scratchPos);
				SegmentPiece piece = buffer.getPointUnsave(scratchPos);
				BeltShape shape = (piece == null) ? null : BeltShape.of(piece.getType());
				if(shape == null) {
					continue; // belt gone client-side; skip until the cell is pruned by the server
				}

				// Total progress extrapolated since the last keyframe; may exceed 1 (item has flowed on
				// to following cells) — the walk below carries it along the belt path so motion stays
				// continuous instead of snapping a cell at a time.
				float elapsed = elapsedSeconds(anim, cellIndex, item.progress, now);
				float travelled = item.progress + perSecond * elapsed;

				if(placeAlongPath(buffer, cellIndex, shape, piece.getOrientation(), travelled, worldTransform)) {
					spriteAt(count).set(item.type, scratchLocal, ITEM_SCALE);
					count++;
				}
			}

			// Drop keyframe state for cells whose stack has moved on / been delivered.
			anim.keySet().retainAll(cells.keySet());
		}
		return count;
	}

	/**
	 * Walks {@code travelled} cell-lengths forward from {@code startCell} along the belt path, then
	 * writes the resulting world position into {@link #scratchLocal}. Each whole unit of travel hands
	 * the stack to the next belt cell, following the same entry/exit faces the server simulation uses;
	 * travel past the end of the line clamps the stack at the final cell's exit face.
	 * <p>
	 * Within a cell the stack rides the shape's two legs: the entry face to the cell centre over the
	 * first half of the travel, the centre to the exit face over the second. For a straight belt both
	 * legs share a direction and this is a single line; for a turn it traces the bend.
	 */
	private boolean placeAlongPath(SegmentBufferManager buffer, long startCell, BeltShape startShape,
								   byte startOrientation, float travelled, Transform worldTransform) {
		long cell = startCell;
		BeltShape shape = startShape;
		byte orientation = startOrientation;
		float remaining = travelled;

		for(int step = 0; step < MAX_WALK && remaining >= 1.0f; step++) {
			long forward = shape.outputIndex(cell, orientation);
			ElementCollection.getPosFromIndex(forward, scratchPos);
			SegmentPiece forwardPiece = buffer.getPointUnsave(scratchPos);
			BeltShape forwardShape = (forwardPiece == null) ? null : BeltShape.of(forwardPiece.getType());
			if(forwardShape == null || !forwardShape.acceptsFrom(forward, forwardPiece.getOrientation(), cell)) {
				remaining = 1.0f; // ran off the end of the belt; sit at this cell's exit face
				break;
			}
			cell = forward;
			shape = forwardShape;
			orientation = forwardPiece.getOrientation();
			remaining -= 1.0f;
		}
		float progress = Math.min(remaining, 1.0f);

		ElementCollection.getPosFromIndex(cell, scratchPos);
		// Entry leg (-0.5 .. 0) then exit leg (0 .. +0.5), measured from the cell centre.
		Vector3i leg = (progress < 0.5f) ? shape.entryFlow(orientation) : shape.exitFlow(orientation);
		Vector3i up = shape.surfaceNormal(orientation, progress);
		float along = progress - 0.5f;
		scratchLocal.set(
				scratchPos.x - halfDim + leg.x * along + up.x * SURFACE_LIFT,
				scratchPos.y - halfDim + leg.y * along + up.y * SURFACE_LIFT,
				scratchPos.z - halfDim + leg.z * along + up.z * SURFACE_LIFT);
		worldTransform.transform(scratchLocal);
		return true;
	}

	/**
	 * Seconds elapsed since the stack on this cell last received a fresh authoritative progress. When the
	 * synced value changes (a new server keyframe), the clock resets so extrapolation restarts from truth.
	 */
	private float elapsedSeconds(Map<Long, CellKeyframe> anim, long cellIndex, float synced, long now) {
		CellKeyframe kf = anim.get(cellIndex);
		if(kf == null || kf.syncedProgress != synced) {
			kf = (kf == null) ? new CellKeyframe() : kf;
			kf.syncedProgress = synced;
			kf.arrivedNanos = now;
			anim.put(cellIndex, kf);
			return 0.0f;
		}
		return (now - kf.arrivedNanos) / 1.0e9f;
	}

	private BeltItemSprite spriteAt(int index) {
		while(pool.size() <= index) {
			pool.add(new BeltItemSprite());
		}
		return pool.get(index);
	}

	private boolean initSheets() {
		ResourceLoader loader = Controller.getResLoader();
		if(loader == null || loader.getImageLoader() == null) {
			return false;
		}
		ArrayList<Sprite> sheets = new ArrayList<>();
		for(String name : loader.getImageLoader().getSpriteMap().keySet()) {
			if(name.startsWith("build-icons-") || name.startsWith("meta-icons-")) {
				sheets.add(loader.getSprite(name));
			}
		}
		if(sheets.isEmpty()) {
			return false; // atlases not loaded yet; retry next frame
		}
		iconSheets = sheets.toArray(new Sprite[0]);
		return true;
	}
}
