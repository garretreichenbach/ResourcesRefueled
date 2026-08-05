package videogoose.resourcesreorganized.element.block.inventory;

import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;

public class ConveyorBeltUpTurn extends ConveyorBeltBlock {

	public ConveyorBeltUpTurn() {
		super("Conveyor Belt Up Turn", BeltShape.TURN_UP, """
				Conveyor belt that bends the line 90 degrees out through its top face.
				Items enter through the back face and leave upward, feeding the belt stacked above it.
				Only accepts input from that back face — a belt aimed at any other face backs up.""");
	}
}
