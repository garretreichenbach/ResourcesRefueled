package videogoose.resourcesrefueled.graphics;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.opengl.GL11;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.view.BuildModeDrawer;
import org.schema.game.client.view.gui.shiphud.newhud.Hud;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.world.Segment;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.systems.FluidSystemModule;

import javax.vecmath.Vector3f;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Handles drawing of fluid in fluid networks, as well as the flow visualization for build mode.
 */
public class FluidNetworkDrawer extends ModWorldDrawer {

	private boolean initialized;
	private final Vector3i HALF_DIM = new Vector3i(Segment.HALF_DIM, Segment.HALF_DIM, Segment.HALF_DIM);

	// Scratch variables to avoid allocating new objects every frame
	private final Vector3i scratchPos = new Vector3i();
	private final Vector3i scratchNeighborPos = new Vector3i();
	private final Vector3i scratchMin = new Vector3i();
	private final Vector3i scratchMax = new Vector3i();
	private final Vector3f minF = new Vector3f();
	private final Vector3f maxF = new Vector3f();


	@Override
	public void onInit() {
		//Any setup work goes here
		initialized = true;
	}

	@Override
	public void draw() {
		if(!initialized) {
			onInit();
		}

		if(shouldDrawFlowPreview()) {
			drawFlowPreview();
		}
		GlUtil.printGlError(); //Print any GL errors after drawing to help catch issues with the fluid rendering code

		if(shouldDrawFluidInWorld()) {
			drawFluidsInWorld();
		}
		GlUtil.printGlError(); //Print any GL errors after drawing to help catch issues with the fluid rendering code
	}

	@Override
	public void update(Timer timer) {
		//Any non-drawing code that needs to run every tick goes here
	}

	@Override
	public void cleanUp() {
		//Any cleanup work goes here
	}

	@Override
	public boolean isInvisible() {
		return false;
	}

	public void drawFluidsInWorld() {
		//Todo: Draw fluids
	}

	public void drawFlowPreview() {
		// Get the player's currently controlled entity
		if(!(GameClient.getCurrentControl() instanceof ManagedUsableSegmentController)) return;

		ManagedUsableSegmentController<?> usableController = (ManagedUsableSegmentController<?>) GameClient.getCurrentControl();
		ManagerContainer<?> container = usableController.getManagerContainer();

		// Get the fluid module for this entity
		FluidSystemModule flowModule = (FluidSystemModule) container.getModMCModule(ElementRegistry.FLUID_TANK.getId());
		if(flowModule == null) return;

		// Get the currently hovered block from BuildModeDrawer
		SegmentPiece currentPiece = BuildModeDrawer.currentPiece;
		if(currentPiece == null) return;

		short currentType = currentPiece.getType();
		long currentIndex = currentPiece.getAbsoluteIndex();

		// Find the network - either from the current block if it's a fluid block,
		// or from adjacent fluid blocks if we're hovering over a non-fluid block
		Set<Long> allNetworkBlocks;

		if(isFluidNetworkBlock(currentType)) {
			allNetworkBlocks = findConnectedNetwork(usableController, currentIndex);
		} else {
			// Not hovering over a fluid block - check adjacent blocks for fluid networks
			allNetworkBlocks = findAdjacentNetwork(usableController, currentIndex);
		}

		if(allNetworkBlocks == null || allNetworkBlocks.isEmpty()) return;

		// Group blocks into separate sections (tanks vs pipes, etc)
		Set<Set<Long>> sections = findNetworkSections(usableController, allNetworkBlocks);

		// Find which section contains the currently hovered block (if it's a fluid block)
		Set<Long> currentSection = null;
		if(isFluidNetworkBlock(currentType)) {
			for(Set<Long> section : sections) {
				if(section.contains(currentIndex)) {
					currentSection = section;
					break;
				}
			}
			// Draw all sections with wireframes
			drawNetworkSections(usableController, sections, currentSection);
			drawHudInfo(usableController, flowModule, currentSection, flowModule.getFlowForNetwork(currentIndex), getCurrentLookedAtNetwork());
		}

		// Always draw flow arrows for pumps in the network
		drawFlowArrows(usableController, allNetworkBlocks);
	}

