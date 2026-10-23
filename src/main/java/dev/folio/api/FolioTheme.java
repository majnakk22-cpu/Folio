package dev.folio.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Тема книги или журнала: фон разворота, цвета текста и вид кнопок. Каждый мод задаёт свою тему сам
 * ({@link BookDefinition.Builder#theme(FolioTheme)}, {@link JournalDefinition.Builder#theme(FolioTheme)}); всё, что
 * не задано, остаётся как в стандартном оформлении Folio ({@link #DEFAULT}).
 *
 * <p>Тема — только данные (цвета и ссылки на текстуры), клиентских классов она не трогает, поэтому её можно создавать
 * в конструкторе мода на любой стороне.
 *
 * <p><b>Цвета</b> — {@code 0xAARRGGBB}. Если альфа не указана ({@code 0xRRGGBB}), цвет считается непрозрачным.
 *
 * <p><b>Фон.</b> Три варианта:
 * <ol>
 *   <li>текстура-скин по умолчанию (ничего не задавать);</li>
 *   <li>своя текстура: {@link Builder#skin(ResourceLocation)} (512×256, разворот 312×196 в углу), либо любого размера
 *       с теми же пропорциями — {@link Builder#skin(ResourceLocation, int, int)};</li>
 *   <li>без текстуры, рамка и страницы заливкой цветом: {@link Builder#panel(int, int, int, int, int)}.</li>
 * </ol>
 *
 * <p><b>Кнопки.</b> Либо цвета (стрелки, закладки, строки-ссылки), либо спрайт на 9 частей
 * ({@link #sprite}) — спрайт рисуется вместо цветной заливки; значки, текст и шевроны рисуются поверх него.
 */
public final class FolioTheme {
    /** Скин по умолчанию (лежит в самом Folio). */
    public static final ResourceLocation DEFAULT_SKIN = new ResourceLocation("folio", "textures/gui/book.png");

    /** Стандартная тема Folio: тёмная книга, золотые заголовки, зелёные ссылки. */
    public static final FolioTheme DEFAULT = builder().build();

    /**
     * Спрайт кнопки, растягиваемый по 9 частям. Состояния лежат в текстуре друг под другом, каждое высотой {@code h}:
     * {@code v} — обычное, {@code v + h} — наведение, {@code v + 2h} — недоступная (для стрелок). Если состояний
     * меньше, лишние можно просто не рисовать: для закладок и строк берутся первые два.
     *
     * @param border ширина неразрываемой рамки (углы), в пикселях текстуры
     */
    public record Sprite(ResourceLocation texture, int u, int v, int w, int h, int border, int texW, int texH) {
    }

    /** Спрайт кнопки: текстура, позиция первого состояния (u, v), размер состояния (w×h), рамка и размер текстуры. */
    public static Sprite sprite(ResourceLocation texture, int u, int v, int w, int h, int border, int texW, int texH) {
        return new Sprite(texture, u, v, w, h, border, texW, texH);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------ данные

    private final Builder d;

    private FolioTheme(Builder source) {
        this.d = source.copy();
        // Эмблема по умолчанию берётся из скина: справа от разворота, в масштабе скина
        if (d.emblemFromSkin && d.emblemEnabled && d.skin != null) {
            float k = d.skinW / 312.0F;
            d.emblemTexture = d.skin;
            d.emblemU = d.skinU + Math.round(320 * k);
            d.emblemV = d.skinV;
            d.emblemSize = Math.round(32 * k);
            d.emblemTexW = d.skinTexW;
            d.emblemTexH = d.skinTexH;
        }
        if (d.emblemFromSkin && d.skin == null) {
            d.emblemTexture = null;
        }
    }

    public Builder toBuilder() {
        return d.copy();
    }

    // фон
    /** Текстура разворота или null, если фон рисуется заливкой ({@link Builder#panel}). */
    public ResourceLocation skin() { return d.skin; }
    public int skinU() { return d.skinU; }
    public int skinV() { return d.skinV; }
    public int skinW() { return d.skinW; }
    public int skinH() { return d.skinH; }
    public int skinTexW() { return d.skinTexW; }
    public int skinTexH() { return d.skinTexH; }
    public int frame() { return d.frame; }
    public int frameInner() { return d.frameInner; }
    public int page() { return d.page; }
    public int pageBorder() { return d.pageBorder; }
    public int spine() { return d.spine; }
    /** Текстура эмблемы обложки или null, если эмблемы нет. */
    public ResourceLocation emblemTexture() { return d.emblemTexture; }
    public int emblemU() { return d.emblemU; }
    public int emblemV() { return d.emblemV; }
    public int emblemSize() { return d.emblemSize; }
    public int emblemTexW() { return d.emblemTexW; }
    public int emblemTexH() { return d.emblemTexH; }

    // текст
    public int title() { return d.title; }
    public int heading() { return d.heading; }
    public int text() { return d.text; }
    public int bold() { return d.bold; }
    public int muted() { return d.muted; }
    public int subtitle() { return d.subtitle; }
    public int link() { return d.link; }
    public int linkHover() { return d.linkHover; }
    public int note() { return d.note; }
    public int noteText() { return d.noteText; }
    public int bullet() { return d.bullet; }
    public int locked() { return d.locked; }
    public int disabledText() { return d.disabledText; }
    public int barFill() { return d.barFill; }
    public int barTrack() { return d.barTrack; }
    public int barBorder() { return d.barBorder; }

    // стрелки
    public int arrowBorder() { return d.arrowBorder; }
    public int arrowBorderHover() { return d.arrowBorderHover; }
    public int arrowFill() { return d.arrowFill; }
    public int arrowFillHover() { return d.arrowFillHover; }
    public int arrowIcon() { return d.arrowIcon; }
    public int arrowIconHover() { return d.arrowIconHover; }
    public int arrowDisabledBorder() { return d.arrowDisabledBorder; }
    public int arrowDisabledFill() { return d.arrowDisabledFill; }
    public int arrowDisabledIcon() { return d.arrowDisabledIcon; }
    public Sprite arrowSprite() { return d.arrowSprite; }

    // закладки
    public int tabOutline() { return d.tabOutline; }
    public int tabIdle() { return d.tabIdle; }
    /** Акцентный цвет закладки «Оглавление» / «Все». */
    public int tabContents() { return d.tabContents; }
    public int tabSelected() { return d.tabSelected; }
    public float tabAccentMix() { return d.tabAccentMix; }
    public Sprite tabSprite() { return d.tabSprite; }

    // строки-ссылки
    public int rowText() { return d.rowText; }
    public int rowTextHover() { return d.rowTextHover; }
    public int rowChevron() { return d.rowChevron; }
    public int rowDivider() { return d.rowDivider; }
    public int rowVisited() { return d.rowVisited; }
    public int rowHoverAlpha() { return d.rowHoverAlpha; }
    public boolean rowAccentBar() { return d.rowAccentBar; }
    public Sprite rowSprite() { return d.rowSprite; }

    // ----------------------------------------------------------------- билдер

    /** Все значения по умолчанию — это нынешний вид Folio, так что можно менять только нужное. */
    public static final class Builder implements Cloneable {
        // фон
        private ResourceLocation skin = DEFAULT_SKIN;
        private int skinU = 0, skinV = 0, skinW = 312, skinH = 196, skinTexW = 512, skinTexH = 256;
        private int frame = 0xFF0C0D10, frameInner = 0xFF1E232C, page = 0xFF232832, pageBorder = 0xFF3A4150, spine = 0xFF14171D;
        private boolean emblemEnabled = true;
        private boolean emblemFromSkin = true;
        private ResourceLocation emblemTexture;
        private int emblemU, emblemV, emblemSize, emblemTexW, emblemTexH;
        // текст
        private int title = 0xFFE8B84A, heading = 0xFFE8B84A, text = 0xFFD5DAE3, bold = 0xFFFFFFFF, muted = 0xFF8A93A3;
        private int subtitle = 0xFFA6AEBD, link = 0xFF5BE0A0, linkHover = 0xFFB6FFD9;
        private int note = 0xFFE8B84A, noteText = 0xFFF0D48A, bullet = 0xFF5BD35B, locked = 0xFF555B66, disabledText = 0xFF555B66;
        private int barFill = 0xFF5BD35B, barTrack = 0xFF1E232C, barBorder = 0xFF0C0D10;
        // стрелки
        private int arrowBorder = 0xFF3A4150, arrowBorderHover = 0xFFE8B84A;
        private int arrowFill = 0xFF1E232C, arrowFillHover = 0xFF2C3340;
        private int arrowIcon = 0xFFB8BFCC, arrowIconHover = 0xFFFFE08A;
        private int arrowDisabledBorder = 0xFF2A2F3A, arrowDisabledFill = 0xFF191C23, arrowDisabledIcon = 0xFF444A55;
        private Sprite arrowSprite;
        // закладки
        private int tabOutline = 0xFF0C0D10, tabIdle = 0xFF1A1E26, tabSelected = 0xFF2A303B, tabContents = 0xFFE8E8E8;
        private float tabAccentMix = 0.25F;
        private Sprite tabSprite;
        // строки-ссылки
        private int rowText = 0xFFD5DAE3, rowTextHover = 0xFFFFFFFF, rowChevron = 0xFF8A93A3;
        private int rowDivider = 0x22FFFFFF, rowVisited = 0xFF5BD35B, rowHoverAlpha = 48;
        private boolean rowAccentBar = true;
        private Sprite rowSprite;

        private Builder() {
        }

        private Builder copy() {
            try {
                return (Builder) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        /** Цвет без альфы (0xRRGGBB) считаем непрозрачным. */
        private static int opaque(int color) {
            return (color >>> 24) == 0 ? color | 0xFF000000 : color;
        }

        public FolioTheme build() {
            if (skin != null && (skinW <= 0 || skinH <= 0 || skinTexW <= 0 || skinTexH <= 0)) {
                throw new IllegalStateException("FolioTheme: неверные размеры скина");
            }
            return new FolioTheme(this);
        }

        // ---- фон

        /** Своя текстура 512×256: разворот 312×196 в левом верхнем углу, эмблема 32×32 в точке (320, 0). */
        public Builder skin(ResourceLocation texture) {
            return skin(texture, 512, 256);
        }

        /**
         * Своя текстура другого размера с теми же пропорциями (например 1024×512 для чёткого фона): разворот
         * занимает {@code 312·k × 196·k} пикселей в углу, эмблема {@code 32·k} в точке {@code (320·k, 0)}, где
         * {@code k = texW / 512}. Фон растягивается до размера книги на экране (312×196).
         */
        public Builder skin(ResourceLocation texture, int texW, int texH) {
            float k = texW / 512.0F;
            this.skin = texture;
            this.skinTexW = texW;
            this.skinTexH = texH;
            this.skinU = 0;
            this.skinV = 0;
            this.skinW = Math.round(312 * k);
            this.skinH = Math.round(196 * k);
            return this;
        }

        /** Если разворот лежит в текстуре не в углу: левый верхний угол и размер области. */
        public Builder skinRegion(int u, int v, int w, int h) {
            this.skinU = u;
            this.skinV = v;
            this.skinW = w;
            this.skinH = h;
            return this;
        }

        /**
         * Без текстуры: рамка, страницы и корешок рисуются заливкой. Подходит, чтобы быстро задать свою палитру.
         *
         * @param frame      внешняя рамка
         * @param frameInner внутренний кант рамки
         * @param page       фон страниц
         * @param pageBorder контур страниц
         * @param spine      корешок между страницами
         */
        public Builder panel(int frame, int frameInner, int page, int pageBorder, int spine) {
            this.skin = null;
            this.frame = opaque(frame);
            this.frameInner = opaque(frameInner);
            this.page = opaque(page);
            this.pageBorder = opaque(pageBorder);
            this.spine = opaque(spine);
            return this;
        }

        /** Эмблема обложки лежит в другой текстуре (или не в стандартном месте скина). */
        public Builder emblem(ResourceLocation texture, int u, int v, int size, int texW, int texH) {
            this.emblemEnabled = true;
            this.emblemFromSkin = false;
            this.emblemTexture = texture;
            this.emblemU = u;
            this.emblemV = v;
            this.emblemSize = size;
            this.emblemTexW = texW;
            this.emblemTexH = texH;
            return this;
        }

        /** Без вращающейся эмблемы на обложке. */
        public Builder noEmblem() {
            this.emblemEnabled = false;
            this.emblemFromSkin = false;
            this.emblemTexture = null;
            return this;
        }

        // ---- текст страниц

        /** Название на левой странице. */
        public Builder title(int color) {
            this.title = opaque(color);
            return this;
        }

        /** Заголовки глав и линия под ними. */
        public Builder heading(int color) {
            this.heading = opaque(color);
            return this;
        }

        /** Обычный и выделенный (**жирный**) текст. */
        public Builder text(int normal, int bold) {
            this.text = opaque(normal);
            this.bold = opaque(bold);
            return this;
        }

        /** Приглушённый текст: счётчики страниц, подписи, надписи мелким шрифтом. */
        public Builder muted(int color) {
            this.muted = opaque(color);
            return this;
        }

        /** Подпись на обложке. */
        public Builder subtitle(int color) {
            this.subtitle = opaque(color);
            return this;
        }

        /** Гиперссылки в тексте и кнопки-ссылки («Назад», «Продолжить чтение»): обычный и при наведении. */
        public Builder link(int normal, int hover) {
            this.link = opaque(normal);
            this.linkHover = opaque(hover);
            return this;
        }

        /** Врезка-совет: цвет полосы и заливки, цвет текста. */
        public Builder note(int bar, int textColor) {
            this.note = opaque(bar);
            this.noteText = opaque(textColor);
            return this;
        }

        /** Маркер пункта списка. */
        public Builder bullet(int color) {
            this.bullet = opaque(color);
            return this;
        }

        /** Акцент закрытых записей журнала («???») и цвет недоступных текстовых кнопок. */
        public Builder locked(int accent, int disabledText) {
            this.locked = opaque(accent);
            this.disabledText = opaque(disabledText);
            return this;
        }

        /** Полоска прогресса на обложке: заполнение, фон, контур. */
        public Builder progressBar(int fill, int track, int border) {
            this.barFill = opaque(fill);
            this.barTrack = opaque(track);
            this.barBorder = opaque(border);
            return this;
        }

        // ---- стрелки листания

        public Builder arrow(int border, int borderHover, int fill, int fillHover, int icon, int iconHover) {
            this.arrowBorder = opaque(border);
            this.arrowBorderHover = opaque(borderHover);
            this.arrowFill = opaque(fill);
            this.arrowFillHover = opaque(fillHover);
            this.arrowIcon = opaque(icon);
            this.arrowIconHover = opaque(iconHover);
            return this;
        }

        /** Недоступная стрелка (нет страницы в этом направлении). */
        public Builder arrowDisabled(int border, int fill, int icon) {
            this.arrowDisabledBorder = opaque(border);
            this.arrowDisabledFill = opaque(fill);
            this.arrowDisabledIcon = opaque(icon);
            return this;
        }

        /** Спрайт стрелки вместо цветной заливки (состояния: обычная, наведение, недоступная). */
        public Builder arrowSprite(Sprite sprite) {
            this.arrowSprite = sprite;
            return this;
        }

        // ---- закладки

        /**
         * Закладки раздела: контур, цвет невыбранной, базовый цвет выбранной и доля акцентного цвета раздела,
         * подмешиваемая к выбранной (0 — без подмешивания, 1 — целиком цвет раздела).
         */
        public Builder tab(int outline, int idle, int selected, float accentMix) {
            this.tabOutline = opaque(outline);
            this.tabIdle = opaque(idle);
            this.tabSelected = opaque(selected);
            this.tabAccentMix = Math.max(0.0F, Math.min(1.0F, accentMix));
            return this;
        }

        /** Акцентный цвет закладки «Оглавление» (в журнале — «Все»): у остальных закладок он свой у каждого раздела. */
        public Builder contentsTab(int color) {
            this.tabContents = opaque(color);
            return this;
        }

        /** Спрайт закладки (состояния: обычная, наведение/выбрана). */
        public Builder tabSprite(Sprite sprite) {
            this.tabSprite = sprite;
            return this;
        }

        // ---- строки-ссылки (разделы, главы, записи журнала)

        /**
         * Строки списков.
         *
         * @param text      цвет названия
         * @param textHover цвет названия при наведении
         * @param chevron   цвет шеврона справа (при наведении переходит в акцентный цвет раздела)
         * @param visited   метка «прочитано»
         */
        public Builder row(int text, int textHover, int chevron, int visited) {
            this.rowText = opaque(text);
            this.rowTextHover = opaque(textHover);
            this.rowChevron = opaque(chevron);
            this.rowVisited = opaque(visited);
            return this;
        }

        /** Пунктирная линия под строкой (ARGB как есть; 0 — без линии). */
        public Builder rowDivider(int argb) {
            this.rowDivider = argb;
            return this;
        }

        /** Сила подсветки строки при наведении, 0–255 (0 — без подсветки) и рисовать ли акцентную полоску слева. */
        public Builder rowHighlight(int hoverAlpha, boolean accentBar) {
            this.rowHoverAlpha = Math.max(0, Math.min(255, hoverAlpha));
            this.rowAccentBar = accentBar;
            return this;
        }

        /** Спрайт строки-ссылки (состояния: обычная, наведение). */
        public Builder rowSprite(Sprite sprite) {
            this.rowSprite = sprite;
            return this;
        }
    }
}
