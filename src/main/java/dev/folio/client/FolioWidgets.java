package dev.folio.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.folio.api.FolioTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Кастомные кнопки руководства: ссылки-строки, текстовые гиперссылки, стрелки и закладки. */
final class FolioWidgets {
    static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Шеврон «>» или «<» из квадратиков (без зависимости от шрифта). */
    static void chevron(GuiGraphics g, int x, int y, int color, boolean right) {
        int[] xs = {0, 1, 2, 3, 2, 1, 0};
        for (int row = 0; row < 7; row++) {
            int dx = right ? xs[row] : 3 - xs[row];
            g.fill(x + dx, y + row, x + dx + 2, y + row + 1, color);
        }
    }

    static void icon(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** Фон разворота: текстура-скин (с масштабированием области) либо заливка цветами темы. */
    static void background(GuiGraphics g, FolioTheme theme, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (theme.skin() != null) {
            if (theme.skinW() == w && theme.skinH() == h) {
                g.blit(theme.skin(), x, y, (float) theme.skinU(), (float) theme.skinV(), w, h,
                        theme.skinTexW(), theme.skinTexH());
            } else {
                g.blit(theme.skin(), x, y, w, h, (float) theme.skinU(), (float) theme.skinV(),
                        theme.skinW(), theme.skinH(), theme.skinTexW(), theme.skinTexH());
            }
        } else {
            g.fill(x, y, x + w, y + h, theme.frame());
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, theme.frameInner());
            int mid = x + w / 2;
            int top = y + 6;
            int bottom = y + h - 6;
            g.fill(x + 6, top, mid - 3, bottom, theme.pageBorder());
            g.fill(x + 7, top + 1, mid - 4, bottom - 1, theme.page());
            g.fill(mid + 3, top, x + w - 6, bottom, theme.pageBorder());
            g.fill(mid + 4, top + 1, x + w - 7, bottom - 1, theme.page());
            g.fill(mid - 3, top, mid + 3, bottom, theme.spine());
        }
        RenderSystem.disableBlend();
    }

    /** Вращающаяся эмблема обложки (если тема её задаёт). cx, cy — центр, scale — множитель к 32 пикселям. */
    static void emblem(GuiGraphics g, FolioTheme theme, int cx, int cy, float angle, float scale) {
        if (theme.emblemTexture() == null || theme.emblemSize() <= 0) {
            return;
        }
        int size = theme.emblemSize();
        PoseStack pose = g.pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        float k = 32.0F * scale / size;
        pose.scale(k, k, 1.0F);
        g.blit(theme.emblemTexture(), -size / 2, -size / 2, (float) theme.emblemU(), (float) theme.emblemV(),
                size, size, theme.emblemTexW(), theme.emblemTexH());
        pose.popPose();
        RenderSystem.disableBlend();
    }

