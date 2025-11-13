package dev.enjarai.arcane_repository.item.custom.page.attribute;

import java.util.List;

import dev.enjarai.arcane_repository.item.custom.page.TypeDependentPage;
import net.minecraft.item.ItemStack;

import static dev.enjarai.arcane_repository.item.ModItems.*;

public final class WhitelistPage extends FilterPage {
    public WhitelistPage(String id) {
        super(id);
    }

    @Override
    public List<TypeDependentPage> getIncompatiblePages(ItemStack page) {
        return List.of(BLACKLIST_PAGE);
    }

    @Override
    public int getColor() {
        return 0x49a440;
    }
}
