package dev.folio.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.folio.api.BookDefinition;
import dev.folio.api.FolioTheme;
import dev.folio.util.Icons;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Книга-руководство: разворот из двух страниц.
 * Оглавление → раздел → глава. В тексте глав есть кликабельные гиперссылки на другие главы,
 * иконки предметов с подсказками, закладки разделов, история переходов («Назад»).
 */
public class BookScreen extends Screen {
    private static final int BOOK_W = 312;
    private static final int BOOK_H = 196;
    private static final int PAGE_W = 132;
    private static final int PAGE_H = 136;
    private static final int LEFT_X = 18;
    private static final int RIGHT_X = 162;
    private static final int TEXT_Y = 34;
    /** Сколько строк (глав или разделов) помещается на правой странице списка. */
    private static final int LIST_ROWS = 5;
    private static final int LIST_ROW_H = 27;

    private enum View { CONTENTS, SECTION, CHAPTER }

    private record State(View view, int section, int chapter, int spread) {
    }

    /** Фрагмент текста с абсолютными координатами на экране (для наведения и кликов). */
    private record Placed(FolioText.Atom atom, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record LineBox(FolioText.Line line, int px, int y) {
    }

    private static final Map<ResourceLocation, int[]> LAST = new HashMap<>();
    private static final Map<ResourceLocation, Set<Integer>> READ = new HashMap<>();

    private View view = View.CONTENTS;
    private int section;
    private int chapter;
    private int spread;
    private final Deque<State> history = new ArrayDeque<>();
    private final Map<Integer, List<List<FolioText.Line>>> cache = new HashMap<>();
    private List<List<FolioText.Line>> pages = List.of();
    private final List<FolioWidgets.TabButton> tabs = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<LineBox> boxes = new ArrayList<>();
    private long fadeStart = Util.getMillis();
    /** Первая видимая закладка, когда разделов слишком много для колонки. */
    private int tabScroll;

    private final BookDefinition def;
    private final FolioTheme theme;

    public BookScreen(BookDefinition def) {
        super(Component.translatable(def.titleKey()));
        this.def = def;
        this.theme = def.theme();
    }

    /** Последнее прочитанное место: [глава, разворот] — своё у каждой книги. */
    private int[] last() {
        return LAST.computeIfAbsent(def.id(), id -> new int[]{-1, 0});
    }

    private Set<Integer> read() {
        return READ.computeIfAbsent(def.id(), id -> new HashSet<>());
    }

    private int bookX() {
        return Math.max(30, (width - BOOK_W) / 2 + 12);
    }

    private int bookY() {
        return (height - BOOK_H) / 2;
    }

    @Override
    protected void init() {
        revealSelectedTab();
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // -------------------------------------------------------------- навигация

    private State current() {
        return new State(view, section, chapter, spread);
    }

    private void go(State target, boolean pushHistory) {
        if (target == null) {
            return;
        }
        if (pushHistory) {
            history.push(current());
        }
        apply(target);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
    }

    private void apply(State state) {
        view = state.view();
        section = state.section();
        chapter = state.chapter();
        spread = state.spread();
        if (view == View.CHAPTER) {
            last()[0] = chapter;
            last()[1] = spread;
            read().add(chapter);
        }
        fadeStart = Util.getMillis();
        revealSelectedTab();
        rebuild();
    }

    private int tabCount() {
        return def.sections().size() + 1;
    }

    private void revealSelectedTab() {
        tabScroll = FolioWidgets.revealTab(tabScroll, view == View.CONTENTS ? 0 : section + 1, tabCount());
    }

    private void scrollTabs(int delta) {
        int target = FolioWidgets.clampTabScroll(tabScroll + delta, tabCount());
        if (target != tabScroll) {
            tabScroll = target;
            rebuild();
        }
    }

    private void goBack() {
        if (!history.isEmpty()) {
            apply(history.pop());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.6F));
        }
    }