    /** Спрайт на 9 частей; state — номер состояния в текстуре (сверху вниз). */
    static void sprite(GuiGraphics g, FolioTheme.Sprite s, int x, int y, int w, int h, int state) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int u = s.u();
        int v = s.v() + state * s.h();
        int sw = s.w();
        int sh = s.h();
        int b = Math.max(0, Math.min(s.border(), Math.min(sw, sh) / 2));
        int cw = Math.min(b, w / 2);
        int ch = Math.min(b, h / 2);
        int mw = w - 2 * cw;
        int mh = h - 2 * ch;
        int smw = sw - 2 * b;
        int smh = sh - 2 * b;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        SpritePiece.draw(g, s, x, y, cw, ch, u, v, b, b);
        SpritePiece.draw(g, s, x + w - cw, y, cw, ch, u + sw - b, v, b, b);
        SpritePiece.draw(g, s, x, y + h - ch, cw, ch, u, v + sh - b, b, b);
        SpritePiece.draw(g, s, x + w - cw, y + h - ch, cw, ch, u + sw - b, v + sh - b, b, b);
        if (mw > 0 && smw > 0) {
            SpritePiece.draw(g, s, x + cw, y, mw, ch, u + b, v, smw, b);
            SpritePiece.draw(g, s, x + cw, y + h - ch, mw, ch, u + b, v + sh - b, smw, b);
        }
        if (mh > 0 && smh > 0) {
            SpritePiece.draw(g, s, x, y + ch, cw, mh, u, v + b, b, smh);
            SpritePiece.draw(g, s, x + w - cw, y + ch, cw, mh, u + sw - b, v + b, b, smh);
        }
        if (mw > 0 && mh > 0 && smw > 0 && smh > 0) {
            SpritePiece.draw(g, s, x + cw, y + ch, mw, mh, u + b, v + b, smw, smh);
        }
        RenderSystem.disableBlend();
    }

    /** Один кусок спрайта: пропускает пустые размеры. */
    private static final class SpritePiece {
        static void draw(GuiGraphics g, FolioTheme.Sprite s, int x, int y, int w, int h, int u, int v, int uw, int vh) {
            if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) {
                return;
            }
            g.blit(s.texture(), x, y, w, h, (float) u, (float) v, uw, vh, s.texW(), s.texH());
        }
    }

    private static final int TAB_TOP = 14;
    private static final int TAB_STEP = 28;
    private static final int TAB_H = 24;

    /** Сколько закладок видно одновременно, когда их слишком много и колонка прокручивается. */
    static final int TAB_WINDOW = 7;
    private static final int TAB_PAGED_STEP = 24;
    private static final int TAB_PAGED_H = 22;

    /** Закладок больше 8: колонка не помещается в книгу, поэтому показывается окно из 7 штук со стрелками прокрутки. */
    static boolean tabsPaged(int count) {
        return count > 8;
    }

    /** Первая видимая закладка (прокрутка) в допустимых пределах. */
    static int clampTabScroll(int scroll, int count) {
        return tabsPaged(count) ? Mth.clamp(scroll, 0, count - TAB_WINDOW) : 0;
    }

    /** Прокрутка, при которой закладка index видна (не меняет её, если уже видна). */
    static int revealTab(int scroll, int index, int count) {
        if (!tabsPaged(count)) {
            return 0;
        }
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + TAB_WINDOW) {
            scroll = index - TAB_WINDOW + 1;
        }
        return clampTabScroll(scroll, count);
    }

    /** Высота закладки при count закладках: обычная, а если колонка не помещается в книгу — уменьшенная. */
    static int tabHeight(int count, int bookH) {
        if (tabsPaged(count)) {
            return TAB_PAGED_H;
        }
        if (count <= 1 || TAB_TOP + (count - 1) * TAB_STEP + TAB_H <= bookH - 8) {
            return TAB_H;
        }
        return Mth.clamp((bookH - 16) / count, 18, TAB_H);
    }

    /** Смещение закладки от верха книги; index — номер среди видимых (при прокрутке — внутри окна). */
    static int tabOffset(int index, int count, int bookH) {
        if (tabsPaged(count)) {
            return TAB_TOP + index * TAB_PAGED_STEP;
        }
        if (count <= 1 || TAB_TOP + (count - 1) * TAB_STEP + TAB_H <= bookH - 8) {
            return TAB_TOP + index * TAB_STEP;
        }
        int h = tabHeight(count, bookH);
        int step = Math.min(TAB_STEP, (bookH - 16 - h) / (count - 1));
        return 8 + index * Math.max(h, step);
    }

    /** Вертикальный шеврон «вверх» или «вниз». */
    static void chevronV(GuiGraphics g, int x, int y, int color, boolean down) {
        for (int row = 0; row < 4; row++) {
            int dy = down ? row : 3 - row;
            g.fill(x + row, y + dy, x + 8 - row, y + dy + 1, color);
        }
    }

    /** Общая часть: анимация наведения, звук листания, озвучка. */
    abstract static class Base extends AbstractWidget {
        private final Runnable action;
        protected final FolioTheme theme;
        protected float hover;

        Base(FolioTheme theme, int x, int y, int w, int h, Component message, Runnable action) {
            super(x, y, w, h, message);
            this.theme = theme;
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        public void playDownSound(SoundManager manager) {
            // звук листания играет сам экран при переходе (иначе он удвоился бы)
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        protected void animate() {
            float target = isHoveredOrFocused() && active ? 1.0F : 0.0F;
            hover = Mth.clamp(hover + (target > hover ? 0.2F : -0.15F), 0.0F, 1.0F);
        }

        protected Font font() {
            return Minecraft.getInstance().font;
        }
    }

    /** Строка-ссылка: иконка, название, подпись; подсветка и бегущее подчёркивание при наведении. */
    static final class LinkButton extends Base {
        private final ItemStack icon;
        private final String sub;
        private final int accent;
        private final boolean visited;

        LinkButton(FolioTheme theme, int x, int y, int w, int h, Component label, String sub, ItemStack icon, int accent,
                   boolean visited, Runnable action) {
            super(theme, x, y, w, h, label, action);
            this.icon = icon;
            this.sub = sub;
            this.accent = accent;
            this.visited = visited;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            animate();
            Font font = font();
            int x = getX(), y = getY();
            if (theme.rowSprite() != null) {
                sprite(g, theme.rowSprite(), x, y, width, height, hover > 0.5F ? 1 : 0);
            }
            if (hover > 0.0F && theme.rowHoverAlpha() > 0) {
                g.fill(x, y, x + width, y + height, withAlpha(accent, (int) (hover * theme.rowHoverAlpha())));
            }
            if (theme.rowAccentBar()) {
                g.fill(x, y, x + 1 + Math.round(2.0F * hover), y + height, accent);
            }
            if ((theme.rowDivider() >>> 24) != 0) {
                for (int dx = 4; dx < width - 2; dx += 3) {
                    g.fill(x + dx, y + height - 1, x + dx + 1, y + height, theme.rowDivider());
                }
            }
            boolean big = height >= 24;
            float scale = big ? 1.0F : 0.75F;
            icon(g, icon, x + 5, y + (height - (int) (16 * scale)) / 2, scale);

            int tx = x + 8 + (int) (16 * scale) + 3 + Math.round(2.0F * hover);
            int labelY = sub != null ? y + 4 : y + (height - 8) / 2;
            int color = lerp(theme.rowText(), theme.rowTextHover(), hover);
            g.drawString(font, getMessage(), tx, labelY, color, false);
            int labelWidth = font.width(getMessage());
            if (hover > 0.0F) {
                g.fill(tx, labelY + 9, tx + Math.round(labelWidth * hover), labelY + 10, accent);
            }
            if (sub != null) {
                String text = font.plainSubstrByWidth(sub, width - (tx - x) - 16);
                g.drawString(font, text, tx, y + 14, theme.muted(), false);
            }
            if (visited) {
                g.fill(x + width - 15, y + 4, x + width - 12, y + 7, theme.rowVisited());
            }
            chevron(g, x + width - 9 + Math.round(2.0F * hover), y + (height - 7) / 2, lerp(theme.rowChevron(), accent, hover), true);
        }
    }

    /** Текстовая гиперссылка: цветной текст, подчёркивание растёт при наведении. */
    static final class TextButton extends Base {
        private final int chevronSide;

        /** chevronSide: -1 — шеврон слева, 1 — справа, 0 — без шеврона. */
        TextButton(FolioTheme theme, int x, int y, Component label, int chevronSide, Runnable action) {
            super(theme, x, y, Minecraft.getInstance().font.width(label) + (chevronSide != 0 ? 9 : 0), 12, label, action);
            this.chevronSide = chevronSide;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            animate();
            Font font = font();
            int x = getX(), y = getY();
            int color = active ? lerp(theme.link(), theme.linkHover(), hover) : theme.disabledText();
            int tx = x + (chevronSide < 0 ? 9 : 0);
            if (chevronSide < 0) {
                chevron(g, x + Math.round(-1.5F * hover), y + 1, color, false);
            }
            g.drawString(font, getMessage(), tx, y + 1, color, false);
            int labelWidth = font.width(getMessage());
            for (int dx = 0; dx < labelWidth; dx += 2) {
                boolean full = dx < labelWidth * hover;
                if (full || active) {
                    g.fill(tx + dx, y + 10, tx + Math.min(dx + (full ? 2 : 1), labelWidth), y + 11,
                            full ? color : withAlpha(color, 90));
                }
            }
            if (chevronSide > 0) {
                chevron(g, tx + labelWidth + 3 + Math.round(1.5F * hover), y + 1, color, true);
            }
        }
    }

    /** Кнопка-стрелка листания. */
    static final class ArrowButton extends Base {
        private final boolean right;

        ArrowButton(FolioTheme theme, int x, int y, boolean right, Component tooltip, Runnable action) {
            super(theme, x, y, 22, 14, tooltip, action);
            this.right = right;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            animate();
            int x = getX(), y = getY();
            if (theme.arrowSprite() != null) {
                sprite(g, theme.arrowSprite(), x, y, width, height, !active ? 2 : (hover > 0.5F ? 1 : 0));
            } else {
                int border = active ? lerp(theme.arrowBorder(), theme.arrowBorderHover(), hover) : theme.arrowDisabledBorder();
                g.fill(x, y, x + width, y + height, border);
                g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                        active ? lerp(theme.arrowFill(), theme.arrowFillHover(), hover) : theme.arrowDisabledFill());
            }
            int color = active ? lerp(theme.arrowIcon(), theme.arrowIconHover(), hover) : theme.arrowDisabledIcon();
            chevron(g, x + (width - 4) / 2 + (right ? 1 : 0) + Math.round((right ? 1 : -1) * hover), y + 3, color, right);
        }
    }

    /** Закладка раздела на краю книги. */
    static final class TabButton extends Base {
        private final ItemStack icon;
        private final int accent;
        private final boolean selected;

        TabButton(FolioTheme theme, int x, int y, int h, Component title, ItemStack icon, int accent, boolean selected, Runnable action) {
            super(theme, x, y, 28, h, title, action);
            this.icon = icon;
            this.accent = accent;
            this.selected = selected;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            animate();
            int shift = selected ? 0 : Math.round(3.0F * (1.0F - hover)) + 3;
            int x = getX() + shift, y = getY();
            int w = width - shift;
            if (theme.tabSprite() != null) {
                sprite(g, theme.tabSprite(), x, y, w, height, selected || hover > 0.5F ? 1 : 0);
            } else {
                g.fill(x, y, x + w, y + height, theme.tabOutline());
                g.fill(x + 1, y + 1, x + w, y + height - 1,
                        selected ? lerp(theme.tabSelected(), accent, theme.tabAccentMix()) : theme.tabIdle());
            }
            g.fill(x + 1, y + 1, x + 3, y + height - 1, selected ? accent : withAlpha(accent, 120 + (int) (hover * 135)));
            icon(g, icon, x + 6, y + (height - 16) / 2, 1.0F);
        }

        boolean hovered() {
            return isHoveredOrFocused();
        }

        Component title() {
            return getMessage();
        }
    }

    /** Маленькая кнопка прокрутки колонки закладок (вверх или вниз). */
    static final class TabScroll extends Base {
        private final boolean down;

        TabScroll(FolioTheme theme, int x, int y, boolean down, Component tooltip, Runnable action) {
            super(theme, x, y, 28, 9, tooltip, action);
            this.down = down;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            animate();
            int x = getX(), y = getY();
            if (theme.arrowSprite() != null) {
                sprite(g, theme.arrowSprite(), x, y, width, height, !active ? 2 : (hover > 0.5F ? 1 : 0));
            } else {
                int border = active ? lerp(theme.arrowBorder(), theme.arrowBorderHover(), hover) : theme.arrowDisabledBorder();
                g.fill(x, y, x + width, y + height, border);
                g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                        active ? lerp(theme.arrowFill(), theme.arrowFillHover(), hover) : theme.arrowDisabledFill());
            }
            int color = active ? lerp(theme.arrowIcon(), theme.arrowIconHover(), hover) : theme.arrowDisabledIcon();
            chevronV(g, x + (width - 8) / 2, y + 3, color, down);
        }
    }
}
