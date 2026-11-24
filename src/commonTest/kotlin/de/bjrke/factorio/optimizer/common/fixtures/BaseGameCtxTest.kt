package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.game.context.GameContext
import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry

open class BaseGameCtxTest : GameCtxTestMixin {

    override val gameCtx = GameContext(MaterialRegistry(TEST_FLUID_NAMES))
}
