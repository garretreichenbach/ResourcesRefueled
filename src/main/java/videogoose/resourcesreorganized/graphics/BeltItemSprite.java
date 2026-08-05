package videogoose.resourcesreorganized.graphics;

import org.schema.common.util.StringTools;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.graphicsengine.forms.PositionableSubSprite;
import org.schema.schine.graphicsengine.forms.Sprite;

import javax.vecmath.Vector3f;

/**
 * A single in-transit conveyor stack rendered as a billboarded item icon, reusing the engine's
 * floating-item draw path ({@link Sprite#draw3D}). Mirrors {@link org.schema.game.common.data.player.inventory.FreeItem}'s
 * sub-sprite resolution so the same build-icon / meta-icon atlases used for items floating in space
 * are used here &mdash; the only difference is that the position is supplied in world space by the
 * {@link ConveyorItemDrawer} (transformed from the belt's entity-local cell), rather than being a
 * free-floating sector item.
 * <p>
 * Instances are pooled and re-targeted each frame, so the fields are plain and mutable.
 */
public final class BeltItemSprite implements PositionableSubSprite {

	private final Vector3f position = new Vector3f();
	private short type;
	private float scale;

	/** Re-targets this sprite for the current frame. {@code worldPos} is in sector/world space. */
	public void set(short type, Vector3f worldPos, float scale) {
		this.type = type;
		this.position.set(worldPos);
		this.scale = scale;
	}

	@Override
	public Vector3f getPos() {
		return position;
	}

	@Override
	public float getScale(long time) {
		return scale;
	}

	@Override
	public int getSubSprite(Sprite sprite) {
		if(type < 0) {
			// Meta items (capsules etc.) live in the meta-icons atlas, keyed by the absolute type id.
			if(sprite.getName().startsWith("meta-icons")) {
				return Math.abs(type);
			}
			return -1;
		}
		if(!ElementKeyMap.exists(type)) {
			return -1;
		}
		int n = ElementKeyMap.getInfo(type).getBuildIconNum() / 256;
		if(sprite.getName().startsWith("build-icons-" + StringTools.formatTwoZero(n))) {
			return ElementKeyMap.getInfo(type).getBuildIconNum() % 256;
		}
		return -1; // this stack does not belong to this atlas page
	}

	@Override
	public boolean canDraw() {
		return true;
	}
}
