package me.lukas81298.flexmc.inventory.crafting;

import org.bukkit.inventory.ItemStack;

/**
 * @author lukas
 * @since 10.08.2017
 */
public class ShapelessRecipe implements Recipe {

    private final ItemStack result;
    private final ItemStack[] ingredients;

    public ShapelessRecipe( ItemStack result, ItemStack... ingredients ) {
        this.result = result;
        this.ingredients = ingredients;
    }

    @Override
    public boolean apply( CraftingInput input ) {
        int s = input.getInputs().size();
        int expected = 0;
        for( ItemStack t : ingredients ) {
            expected += t.getAmount();
            if( !input.hasInputItems( t, t.getAmount() ) ) {
                return false;
            }
        }
        return s == expected;
    }

    @Override
    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe() {
        org.bukkit.inventory.ShapelessRecipe c = new org.bukkit.inventory.ShapelessRecipe( result );
        for ( ItemStack ingredient : ingredients ) {
            c.addIngredient( ingredient.getAmount(), ingredient.getType(), ingredient.getDurability() );
        }
        return c;
    }
}
