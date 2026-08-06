package videogoose.resourcesreorganized.logistics.item.belt;

/**
 * Why a stack on a conveyor belt is not moving.
 * <p>
 * A stalled stack is visually identical to a broken one &mdash; it simply parks at its cell's exit
 * face and stops &mdash; so without a reason the player cannot tell "correctly waiting for space" from
 * "this belt is aimed at nothing". The simulator records the reason on the {@link BeltItem}; it is
 * synced to clients as a single byte and turned into text there, so the wording stays client-side.
 */
public enum StallReason {

	/** Not stalled. */
	NONE(null),

	/**
	 * The next cell is a belt, but its entry face does not point back here. Handover is deliberately
	 * strict, so a mis-rotated turn is a dead end rather than a silent side-load.
	 */
	NEXT_BELT_MISAIMED("Next belt faces the wrong way - rotate it to face this one"),

	/** The next belt already holds a stack. Ordinary back-pressure; resolves on its own. */
	NEXT_BELT_OCCUPIED("Waiting - next belt is occupied"),

	/** There is no belt ahead and no block registering an inventory at that position. */
	NO_DESTINATION("Nothing ahead to deliver into"),

	/** An inventory is there but will not take the stack, usually full or out of cargo capacity. */
	DESTINATION_FULL("Destination is full or has no cargo capacity"),

	/** The destination took part of the stack; the remainder is waiting for space. */
	DESTINATION_PARTIAL("Destination took part of the stack - waiting for space");

	private static final StallReason[] VALUES = values();

	private final String message;

	StallReason(String message) {
		this.message = message;
	}

	/** Player-facing description, or {@code null} for {@link #NONE}. */
	public String message() {
		return message;
	}

	/** Whether this is a transient condition that clears itself rather than a build mistake. */
	public boolean isTransient() {
		return this == NEXT_BELT_OCCUPIED || this == DESTINATION_PARTIAL;
	}

	/** Wire form: the ordinal, clamped so an unknown byte from a newer build reads as {@link #NONE}. */
	public byte code() {
		return (byte) ordinal();
	}

	/** Inverse of {@link #code()}, tolerant of out-of-range values. */
	public static StallReason fromCode(byte code) {
		return (code >= 0 && code < VALUES.length) ? VALUES[code] : NONE;
	}
}
