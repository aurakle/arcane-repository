package dev.enjarai.arcane_repository.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StorageMetadataComponent(int occupiedStacks, int occupiedTypes) {
    public static final Codec<StorageMetadataComponent> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
        Codec.INT.optionalFieldOf("occupiedStacks", 0).forGetter(StorageMetadataComponent::occupiedStacks),
        Codec.INT.optionalFieldOf("occupiedTypes", 0).forGetter(StorageMetadataComponent::occupiedTypes)
      ).apply(instance, StorageMetadataComponent::new)
    );

    public StorageMetadataComponent withOccupiedStacks(int newOccupiedStacks) {
        return new StorageMetadataComponent(newOccupiedStacks, occupiedTypes);
    }

    public StorageMetadataComponent withOccupiedTypes(int newOccupiedTypes) {
        return new StorageMetadataComponent(occupiedStacks, newOccupiedTypes);
    }
}
