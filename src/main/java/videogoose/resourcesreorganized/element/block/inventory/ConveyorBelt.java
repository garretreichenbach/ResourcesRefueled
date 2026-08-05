package videogoose.resourcesreorganized.element.block.inventory;

import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;

public class ConveyorBelt extends ConveyorBeltBlock {

	public ConveyorBelt() {
		super("Conveyor Belt", BeltShape.STRAIGHT, """
				Basic item transport line.
				Extracts from adjacent inventories without a pump.
				Carries items straight through: in the back face, out the front.
				Use the turn variants to route around bends, and inventory ports for filtering, splitting, and combining.""");
	}
}
