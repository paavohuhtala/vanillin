package vanillin.vanillinmod

import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SimpleCookingSerializer
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import thedarkcolour.kotlinforforge.forge.registerObject

object ModRecipes {
    val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Vanillin.ID)

    // var FIRING_RECIPE: RecipeType<FiringRecipe>? = null

    val FIRING_RECIPE: RecipeType<FiringRecipe> by lazy {
        RecipeType.register("vanillin:kiln_firing")
    }

    val FIRING_RECIPE_SERIALIZER: SimpleCookingSerializer<FiringRecipe> by RECIPE_SERIALIZERS.registerObject("kiln_firing") {
        SimpleCookingSerializer(::FiringRecipe, 100)
    }
}