package videogoose.resourcesreorganized.element.block.inventory;

import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;

public class ConveyorBeltRightTurn extends ConveyorBeltBlock {

	public ConveyorBeltRightTurn() {
		super("Conveyor Belt Right Turn", BeltShape.TURN_RIGHT, """
				Conveyor belt that bends the line 90 degrees to the right.
				Items enter through a side face and leave through the front, staying on the belt surface.
				Only accepts input from that side face — a belt aimed at any other face backs up.""");
	}
}
