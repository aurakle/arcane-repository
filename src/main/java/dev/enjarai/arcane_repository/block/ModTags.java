package dev.enjarai.arcane_repository.block;

import dev.enjarai.arcane_repository.ArcaneRepository;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class ModTags {
    public static final TagKey<Block> INDEX_INTERACTABLE = TagKey.of(RegistryKeys.BLOCK, ArcaneRepository.id("index_interactable"));
}
