package dev.enjarai.arcane_repository.item.custom.page.type;

import com.google.common.collect.ImmutableList;
import dev.enjarai.arcane_repository.client.tooltip.ItemStorageTooltipData;
import dev.enjarai.arcane_repository.item.ItemSettings;
import dev.enjarai.arcane_repository.item.ModDataComponentTypes;
import dev.enjarai.arcane_repository.item.component.OverstackingStorageComponent;
import dev.enjarai.arcane_repository.item.component.StorageMetadataComponent;
import dev.enjarai.arcane_repository.util.request.ExtractionRequest;
import dev.enjarai.arcane_repository.ArcaneRepository;
import dev.enjarai.arcane_repository.block.entity.MysticalLecternBlockEntity;
import dev.enjarai.arcane_repository.item.custom.page.AttributePageItem;
import dev.enjarai.arcane_repository.item.custom.page.TypePageItem;
import dev.enjarai.arcane_repository.item.custom.page.attribute.FilterPage;
import dev.enjarai.arcane_repository.util.BigStack;
import dev.enjarai.arcane_repository.util.ContentsIndex;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static dev.enjarai.arcane_repository.item.ModItems.*;

public class ItemStorageTypePage extends TypePageItem implements ItemInsertableTypePage {
    private static final ComponentType<StorageMetadataComponent> COMPONENT_TYPE = ModDataComponentTypes.STORAGE_METADATA;
    public static final String MAX_STACKS_TAG = "max_stacks";
    public static final String MAX_TYPES_TAG = "max_types";

    public ItemStorageTypePage(String id) {
        super(new ItemSettings(), id);
    }

    private StorageMetadataComponent getComponent(ItemStack stack) {
        return stack.get(COMPONENT_TYPE);
    }

    @Override
    public int getColor() {
        return 0x88ff88;
    }

    @Override
    public MutableText getTypeDisplayName() {
        return super.getTypeDisplayName().formatted(Formatting.DARK_AQUA);
    }

    @Override
    public void onCraftToBook(ItemStack page, ItemStack book) {
        super.onCraftToBook(page, book);

        NbtCompound attributes = getAttributes(book).copy();

        attributes.putInt(MAX_STACKS_TAG, 2);
        attributes.putInt(MAX_TYPES_TAG, 4);

        book.set(ModDataComponentTypes.PAGE_ITEM_ATTRIBUTES, attributes);
        book.set(COMPONENT_TYPE, new StorageMetadataComponent(0, 0));
        book.set(ModDataComponentTypes.OVERSTACKING_STORAGE, new OverstackingStorageComponent(List.of()));
    }

    public int getMaxTypes(ItemStack book) {
        return getAttributes(book).getInt(MAX_TYPES_TAG);
    }

    public int getMaxStack(ItemStack book) {
        return getAttributes(book).getInt(MAX_STACKS_TAG);
    }