	private void drawHudInfo(ManagedUsableSegmentController<?> usableController, FluidSystemModule fluidModule, Set<Long> currentSection, float flow, FluidSystemModule.FluidNetwork currentNetwork) {
		Hud hud = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud();
		//Add some vertical spacing so we aren't drawing over other info
		String text = "\n\n\n\nCapacity: " + currentNetwork.tankCapacity + "L\nCurrent Volume: " + currentNetwork.fluidLevel+ "L\nContents: " + currentNetwork.getFluidName();
		if(flow == 0) {
			text += "\nFlow: No flow";
		} else if(flow > 0) {
			text += "\nFlow: Outflow (" + flow + "L/s)";
		} else {
			text += "\nFlow: Inflow (" + (-flow) + "L/s)";
		}
		hud.getHelpManager().addInfo(HudContextHelperContainer.Hos.MOUSE, ContextFilter.NORMAL, text);
	}

	public boolean shouldDrawFluidInWorld() {
		return true; //Todo: Distance setting for fluid rendering?
	}

	public boolean shouldDrawFlowPreview() {
		short selectedType = GameClient.getPICM().getSelectedTypeWithSub();
		// Draw if we're in build mode AND (holding a fluid-compatible block OR advanced build mode is active)
		// AND we're hovering over ANY block (doesn't have to be a fluid block)
		return GameClient.getPICM().isInAnyStructureBuildMode()
			&& (isFluidNetworkBlock(selectedType) || PlayerInteractionControlManager.isAdvancedBuildMode(GameClient.getClientState()))
			&& BuildModeDrawer.currentPiece != null;
	}

	private boolean isFluidNetworkBlock(short type) {
		return ElementRegistry.isPipe(type) || ElementRegistry.canInteractWithFluid(type);
	}

	/**
	 * Groups network blocks into separate contiguous sections.
	 * Each section is a group of blocks of the same type that are directly adjacent.
	 */
	private Set<Set<Long>> findNetworkSections(SegmentController controller, Set<Long> allBlocks) {
		Set<Set<Long>> sections = new HashSet<>();
		Set<Long> processed = new HashSet<>();
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();

		for(long blockIndex : allBlocks) {
			if(processed.contains(blockIndex)) continue;

			// Get the type of this block
			ElementCollection.getPosFromIndex(blockIndex, scratchPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchPos);
			if(piece == null) continue;

			short blockType = piece.getType();

			// BFS to find all adjacent blocks of the same type
			Set<Long> section = new HashSet<>();
			Queue<Long> queue = new LinkedList<>();
			queue.add(blockIndex);
			section.add(blockIndex);
			processed.add(blockIndex);

			while(!queue.isEmpty()) {
				long current = queue.poll();
				ElementCollection.getPosFromIndex(current, scratchPos);

				long[] neighbors = {ElementCollection.getIndex(scratchPos.x + 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x - 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y + 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y - 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z + 1), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z - 1)};

				for(long neighbor : neighbors) {
					if(processed.contains(neighbor)) continue;
					if(!allBlocks.contains(neighbor)) continue;

					// Check if neighbor has the same type
					ElementCollection.getPosFromIndex(neighbor, scratchNeighborPos);
					SegmentPiece neighborPiece = buffer.getPointUnsave(scratchNeighborPos);

					if(neighborPiece != null && neighborPiece.getType() == blockType) {
						section.add(neighbor);
						processed.add(neighbor);
						queue.add(neighbor);
					}
				}
			}

			sections.add(section);
		}

