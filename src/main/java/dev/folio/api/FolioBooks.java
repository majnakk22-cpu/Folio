package dev.folio.api;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Реестр книг. Регистрируйте книгу в конструкторе мода (код выполняется и на клиенте, и на сервере). */
public final class FolioBooks {
    private static final Map<ResourceLocation, BookDefinition> BOOKS = new HashMap<>();

    public static void register(BookDefinition definition) {
        BOOKS.put(definition.id(), definition);
    }

    /** Книга по id или null. */
    public static BookDefinition get(ResourceLocation id) {
        return BOOKS.get(id);
    }

    private FolioBooks() {
    }
}
