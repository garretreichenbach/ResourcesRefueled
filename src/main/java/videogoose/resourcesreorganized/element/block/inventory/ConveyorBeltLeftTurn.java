package videogoose.resourcesreorganized.element.block.inventory;

import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;

public class ConveyorBeltLeftTurn extends ConveyorBeltBlock {

	public ConveyorBeltLeftTurn() {
		super("Conveyor Belt Left Turn", BeltShape.TURN_LEFT, """
				Conveyor belt that bends the line 90 degrees to the left.
				Items enter through a side face and leave through the front, staying on the belt surface.
				Only accepts input from that side face — a belt aimed at any other face backs up.""");
	}
}