    public ContentsIndex getContents(ItemStack book) {
        ContentsIndex result = new ContentsIndex();
        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);
        if (containerComponent != null) {
            containerComponent.stacks().forEach(result::add);
        }
        return result;
    }

    protected int getFullness(ItemStack book) {
        int result = 0;
        for (BigStack bigStack : getContents(book).getAll()) {
            result += bigStack.getAmount() * getItemOccupancy(bigStack.getItem());
        }
        return result;
    }

    private int getItemOccupancy(Item item) {
        if (item.getMaxCount() == 1) {
            return 8;
        }
        return 1;
    }

    private Optional<OverstackingStorageComponent.TypeStack> canMergeStack(ItemStack stack, List<OverstackingStorageComponent.TypeStack> items) {
        return items.stream()
                .filter(item -> ItemStack.areItemsAndComponentsEqual(item.stack(), stack))
                .findFirst();
    }

    protected boolean canInsert(ItemStack book, ItemStack itemStack) {
        return itemStack.getItem().canBeNested();
    }

    private boolean canInsertFiltered(ItemStack book, ItemStack itemStack) {
        if (!canInsert(book, itemStack)) return false;

        if (!isFiltered(book)) return true;
        return doesFilterPermit(book, itemStack);
    }

    protected int getBaseInsertPriority(ItemStack book) {
        return 0;
    }

    public int getInsertPriority(ItemStack book, ItemStack stack) {
        if (!canInsertFiltered(book, stack)) return -1;
        var base = getBaseInsertPriority(book);
        if (doesFilterPermit(book, stack)) return base + 100;
        return base;
    }

    public int tryAddItem(ItemStack book, ItemStack stack) {
        if (stack.isEmpty() || !canInsertFiltered(book, stack)) {
            return 0;
        }

        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);
        if (containerComponent == null) {
            return 0;
        }

        var itemsList = containerComponent.stacks();

        int maxFullness = getMaxStack(book) * 64;
        int fullnessLeft = maxFullness - getFullness(book);
        int canBeTakenAmount = Math.min(stack.getCount(), fullnessLeft / getItemOccupancy(stack.getItem()));
        if (canBeTakenAmount == 0) {
            return 0;
        }

        var mergeAbleStack = canMergeStack(stack, itemsList);
        if (mergeAbleStack.isPresent()) {
            var mergeStack = mergeAbleStack.get();
            itemsList.remove(mergeStack);
            mergeStack.with(mergeStack.count() + canBeTakenAmount).ifPresent(itemsList::addFirst);
        } else {
            if (itemsList.size() >= getMaxTypes(book)) {
                return 0;
            }

            ItemStack insertStack = stack.copyWithCount(canBeTakenAmount);
            itemsList.addFirst(OverstackingStorageComponent.TypeStack.from(insertStack));
        }

        book.set(ModDataComponentTypes.OVERSTACKING_STORAGE, containerComponent.with(itemsList));

        saveOccupancy(book,
                maxFullness - fullnessLeft + canBeTakenAmount * getItemOccupancy(stack.getItem()),
                itemsList.size());

        return canBeTakenAmount;
    }

    @Override
    public int book$tryInsertItemStack(ItemStack book, PlayerEntity player, ItemStack insert) {
        var affected = tryAddItem(book, insert);
        insert.decrement(affected);
        return affected;
    }

    @Override
    public int lectern$tryInsertItemStack(MysticalLecternBlockEntity lectern, ItemStack insert) {
        var affected = tryAddItem(lectern.getBook(), insert);
        insert.decrement(affected);
        return affected;
    }

    public Optional<ItemStack> removeFirstStack(ItemStack book) {
        return removeFirstStack(book, null);
    }

    public Optional<ItemStack> removeFirstStack(ItemStack book, Integer maxAmount) {
        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);
        if (containerComponent == null) {
            return Optional.empty();
        }

        var newStacks = containerComponent.stacks();
        if (newStacks.isEmpty()) {
            return Optional.empty();
        }

        var firstItem = newStacks.getFirst();

        int itemCount = firstItem.count();
        int takeCount = Math.min(itemCount, firstItem.stack().getMaxCount());
        if (maxAmount != null) {
            takeCount = Math.min(takeCount, maxAmount);
        }

        var extracted = firstItem.stack().copyWithCount(takeCount);

        newStacks.removeFirst();
        firstItem.with(itemCount - takeCount).ifPresent(newStacks::addFirst);

        book.set(ModDataComponentTypes.OVERSTACKING_STORAGE, containerComponent.with(newStacks));
        saveOccupancy(book, getFullness(book), Math.toIntExact(newStacks.size()));

        return Optional.of(extracted);
    }

    public Optional<ItemStack> tryRemoveFirstStack(ItemStack book, int amount, Predicate<ItemStack> condition) {
        var stack = removeFirstStack(book, amount);
        if (stack.isPresent() && !condition.test(stack.get())) {
            tryAddItem(book, stack.get());
            return Optional.empty();
        }
        return stack;
    }

    public List<ItemStack> extractItems(ItemStack book, ExtractionRequest request, boolean apply) {
        if (request.isSatisfied()) {
            return Collections.emptyList();
        }

        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);

        if (containerComponent == null) {
            return Collections.emptyList();
        }

        var itemsList = containerComponent.stacks();

        if (itemsList.isEmpty())
            return Collections.emptyList();

        ImmutableList.Builder<ItemStack> result = ImmutableList.builder();
        var remainder = new ArrayList<OverstackingStorageComponent.TypeStack>();

        for (OverstackingStorageComponent.TypeStack typeStack : itemsList) {
            if (request.matches(typeStack.stack().getItem())) {
                int itemCount = typeStack.count();
                int extractAmount = Math.min(itemCount, request.getAmountUnsatisfied());
                int stackSize = typeStack.stack().getItem().getMaxCount();

                request.satisfy(extractAmount);
                if (apply) {
                    typeStack.with(itemCount - extractAmount).ifPresent(remainder::add);
                } else {
                    remainder.add(typeStack);
                }

                while (extractAmount > 0) {
                    int extractAmountStack = Math.min(extractAmount, stackSize);

                    ItemStack extractStack = typeStack.stack().copy();
                    extractStack.setCount(extractAmountStack);
                    result.add(extractStack);

                    extractAmount -= extractAmountStack;
                }

                continue;
            }
            remainder.add(typeStack);
        }

        book.set(ModDataComponentTypes.OVERSTACKING_STORAGE, containerComponent.with(remainder));
        saveOccupancy(book, getFullness(book), remainder.size());

        return result.build();
    }

    public void saveOccupancy(ItemStack book, int stacks, int types) {
        book.set(COMPONENT_TYPE, getComponent(book).withOccupiedStacks(stacks).withOccupiedTypes(types));
    }

    public void playRemoveOneSound(PlayerEntity player) {
        ArcaneRepository.playUISound(
                player, SoundEvents.ITEM_BUNDLE_REMOVE_ONE, SoundCategory.PLAYERS, player.getEyePos());
        ArcaneRepository.playUISound(
                player, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, player.getEyePos(), 0.4f);
    }

    public void playInsertSound(PlayerEntity player) {
        ArcaneRepository.playUISound(
                player, SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, player.getEyePos());
        ArcaneRepository.playUISound(
                player, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, player.getEyePos(), 0.4f);
    }

    public boolean isEmpty(ItemStack book) {
        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);
        return containerComponent == null || containerComponent.stacks().isEmpty();
    }

    @Override
    public boolean book$onStackClicked(ItemStack book, Slot slot, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.RIGHT) {
            return false;
        }
        ItemStack itemStack = slot.getStack();
        if (itemStack.isEmpty()) {
            playRemoveOneSound(player);
            removeFirstStack(book).ifPresent(removedStack -> tryAddItem(book, slot.insertStack(removedStack)));
        } else {
            int amount = tryAddItem(book, itemStack);
            if (amount > 0) {
                playInsertSound(player);
                itemStack.decrement(amount);
            }
        }
        return true;
    }

    @Override
    public boolean book$onClicked(ItemStack book, ItemStack cursorStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
        if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) {
            return false;
        }
        if (cursorStack.isEmpty()) {
            removeFirstStack(book).ifPresent(itemStack -> {
                playRemoveOneSound(player);
                cursorStackReference.set(itemStack);
            });
        } else {
            int amount = tryAddItem(book, cursorStack);
            if (amount > 0) {
                playInsertSound(player);
                cursorStack.decrement(amount);
            }
        }
        return true;
    }

    @Override
    public void book$appendTooltip(ItemStack book, @Nullable TooltipContext world, List<Text> tooltip, TooltipType type) {
        var filter = getAttributes(book).get(FilterPage.FILTER);
        filter.ifPresent(f -> {
            tooltip.add(Text.literal(""));
            tooltip.add(Text.translatable("item.arcane_repository.page.tooltip.attribute." + (f.isBlacklist() ? "blacklist" : "whitelist"))
                    .append(":")
                    .formatted(Formatting.GRAY));

            for (var item : f.items()) {
                tooltip.add(Text.literal(" ").append(item.getName()).formatted(Formatting.GRAY));
            }
        });
    }

    @Override
    public void book$appendPropertiesTooltip(ItemStack book, @Nullable TooltipContext context, List<Text> tooltip, TooltipType type) {
        var component = getComponent(book);

        var stacksOccupied = component.occupiedStacks();
        var stacksTotal = getMaxStack(book) * 64;
        double stacksFullRatio = (double) stacksOccupied / stacksTotal;
        var typesOccupied = component.occupiedTypes();
        var typesTotal = getMaxTypes(book);
        double typesFullRatio = (double) typesOccupied / typesTotal;

        tooltip.add(Text.translatable("item.arcane_repository.repository_book.tooltip.type.item_storage.stacks",
                        stacksOccupied, stacksTotal)
                .formatted(stacksFullRatio < 0.75 ? Formatting.GREEN :
                        stacksFullRatio == 1 ? Formatting.RED : Formatting.GOLD));
        tooltip.add(Text.translatable("item.arcane_repository.repository_book.tooltip.type.item_storage.types",
                        typesOccupied, typesTotal)
                .formatted(typesFullRatio < 0.75 ? Formatting.GREEN :
                        typesFullRatio == 1 ? Formatting.RED : Formatting.GOLD));
    }

    @Override
    public boolean book$onInventoryScroll(ItemStack book, PlayerEntity player, byte scrollDirection) {
        var containerComponent = book.get(ModDataComponentTypes.OVERSTACKING_STORAGE);

        if (containerComponent == null) return false;

        var itemsList = containerComponent.stacks();

        if (itemsList.isEmpty()) return false;

        Collections.rotate(itemsList, -scrollDirection);
        book.set(ModDataComponentTypes.OVERSTACKING_STORAGE, containerComponent.with(itemsList));

        if (player.getWorld().isClient()) {
            player.playSoundToPlayer(SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 0.4f, 0.8f);
        }

        return true;
    }

    @Override
    public Optional<TooltipData> book$getTooltipData(ItemStack book) {
        return Optional.of(new ItemStorageTooltipData(getContents(book)));
    }

    public static abstract class ItemStorageAttributePage extends AttributePageItem {
        public ItemStorageAttributePage(String id) {
            super(id);
        }

        @Override
        public @Nullable MutableText getAttributeDisplayName() {
            return null;
        }

        @Override
        public List<TypePageItem> getCompatibleTypes(ItemStack page) {
            return List.of(ITEM_STORAGE_TYPE_PAGE, FOOD_STORAGE_TYPE_PAGE, BLOCK_STORAGE_TYPE_PAGE);
        }

        public double getStacksMultiplier(ItemStack page) {
            return 1;
        }

        public double getTypesMultiplier(ItemStack page) {
            return 1;
        }

        @Override
        public void appendAttributes(ItemStack page, NbtCompound nbt) {
            multiplyIntAttribute(nbt, MAX_STACKS_TAG, getStacksMultiplier(page));
            multiplyIntAttribute(nbt, MAX_TYPES_TAG, getTypesMultiplier(page));
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable TooltipContext context, List<Text> tooltip, TooltipType type) {
            super.appendTooltip(stack, context, tooltip, type);

            var stacks = getStacksMultiplier(stack);
            var types = getTypesMultiplier(stack);

            if (stacks != 1)
                tooltip.add(Text.translatable("item.arcane_repository.page.tooltip.type.item_storage.stacks", stacks)
                        .formatted(Formatting.DARK_GREEN));
            if (types != 1)
                tooltip.add(Text.translatable("item.arcane_repository.page.tooltip.type.item_storage.types", types)
                        .formatted(Formatting.DARK_GREEN));
        }
    }
}
