package vanillin.vanillinmod

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*

class FiringRecipe : AbstractCookingRecipe {
    constructor(
        a: ResourceLocation,
        b: String,
        c: Ingredient,
        d: ItemStack,
        e: Float,
        f: Int
    ) : super(ModRecipes.FIRING_RECIPE, a, b, c, d, e, f) {

    }

    override fun getSerializer(): RecipeSerializer<*> {
        return ModRecipes.FIRING_RECIPE_SERIALIZER
    }
}