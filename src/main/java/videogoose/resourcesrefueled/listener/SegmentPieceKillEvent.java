package videogoose.resourcesrefueled.listener;

import api.listener.fastevents.segmentpiece.SegmentPieceKilledListener;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.controller.SendableSegmentController;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.data.SegmentPiece;

public class SegmentPieceKillEvent implements SegmentPieceKilledListener {

	@Override
	public void onBlockKilled(SegmentPiece segmentPiece, SendableSegmentController sendableSegmentController, @Nullable Damager damager, boolean b) {

	}
}
