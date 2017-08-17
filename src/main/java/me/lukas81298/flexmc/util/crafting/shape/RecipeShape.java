package me.lukas81298.flexmc.util.crafting.shape;

import me.lukas81298.flexmc.util.crafting.CraftingInput;

/**
 * @author lukas
 * @since 17.08.2017
 */
public interface RecipeShape {

    boolean matches( CraftingInput input );

    int getWidth();

    int getHeight();
}