    private State chapterState(int index, int spreadIndex) {
        int idx = Mth.clamp(index, 0, def.chapters().size() - 1);
        return new State(View.CHAPTER, def.chapters().get(idx).section(), idx, Math.max(0, spreadIndex));
    }

    private int spreadCount(int chapterIndex) {
        return (pagesOf(chapterIndex).size() + 1) / 2;
    }

    private List<List<FolioText.Line>> pagesOf(int chapterIndex) {
        return cache.computeIfAbsent(chapterIndex, i -> {
            String key = def.langPrefix() + "." + def.chapters().get(i).id() + ".text";
            return FolioText.paginate(FolioText.layout(font, Component.translatable(key).getString(), PAGE_W), PAGE_H);
        });
    }

    private State nextState() {
        switch (view) {
            case CONTENTS:
                if (spread + 1 < listPages(def.sections().size())) {
                    return new State(View.CONTENTS, 0, 0, spread + 1);
                }
                return chapterState(0, 0);
            case SECTION:
                if (spread + 1 < listPages(chaptersIn(section))) {
                    return new State(View.SECTION, section, 0, spread + 1);
                }
                return chapterState(def.firstChapterOfSection(section), 0);
            default:
                if (spread + 1 < spreadCount(chapter)) {
                    return chapterState(chapter, spread + 1);
                }
                return chapter + 1 < def.chapters().size() ? chapterState(chapter + 1, 0) : null;
        }
    }

    private static int listPages(int count) {
        return Math.max(1, (count + LIST_ROWS - 1) / LIST_ROWS);
    }

    private int chaptersIn(int sectionIndex) {
        int count = 0;
        for (BookDefinition.Chapter c : def.chapters()) {
            if (c.section() == sectionIndex) {
                count++;
            }
        }
        return count;
    }

    private State prevState() {
        if (view == View.CONTENTS) {
            return spread > 0 ? new State(View.CONTENTS, 0, 0, spread - 1) : null;
        }
        if (view == View.SECTION) {
            return spread > 0 ? new State(View.SECTION, section, 0, spread - 1) : null;
        }
        if (spread > 0) {
            return chapterState(chapter, spread - 1);
        }
        if (chapter > 0) {
            return chapterState(chapter - 1, spreadCount(chapter - 1) - 1);
        }
        return new State(View.CONTENTS, 0, 0, 0);
    }

    // ------------------------------------------------------------- виджеты

