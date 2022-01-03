package vanillin.vanillinmod.block

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen
import net.minecraft.client.gui.screens.recipebook.SmeltingRecipeBookComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import vanillin.vanillinmod.ModRecipes
import java.util.*


class Kiln(props: Properties) : AbstractFurnaceBlock(props) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return KilnBlockEntity(pos, state)
    }

    override fun openContainer(level: Level, pos: BlockPos, player: Player) {
        when (val blockEntity = level.getBlockEntity(pos)) {
            is KilnBlockEntity -> {
                player.openMenu(blockEntity)
            }
        }
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        blockState: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide) {
            return null
        }

        return createTickerHelper(
            blockEntityType,
            ModBlocks.KILN_BLOCk_ENTITY,
            KilnBlockEntity.Companion::serverTick,
        )
    }

    override fun animateTick(blockState: BlockState, level: Level, blockPos: BlockPos, random: Random) {
        if (blockState.getValue(LIT)) {
            val d0 = blockPos.x.toDouble() + 0.5
            val d1 = blockPos.y.toDouble()
            val d2 = blockPos.z.toDouble() + 0.5
            if (random.nextDouble() < 0.1) {
                level.playLocalSound(
                    d0,
                    d1,
                    d2,
                    SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f,
                    false
                )
            }
            val direction: Direction = blockState.getValue(FACING)
            val axis: Direction.Axis = direction.axis
            val d3 = 0.52
            val d4: Double = random.nextDouble() * 0.6 - 0.3
            val d5 = if (axis === Direction.Axis.X) direction.stepX.toDouble() * 0.52 else d4
            val d6: Double = random.nextDouble() * 6.0 / 16.0
            val d7 = if (axis === Direction.Axis.Z) direction.stepZ.toDouble() * 0.52 else d4
            level.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
        }
    }
}

