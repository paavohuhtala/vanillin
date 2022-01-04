package vanillin.vanillinmod.block

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
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
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import thedarkcolour.kotlinforforge.forge.registerObject
import vanillin.vanillinmod.ModRecipes
import vanillin.vanillinmod.Vanillin
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
                } else {
                    kiln.cookingProgress = 0
                }
            } else if (!kiln.isLit && kiln.cookingProgress > 0) {
                kiln.cookingProgress = Mth.clamp(kiln.cookingProgress - 2, 0, kiln.cookingTotalTime)
            }

            if (wasLit != kiln.isLit) {
                val newState = blockState.setValue(AbstractFurnaceBlock.LIT, kiln.isLit)
                level.setBlock(blockPos, newState, 3)
                setChanged(level, blockPos, newState)
            }
        }
    }
}

object KilnUi {
    val MENU_TYPES: DeferredRegister<MenuType<*>> = DeferredRegister.create(ForgeRegistries.CONTAINERS, Vanillin.ID)

    val KILN_MENU: MenuType<KilnMenu> by MENU_TYPES.registerObject("kiln") {
        MenuType(::KilnMenu)
    }

    fun registerMenus() {
        MenuScreens.register(KILN_MENU, ::KilnMenuScreen)
    }
}

fun isFuel(stack: ItemStack): Boolean {
    return ForgeHooks.getBurnTime(stack, ModRecipes.FIRING_RECIPE) > 0
}

class KilnFuelSlot(pContainer: Container, pIndex: Int, pX: Int, pY: Int) : Slot(pContainer, pIndex, pX, pY) {
    override fun mayPlace(pStack: ItemStack): Boolean {
        // Do we really want to allow empty buckets?
        return isFuel(pStack) || FurnaceFuelSlot.isBucket(pStack)
    }
}

// Code mostly based on AbstractFurnaceMenu
class KilnMenu : AbstractContainerMenu {
    companion object {
        const val INGREDIENT_SLOT = 0
        const val FUEL_SLOT = 1
        const val RESULT_SLOT = 2

        private const val INV_SLOT_START = 3
        private const val INV_SLOT_END = 30
        private const val USE_ROW_SLOT_START = 30
        private const val USE_ROW_SLOT_END = 39
    }

    private val container: Container
    private val containerData: ContainerData
    private val level: Level

    private val recipeType = ModRecipes.FIRING_RECIPE

    constructor(containerId: Int, inventory: Inventory) : this(
        containerId,
        inventory,
        SimpleContainer(3),
        SimpleContainerData(4)
    ) {

    }

    constructor(
        containerId: Int,
        playerInventory: Inventory,
        container: Container,
        containerData: ContainerData
    ) : super(
        KilnUi.KILN_MENU,
        containerId,
    ) {
        checkContainerSize(container, 3)
        checkContainerDataCount(containerData, 4)

        this.container = container
        this.containerData = containerData
        this.level = playerInventory.player.level

        addSlot(Slot(container, INGREDIENT_SLOT, 56, 17))
        addSlot(KilnFuelSlot(container, FUEL_SLOT, 56, 53))
        addSlot(FurnaceResultSlot(playerInventory.player, container, RESULT_SLOT, 116, 35))

        for (y in 0..2) {
            for (x in 0..8) {
                addSlot(Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18))
            }
        }

        for (k in 0..8) {
            addSlot(Slot(playerInventory, k, 8 + k * 18, 142))
        }

        addDataSlots(containerData)
    }

    override fun stillValid(pPlayer: Player) = container.stillValid(pPlayer)

    private fun canSmelt(stack: ItemStack): Boolean {
        return level.recipeManager.getRecipeFor(recipeType, SimpleContainer(stack), level).isPresent
    }

    override fun quickMoveStack(pPlayer: Player, pIndex: Int): ItemStack {
        val slot = slots.getOrNull(pIndex)

        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY
        }

        val item = slot.item
        val stack = item.copy()

        when (pIndex) {
            RESULT_SLOT -> {
                if (!moveItemStackTo(item, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                    return ItemStack.EMPTY
                }

                slot.onQuickCraft(item, stack)
            }
            INGREDIENT_SLOT, FUEL_SLOT -> {
                if (!moveItemStackTo(item, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY
                }
            }
            in INV_SLOT_START until USE_ROW_SLOT_END -> {
                if (canSmelt(item)) {
                    if (!moveItemStackTo(item, INGREDIENT_SLOT, INGREDIENT_SLOT + 1, false)) {
                        return ItemStack.EMPTY
                    }
                } else if (isFuel(item)) {
                    if (!moveItemStackTo(item, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                        return ItemStack.EMPTY
                    }
                }
                // If the item is in the inventory, try moving it to the hotbar
                else if (pIndex in INV_SLOT_START until USE_ROW_SLOT_START) {
                    if (!moveItemStackTo(item, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                        return ItemStack.EMPTY
                    }
                }
                // The item must be in hotbar, try moving it to inventory
                else {
                    if (!moveItemStackTo(item, INV_SLOT_START, INV_SLOT_END, false)) {
                        return ItemStack.EMPTY
                    }
                }
            }
        }

        if (item.isEmpty) {
            slot.set(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        if (item.count == stack.count) {
            return ItemStack.EMPTY
        }

        slot.onTake(pPlayer, item)

        return stack
    }

    private val litTime get(): Int = containerData.get(0)
    private val litDuration get(): Int = containerData.get(1)
    private val cookingProgress get(): Int = containerData.get(2)
    private val cookingTotalTime get(): Int = containerData.get(3)

    val isLit get() = litTime > 0
    private val isCooking get() = cookingTotalTime != 0 && cookingProgress != 0

    val burnProgress
        get() = if (isCooking) {
            (cookingProgress * 24) / cookingTotalTime
        } else {
            0
        }

    val litProgress
        get(): Int {
            val i = if (litDuration == 0) {
                200
            } else {
                litDuration
            }

            return litTime * 13 / i
        }
}

@OnlyIn(Dist.CLIENT)
class KilnMenuScreen : AbstractContainerScreen<KilnMenu> {
    private val texture: ResourceLocation = ResourceLocation("textures/gui/container/furnace.png")

    constructor(menu: KilnMenu, playerInventory: Inventory, title: Component) : super(menu, playerInventory, title)

    override fun init() {
        super.init()
        titleLabelX = (imageWidth - font.width(title)) / 2
    }

    override fun renderBg(pPoseStack: PoseStack, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.setShaderTexture(0, texture)

        val left = leftPos
        val top = topPos
        this.blit(pPoseStack, left, top, 0, 0, imageWidth, imageHeight)

        if (menu.isLit) {
            val k = menu.litProgress
            this.blit(pPoseStack, left + 56, top + 36 + 12 - k, 176, 12 - k, 14, k + 1)
        }

        val l = menu.burnProgress
        this.blit(pPoseStack, left + 79, top + 34, 176, 14, l + 1, 16)
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        this.renderBackground(pPoseStack)
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)
        this.renderTooltip(pPoseStack, pMouseX, pMouseY)
    }
}