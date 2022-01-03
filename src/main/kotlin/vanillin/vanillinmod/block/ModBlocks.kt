package vanillin.vanillinmod.block

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import vanillin.vanillinmod.Vanillin
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Material
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import thedarkcolour.kotlinforforge.forge.registerObject

object ModBlocks {
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(ForgeRegistries.BLOCKS, Vanillin.ID)
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, Vanillin.ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, Vanillin.ID)

    /*val RECLAIMER by BLOCKS.registerObject("reclaimer") {
        Block(BlockBehaviour.Properties.of(Material.STONE))
    }*/

    val KILN by BLOCKS.registerObject("kiln") {
        Kiln(
            BlockBehaviour.Properties.of(Material.STONE)
                .lightLevel { state ->
                    if (state.getValue(BlockStateProperties.LIT)) {
                        return@lightLevel 13
                    }
                    return@lightLevel 0
                })
    }

    val KILN_ITEM by ITEMS.registerObject("kiln_item") {
        BlockItem(KILN, Item.Properties().tab(CreativeModeTab.TAB_MISC))
    }

    val KILN_BLOCk_ENTITY: BlockEntityType<KilnBlockEntity> by BLOCK_ENTITIES.registerObject("kiln_block_entity") {
        BlockEntityType.Builder.of({ pos, state -> KilnBlockEntity(pos, state) }, KILN).build(null)
    }
}