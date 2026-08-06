package videogoose.resourcesreorganized.graphics;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.opengl.GL11;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.BuildModeDrawer;
import org.schema.game.client.view.gui.shiphud.newhud.Hud;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.world.Segment;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.logistics.item.belt.BeltItem;
import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;
import videogoose.resourcesreorganized.logistics.item.belt.StallReason;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import javax.vecmath.Vector4f;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.systems.ItemTransportSystemModule;

import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Debug-mode overlay that draws the item path through each conveyor belt — a straight arrow for a
 * straight belt, an L for a turn — from the block's {@link BeltShape} and orientation. Mirrors the
 * fluid pump flow arrows. Only active when {@code debug_mode} is on and the player is looking at a
 * conveyor belt while in build mode.
 */
public class ConveyorNetworkDrawer extends ModWorldDrawer {

	private static final long PEAK_HOLD_MS = 2000L;
	private static final long STATUS_HOLD_MS = 2000L;

	private final Vector3i HALF_DIM = new Vector3i(Segment.HALF_DIM, Segment.HALF_DIM, Segment.HALF_DIM);
	private final Vector3i scratchPos = new Vector3i();
	private final Vector3i scratchNeighborPos = new Vector3i();

	// Peak-hold state for the HUD item-count readout (type -> [peakCount, expiresAtMillis]).
	private final Map<Short, long[]> peakHold = new HashMap<>();
	private long peakRunId = Long.MIN_VALUE;
	private long lastMovingMillis;

	/** How often the stall sweep runs at all — this walks every client belt module, so not every frame. */
	private static final long INDICATOR_SWEEP_MS = 500;
	/** How long before the same stuck cell announces itself again. */
	private static final long INDICATOR_REPEAT_MS = 12_000;
	/** Drop a cell's throttle entry once it has been quiet this long, so a new stall shows straight away. */
	private static final long INDICATOR_FORGET_MS = 30_000;
	/** Seconds a label spends rising and fading. */
	private static final float INDICATOR_LIFE_SECONDS = 4.0f;
	/** Amber: a warning about a build mistake, not an error the player cannot act on. */
	private static final Vector4f STALL_COLOR = new Vector4f(1.0f, 0.72f, 0.2f, 1.0f);

	// Per-cell throttle for the in-world stall labels.
	private final Map<Long, Long> lastIndicatorShown = new HashMap<>();
	private long lastIndicatorSweep;

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
		// Always-on info hint: shows what (if anything) is riding the belt the player looks at.
		drawLookedAtItemHint();
		// Occasional in-world labels explaining stuck belts, at the block that is stuck.
		drawStallIndicators();

		// Admin-only debug draw toggle (F1 + F10 in-game), shared with vanilla physics debug. Everything
		// below is calibration aid only — in normal play the vanilla build arrow (pointed at the belt's
		// output via BlockFacingArrowAPI) is what the player builds with.
		if(!EngineSettings.P_PHYSICS_DEBUG_ACTIVE.isOn()) {
			return;
		}
		// Flow path the held belt would have once placed.
		drawHeldBeltPreview();
		if(!(GameClient.getCurrentControl() instanceof ManagedUsableSegmentController<?> controller)) {
			return;
		}
		if(!GameClient.getPICM().isInAnyStructureBuildMode()) {
			return;
		}
		SegmentPiece currentPiece = BuildModeDrawer.currentPiece;
		if(currentPiece == null) {
			return;
		}

		long startIndex;
		if(ElementRegistry.isConveyorBelt(currentPiece.getType())) {
			startIndex = currentPiece.getAbsoluteIndex();
		} else {
			startIndex = findAdjacentConveyor(controller, currentPiece.getAbsoluteIndex());
			if(startIndex == -1) {
				return;
			}
		}

