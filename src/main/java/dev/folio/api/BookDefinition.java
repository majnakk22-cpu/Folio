package dev.folio.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Описание книги-руководства: оглавление → разделы → главы.
 *
 * <p>Тексты берутся из lang-файлов мода:
 * <ul>
 *   <li>название и подзаголовок на обложке: ключи {@link #titleKey()} и {@link #subtitleKey()};</li>
 *   <li>раздел N: {@code <langPrefix>.section.N.title} и {@code .desc};</li>
 *   <li>глава X: {@code <langPrefix>.X.title}, {@code .desc} (короткая подпись) и {@code .text} (разметка, см. README).</li>
 * </ul>
 */
public final class BookDefinition {
    public static final ResourceLocation DEFAULT_SKIN = FolioTheme.DEFAULT_SKIN;

    /** Раздел: иконка (id предмета) и цвет акцента (ARGB). */
    public record Section(String icon, int color) {
    }

    /** Глава: id, номер раздела и иконка (id предмета). Порядок глав = порядок чтения. */
    public record Chapter(String id, int section, String icon) {
    }

    private final ResourceLocation id;
    private final String langPrefix;
    private final String titleKey;
    private final String subtitleKey;
    private final FolioTheme theme;
    private final List<Section> sections;
    private final List<Chapter> chapters;

    private BookDefinition(Builder b) {
        this.id = b.id;
        this.langPrefix = b.langPrefix;
        this.titleKey = b.titleKey;
        this.subtitleKey = b.subtitleKey;
        this.theme = b.skinOverride == null ? b.theme : b.theme.toBuilder().skin(b.skinOverride).build();
        this.sections = Collections.unmodifiableList(new ArrayList<>(b.sections));
        this.chapters = Collections.unmodifiableList(new ArrayList<>(b.chapters));
    }

    public ResourceLocation id() {
        return id;
    }

    public String langPrefix() {
        return langPrefix;
    }

    public String titleKey() {
        return titleKey;
    }

    public String subtitleKey() {
        return subtitleKey;
    }

    /** Тема книги: фон, цвета текста, вид кнопок (см. {@link FolioTheme}). */
    public FolioTheme theme() {
        return theme;
    }

    /** Текстура фона из темы (null, если фон рисуется заливкой). Оставлено для совместимости. */
    public ResourceLocation skin() {
        return theme.skin();
    }

    public List<Section> sections() {
        return sections;
    }

    public List<Chapter> chapters() {
        return chapters;
    }

    /** Номер главы по id или -1. */
    public int indexOf(String chapterId) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(chapterId)) {
                return i;
            }
        }
        return -1;
    }

    public int firstChapterOfSection(int section) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).section() == section) {
                return i;
            }
        }
        return 0;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String langPrefix;
        private String titleKey;
        private String subtitleKey;
        private FolioTheme theme = FolioTheme.DEFAULT;
        private ResourceLocation skinOverride;
        private final List<Section> sections = new ArrayList<>();
        private final List<Chapter> chapters = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = id;
            this.langPrefix = "book." + id.getNamespace() + "." + id.getPath();
            this.titleKey = langPrefix + ".title";
            this.subtitleKey = langPrefix + ".subtitle";
        }

        /** Префикс ключей lang для разделов и глав. */
        public Builder langPrefix(String prefix) {
            this.langPrefix = prefix;
            return this;
        }

        public Builder title(String key) {
            this.titleKey = key;
            return this;
        }

        public Builder subtitle(String key) {
            this.subtitleKey = key;
            return this;
        }

        /** Тема книги: фон, цвета, кнопки. По умолчанию — стандартная тема Folio. */
        public Builder theme(FolioTheme theme) {
            this.theme = theme == null ? FolioTheme.DEFAULT : theme;
            return this;
        }

        /** Коротко: заменить только текстуру фона (512×256), остальное — из темы. */
        public Builder skin(ResourceLocation skin) {
            this.skinOverride = skin;
            return this;
        }

        /** Добавляет раздел; его номер — порядок добавления (с нуля). */
        public Builder section(String icon, int argbColor) {
            sections.add(new Section(icon, argbColor));
            return this;
        }

        public Builder chapter(String id, int section, String icon) {
            chapters.add(new Chapter(id, section, icon));
            return this;
        }

        public BookDefinition build() {
            if (sections.isEmpty() || chapters.isEmpty()) {
                throw new IllegalStateException("Книга " + id + ": нужен хотя бы один раздел и одна глава");
            }
            for (Chapter chapter : chapters) {
                if (chapter.section() < 0 || chapter.section() >= sections.size()) {
                    throw new IllegalStateException("Книга " + id + ": у главы " + chapter.id() + " неверный раздел");
                }
            }
            return new BookDefinition(this);
        }
    }
}
