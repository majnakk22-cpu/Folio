package dev.folio.client;

import dev.folio.api.FolioTheme;
import dev.folio.api.JournalDefinition;
import dev.folio.util.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Журнал оператора: слева список записей (с вкладками «Все / Записки / Голоса / События»),
 * справа текст выбранной. Закрытые записи показаны как «???».
 */
public class JournalScreen extends Screen {
    private static final int BOOK_W = 312;
    private static final int BOOK_H = 196;
    private static final int PAGE_W = 132;
    private static final int PAGE_H = 136;
    private static final int LEFT_X = 18;
    private static final int RIGHT_X = 162;
    private static final int TEXT_Y = 34;
    private static final int ROW_H = 15;
    private static final int ROWS = 9;

    private final Set<String> unlocked;
    private final List<JournalDefinition.Entry> filtered = new ArrayList<>();
    private final List<FolioWidgets.TabButton> tabs = new ArrayList<>();
    private int category = -1;
    /** Первая видимая закладка, когда категорий слишком много для колонки. */
    private int tabScroll;
    private int listPage;
    private String selected;
    private int textPage;
    private List<List<FolioText.Line>> entryPages = List.of();

    private final JournalDefinition def;
    private final FolioTheme theme;

    public JournalScreen(JournalDefinition def, Set<String> unlocked, String openId) {
        super(Component.translatable(def.titleKey()));
        this.def = def;
        this.theme = def.theme();
        this.unlocked = unlocked;
        this.selected = openId == null || openId.isEmpty() ? null : openId;
        if (selected != null) {
            JournalDefinition.Entry entry = def.get(selected);
            if (entry != null) {
                category = -1;
            } else {
                selected = null;
            }
        }
    }

    private int bookX() {
        return Math.max(30, (width - BOOK_W) / 2 + 12);
    }

    private int bookY() {
        return (height - BOOK_H) / 2;
    }

