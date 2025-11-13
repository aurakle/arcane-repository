package dev.enjarai.arcane_repository.item.custom.page.attribute;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.enjarai.arcane_repository.item.ItemSettings;
import dev.enjarai.arcane_repository.item.ModDataComponentTypes;
import dev.enjarai.arcane_repository.item.component.FilterComponent;
import dev.enjarai.arcane_repository.item.custom.page.AttributePageItem;
import dev.enjarai.arcane_repository.item.custom.page.TypePageItem;
import io.wispforest.endec.Endec;
import io.wispforest.endec.impl.KeyedEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;

import static dev.enjarai.arcane_repository.item.ModItems.*;

public abstract sealed class FilterPage extends AttributePageItem permits BlacklistPage, WhitelistPage {
    public static final KeyedEndec<Optional<Filter>> FILTER = Filter.ENDEC.optionalOf().keyed("filter", () -> Optional.empty());

    public FilterPage(String id) {
        super(new ItemSettings().component(ModDataComponentTypes.FILTER, new FilterComponent()), id);
    }

    @Override
    public List<TypePageItem> getCompatibleTypes(ItemStack page) {
        return List.of(INDEXING_TYPE_PAGE, INDEX_SLAVE_TYPE_PAGE, ITEM_STORAGE_TYPE_PAGE, FOOD_STORAGE_TYPE_PAGE, BLOCK_STORAGE_TYPE_PAGE);
    }

    @Override
    public boolean bookCanHaveMultiple(ItemStack page) {
        return false;
    }

    @Override
    public @Nullable MutableText getAttributeDisplayName() {
        return null;
    }

    @Override
    public void appendAttributes(ItemStack page, NbtCompound nbt) {
        var comp = page.get(ModDataComponentTypes.FILTER);
        boolean isBlacklist = this instanceof BlacklistPage;
        List<Item> items = comp.items();
        nbt.put(FILTER, Optional.of(new Filter(isBlacklist, items)));
    }

    @Override
    public boolean onClicked(ItemStack page, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
        if (clickType == ClickType.RIGHT && !otherStack.isEmpty()) {
            var comp = page.get(ModDataComponentTypes.FILTER);
            page.set(ModDataComponentTypes.FILTER, comp.toggle(otherStack.getItem()));
            //TODO: sound
            return true;
        }

        return false;
    }

    @Override
    public boolean onStackClicked(ItemStack page, Slot slot, ClickType clickType, PlayerEntity player) {
        var otherStack = slot.getStack();
        if (clickType == ClickType.RIGHT && !otherStack.isEmpty()) {
            var comp = page.get(ModDataComponentTypes.FILTER);
            page.set(ModDataComponentTypes.FILTER, comp.toggle(otherStack.getItem()));
            //TODO: sound
            return true;
        }

        return false;
    }

    @Override
    public void appendTooltip(ItemStack page, @Nullable TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(page, context, tooltip, type);

        var comp = page.get(ModDataComponentTypes.FILTER);
        tooltip.add(Text.literal(" ")
            .append(Text.translatable("item.arcane_repository.page.tooltip.attribute." + (this instanceof BlacklistPage ? "blacklist" : "whitelist")))
            .fillStyle(Style.EMPTY.withColor(getColor()))
            .append(":"));

        for (var item : comp.items()) {
            tooltip.add(Text.literal("  ").append(item.getName()).formatted(Formatting.GRAY));
        }
    }

    public static record Filter(boolean isBlacklist, List<Item> items) {
        public static final Endec<Filter> ENDEC = StructEndecBuilder.of(
            Endec.BOOLEAN.fieldOf("is_blacklist", Filter::isBlacklist),
            MinecraftEndecs.ofRegistry(Registries.ITEM).listOf().fieldOf("items", Filter::items),
            Filter::new
        );

        public boolean isWhitelist() {
            return !isBlacklist;
        }
    }
}
