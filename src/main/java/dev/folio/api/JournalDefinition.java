package dev.folio.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Описание журнала: категории и записи. Открытые записи хранятся в постоянных данных игрока.
 *
 * <p>Тексты записи X: {@code <langPrefix>.X.title} и {@code <langPrefix>.X.text} (с той же разметкой, что и в книгах).
 */
public final class JournalDefinition {

    /** Категория (вкладка): ключ названия, иконка (id предмета) и цвет (ARGB). */
    public record Category(String nameKey, String icon, int color) {
    }

    /** Запись: id, номер категории и иконка (id предмета). */
    public record Entry(String id, int category, String icon) {
    }

    private final ResourceLocation id;
    private final String langPrefix;
    private final String titleKey;
    private final String storageKey;
    private final FolioTheme theme;
    private final List<Category> categories;
    private final List<Entry> entries;
    private final Map<String, Entry> byId = new HashMap<>();

    private JournalDefinition(Builder b) {
        this.id = b.id;
        this.langPrefix = b.langPrefix;
        this.titleKey = b.titleKey;
        this.storageKey = b.storageKey;
        this.theme = b.skinOverride == null ? b.theme : b.theme.toBuilder().skin(b.skinOverride).build();
        this.categories = Collections.unmodifiableList(new ArrayList<>(b.categories));
        this.entries = Collections.unmodifiableList(new ArrayList<>(b.entries));
        for (Entry entry : entries) {
            byId.put(entry.id(), entry);
        }
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

    /** Ключ в постоянных данных игрока, где лежит список открытых записей. */
    public String storageKey() {
        return storageKey;
    }

    /** Тема журнала: фон, цвета текста, вид кнопок (см. {@link FolioTheme}). */
    public FolioTheme theme() {
        return theme;
    }

    /** Текстура фона из темы (null, если фон рисуется заливкой). Оставлено для совместимости. */
    public ResourceLocation skin() {
        return theme.skin();
    }

    public List<Category> categories() {
        return categories;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Запись по id или null. */
    public Entry get(String entryId) {
        return byId.get(entryId);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String langPrefix;
        private String titleKey;
        private String storageKey;
        private FolioTheme theme = FolioTheme.DEFAULT;
        private ResourceLocation skinOverride;
        private final List<Category> categories = new ArrayList<>();
        private final List<Entry> entries = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = id;
            this.langPrefix = "journal." + id.getNamespace() + "." + id.getPath();
            this.titleKey = langPrefix + ".title";
            this.storageKey = "folio_journal_" + id.getNamespace() + "_" + id.getPath();
        }

        public Builder langPrefix(String prefix) {
            this.langPrefix = prefix;
            return this;
        }

        public Builder title(String key) {
            this.titleKey = key;
            return this;
        }

        /** Ключ в данных игрока (по умолчанию генерируется из id; задайте свой, чтобы сохранить старые сохранения). */
        public Builder storageKey(String key) {
            this.storageKey = key;
            return this;
        }

        /** Тема журнала: фон, цвета, кнопки. По умолчанию — стандартная тема Folio. */
        public Builder theme(FolioTheme theme) {
            this.theme = theme == null ? FolioTheme.DEFAULT : theme;
            return this;
        }

        /** Коротко: заменить только текстуру фона (512×256), остальное — из темы. */
        public Builder skin(ResourceLocation skin) {
            this.skinOverride = skin;
            return this;
        }

        /** Добавляет категорию; её номер — порядок добавления (с нуля). */
        public Builder category(String nameKey, String icon, int argbColor) {
            categories.add(new Category(nameKey, icon, argbColor));
            return this;
        }

        public Builder entry(String id, int category, String icon) {
            entries.add(new Entry(id, category, icon));
            return this;
        }

        public JournalDefinition build() {
            if (categories.isEmpty()) {
                throw new IllegalStateException("Журнал " + id + ": нужна хотя бы одна категория");
            }
            for (Entry entry : entries) {
                if (entry.category() < 0 || entry.category() >= categories.size()) {
                    throw new IllegalStateException("Журнал " + id + ": у записи " + entry.id() + " неверная категория");
                }
            }
            return new JournalDefinition(this);
        }
    }
}
