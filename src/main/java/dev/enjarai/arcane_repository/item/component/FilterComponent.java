package dev.enjarai.arcane_repository.item.component;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

public record FilterComponent(List<Item> items) {
    public static final Codec<FilterComponent> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
        Registries.ITEM.getCodec().listOf().optionalFieldOf("items", List.of()).forGetter(FilterComponent::items)
      ).apply(instance, FilterComponent::new)
    );

    public FilterComponent() {
        this(List.of());
    }

    public FilterComponent toggle(Item item) {
        var items = new ArrayList<>(this.items);
        if (items.contains(item)) {
            items.remove(item);
        } else {
            items.add(item);
        }

        return new FilterComponent(items);
    }
}
