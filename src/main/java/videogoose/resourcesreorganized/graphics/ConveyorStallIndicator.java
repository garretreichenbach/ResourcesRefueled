package videogoose.resourcesreorganized.graphics;

import com.bulletphysics.linearmath.Transform;
import org.schema.game.client.view.effects.Indication;

import javax.vecmath.Vector4f;

/**
 * A short-lived label that floats up out of a stalled conveyor cell and fades, saying <i>why</i> the
 * stack is stuck.
 * <p>
 * The reason belongs in the world rather than on the HUD: it is about one specific block, the player
 * needs to look at that block to fix it, and the HUD is already contended. Rising and fading also
 * makes it self-limiting — it says its piece and gets out of the way, instead of being another
 * permanent readout to ignore.
 * <p>
 * Handed to {@code HudIndicatorOverlay.toDrawTexts}, which draws world-anchored text and drops the
 * indication once {@link #isAlive()} goes false.
 */
public class ConveyorStallIndicator extends Indication {

	/** How far (in blocks) the label drifts upward over its life. */
	private static final float RISE = 1.2f;

	/** Beyond this distance the engine stops drawing the label. */
	static final float MAX_DISTANCE = 96.0f;

	private final Transform current = new Transform();
	private final Vector4f color = new Vector4f();
	private final long bornMillis;
	private final float lifeSeconds;

	ConveyorStallIndicator(Transform worldStart, String text, Vector4f baseColor, float lifeSeconds) {
		super(worldStart, text);
		this.lifeSeconds = lifeSeconds;
		this.lifetime = lifeSeconds;
		this.bornMillis = System.currentTimeMillis();
		this.color.set(baseColor);
		this.current.set(worldStart);
		setDist(MAX_DISTANCE);
	}

	/**
	 * Fraction of this label's life elapsed, {@code [0, 1]}. Timed from the wall clock rather than the
	 * inherited {@code timeLived} so the rise and the fade stay in step with each other regardless of
	 * how the overlay chooses to tick indications.
	 */
	private float age() {
		float elapsed = (System.currentTimeMillis() - bornMillis) / 1000.0f;
		return Math.max(0.0f, Math.min(1.0f, elapsed / lifeSeconds));
	}

	@Override
	public Transform getCurrentTransform() {
		float t = age();
		current.set(start);
		current.origin.y += RISE * t;
		return current;
	}

	@Override
	public Vector4f getColor() {
		// Hold full opacity for the first half, then fade out — long enough to read, short enough not to
		// linger over the block the player is trying to fix.
		float t = age();
		float alpha = t < 0.5f ? 1.0f : 1.0f - ((t - 0.5f) / 0.5f);
		color.w = Math.max(0.0f, alpha);
		return color;
	}

	@Override
	public boolean isAlive() {
		return age() < 1.0f;
	}
}
