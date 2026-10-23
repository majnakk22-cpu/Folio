package dev.folio.api;

import dev.folio.network.FolioNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр журналов и серверный API: открыть запись игроку, проверить, показать журнал.
 * Открытые записи лежат в постоянных данных игрока (переживают смерть).
 */
public final class FolioJournals {
    private static final Map<ResourceLocation, JournalDefinition> JOURNALS = new HashMap<>();

    public static void register(JournalDefinition definition) {
        JOURNALS.put(definition.id(), definition);
    }

    /** Журнал по id или null. */
    public static JournalDefinition definition(ResourceLocation id) {
        return JOURNALS.get(id);
    }

    /** Список id открытых записей игрока. */
    public static List<String> unlocked(ServerPlayer player, ResourceLocation journalId) {
        JournalDefinition def = JOURNALS.get(journalId);
        List<String> result = new ArrayList<>();
        if (def == null) {
            return result;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = persisted.getList(def.storageKey(), Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    public static boolean has(ServerPlayer player, ResourceLocation journalId, String entryId) {
        return unlocked(player, journalId).contains(entryId);
    }

    /**
     * Открывает запись игроку. Возвращает true, если запись новая.
     * Неизвестный журнал или запись игнорируются (защита от опечаток).
     */
    public static boolean unlock(ServerPlayer player, ResourceLocation journalId, String entryId) {
        JournalDefinition def = JOURNALS.get(journalId);
        if (def == null || def.get(entryId) == null) {
            return false;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = persisted.getList(def.storageKey(), Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(entryId)) {
                return false;
            }
        }
        list.add(StringTag.valueOf(entryId));
        persisted.put(def.storageKey(), list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        player.displayClientMessage(Component.translatable("folio.journal.new",
                Component.translatable(def.langPrefix() + "." + entryId + ".title")).withStyle(ChatFormatting.GOLD), true);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.3F);
        return true;
    }

    /** Показывает игроку журнал; entryId — запись, которую открыть сразу (null — без выбора). */
    public static void open(ServerPlayer player, ResourceLocation journalId, String entryId) {
        if (JOURNALS.containsKey(journalId)) {
            FolioNetwork.sendOpenJournal(player, journalId, unlocked(player, journalId), entryId);
        }
    }

    private FolioJournals() {
    }
}
