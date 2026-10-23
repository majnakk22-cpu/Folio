package dev.folio.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Иконки предметов по id вида "modid:item" (неизвестный предмет заменяется книгой). */
public final class Icons {

    public static ItemStack stack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return new ItemStack(item == null || item == Items.AIR ? Items.BOOK : item);
    }

    private Icons() {
    }
}
