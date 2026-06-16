package videogoose.resourcesreorganized.graphics;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.opengl.GL11;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.BuildModeDrawer;
import org.schema.game.client.view.gui.shiphud.newhud.Hud;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
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
import videogoose.resourcesreorganized.logistics.item.belt.BeltDirection;
import videogoose.resourcesreorganized.logistics.item.belt.BeltItem;
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
 * Debug-mode overlay that draws an arrow on each conveyor belt showing the item-travel direction
 * (as decoded by {@link BeltDirection#offset(byte)} from the block's orientation). Mirrors the
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

		// Admin-only debug draw toggle (F1 + F10 in-game), shared with vanilla physics debug.
		if(!EngineSettings.P_PHYSICS_DEBUG_ACTIVE.isOn()) {
			return;
		}
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
		if(currentPiece.getType() == ElementRegistry.CONVEYOR_BELT.getId()) {
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
		if(looked == null || looked.getType() != ElementRegistry.CONVEYOR_BELT.getId()) {
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

	private void drawTravelArrows(SegmentController controller, Set<Long> belts) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();

		GlUtil.glPushMatrix();
		Transform worldTransform = controller.getWorldTransformOnClient();
		GlUtil.glMultMatrix(worldTransform);

		// Camera position in entity-local space, so we can float each arrow toward the viewer
		// (off the block surface) instead of burying it inside the opaque block.
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

		for(long blockIndex : belts) {
			ElementCollection.getPosFromIndex(blockIndex, scratchPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchPos);
			if(piece == null || piece.getType() != ElementRegistry.CONVEYOR_BELT.getId()) {
				continue;
			}
			Vector3i d = BeltDirection.offset(piece.getOrientation());
			Vector3f center = new Vector3f(scratchPos.x - HALF_DIM.x, scratchPos.y - HALF_DIM.y, scratchPos.z - HALF_DIM.z);
			Vector3f toCamera = new Vector3f();
			toCamera.sub(cameraLocal, center);
			float distance = toCamera.length();
			if(distance > 1.0e-4f) {
				toCamera.scale(1.0f / distance);
				center.scaleAdd(0.55f, toCamera, center);
			}
			drawArrow(center, d);
		}

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
				if(piece != null && piece.getType() == ElementRegistry.CONVEYOR_BELT.getId()) {
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
			if(piece != null && piece.getType() == ElementRegistry.CONVEYOR_BELT.getId()) {
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

	private void drawArrow(Vector3f start, Vector3i direction) {
		Vector3f dir = new Vector3f(direction.x, direction.y, direction.z);
		float len = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
		if(len < 0.001f) {
			return;
		}
		dir.scale(1.0f / len);

		Vector3f end = new Vector3f(start.x + dir.x * 0.4f, start.y + dir.y * 0.4f, start.z + dir.z * 0.4f);

		GL11.glBegin(GL11.GL_LINES);
		GL11.glVertex3f(start.x - dir.x * 0.4f, start.y - dir.y * 0.4f, start.z - dir.z * 0.4f);
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glEnd();

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

		Vector3f base = new Vector3f(end.x - dir.x * headLength, end.y - dir.y * headLength, end.z - dir.z * headLength);

		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(base.x + perp1.x, base.y + perp1.y, base.z + perp1.z);
		GL11.glVertex3f(base.x + perp2.x, base.y + perp2.y, base.z + perp2.z);

		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(base.x + perp2.x, base.y + perp2.y, base.z + perp2.z);
		GL11.glVertex3f(base.x - perp1.x, base.y - perp1.y, base.z - perp1.z);

		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(base.x - perp1.x, base.y - perp1.y, base.z - perp1.z);
		GL11.glVertex3f(base.x - perp2.x, base.y - perp2.y, base.z - perp2.z);

		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(base.x - perp2.x, base.y - perp2.y, base.z - perp2.z);
		GL11.glVertex3f(base.x + perp1.x, base.y + perp1.y, base.z + perp1.z);
		GL11.glEnd();
	}
}
