@file:Suppress("SpellCheckingInspection")

package vanillin.vanillinmod

import vanillin.vanillinmod.block.ModBlocks
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.runForDist
import vanillin.vanillinmod.block.ModBlocks.KILN_ITEM

/**
 * Main mod class. Should be an `object` declaration annotated with `@Mod`.
 * The modid should be declared in this object and should match the modId entry
 * in mods.toml.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(Vanillin.ID)
object Vanillin {
    const val ID = "vanillin"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.BLOCKS.register(MOD_BUS)
        ModBlocks.ITEMS.register(MOD_BUS)
        ModBlocks.BLOCK_ENTITIES.register(MOD_BUS)

        ModRecipes.RECIPE_SERIALIZERS.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)

                MinecraftForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedInEvent ->
                    LOGGER.log(Level.INFO, "player logged in :D ${event.player.name}")

                    if (!event.player.level.isClientSide) {
                        LOGGER.log(Level.INFO, "giving kiln :D $KILN_ITEM")
                        event.player.addItem(KILN_ITEM.defaultInstance)
                    }
                }

                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)

                "test"
            })

        println(obj)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "common setup :D")
        event.enqueueWork {
            LOGGER.log(Level.INFO, "what the heck :D")
            LOGGER.log(Level.INFO, ModRecipes.FIRING_RECIPE_SERIALIZER)
            LOGGER.log(Level.INFO, Registry.RECIPE_TYPE.keySet().toString())
            // ModRecipes.FIRING_RECIPE = RecipeType.register("kiln_firing")
            /*LOGGER.log(Level.INFO, ModRecipes.FIRING_RECIPE.toString())*/
        }
    }
}