		return sections;
	}

	/**
	 * Finds all blocks connected to the given index via face-adjacency.
	 * This performs a BFS to find all fluid network blocks connected to the starting position.
	 */
	private Set<Long> findConnectedNetwork(SegmentController controller, long startIndex) {
		Set<Long> visited = new HashSet<>();
		Queue<Long> queue = new LinkedList<>();
		queue.add(startIndex);
		visited.add(startIndex);

		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();
		while(!queue.isEmpty()) {
			long current = queue.poll();

			// Get the 6 face-adjacent positions
			ElementCollection.getPosFromIndex(current, scratchPos);

			long[] neighbors = {ElementCollection.getIndex(scratchPos.x + 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x - 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y + 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y - 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z + 1), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z - 1)};

			for(long neighbor : neighbors) {
				if(visited.contains(neighbor)) continue;

				// Check if this neighbor is a fluid network block
				ElementCollection.getPosFromIndex(neighbor, scratchNeighborPos);
				SegmentPiece piece = buffer.getPointUnsave(scratchNeighborPos);

				if(piece != null && isFluidNetworkBlock(piece.getType())) {
					visited.add(neighbor);
					queue.add(neighbor);
				}
			}
		}

		return visited;
	}

	/**
	 * Finds a fluid network adjacent to the given non-fluid block position.
	 * Checks all 6 face-adjacent positions for fluid network blocks and returns
	 * the network of the first one found.
	 */
	private Set<Long> findAdjacentNetwork(SegmentController controller, long centerIndex) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();

		// Get the 6 face-adjacent positions
		ElementCollection.getPosFromIndex(centerIndex, scratchPos);

		long[] neighbors = {ElementCollection.getIndex(scratchPos.x + 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x - 1, scratchPos.y, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y + 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y - 1, scratchPos.z), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z + 1), ElementCollection.getIndex(scratchPos.x, scratchPos.y, scratchPos.z - 1)};

		// Check each neighbor for a fluid network block
		for(long neighbor : neighbors) {
			ElementCollection.getPosFromIndex(neighbor, scratchNeighborPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchNeighborPos);

			if(piece != null && isFluidNetworkBlock(piece.getType())) {
				// Found an adjacent fluid block - return its network
				return findConnectedNetwork(controller, neighbor);
			}
		}

		// No adjacent fluid network found
		return null;
	}

	/**
	 * Draws bounding boxes for each section of the network.
	 * The section containing the currently hovered block is drawn in yellow, others in cyan.
	 */
	private void drawNetworkSections(SegmentController controller, Set<Set<Long>> sections, Set<Long> currentSection) {
		// Setup OpenGL state for drawing
		GlUtil.glPushMatrix();

		// Get the entity's world transform
		Transform worldTransform = controller.getWorldTransformOnClient();
		GlUtil.glMultMatrix(worldTransform);

		// Disable texturing and enable blending for transparency
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_LINE_SMOOTH);

		// Draw each section
		for(Set<Long> section : sections) {
			// Calculate bounding box for this section using scratch variables
			scratchMin.set(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
			scratchMax.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

			for(long blockIndex : section) {
				ElementCollection.getPosFromIndex(blockIndex, scratchPos);
				scratchMin.set(Math.min(scratchMin.x, scratchPos.x), Math.min(scratchMin.y, scratchPos.y), Math.min(scratchMin.z, scratchPos.z));
				scratchMax.set(Math.max(scratchMax.x, scratchPos.x), Math.max(scratchMax.y, scratchPos.y), Math.max(scratchMax.z, scratchPos.z));
			}

			// Convert to world coordinates (offset by segment half dimension)
			minF.set(scratchMin.x - HALF_DIM.x - 0.5f, scratchMin.y - HALF_DIM.y - 0.5f, scratchMin.z - HALF_DIM.z - 0.5f);
			maxF.set(scratchMax.x - HALF_DIM.x + 0.5f, scratchMax.y - HALF_DIM.y + 0.5f, scratchMax.z - HALF_DIM.z + 0.5f);

			// Set color based on whether this is the current section
			if(section == currentSection) {
				// Yellow for the section the player is looking at
				GL11.glLineWidth(3.0f);
				GlUtil.glColor4f(1.0f, 1.0f, 0.2f, 0.9f);
			} else {
				// Cyan for other sections
				GL11.glLineWidth(2.0f);
				GlUtil.glColor4f(0.4f, 0.8f, 1.0f, 0.6f);
			}

			// Draw wireframe outline
			drawBoundingBoxOutline(minF, maxF);
		}

		// Restore OpenGL state
		GlUtil.glEnable(GL11.GL_TEXTURE_2D);
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glEnable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(1.0f);
		GlUtil.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

		GlUtil.glPopMatrix();
	}

	/**
	 * Draws arrows at pump locations to show the direction of fluid flow.
	 */
	private void drawFlowArrows(SegmentController controller, Set<Long> networkBlocks) {
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();

		// Setup OpenGL state
		GlUtil.glPushMatrix();
		Transform worldTransform = controller.getWorldTransformOnClient();
		GlUtil.glMultMatrix(worldTransform);

		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glDisable(GL11.GL_DEPTH_TEST); // Disable depth testing so arrows draw through blocks
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(3.0f); // Thicker lines for better visibility

		// Find all pumps in the network
		for(long blockIndex : networkBlocks) {
			ElementCollection.getPosFromIndex(blockIndex, scratchPos);
			SegmentPiece piece = buffer.getPointUnsave(scratchPos);

			if(piece == null || piece.getType() != ElementRegistry.PIPE_PUMP.getId()) continue;

			// Get pump orientation to determine flow direction
			byte orientation = piece.getOrientation();
			Vector3i flowDirection = getFlowDirectionFromOrientation(orientation);

			// Calculate arrow position (center of the pump block)
			Vector3f pumpCenter = new Vector3f(scratchPos.x - HALF_DIM.x, scratchPos.y - HALF_DIM.y, scratchPos.z - HALF_DIM.z);

			// Draw arrow in the flow direction (larger arrow for better visibility)
			GlUtil.glColor4f(0.2f, 1.0f, 0.3f, 0.95f); // Bright green for flow direction
			drawArrow(pumpCenter, flowDirection);
		}

		// Restore OpenGL state
		GlUtil.glEnable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_DEPTH_TEST);
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glEnable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(1.0f);
		GlUtil.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

		GlUtil.glPopMatrix();
	}

	/**
	 * Gets the flow direction vector based on block orientation.
	 * Returns null if orientation cannot be determined.
	 */
	private Vector3i getFlowDirectionFromOrientation(byte orientation) {
		// Block orientations in StarMade:
		// The orientation byte encodes the direction the "front" of the block faces
		// We need to map this to a direction vector

		// Decode orientation to get the forward direction
		// StarMade uses Element.DIRxxx constants for face directions
		int dir = orientation & 0x07; // Lower 3 bits contain direction

		// Adjusted mapping to match pump model's actual orientation
		switch(dir) {
			case 0:
				return new Vector3i(0, 0, 1); // Front (Z-)
			case 1:
				return new Vector3i(0, 0, -1);  // Back (Z+)
			case 2:
				return new Vector3i(0, 1, 0);  // Top (Y+)
			case 3:
				return new Vector3i(0, -1, 0); // Bottom (Y-)
			case 4:
				return new Vector3i(-1, 0, 0); // Right (X-)
			case 5:
				return new Vector3i(1, 0, 0);  // Left (X+)
			default:
				return new Vector3i(0, 0, 1); // Default forward
		}
	}

	/**
	 * Draws a 3D arrow starting from the given position pointing in the given direction.
	 *
	 * @param start     The starting position (center of the block)
	 * @param direction The direction vector (will be normalized)
	 */
	private void drawArrow(Vector3f start, Vector3i direction) {
		// Normalize direction
		Vector3f dir = new Vector3f(direction.x, direction.y, direction.z);
		float len = (float) Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
		if(len < 0.001f) return;
		dir.scale(1.0f / len);

		// Calculate arrow shaft end point
		Vector3f end = new Vector3f(start.x + dir.x * 0.8f, start.y + dir.y * 0.8f, start.z + dir.z * 0.8f);

		// Draw main shaft
		GL11.glBegin(GL11.GL_LINES);
		GL11.glVertex3f(start.x, start.y, start.z);
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glEnd();

		// Draw arrowhead (cone at the end)
		float arrowheadLength = 0.8f * 0.3f;
		float arrowheadWidth = 0.8f * 0.15f;

		// Calculate perpendicular vectors for the arrowhead
		Vector3f perp1 = new Vector3f();
		Vector3f perp2 = new Vector3f();

		if(Math.abs(dir.y) < 0.9f) {
			// If not pointing mostly up/down, use Y as reference
			perp1.set(dir.z, 0, -dir.x);
		} else {
			// If pointing up/down, use X as reference
			perp1.set(0, -dir.z, dir.y);
		}
		perp1.normalize();
		perp2.cross(dir, perp1);
		perp2.normalize();

		// Scale perpendiculars
		perp1.scale(arrowheadWidth);
		perp2.scale(arrowheadWidth);

		// Calculate arrowhead base position
		Vector3f arrowBase = new Vector3f(end.x - dir.x * arrowheadLength, end.y - dir.y * arrowheadLength, end.z - dir.z * arrowheadLength);

		// Draw arrowhead as 4 triangular faces forming a cone
		GL11.glBegin(GL11.GL_TRIANGLES);

		// Face 1
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(arrowBase.x + perp1.x, arrowBase.y + perp1.y, arrowBase.z + perp1.z);
		GL11.glVertex3f(arrowBase.x + perp2.x, arrowBase.y + perp2.y, arrowBase.z + perp2.z);

		// Face 2
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(arrowBase.x + perp2.x, arrowBase.y + perp2.y, arrowBase.z + perp2.z);
		GL11.glVertex3f(arrowBase.x - perp1.x, arrowBase.y - perp1.y, arrowBase.z - perp1.z);

		// Face 3
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(arrowBase.x - perp1.x, arrowBase.y - perp1.y, arrowBase.z - perp1.z);
		GL11.glVertex3f(arrowBase.x - perp2.x, arrowBase.y - perp2.y, arrowBase.z - perp2.z);

		// Face 4
		GL11.glVertex3f(end.x, end.y, end.z);
		GL11.glVertex3f(arrowBase.x - perp2.x, arrowBase.y - perp2.y, arrowBase.z - perp2.z);
		GL11.glVertex3f(arrowBase.x + perp1.x, arrowBase.y + perp1.y, arrowBase.z + perp1.z);

		GL11.glEnd();
	}


	/**
	 * Draws a wireframe outline of a bounding box between min and max corners.
	 */
	private void drawBoundingBoxOutline(Vector3f min, Vector3f max) {
		GL11.glBegin(GL11.GL_LINES);

		// Bottom edges
		GL11.glVertex3f(min.x, min.y, min.z);
		GL11.glVertex3f(max.x, min.y, min.z);

		GL11.glVertex3f(max.x, min.y, min.z);
		GL11.glVertex3f(max.x, min.y, max.z);

		GL11.glVertex3f(max.x, min.y, max.z);
		GL11.glVertex3f(min.x, min.y, max.z);

		GL11.glVertex3f(min.x, min.y, max.z);
		GL11.glVertex3f(min.x, min.y, min.z);

		// Top edges
		GL11.glVertex3f(min.x, max.y, min.z);
		GL11.glVertex3f(max.x, max.y, min.z);

		GL11.glVertex3f(max.x, max.y, min.z);
		GL11.glVertex3f(max.x, max.y, max.z);

		GL11.glVertex3f(max.x, max.y, max.z);
		GL11.glVertex3f(min.x, max.y, max.z);

		GL11.glVertex3f(min.x, max.y, max.z);
		GL11.glVertex3f(min.x, max.y, min.z);

		// Vertical edges
		GL11.glVertex3f(min.x, min.y, min.z);
		GL11.glVertex3f(min.x, max.y, min.z);

		GL11.glVertex3f(max.x, min.y, min.z);
		GL11.glVertex3f(max.x, max.y, min.z);

		GL11.glVertex3f(max.x, min.y, max.z);
		GL11.glVertex3f(max.x, max.y, max.z);

		GL11.glVertex3f(min.x, min.y, max.z);
		GL11.glVertex3f(min.x, max.y, max.z);

		GL11.glEnd();
	}

	/**
	 * Gets the FluidTankSystemModule for the player's current entity.
	 * @return The module, or null if not available
	 */
	public FluidSystemModule getCurrentFluidModule() {
		if(!(GameClient.getCurrentControl() instanceof ManagedUsableSegmentController)) {
			return null;
		}

		ManagedUsableSegmentController<?> controller = (ManagedUsableSegmentController<?>) GameClient.getCurrentControl();
		ManagerContainer<?> container = controller.getManagerContainer();

		return (FluidSystemModule) container.getModMCModule(ElementRegistry.FLUID_TANK.getId());
	}

	/**
	 * Gets the fluid network that contains the block the player is currently looking at.
	 * @return The network, or null if not looking at a fluid network block
	 */
	public FluidSystemModule.FluidNetwork getCurrentLookedAtNetwork() {
		FluidSystemModule module = getCurrentFluidModule();
		if(module == null) return null;

		// Get the currently hovered block from BuildModeDrawer
		SegmentPiece currentPiece = BuildModeDrawer.currentPiece;
		if(currentPiece == null) return null;

		long currentIndex = currentPiece.getAbsoluteIndex();
		short currentType = currentPiece.getType();

		// Check if this block is part of a fluid network
		boolean isFluidBlock = isFluidNetworkBlock(currentType);

		// If not a fluid block, check adjacent blocks
		if(!isFluidBlock) {
			currentIndex = findAdjacentFluidBlock(currentIndex);
			if(currentIndex == -1) return null;
		}

		// Find which network contains this block
		for(FluidSystemModule.FluidNetwork network : module.getNetworks()) {
			if(network.memberIndices.contains(currentIndex)) {
				return network;
			}
		}

		return null;
	}

	/**
	 * Finds an adjacent fluid network block to the given position.
	 * @param centerIndex The center block index
	 * @return The index of an adjacent fluid block, or -1 if none found
	 */
	public long findAdjacentFluidBlock(long centerIndex) {
		if(!(GameClient.getCurrentControl() instanceof ManagedUsableSegmentController)) {
			return -1;
		}

		ManagedUsableSegmentController<?> controller = (ManagedUsableSegmentController<?>) GameClient.getCurrentControl();
		SegmentBufferManager buffer = (SegmentBufferManager) controller.getSegmentBuffer();

		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(centerIndex, pos);

		long[] neighbors = {ElementCollection.getIndex(pos.x + 1, pos.y, pos.z), ElementCollection.getIndex(pos.x - 1, pos.y, pos.z), ElementCollection.getIndex(pos.x, pos.y + 1, pos.z), ElementCollection.getIndex(pos.x, pos.y - 1, pos.z), ElementCollection.getIndex(pos.x, pos.y, pos.z + 1), ElementCollection.getIndex(pos.x, pos.y, pos.z - 1)};

		Vector3i neighborPos = new Vector3i();
		for(long neighbor : neighbors) {
			ElementCollection.getPosFromIndex(neighbor, neighborPos);
			SegmentPiece piece = buffer.getPointUnsave(neighborPos);

			if(piece != null && isFluidNetworkBlock(piece.getType())) {
				return neighbor;
			}
		}

		return -1;
	}
}
