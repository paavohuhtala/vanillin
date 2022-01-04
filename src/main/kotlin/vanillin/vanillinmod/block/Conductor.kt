package vanillin.vanillinmod.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import kotlin.math.max

class Conductor(properties: Properties) : Block(properties) {
    companion object {
        private val POWER: IntegerProperty = BlockStateProperties.POWER
    }

    private var shouldSignal = true

    override fun getDirectSignal(
        pState: BlockState,
        blockGetter: BlockGetter,
        pPos: BlockPos,
        pDirection: Direction
    ): Int {
        if (!shouldSignal) {
            return 0
        }

        return pState.getSignal(blockGetter, pPos, pDirection)
    }

    override fun isSignalSource(pState: BlockState): Boolean = shouldSignal

    override fun neighborChanged(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pBlock: Block,
        pFromPos: BlockPos,
        pIsMoving: Boolean
    ) {
        if (pLevel.isClientSide) {
            return
        }

        updatePowerStrength(pLevel, pPos, pState)
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(POWER)
    }

    private fun updatePowerStrength(level: Level, pos: BlockPos, state: BlockState) {
        val targetStrength = calculateTargetStrength(level, pos)

        if (state.getValue(POWER) == targetStrength) {
            return
        }

        if (level.getBlockState(pos) == state) {
            level.setBlock(pos, state.setValue(POWER, targetStrength), 2)
        }

        level.updateNeighborsAt(pos, this)

        for (direction in Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this)
        }
    }

    private fun calculateTargetStrength(level: Level, pos: BlockPos): Int {
        shouldSignal = false
        val incomingSignal = level.getBestNeighborSignal(pos)

        shouldSignal = true

        var strongestNeighborSignal = 0

        if (incomingSignal < 15) {
            for (direction in Direction.values()) {
                val neighborPos = pos.relative(direction)
                val neighborState = level.getBlockState(neighborPos)
                strongestNeighborSignal = max(strongestNeighborSignal, getWireSignal(neighborState))
            }
        }

        return max(strongestNeighborSignal - 1, incomingSignal)
    }

    private fun getWireSignal(state: BlockState): Int {
        return if (state.`is`(this)) {
            state.getValue(POWER)
        } else {
            0
        }
    }

    override fun getSignal(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pDirection: Direction): Int {
        if (!shouldSignal) {
            return 0
        }

        return pState.getValue(POWER)
    }

    override fun onPlace(pState: BlockState, pLevel: Level, pPos: BlockPos, pOldState: BlockState, pIsMoving: Boolean) {
        if (pLevel.isClientSide) {
            return
        }

        if (pOldState.`is`(pState.block)) {
            return
        }

        updatePowerStrength(pLevel, pPos, pState)
    }

    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pIsMoving: Boolean
    ) {
        if (pIsMoving) {
            return
        }

        if (pState.`is`(pNewState.block)) {
            return
        }

        @Suppress("DEPRECATION")
        super.onRemove(pState, pLevel, pPos, pNewState, false)

        if (pLevel.isClientSide) {
            return
        }

        for (direction in Direction.values()) {
            pLevel.updateNeighborsAt(pPos.relative(direction), this)
        }

        updatePowerStrength(pLevel, pPos, pState)
    }
}