package dev.folio.client;

import dev.folio.api.BookDefinition;
import dev.folio.api.FolioBooks;
import dev.folio.api.FolioJournals;
import dev.folio.api.JournalDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;

/** Клиентские точки входа: открыть книгу или журнал. Вызывать только на клиенте (через DistExecutor). */
public final class FolioClient {

    public static void openBook(ResourceLocation bookId) {
        BookDefinition definition = FolioBooks.get(bookId);
        if (definition != null) {
            Minecraft.getInstance().setScreen(new BookScreen(definition));
        }
    }

    public static void openJournal(ResourceLocation journalId, List<String> unlocked, String openId) {
        JournalDefinition definition = FolioJournals.definition(journalId);
        if (definition != null) {
            Minecraft.getInstance().setScreen(new JournalScreen(definition, new HashSet<>(unlocked), openId));
        }
    }

    private FolioClient() {
    }
}