		Set<Long> belts = findConnectedConveyors(controller, startIndex);
		if(belts.isEmpty()) {
			return;
		}
		drawTravelArrows(controller, belts);
		GlUtil.printGlError();
	}

	/**
	 * Adds a mouse HUD hint summarising the items riding the conveyor run the player is looking at:
	 * each item type's total count, plus whether the line is moving or stalled. Aggregating over the
	 * whole connected run (rather than the single cell) keeps the readout stable as stacks shuffle
	 * between cells — a single cell flickers as items pass through it. Reads the synced client data.
	 */
	private void drawLookedAtItemHint() {
		SegmentPiece looked = lookedAtPiece();
		if(looked == null || !ElementRegistry.isConveyorBelt(looked.getType())) {
			return;
		}
		if(!(looked.getSegmentController() instanceof ManagedUsableSegmentController<?> controller)) {
			return;
		}
		if(!(controller.getManagerContainer().getModMCModule(ElementRegistry.CONVEYOR_BELT.getId()) instanceof ItemTransportSystemModule module)) {
			return;
		}
		Map<Long, BeltItem> cells = module.getCellItems();
		if(cells.isEmpty()) {
			return;
		}

		Map<Short, Integer> totals = new LinkedHashMap<>();
		// A line is only "stalled" when every stack on it is pinned at a cell boundary (nothing
		// advancing). Items briefly hit progress 1.0 during normal hand-off, so "any stalled" would
		// false-trip on a busy-but-flowing belt.
		boolean allStalled = true;
		long runId = Long.MAX_VALUE;
		// Hold the map monitor while reading: the server/network thread can structurally modify it
		// concurrently (the renderer and module mutators lock the same map).
		synchronized(cells) {
			for(long beltIndex : findConnectedConveyors(controller, looked.getAbsoluteIndex())) {
				if(beltIndex < runId) {
					runId = beltIndex;
				}
				BeltItem item = cells.get(beltIndex);
				if(item == null) {
					continue;
				}
				totals.merge(item.type, item.count, Integer::sum);
				if(item.progress < 1.0f) {
					allStalled = false;
				}
			}
		}
		if(totals.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		// Peak-hold the counts so the readout doesn't bounce as stacks are pulled/delivered: show the
		// max seen over the last PEAK_HOLD_MS, decaying once items actually leave. Reset on run change.
		if(runId != peakRunId) {
			peakHold.clear();
			peakRunId = runId;
			lastMovingMillis = now;
		}
		// Hold the status the same way: only show "Stalled" once no movement has been seen for
		// STATUS_HOLD_MS, so brief all-at-boundary moments during normal flow don't flip it.
		if(!allStalled) {
			lastMovingMillis = now;
		}
		boolean stalled = (now - lastMovingMillis) >= STATUS_HOLD_MS;
		peakHold.keySet().retainAll(totals.keySet());

		StringBuilder text = new StringBuilder();
		for(Map.Entry<Short, Integer> entry : totals.entrySet()) {
			short type = entry.getKey();
			int current = entry.getValue();
			long[] hold = peakHold.get(type);
			int peak;
			if(hold == null || current >= hold[0] || now >= hold[1]) {
				peak = current;
				peakHold.put(type, new long[] {current, now + PEAK_HOLD_MS});
			} else {
				peak = (int) hold[0];
			}
			ElementInformation info = ElementKeyMap.getInfoFast(type);
			String name = (info != null) ? info.getName() : ("Type " + type);
			text.append(name).append(" x").append(peak).append('\n');
		}
		text.append(stalled ? "Stalled" : "In Transit");

		Hud hud = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud();
		hud.getHelpManager().addInfo(HudContextHelperContainer.Hos.MOUSE, ContextFilter.NORMAL, text.toString());
	}

	/**
	 * The block under the player's crosshair (camera-forward raycast), or null. Unlike the build-mode
	 * "selected block", this is the block actually being looked at in any control mode.
	 */
	private SegmentPiece lookedAtPiece() {
		GameClientState state = GameClient.getClientState();
		if(state == null || state.getPhysics() == null) {
			return null;
		}
		Vector3f camPos = new Vector3f(Controller.getCamera().getPos());
		Vector3f camTo = new Vector3f(camPos);
		Vector3f forward = new Vector3f(Controller.getCamera().getForward());
		forward.scale(100.0f);
		camTo.add(forward);
		CollisionWorld.ClosestRayResultCallback result =
				((PhysicsExt) state.getPhysics()).testRayCollisionPoint(camPos, camTo, false, null, null, false, true, false);
		if(result != null && result.hasHit() && result instanceof CubeRayCastResult cube && cube.getSegment() != null) {
			return new SegmentPiece(cube.getSegment(), cube.getCubePos());
		}
		return null;
	}

	/**
	 * Draws the flow path the currently held belt block would have once placed, in the cell the build
	 * cursor is targeting. Debug-gated, like the placed-belt arrows.
	 * <p>
	 * The vanilla build arrow already points at the belt's output (the mod registers each shape's exit
	 * face with {@code BlockFacingArrowAPI}), so this is not needed to build with. It stays because one
	 * arrow can only show the output: this draws the entry leg as well, which is what you need when
	 * checking that a turn's model, its simulated routing, and its arrow all agree.
	 */
	private void drawHeldBeltPreview() {
		if(!(GameClient.getCurrentControl() instanceof ManagedUsableSegmentController<?> controller)) {
			return;
		}
		PlayerInteractionControlManager picm = GameClient.getPICM();
		if(picm == null || !picm.isInAnyStructureBuildMode()) {
			return;
		}
		BeltShape shape = BeltShape.of(picm.getSelectedTypeWithSub());
		if(shape == null) {
			return;
		}
		SegmentPiece looked = BuildModeDrawer.currentPiece;
		int side = BuildModeDrawer.currentSide;
		if(looked == null || side < 0 || side >= Element.DIRECTIONSi.length) {
			return;
		}
		// The cell the block would land in, derived exactly as BuildModeDrawer derives toBuildPos:
		// the looked-at block offset by the face the cursor is on.
		Vector3i offset = Element.DIRECTIONSi[side];
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(looked.getAbsoluteIndex(), pos);
		pos.add(offset);

		byte orientation = (byte) picm.getBlockOrientation();
		Vector3f cameraLocal = beginArrowDraw(controller);
		drawShapeLegs(shape, orientation, pos, cameraLocal);
		endArrowDraw();

		if(ConfigManager.isDebugMode()) {
			// Exact numbers for calibration, so a disagreement can be reported precisely instead of
			// eyeballed off the arrows. Axes are entity-local.
			Hud hud = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud();
			hud.getHelpManager().addInfo(HudContextHelperContainer.Hos.MOUSE, ContextFilter.NORMAL,
					shape.name() + "  orient=" + (orientation & 0xFF)
							+ "\nin " + axisNames(shape, orientation, true)
							+ "  out " + axisNames(shape, orientation, false)
							+ "  up " + axisName(shape.surfaceNormal(orientation, 0.0f)));
		}
	}

	/**
	 * Entity-local axis labels for all of a shape's entry or exit faces, e.g. {@code -Z} or
	 * {@code -X/+X} for a shape with two. Comma-free so it stays readable inline in the HUD line.
	 */
	private static String axisNames(BeltShape shape, byte orientation, boolean entry) {
		int n = entry ? shape.entryCount() : shape.exitCount();
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < n; i++) {
			if(i > 0) {
				sb.append('/');
			}
			sb.append(axisName(entry ? shape.entryFlow(orientation, i) : shape.exitFlow(orientation, i)));
		}
		return sb.toString();
	}

	/** Entity-local axis label for a unit direction, e.g. {@code +X}. */
	private static String axisName(Vector3i d) {
		if(d.x != 0) {
			return d.x > 0 ? "+X" : "-X";
		}
		if(d.y != 0) {
			return d.y > 0 ? "+Y" : "-Y";
		}
		if(d.z != 0) {
			return d.z > 0 ? "+Z" : "-Z";
		}
		return "0";
	}

	/**
	 * Floats an occasional label out of any belt cell whose stack is stuck, saying why.
	 * <p>
	 * Deliberately intermittent and per-cell: a stalled belt is a persistent condition, so showing the
	 * reason continuously would just be a permanent world-space annotation the player learns to ignore,
	 * and a long line of them would be unreadable. Each cell re-announces itself on its own timer.
	 * <p>
	 * Purely transient stalls (a belt waiting behind an occupied one) are skipped — they resolve
	 * themselves, and labelling every queued stack behind a busy junction is noise. Only conditions that
	 * need the player to change something are announced.
	 */
	private void drawStallIndicators() {
		long now = System.currentTimeMillis();
		if(now - lastIndicatorSweep < INDICATOR_SWEEP_MS) {
			return;
		}
		lastIndicatorSweep = now;

		for(ItemTransportSystemModule module : ItemTransportSystemModule.snapshotInstances()) {
			SegmentController controller = module.getSegmentController();
			if(controller == null || controller.isOnServer()) {
				continue; // client copies only
			}
			Transform worldTransform = controller.getWorldTransformOnClient();
			Map<Long, BeltItem> cells = module.getCellItems();
			// Same monitor the network thread holds while replacing this map.
			synchronized(cells) {
				for(Map.Entry<Long, BeltItem> entry : cells.entrySet()) {
					BeltItem item = entry.getValue();
					if(item == null || item.stall == null || item.stall == StallReason.NONE || item.stall.isTransient()) {
						continue;
					}
					long cellIndex = entry.getKey();
					Long last = lastIndicatorShown.get(cellIndex);
					if(last != null && now - last < INDICATOR_REPEAT_MS) {
						continue;
					}
					lastIndicatorShown.put(cellIndex, now);
					spawnStallIndicator(worldTransform, cellIndex, item.stall, detail(controller, cellIndex, item.stall));
				}
			}
		}
		// Forget cells that are no longer stalled so a recurrence announces itself immediately.
		lastIndicatorShown.keySet().removeIf(cell -> now - lastIndicatorShown.get(cell) > INDICATOR_FORGET_MS);
	}

	/**
	 * Works out the specifics behind a stall so the label can name the offending block instead of just
	 * the category. Everything here is derived client-side from the stalled cell's own shape and
	 * orientation, so nothing extra has to be synced &mdash; the reason byte is enough to know which
	 * question to ask.
	 * <p>
	 * "Next belt faces the wrong way" is only actionable once the player knows <i>which</i> belt and
	 * <i>which</i> way, and those are exactly the two things a stall message usually leaves out.
	 */
	private String detail(SegmentController controller, long cellIndex, StallReason reason) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		ElementCollection.getPosFromIndex(cellIndex, scratchPos);
		SegmentPiece piece = buffer.getPointUnsave(scratchPos);
		BeltShape shape = (piece == null) ? null : BeltShape.of(piece.getType());
		if(shape == null) {
			return "";
		}
		long forward = shape.outputIndex(cellIndex, piece.getOrientation());
		ElementCollection.getPosFromIndex(forward, scratchNeighborPos);

		if(reason == StallReason.NEXT_BELT_MISAIMED) {
			// Name the block to rotate, where it CURRENTLY takes input from, and where it needs to. Stating
			// only the requirement is nearly useless — the player already assumes it should accept from
			// here; what they cannot see is the side it is actually listening on. The gap between the two
			// is the whole diagnosis, and on a turn those are different faces, not opposite ones.
			SegmentPiece next = buffer.getPointUnsave(scratchNeighborPos);
			BeltShape nextShape = (next == null) ? null : BeltShape.of(next.getType());
			if(nextShape == null) {
				return "\nblock at " + str(scratchNeighborPos) + " is not a belt";
			}
			Vector3i actual = new Vector3i();
			ElementCollection.getPosFromIndex(
					nextShape.inputIndex(forward, next.getOrientation()), actual);
			return "\n" + nextShape.name() + " at " + str(scratchNeighborPos)
					+ "\ntakes input from " + str(actual) + ", needs " + str(scratchPos);
		}
		if(reason == StallReason.NO_DESTINATION || reason == StallReason.DESTINATION_FULL) {
			return "\nat " + str(scratchNeighborPos);
		}
		return "";
	}

	private static String str(Vector3i p) {
		return "(" + p.x + ", " + p.y + ", " + p.z + ")";
	}

	private void spawnStallIndicator(Transform worldTransform, long cellIndex, StallReason reason, String detail) {
		ElementCollection.getPosFromIndex(cellIndex, scratchPos);
		Vector3f local = new Vector3f(
				scratchPos.x - HALF_DIM.x,
				scratchPos.y - HALF_DIM.y + 0.6f,
				scratchPos.z - HALF_DIM.z);
		Transform start = new Transform(worldTransform);
		worldTransform.basis.transform(local);
		start.origin.add(local);

		HudIndicatorOverlay.toDrawTexts.add(new ConveyorStallIndicator(
				start, reason.message() + detail, STALL_COLOR, INDICATOR_LIFE_SECONDS));
	}

	private void drawTravelArrows(SegmentController controller, Set<Long> belts) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		Vector3f cameraLocal = beginArrowDraw(controller);

		for(long blockIndex : belts) {
			ElementCollection.getPosFromIndex(blockIndex, scratchPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchPos);
			BeltShape shape = (piece == null) ? null : BeltShape.of(piece.getType());
			if(shape == null) {
				continue;
			}
			drawShapeLegs(shape, piece.getOrientation(), scratchPos, cameraLocal);
		}

		endArrowDraw();
	}

	/**
	 * Draws one belt's flow path at block position {@code pos}: a single green arrow for a straight
	 * belt, or a cyan entry leg into a green exit arrow for a turn, plus a magenta tick along the belt
	 * surface normal. Entry and exit come straight from the block's {@link BeltShape} — the same faces
	 * the simulator routes items through — so the drawing always matches the actual behaviour.
	 */
	private void drawShapeLegs(BeltShape shape, byte orientation, Vector3i pos, Vector3f cameraLocal) {
		Vector3i up = shape.surfaceNormal(orientation, 0.0f);

		Vector3f center = new Vector3f(pos.x - HALF_DIM.x, pos.y - HALF_DIM.y, pos.z - HALF_DIM.z);
		// Float the drawing toward the viewer so it isn't buried inside the opaque block.
		Vector3f toCamera = new Vector3f();
		toCamera.sub(cameraLocal, center);
		float distance = toCamera.length();
		if(distance > 1.0e-4f) {
			toCamera.scale(1.0f / distance);
			center.scaleAdd(0.55f, toCamera, center);
		}

		boolean straight = shape.entryCount() == 1 && shape.exitCount() == 1
				&& sameDir(shape.entryFlow(orientation), shape.exitFlow(orientation));
		if(straight) {
			// Single centred travel arrow — entry and exit share a line, so an L would just be a line.
			GlUtil.glColor4f(0.2f, 1.0f, 0.3f, 0.95f);
			drawArrow(center, shape.exitFlow(orientation));
		} else {
			// Draw every leg: cyan entry legs (entry face -> elbow) show where stacks arrive, green exit
			// arrows (elbow -> exit face) where they leave. A splitter fans several greens out of one
			// elbow, a merger several cyans in.
			GlUtil.glColor4f(0.2f, 0.7f, 1.0f, 0.95f);
			for(int i = 0, n = shape.entryCount(); i < n; i++) {
				Vector3i in = shape.entryFlow(orientation, i);
				Vector3f entry = new Vector3f(center.x - in.x * 0.4f, center.y - in.y * 0.4f, center.z - in.z * 0.4f);
				drawSegment(entry, center);
			}
			GlUtil.glColor4f(0.2f, 1.0f, 0.3f, 0.95f);
			for(int i = 0, n = shape.exitCount(); i < n; i++) {
				Vector3i out = shape.exitFlow(orientation, i);
				Vector3f exit = new Vector3f(center.x + out.x * 0.4f, center.y + out.y * 0.4f, center.z + out.z * 0.4f);
				drawArrowBetween(center, exit);
			}
		}

		// Surface-up normal (magenta) — the belt's "up" face, so the model's top can be aligned too.
		GlUtil.glColor4f(1.0f, 0.3f, 1.0f, 0.95f);
		Vector3f upEnd = new Vector3f(center.x + up.x * 0.3f, center.y + up.y * 0.3f, center.z + up.z * 0.3f);
		drawSegment(center, upEnd);
	}

	/**
	 * Pushes the entity transform and the line-drawing GL state, returning the camera position in
	 * entity-local space. Pair with {@link #endArrowDraw()}.
	 */
	private Vector3f beginArrowDraw(SegmentController controller) {
		GlUtil.glPushMatrix();
		Transform worldTransform = controller.getWorldTransformOnClient();
		GlUtil.glMultMatrix(worldTransform);

		Vector3f cameraLocal = new Vector3f(Controller.getCamera().getPos());
		Transform inverse = new Transform(worldTransform);
		inverse.inverse();
		inverse.transform(cameraLocal);

		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glDisable(GL11.GL_DEPTH_TEST);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(3.0f);
		GlUtil.glColor4f(0.2f, 1.0f, 0.3f, 0.95f);
		return cameraLocal;
	}

	private void endArrowDraw() {
		GlUtil.glEnable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_DEPTH_TEST);
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glEnable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(1.0f);
		GlUtil.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		GlUtil.glPopMatrix();
	}

	private Set<Long> findConnectedConveyors(SegmentController controller, long startIndex) {
		Set<Long> visited = new HashSet<>();
		Queue<Long> queue = new LinkedList<>();
		queue.add(startIndex);
		visited.add(startIndex);

		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		while(!queue.isEmpty()) {
			long current = queue.poll();
			ElementCollection.getPosFromIndex(current, scratchPos);
			long[] neighbors = neighborsOf(scratchPos);
			for(long neighbor : neighbors) {
				if(visited.contains(neighbor)) {
					continue;
				}
				ElementCollection.getPosFromIndex(neighbor, scratchNeighborPos);
				SegmentPiece piece = buffer.getPointUnsave(scratchNeighborPos);
				if(piece != null && ElementRegistry.isConveyorBelt(piece.getType())) {
					visited.add(neighbor);
					queue.add(neighbor);
				}
			}
		}
		return visited;
	}

	private long findAdjacentConveyor(SegmentController controller, long centerIndex) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		ElementCollection.getPosFromIndex(centerIndex, scratchPos);
		for(long neighbor : neighborsOf(scratchPos)) {
			ElementCollection.getPosFromIndex(neighbor, scratchNeighborPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchNeighborPos);
			if(piece != null && ElementRegistry.isConveyorBelt(piece.getType())) {
				return neighbor;
			}
		}
		return -1;
	}

	private static long[] neighborsOf(Vector3i pos) {
		return new long[] {
				ElementCollection.getIndex(pos.x + 1, pos.y, pos.z),
				ElementCollection.getIndex(pos.x - 1, pos.y, pos.z),
				ElementCollection.getIndex(pos.x, pos.y + 1, pos.z),
				ElementCollection.getIndex(pos.x, pos.y - 1, pos.z),
				ElementCollection.getIndex(pos.x, pos.y, pos.z + 1),
				ElementCollection.getIndex(pos.x, pos.y, pos.z - 1)
		};
	}

	/** Centred travel arrow: shaft spans {@code start ± direction*0.4} with the head at the forward end. */
	private void drawArrow(Vector3f start, Vector3i direction) {
		Vector3f dir = new Vector3f(direction.x, direction.y, direction.z);
		float len = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
		if(len < 0.001f) {
			return;
		}
		dir.scale(1.0f / len);
		Vector3f tail = new Vector3f(start.x - dir.x * 0.4f, start.y - dir.y * 0.4f, start.z - dir.z * 0.4f);
		Vector3f end = new Vector3f(start.x + dir.x * 0.4f, start.y + dir.y * 0.4f, start.z + dir.z * 0.4f);
		drawSegment(tail, end);
		drawArrowHead(end, dir);
	}

	/** Arrow whose shaft runs {@code start -> end}, head at {@code end}. Used for a corner's output leg. */
	private void drawArrowBetween(Vector3f start, Vector3f end) {
		Vector3f dir = new Vector3f(end.x - start.x, end.y - start.y, end.z - start.z);
		float len = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
		if(len < 0.001f) {
			return;
		}
		dir.scale(1.0f / len);
		drawSegment(start, end);
		drawArrowHead(end, dir);
	}

	private void drawSegment(Vector3f a, Vector3f b) {
		GL11.glBegin(GL11.GL_LINES);
		GL11.glVertex3f(a.x, a.y, a.z);
		GL11.glVertex3f(b.x, b.y, b.z);
		GL11.glEnd();
	}

	/** Draws a small cone arrowhead at {@code tip} pointing along the unit vector {@code dir}. */
	private void drawArrowHead(Vector3f tip, Vector3f dir) {
		float headLength = 0.18f;
		float headWidth = 0.1f;

		Vector3f perp1 = new Vector3f();
		Vector3f perp2 = new Vector3f();
		if(Math.abs(dir.y) < 0.9f) {
			perp1.set(dir.z, 0, -dir.x);
		} else {
			perp1.set(0, -dir.z, dir.y);
		}
		perp1.normalize();
		perp2.cross(dir, perp1);
		perp2.normalize();
		perp1.scale(headWidth);
		perp2.scale(headWidth);

		Vector3f base = new Vector3f(tip.x - dir.x * headLength, tip.y - dir.y * headLength, tip.z - dir.z * headLength);

		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glVertex3f(tip.x, tip.y, tip.z);
		GL11.glVertex3f(base.x + perp1.x, base.y + perp1.y, base.z + perp1.z);
		GL11.glVertex3f(base.x + perp2.x, base.y + perp2.y, base.z + perp2.z);

		GL11.glVertex3f(tip.x, tip.y, tip.z);
		GL11.glVertex3f(base.x + perp2.x, base.y + perp2.y, base.z + perp2.z);
		GL11.glVertex3f(base.x - perp1.x, base.y - perp1.y, base.z - perp1.z);

		GL11.glVertex3f(tip.x, tip.y, tip.z);
		GL11.glVertex3f(base.x - perp1.x, base.y - perp1.y, base.z - perp1.z);
		GL11.glVertex3f(base.x - perp2.x, base.y - perp2.y, base.z - perp2.z);

		GL11.glVertex3f(tip.x, tip.y, tip.z);
		GL11.glVertex3f(base.x - perp2.x, base.y - perp2.y, base.z - perp2.z);
		GL11.glVertex3f(base.x + perp1.x, base.y + perp1.y, base.z + perp1.z);
		GL11.glEnd();
	}

	private static boolean sameDir(Vector3i a, Vector3i b) {
		return a.x == b.x && a.y == b.y && a.z == b.z;
	}
}