class KilnBlockEntity(pos: BlockPos, state: BlockState) : AbstractFurnaceBlockEntity(
    ModBlocks.KILN_BLOCk_ENTITY,
    pos, state, ModRecipes.FIRING_RECIPE
) {
    override fun createMenu(a: Int, inventory: Inventory): AbstractContainerMenu {
        return KilnMenu(a, inventory, this, this.dataAccess)
    }

    override fun getDefaultName(): Component {
        return TranslatableComponent("container.vanillin.kiln")
    }

    fun canBurn(recipe: Recipe<*>?): Boolean {
        if (inputStack.isEmpty || recipe == null) {
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val assembledRecipe = (recipe as Recipe<KilnBlockEntity>).assemble(this)

        if (assembledRecipe.isEmpty) {
            // Invalid recipe -> fail
            return false
        }

        if (resultStack.isEmpty) {
            // Result slot is empty -> succeed
            return true
        }

        if (!resultStack.sameItem(assembledRecipe)) {
            // Items are incompatible -> fail
            return false
        }

        val newCount = resultStack.count + assembledRecipe.count

        if (newCount > this.maxStackSize || newCount > resultStack.maxStackSize) {
            // Recipe results would not fit in the output slot -> fail
            return false
        }

        // What does this accomplish?
        return newCount <= assembledRecipe.maxStackSize
    }

    fun burn(recipe: Recipe<*>?): Boolean {
        if (recipe == null || !canBurn(recipe)) {
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val assembledRecipe = (recipe as Recipe<KilnBlockEntity>).assemble(this)

        if (resultStack.isEmpty) {
            items[SLOT_RESULT] = assembledRecipe.copy()
        } else {
            resultStack.grow(assembledRecipe.count)
        }

        inputStack.shrink(1)

        return true
    }

    val inputStack get() = items[SLOT_INPUT]
    val fuelStack get() = items[SLOT_FUEL]
    val resultStack get() = items[SLOT_RESULT]

    companion object {
        // Based on AbstractFurnaceBlockEntity.serverTick
        fun serverTick(level: Level, blockPos: BlockPos, blockState: BlockState, kiln: KilnBlockEntity) {
            val wasLit = kiln.isLit

            if (kiln.isLit) {
                kiln.litTime -= 1
            }

            val fuelStack = kiln.fuelStack
            val inputStack = kiln.inputStack

            if ((kiln.isLit || !fuelStack.isEmpty) && !inputStack.isEmpty) {
                val recipe = level.recipeManager.getRecipeFor(ModRecipes.FIRING_RECIPE, kiln, level).orElse(null)

                if (!kiln.isLit && kiln.canBurn(recipe)) {
                    val burnDuration = kiln.getBurnDuration(fuelStack)
                    kiln.litTime = burnDuration
                    kiln.litDuration = burnDuration

                    // TODO: Is this check redundant?
                    if (kiln.isLit) {
                        // Is this a lava bucket?
                        if (fuelStack.hasContainerItem()) {
                            kiln.items[SLOT_FUEL] = fuelStack.containerItem
                        } else if (!fuelStack.isEmpty) {
                            val item = fuelStack.item
                            fuelStack.shrink(1)
                            if (fuelStack.isEmpty) {
                                kiln.items[SLOT_FUEL] = ItemStack.EMPTY
                            }
                        }
                    }
                }

                if (kiln.isLit && kiln.canBurn(recipe)) {
                    kiln.cookingProgress += 1

                    if (kiln.cookingProgress == kiln.cookingTotalTime) {
                        kiln.cookingProgress = 0
                        kiln.cookingTotalTime = recipe.cookingTime

                        if (kiln.burn(recipe)) {
                            kiln.recipeUsed = recipe
                        }
                    }
                }
                else {
                    kiln.cookingProgress = 0
                }
            } else if (!kiln.isLit && kiln.cookingProgress > 0) {
                kiln.cookingProgress = Mth.clamp(kiln.cookingProgress - 2, 0, kiln.cookingTotalTime)
            }

            if (wasLit != kiln.isLit) {
                var newState = blockState.setValue(AbstractFurnaceBlock.LIT, kiln.isLit)
                level.setBlock(blockPos, newState, 3)
                setChanged(level, blockPos, newState)
            }
        }
    }
}

object KilnUi {
    val KILN_MENU: MenuType<KilnMenu> by lazy {
        @Suppress("DEPRECATION")
        Registry.register(Registry.MENU, "kiln", MenuType(::KilnMenu))
    }

    val KILN_MENU_SCREEN by lazy {
        MenuScreens.register(KILN_MENU, ::KilMenuScreen)
    }
}

class KilnMenu : AbstractFurnaceMenu {
    constructor(a: Int, inventory: Inventory) : super(
        KilnUi.KILN_MENU,
        ModRecipes.FIRING_RECIPE,
        RecipeBookType.FURNACE,
        a,
        inventory
    ) {

    }

    constructor(a: Int, inventory: Inventory, container: Container, containerData: ContainerData) : super(
        MenuType.FURNACE,
        ModRecipes.FIRING_RECIPE,
        RecipeBookType.FURNACE,
        a,
        inventory,
        container,
        containerData
    ) {

    }
}

/*@OnlyIn(Dist.CLIENT)
class FiringRecipeBookComponent : AbstractFurnaceRecipeBookComponent() {
    override fun getFuelItems(): MutableSet<Item> {
        @Suppress("DEPRECATION")
        return AbstractFurnaceBlockEntity.getFuel().keys
    }

    override fun getRecipeFilterName(): Component? {
        return TranslatableComponent("gui.vanillin.recipebook.toggleRecipes.firable")
    }

}*/

@OnlyIn(Dist.CLIENT)
class KilMenuScreen(
    menu: KilnMenu, inventory: Inventory, component: Component,
) : AbstractFurnaceScreen<KilnMenu>(
    menu,
    SmeltingRecipeBookComponent(),
    // FiringRecipeBookComponent(),
    inventory,
    component,
    ResourceLocation("textures/gui/container/furnace.png")
) {

}
