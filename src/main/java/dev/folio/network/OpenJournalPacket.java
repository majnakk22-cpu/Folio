package dev.folio.network;

import dev.folio.client.FolioClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер → клиент: открыть журнал с открытыми записями (и сразу показать запись openId, если она задана). */
public final class OpenJournalPacket {
    private final ResourceLocation journalId;
    private final List<String> unlocked;
    private final String openId;

    public OpenJournalPacket(ResourceLocation journalId, List<String> unlocked, String openId) {
        this.journalId = journalId;
        this.unlocked = unlocked;
        this.openId = openId == null ? "" : openId;
    }

    public static void encode(OpenJournalPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.journalId);
        buffer.writeVarInt(packet.unlocked.size());
        for (String id : packet.unlocked) {
            buffer.writeUtf(id);
        }
        buffer.writeUtf(packet.openId);
    }

    public static OpenJournalPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation journal = buffer.readResourceLocation();
        int size = buffer.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buffer.readUtf());
        }
        return new OpenJournalPacket(journal, ids, buffer.readUtf());
    }

    public static void handle(OpenJournalPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FolioClient.openJournal(packet.journalId, packet.unlocked, packet.openId)));
        context.get().setPacketHandled(true);
    }
}