    @Override
    protected void init() {
        applyFilter();
        if (selected != null) {
            int index = indexOfSelected();
            if (index >= 0) {
                listPage = index / ROWS;
            }
            buildEntryPages();
        }
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ данные

    private void applyFilter() {
        filtered.clear();
        for (JournalDefinition.Entry entry : def.entries()) {
            if (category < 0 || entry.category() == category) {
                filtered.add(entry);
            }
        }
        int pages = Math.max(1, (filtered.size() + ROWS - 1) / ROWS);
        listPage = Mth.clamp(listPage, 0, pages - 1);
    }

    private int indexOfSelected() {
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    private int listPages() {
        return Math.max(1, (filtered.size() + ROWS - 1) / ROWS);
    }

    private void buildEntryPages() {
        textPage = 0;
        if (selected == null) {
            entryPages = List.of();
            return;
        }
        String markup;
        if (unlocked.contains(selected)) {
            markup = "# " + Component.translatable(def.langPrefix() + "." + selected + ".title").getString() + "\n\n"
                    + Component.translatable(def.langPrefix() + "." + selected + ".text").getString();
        } else {
            markup = "# " + Component.translatable("folio.journal.unknown").getString() + "\n\n"
                    + Component.translatable("folio.journal.locked").getString();
        }
        entryPages = FolioText.paginate(FolioText.layout(font, markup, PAGE_W), PAGE_H);
    }

    private long unlockedCount() {
        return def.entries().stream().filter(e -> unlocked.contains(e.id())).count();
    }

    // ----------------------------------------------------------------- виджеты

    private void rebuild() {
        clearWidgets();
        tabs.clear();
        int x0 = bookX();
        int y0 = bookY();

        int tabCount = 1 + def.categories().size();
        boolean paged = FolioWidgets.tabsPaged(tabCount);
        tabScroll = FolioWidgets.clampTabScroll(tabScroll, tabCount);
        if (paged) {
            FolioWidgets.TabScroll tabUp = addRenderableWidget(new FolioWidgets.TabScroll(theme, x0 - 26, y0 + 3, false,
                    Component.translatable("folio.gui.tabs_up"), () -> scrollTabs(-1)));
            tabUp.active = tabScroll > 0;
            FolioWidgets.TabScroll tabDown = addRenderableWidget(new FolioWidgets.TabScroll(theme, x0 - 26, y0 + BOOK_H - 12, true,
                    Component.translatable("folio.gui.tabs_down"), () -> scrollTabs(1)));
            tabDown.active = tabScroll < tabCount - FolioWidgets.TAB_WINDOW;
        }
        for (int i = 0; i < tabCount; i++) {
            if (paged && (i < tabScroll || i >= tabScroll + FolioWidgets.TAB_WINDOW)) {
                continue;
            }
            final int cat = i - 1;
            Component tabTitle = cat < 0 ? Component.translatable("folio.journal.all")
                    : Component.translatable(def.categories().get(cat).nameKey());
            ItemStack tabIcon = cat < 0 ? new ItemStack(Items.BOOK) : Icons.stack(def.categories().get(cat).icon());
            int tabColor = cat < 0 ? theme.tabContents() : def.categories().get(cat).color();
            tabs.add(addRenderableWidget(new FolioWidgets.TabButton(theme, x0 - 26, y0 + FolioWidgets.tabOffset(paged ? i - tabScroll : i, tabCount, BOOK_H),
                    FolioWidgets.tabHeight(tabCount, BOOK_H),
                    tabTitle, tabIcon, tabColor, category == cat, () -> {
                category = cat;
                listPage = 0;
                applyFilter();
                rebuild();
                playTurn(1.0F);
            })));
        }

        // Строки списка
        int from = listPage * ROWS;
        for (int row = 0; row < ROWS && from + row < filtered.size(); row++) {
            JournalDefinition.Entry entry = filtered.get(from + row);
            boolean open = unlocked.contains(entry.id());
            Component label;
            ItemStack icon;
            int accent;
            if (open) {
                String title = Component.translatable(def.langPrefix() + "." + entry.id() + ".title").getString();
                label = Component.literal(trim(title, PAGE_W - 40));
                icon = Icons.stack(entry.icon());
                accent = def.categories().get(entry.category()).color();
            } else {
                label = Component.translatable("folio.journal.unknown");
                icon = new ItemStack(Items.BOOK);
                accent = theme.locked();
            }
            final String id = entry.id();
            addRenderableWidget(new FolioWidgets.LinkButton(theme, x0 + LEFT_X, y0 + TEXT_Y + row * ROW_H, PAGE_W, ROW_H - 1,
                    label, null, icon, accent, id.equals(selected), () -> {
                selected = id;
                buildEntryPages();
                rebuild();
                playTurn(1.1F);
            }));
        }

        // Стрелки: список слева, страницы записи справа
        int navY = y0 + BOOK_H - 24;
        FolioWidgets.ArrowButton listPrev = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + 16, navY, false,
                Component.translatable("folio.gui.prev"), () -> turnList(-1)));
        listPrev.active = listPage > 0;
        FolioWidgets.ArrowButton listNext = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + 40, navY, true,
                Component.translatable("folio.gui.next"), () -> turnList(1)));
        listNext.active = listPage < listPages() - 1;
        FolioWidgets.ArrowButton textPrev = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + RIGHT_X + PAGE_W - 48, navY, false,
                Component.translatable("folio.gui.prev"), () -> turnText(-1)));
        textPrev.active = textPage > 0;
        FolioWidgets.ArrowButton textNext = addRenderableWidget(new FolioWidgets.ArrowButton(theme, x0 + RIGHT_X + PAGE_W - 24, navY, true,
                Component.translatable("folio.gui.next"), () -> turnText(1)));
        textNext.active = textPage < entryPages.size() - 1;
    }

    private void scrollTabs(int delta) {
        int count = 1 + def.categories().size();
        int target = FolioWidgets.clampTabScroll(tabScroll + delta, count);
        if (target != tabScroll) {
            tabScroll = target;
            rebuild();
        }
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private void turnList(int delta) {
        int target = Mth.clamp(listPage + delta, 0, listPages() - 1);
        if (target != listPage) {
            listPage = target;
            rebuild();
            playTurn(1.0F);
        }
    }

    private void turnText(int delta) {
        int target = Mth.clamp(textPage + delta, 0, Math.max(0, entryPages.size() - 1));
        if (target != textPage) {
            textPage = target;
            rebuild();
            playTurn(1.0F);
        }
    }

    private void playTurn(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 0.6F));
    }

    // -------------------------------------------------------------------- ввод

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int x0 = bookX();
        if (FolioWidgets.tabsPaged(1 + def.categories().size()) && mouseX < x0) {
            scrollTabs(delta > 0 ? -1 : 1);
            return true;
        }
        if (mouseX >= x0 + RIGHT_X) {
            turnText(delta > 0 ? -1 : 1);
        } else {
            turnList(delta > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                turnText(1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                turnText(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                turnList(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                turnList(-1);
                return true;
            }
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x0 = bookX();
        int y0 = bookY();

        FolioWidgets.background(g, theme, x0, y0, BOOK_W, BOOK_H);

        g.drawCenteredString(font, Component.translatable(def.titleKey()),
                x0 + LEFT_X + PAGE_W / 2, y0 + 12, theme.title());
        g.drawCenteredString(font, Component.translatable("folio.journal.progress", unlockedCount(), def.entries().size()),
                x0 + RIGHT_X + PAGE_W / 2, y0 + 12, theme.muted());

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("folio.journal.empty"),
                    x0 + LEFT_X + PAGE_W / 2, y0 + TEXT_Y + 40, theme.muted());
        }
        g.drawCenteredString(font, Component.translatable("folio.journal.page", listPage + 1, listPages()),
                x0 + LEFT_X + 94, y0 + BOOK_H - 21, theme.muted());

        // Текст выбранной записи
        if (!entryPages.isEmpty()) {
            int y = y0 + TEXT_Y;
            int px = x0 + RIGHT_X;
            for (FolioText.Line line : entryPages.get(textPage)) {
                if (line.kind == FolioText.Kind.HEADING) {
                    g.fill(px, y + 11, px + PAGE_W, y + 12, FolioWidgets.withAlpha(theme.heading(), 90));
                }
                for (FolioText.Atom atom : line.atoms) {
                    if (atom.icon != null || (atom.isSpace() && atom.linkId < 0)) {
                        continue;
                    }
                    int color = line.kind == FolioText.Kind.HEADING ? theme.heading() : theme.text();
                    g.drawString(font, atom.component(), px + atom.x, y + 1, color, false);
                }
                y += line.height();
            }
            g.drawCenteredString(font, Component.translatable("folio.journal.page", textPage + 1, entryPages.size()),
                    x0 + RIGHT_X + 40, y0 + BOOK_H - 21, theme.muted());
        } else {
            g.drawCenteredString(font, Component.translatable("folio.journal.locked"),
                    x0 + RIGHT_X + PAGE_W / 2, y0 + TEXT_Y + 40, theme.muted());
        }

        super.render(g, mouseX, mouseY, partialTick);

        for (FolioWidgets.TabButton tab : tabs) {
            if (tab.hovered()) {
                g.renderTooltip(font, tab.title(), mouseX, mouseY);
            }
        }
    }
}
