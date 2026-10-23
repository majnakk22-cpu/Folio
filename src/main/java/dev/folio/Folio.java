package dev.folio;

import com.mojang.logging.LogUtils;
import dev.folio.network.FolioNetwork;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Folio — основа книжного интерфейса для модов (аналог Patchouli): экраны книги и журнала, разметка текста,
 * кнопки, описание книг и журналов, хранение открытых записей и сетевой пакет. Предметы, тексты и содержимое
 * остаются в самом моде. Подробности — в README библиотеки.
 */
@Mod(Folio.MODID)
public class Folio {
    public static final String MODID = "folio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Folio() {
        FolioNetwork.register();
    }
}
