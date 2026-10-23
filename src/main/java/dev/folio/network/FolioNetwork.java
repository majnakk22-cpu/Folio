package dev.folio.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

/** Сетевой канал библиотеки. */
public final class FolioNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("folio", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenJournalPacket.class, OpenJournalPacket::encode,
                OpenJournalPacket::decode, OpenJournalPacket::handle);
    }

    public static void sendOpenJournal(ServerPlayer player, ResourceLocation journalId, List<String> unlocked, String openId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenJournalPacket(journalId, unlocked, openId));
    }

    private FolioNetwork() {
    }
}