    private void rebuild() {
        clearWidgets();
        tabs.clear();
        int x0 = bookX();
        int y0 = bookY();

        // Закладки: оглавление + пять разделов
        addTab(x0, y0, 0, Component.translatable("folio.gui.contents"), new ItemStack(Items.BOOK),
                theme.tabContents(), view == View.CONTENTS, () -> go(new State(View.CONTENTS, 0, 0, 0), true));
        for (int s = 0; s < def.sections().size(); s++) {
            final int sec = s;
            addTab(x0, y0, s + 1, Component.translatable(def.langPrefix() + ".section." + s + ".title"),
                    Icons.stack(def.sections().get(s).icon()), def.sections().get(s).color(),
                    view != View.CONTENTS && section == s, () -> go(new State(View.SECTION, sec, 0, 0), true));
        }

        // Стрелки прокрутки закладок (если разделов много)
        if (FolioWidgets.tabsPaged(tabCount())) {
            FolioWidgets.TabScroll tabUp = addRenderableWidget(new FolioWidgets.TabScroll(theme, x0 - 26, y0 + 3, false,
                    Component.translatable("folio.gui.tabs_up"), () -> scrollTabs(-1)));
            tabUp.active = tabScroll > 0;
            FolioWidgets.TabScroll tabDown = addRenderableWidget(new FolioWidgets.TabScroll(theme, x0 - 26, y0 + BOOK_H - 12, true,
                    Component.translatable("folio.gui.tabs_down"), () -> scrollTabs(1)));
            tabDown.active = tabScroll < tabCount() - FolioWidgets.TAB_WINDOW;
        }

        // Нижняя панель навигации
        int navY = y0 + BOOK_H - 24;
        State prev = prevState();
        State next = nextState();
        FolioWidgets.ArrowButton prevButton = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + 16, navY, false,
                Component.translatable("folio.gui.prev"), () -> go(prevState(), false)));
        prevButton.active = prev != null;
        FolioWidgets.ArrowButton nextButton = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + BOOK_W - 38, navY, true,
                Component.translatable("folio.gui.next"), () -> go(nextState(), false)));
        nextButton.active = next != null;
        FolioWidgets.TextButton back = addRenderableWidget(new FolioWidgets.TextButton(theme, x0 + 44, navY + 1,
                Component.translatable("folio.gui.back"), -1, this::goBack));
        back.active = !history.isEmpty();
        if (view == View.CHAPTER) {
            FolioWidgets.TextButton up = addRenderableWidget(new FolioWidgets.TextButton(theme,
                    x0 + BOOK_W - 44 - font.width(Component.translatable("folio.gui.section_up")) - 9, navY + 1,
                    Component.translatable("folio.gui.section_up"), 1,
                    () -> go(new State(View.SECTION, section, 0, 0), true)));
            up.active = true;
        }

        switch (view) {
            case CONTENTS -> buildContents(x0, y0);
            case SECTION -> buildSection(x0, y0);
            default -> pages = pagesOf(chapter);
        }
    }

    private void addTab(int x0, int y0, int index, Component title, ItemStack icon, int accent, boolean selected, Runnable action) {
        int count = tabCount();
        boolean paged = FolioWidgets.tabsPaged(count);
        if (paged && (index < tabScroll || index >= tabScroll + FolioWidgets.TAB_WINDOW)) {
            return;
        }
        int slot = paged ? index - tabScroll : index;
        tabs.add(addRenderableWidget(new FolioWidgets.TabButton(theme, x0 - 26,
                y0 + FolioWidgets.tabOffset(slot, count, BOOK_H), FolioWidgets.tabHeight(count, BOOK_H),
                title, icon, accent, selected, action)));
    }

    private void buildContents(int x0, int y0) {
        if (last()[0] >= 0) {
            Component label = Component.translatable("folio.gui.continue");
            int bw = font.width(label) + 9;
            addRenderableWidget(new FolioWidgets.TextButton(theme, x0 + LEFT_X + (PAGE_W - bw) / 2, y0 + 150, label, 1,
                    () -> go(chapterState(last()[0], last()[1]), true)));
        }
        int firstRow = spread * LIST_ROWS;
        for (int s = firstRow; s < def.sections().size() && s < firstRow + LIST_ROWS; s++) {
            final int sec = s;
            int count = chaptersIn(s);
            addRenderableWidget(new FolioWidgets.LinkButton(theme, x0 + RIGHT_X, y0 + TEXT_Y + (s - firstRow) * LIST_ROW_H, PAGE_W, 25,
                    Component.translatable(def.langPrefix() + ".section." + s + ".title"),
                    Component.translatable("folio.gui.chapters", count).getString(),
                    Icons.stack(def.sections().get(s).icon()), def.sections().get(s).color(), false,
                    () -> go(new State(View.SECTION, sec, 0, 0), true)));
        }
    }

    private void buildSection(int x0, int y0) {
        int row = 0;
        int firstRow = spread * LIST_ROWS;
        for (int i = 0; i < def.chapters().size(); i++) {
            BookDefinition.Chapter c = def.chapters().get(i);
            if (c.section() != section) {
                continue;
            }
            int position = row++;
            if (position < firstRow || position >= firstRow + LIST_ROWS) {
                continue;
            }
            final int index = i;
            addRenderableWidget(new FolioWidgets.LinkButton(theme, x0 + RIGHT_X, y0 + TEXT_Y + (position - firstRow) * LIST_ROW_H, PAGE_W, 25,
                    Component.translatable(def.langPrefix() + "." + c.id() + ".title"),
                    Component.translatable(def.langPrefix() + "." + c.id() + ".desc").getString(),
                    Icons.stack(c.icon()), def.sections().get(section).color(), read().contains(i),
                    () -> go(chapterState(index, 0), true)));
        }
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 3) {
            goBack();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && view == View.CHAPTER) {
            for (Placed p : placed) {
                if (p.atom().target != null && p.contains(mouseX, mouseY)) {
                    int index = def.indexOf(p.atom().target);
                    if (index >= 0) {
                        go(chapterState(index, 0), true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (FolioWidgets.tabsPaged(tabCount()) && mouseX < bookX()) {
            scrollTabs(delta > 0 ? -1 : 1);
            return true;
        }
        if (view == View.CHAPTER) {
            go(delta > 0 ? prevState() : nextState(), false);
            return true;
        }
        int pages = listPages(view == View.CONTENTS ? def.sections().size() : chaptersIn(section));
        int target = Mth.clamp(spread + (delta > 0 ? -1 : 1), 0, pages - 1);
        if (target != spread) {
            go(new State(view, section, 0, target), false);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                go(nextState(), false);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                go(prevState(), false);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                goBack();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                go(new State(View.CONTENTS, 0, 0, 0), true);
                return true;
            }
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
    }

    // ------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x0 = bookX();
        int y0 = bookY();
        float fade = Mth.clamp((Util.getMillis() - fadeStart) / 220.0F, 0.0F, 1.0F);
        int alpha = (int) (255 * fade);

        FolioWidgets.background(g, theme, x0, y0, BOOK_W, BOOK_H);

        // Заголовок разворота
        Component title = switch (view) {
            case CONTENTS -> Component.translatable(def.titleKey());
            case SECTION -> Component.translatable(def.langPrefix() + ".section." + section + ".title");
            default -> Component.translatable(def.langPrefix() + "." + def.chapters().get(chapter).id() + ".title");
        };
        // Заголовок — по центру левой страницы (не над корешком); справа — подпись раздела
        drawFitted(g, title, x0 + LEFT_X + PAGE_W / 2, y0 + 12, FolioWidgets.withAlpha(theme.title(), Math.max(alpha, 40)), PAGE_W - 12);
        Component rightHeader;
        int rightColor;
        switch (view) {
            case CONTENTS -> {
                rightHeader = Component.translatable("folio.gui.sections_title");
                rightColor = theme.muted();
            }
            case SECTION -> {
                rightHeader = Component.translatable("folio.gui.chapters_title");
                rightColor = def.sections().get(section).color();
            }
            default -> {
                rightHeader = Component.translatable(def.langPrefix() + ".section." + def.chapters().get(chapter).section() + ".title");
                rightColor = def.sections().get(def.chapters().get(chapter).section()).color();
            }
        }
        drawFitted(g, rightHeader, x0 + RIGHT_X + PAGE_W / 2, y0 + 12, FolioWidgets.withAlpha(rightColor, Math.max(alpha, 40)), PAGE_W - 12);

        placed.clear();
        boxes.clear();
        FolioText.Atom hoveredIconAtom = null;
        String hoveredTarget = null;
        int hoveredLink = -1;

        switch (view) {
            case CONTENTS -> renderCover(g, x0, y0, alpha);
            case SECTION -> renderSectionIntro(g, x0, y0, alpha);
            default -> {
                int first = spread * 2;
                layoutPage(pages.get(first), x0 + LEFT_X, y0 + TEXT_Y);
                if (first + 1 < pages.size()) {
                    layoutPage(pages.get(first + 1), x0 + RIGHT_X, y0 + TEXT_Y);
                }
                for (Placed p : placed) {
                    if (p.contains(mouseX, mouseY)) {
                        if (p.atom().linkId >= 0) {
                            hoveredLink = p.atom().linkId;
                            hoveredTarget = p.atom().target;
                        } else if (p.atom().icon != null) {
                            hoveredIconAtom = p.atom();
                        }
                    }
                }
                drawLines(g, hoveredLink, hoveredIconAtom, alpha);
                Component counter = Component.translatable("folio.gui.page", first + 1,
                        Math.min(pages.size(), first + 2), pages.size());
                drawPageCounter(g, counter, x0, y0);
            }
        }

        if (view != View.CHAPTER) {
            int pages = listPages(view == View.CONTENTS ? def.sections().size() : chaptersIn(section));
            if (pages > 1) {
                drawPageCounter(g, Component.translatable("folio.journal.page", spread + 1, pages), x0, y0);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Подсказки: закладки, ссылки, предметы
        for (FolioWidgets.TabButton tab : tabs) {
            if (tab.hovered()) {
                g.renderTooltip(font, tab.title(), mouseX, mouseY);
            }
        }
        if (hoveredTarget != null) {
            int index = def.indexOf(hoveredTarget);
            if (index >= 0) {
                g.renderTooltip(font, Component.translatable("folio.gui.goto",
                        Component.translatable(def.langPrefix() + "." + hoveredTarget + ".title")), mouseX, mouseY);
            }
        } else if (hoveredIconAtom != null) {
            g.renderTooltip(font, hoveredIconAtom.icon, mouseX, mouseY);
        }
    }

    /**
     * Счётчик страниц: на левой странице, между кнопкой «Назад» и корешком (по центру книги он ложился на корешок
     * и рамку). Если не помещается — сжимается.
     */
    private void drawPageCounter(GuiGraphics g, Component text, int x0, int y0) {
        int from = x0 + 44 + font.width(Component.translatable("folio.gui.back")) + 9 + 6;
        int to = x0 + LEFT_X + PAGE_W - 2;
        drawFitted(g, text, (from + to) / 2, y0 + BOOK_H - 21, theme.muted(), Math.max(20, to - from));
    }

    /** Рисует текст по центру cx; если он шире maxWidth — сжимает, чтобы не вылезал за страницу. */
    private void drawFitted(GuiGraphics g, Component text, int cx, int y, int color, int maxWidth) {
        int w = font.width(text);
        float scale = w > maxWidth ? maxWidth / (float) w : 1.0F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y + (1.0F - scale) * 4.0F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, -w / 2, 0, color, true);
        pose.popPose();
    }

    private void layoutPage(List<FolioText.Line> lines, int px, int py) {
        int y = py;
        for (FolioText.Line line : lines) {
            boxes.add(new LineBox(line, px, y));
            for (FolioText.Atom atom : line.atoms) {
                if (atom.linkId >= 0 || atom.icon != null) {
                    placed.add(new Placed(atom, px + atom.x, y, Math.max(1, atom.width), FolioText.LINE_H));
                }
            }
            y += line.height();
        }
    }

    private void drawLines(GuiGraphics g, int hoveredLink, FolioText.Atom hoveredIcon, int alpha) {
        for (LineBox box : boxes) {
            FolioText.Line line = box.line();
            int px = box.px();
            int y = box.y();
            switch (line.kind) {
                case BULLET -> {
                    if (!line.continuation) {
                        g.fill(px + 2, y + 4, px + 5, y + 7, FolioWidgets.withAlpha(theme.bullet(), alpha));
                    }
                }
                case NOTE -> {
                    g.fill(px, y - (line.noteFirst ? 1 : 0), px + PAGE_W, y + FolioText.LINE_H, FolioWidgets.withAlpha(theme.note(), (int) (alpha * 0.10F)));
                    g.fill(px, y - (line.noteFirst ? 1 : 0), px + 2, y + FolioText.LINE_H, FolioWidgets.withAlpha(theme.note(), alpha));
                }
                case HEADING -> {
                    g.fill(px, y + 11, px + PAGE_W, y + 12, FolioWidgets.withAlpha(theme.heading(), (int) (alpha * 0.35F)));
                }
                default -> {
                }
            }
            for (FolioText.Atom atom : line.atoms) {
                int ax = px + atom.x;
                if (atom.icon != null) {
                    if (atom == hoveredIcon) {
                        g.fill(ax - 1, y - 1, ax + FolioText.ICON + 1, y + FolioText.ICON + 1, 0x33FFFFFF);
                    }
                    FolioWidgets.icon(g, atom.icon, ax, y, FolioText.ICON / 16.0F);
                    continue;
                }
                boolean isLink = atom.linkId >= 0;
                boolean hot = isLink && atom.linkId == hoveredLink;
                int rgb;
                if (isLink) {
                    rgb = hot ? theme.linkHover() : theme.link();
                } else if (line.kind == FolioText.Kind.HEADING) {
                    rgb = theme.heading();
                } else if (line.kind == FolioText.Kind.NOTE) {
                    rgb = theme.noteText();
                } else {
                    rgb = atom.bold ? theme.bold() : theme.text();
                }
                if (atom.isSpace() && !isLink) {
                    continue;
                }
                int color = FolioWidgets.withAlpha(rgb, alpha);
                if (alpha > 8) {
                    g.drawString(font, atom.component(), ax, y + 1, color, false);
                }
                if (isLink) {
                    g.fill(ax, y + 10, ax + atom.width, y + 11, FolioWidgets.withAlpha(rgb, hot ? alpha : (int) (alpha * 0.45F)));
                }
            }
        }
    }

    private void renderCover(GuiGraphics g, int x0, int y0, int alpha) {
        int cx = x0 + LEFT_X + PAGE_W / 2;
        float angle = (Util.getMillis() % 14000L) / 14000.0F * 360.0F;
        FolioWidgets.emblem(g, theme, cx, y0 + 66, angle, 1.6F);

        int y = y0 + 98;
        for (FormattedCharSequence line : font.split(Component.translatable(def.subtitleKey()), PAGE_W - 12)) {
            g.drawString(font, line, cx - font.width(line) / 2, y, FolioWidgets.withAlpha(theme.subtitle(), alpha), false);
            y += 10;
        }

        // Полоска прочитанного
        int total = def.chapters().size();
        int barW = 100;
        int bx = cx - barW / 2;
        int by = y0 + 134;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 5, theme.barBorder());
        g.fill(bx, by, bx + barW, by + 4, theme.barTrack());
        g.fill(bx, by, bx + barW * read().size() / total, by + 4, theme.barFill());
        g.drawCenteredString(font, Component.translatable("folio.gui.progress", read().size(), total),
                cx, by + 7, theme.muted());
    }

    private void renderSectionIntro(GuiGraphics g, int x0, int y0, int alpha) {
        int accent = def.sections().get(section).color();
        int cx = x0 + LEFT_X + PAGE_W / 2;
        g.fill(cx - 24, y0 + 40, cx + 24, y0 + 88, FolioWidgets.withAlpha(accent, 40));
        g.fill(cx - 24, y0 + 40, cx + 24, y0 + 42, accent);
        g.fill(cx - 24, y0 + 86, cx + 24, y0 + 88, accent);
        FolioWidgets.icon(g, Icons.stack(def.sections().get(section).icon()), cx - 16, y0 + 48, 2.0F);

        int y = y0 + 100;
        for (FormattedCharSequence line : font.split(Component.translatable(def.langPrefix() + ".section." + section + ".desc"), PAGE_W - 8)) {
            g.drawString(font, line, cx - font.width(line) / 2, y, FolioWidgets.withAlpha(theme.text(), alpha), false);
            y += 10;
        }
    }
}
