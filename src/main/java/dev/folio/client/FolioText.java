package dev.folio.client;

import dev.folio.util.Icons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Мини-разметка текста глав и раскладка по страницам.
 *
 * Строка начинается с:  "# " — заголовок,  "- " — пункт списка,  "> " — врезка (совет).  Пустая строка — отступ.
 * Внутри строки:
 *   **жирный**                       — выделение
 *   [[id главы|текст ссылки]]        — гиперссылка на главу (кликабельная)
 *   {{item:modid:item_id}}            — иконка предмета (с подсказкой при наведении)
 */
public final class FolioText {
    public static final int LINE_H = 11;
    public static final int ICON = 11;

    public enum Kind { TEXT, HEADING, BULLET, NOTE, SPACER }

    /** Слово, пробел или иконка внутри строки. */
    public static final class Atom {
        public final String text;
        public final boolean bold;
        public final int linkId;
        public final String target;
        public final ItemStack icon;
        public int x;
        public int width;

        Atom(String text, boolean bold, int linkId, String target, ItemStack icon, int width) {
            this.text = text;
            this.bold = bold;
            this.linkId = linkId;
            this.target = target;
            this.icon = icon;
            this.width = width;
        }

        public Component component() {
            Component c = Component.literal(text);
            return bold ? c.copy().withStyle(ChatFormatting.BOLD) : c;
        }

        public boolean isSpace() {
            return text != null && text.equals(" ");
        }
    }

    public static final class Line {
        public final Kind kind;
        public final int indent;
        public final boolean continuation;
        public final List<Atom> atoms = new ArrayList<>();
        public boolean noteFirst;
        public boolean noteLast;

        Line(Kind kind, int indent, boolean continuation) {
            this.kind = kind;
            this.indent = indent;
            this.continuation = continuation;
        }

        public int height() {
            return switch (kind) {
                case SPACER -> 5;
                case HEADING -> 14;
                default -> LINE_H;
            };
        }
    }

    private record Tok(String text, boolean bold, int linkId, String target, String icon) {
    }

    // ------------------------------------------------------------ раскладка

    public static List<Line> layout(Font font, String text, int width) {
        List<Line> lines = new ArrayList<>();
        int[] linkCounter = {0};
        for (String raw : text.split("\n", -1)) {
            if (raw.isBlank()) {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).kind != Kind.SPACER) {
                    lines.add(new Line(Kind.SPACER, 0, false));
                }
                continue;
            }
            Kind kind = Kind.TEXT;
            String body = raw;
            int indent = 0;
            if (raw.startsWith("# ")) {
                kind = Kind.HEADING;
                body = raw.substring(2);
                if (!lines.isEmpty() && lines.get(lines.size() - 1).kind != Kind.SPACER) {
                    lines.add(new Line(Kind.SPACER, 0, false));
                }
            } else if (raw.startsWith("- ")) {
                kind = Kind.BULLET;
                body = raw.substring(2);
                indent = 9;
            } else if (raw.startsWith("> ")) {
                kind = Kind.NOTE;
                body = raw.substring(2);
                indent = 7;
            }
            int firstIndex = lines.size();
            wrap(font, tokenize(body, kind == Kind.HEADING, linkCounter), kind, indent, width, lines);
            if (kind == Kind.NOTE && lines.size() > firstIndex) {
                lines.get(firstIndex).noteFirst = true;
                lines.get(lines.size() - 1).noteLast = true;
            }
        }
        return lines;
    }

    private static List<Tok> tokenize(String s, boolean forceBold, int[] linkCounter) {
        List<Tok> toks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean bold = forceBold;
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("**", i)) {
                flush(toks, buffer, bold);
                bold = forceBold || !bold;
                i += 2;
            } else if (s.startsWith("[[", i) && s.indexOf("]]", i) > 0) {
                int end = s.indexOf("]]", i);
                String body = s.substring(i + 2, end);
                int bar = body.indexOf('|');
                String target = bar >= 0 ? body.substring(0, bar).trim() : body.trim();
                String label = bar >= 0 ? body.substring(bar + 1) : body;
                flush(toks, buffer, bold);
                toks.add(new Tok(label, bold, linkCounter[0]++, target, null));
                i = end + 2;
            } else if (s.startsWith("{{item:", i) && s.indexOf("}}", i) > 0) {
                int end = s.indexOf("}}", i);
                flush(toks, buffer, bold);
                toks.add(new Tok(null, false, -1, null, s.substring(i + 7, end).trim()));
                i = end + 2;
            } else {
                buffer.append(s.charAt(i));
                i++;
            }
        }
        flush(toks, buffer, bold);
        return toks;
    }

    private static void flush(List<Tok> toks, StringBuilder buffer, boolean bold) {
        if (buffer.length() > 0) {
            toks.add(new Tok(buffer.toString(), bold, -1, null, null));
            buffer.setLength(0);
        }
    }

    private static int widthOf(Font font, String text, boolean bold) {
        Component c = Component.literal(text);
        return font.width(bold ? c.copy().withStyle(ChatFormatting.BOLD) : c);
    }

    private static void wrap(Font font, List<Tok> toks, Kind kind, int indent, int maxWidth, List<Line> out) {
        Line current = new Line(kind, indent, false);
        int x = indent;
        int spaceWidth = font.width(" ");
        for (Tok tok : toks) {
            if (tok.icon() != null) {
                int w = ICON + 2;
                if (x + w > maxWidth && !current.atoms.isEmpty()) {
                    out.add(current);
                    current = new Line(kind, indent, true);
                    x = indent;
                }
                Atom atom = new Atom(null, false, -1, null, Icons.stack(tok.icon()), ICON);
                atom.x = x;
                current.atoms.add(atom);
                x += w;
                continue;
            }
            String s = tok.text();
            int i = 0;
            while (i < s.length()) {
                int j = i;
                if (s.charAt(i) == ' ') {
                    while (j < s.length() && s.charAt(j) == ' ') {
                        j++;
                    }
                    if (x > indent) {
                        Atom space = new Atom(" ", tok.bold(), tok.linkId(), tok.target(), null, spaceWidth);
                        space.x = x;
                        current.atoms.add(space);
                        x += spaceWidth;
                    }
                    i = j;
                    continue;
                }
                while (j < s.length() && s.charAt(j) != ' ') {
                    j++;
                }
                String word = s.substring(i, j);
                i = j;
                while (!word.isEmpty()) {
                    int w = widthOf(font, word, tok.bold());
                    if (x + w > maxWidth && x > indent) {
                        out.add(current);
                        current = new Line(kind, indent, true);
                        x = indent;
                        continue;
                    }
                    if (x + w > maxWidth) {
                        // слишком длинное слово: режем по символам
                        int cut = 1;
                        while (cut < word.length() && x + widthOf(font, word.substring(0, cut + 1), tok.bold()) <= maxWidth) {
                            cut++;
                        }
                        String part = word.substring(0, cut);
                        Atom atom = new Atom(part, tok.bold(), tok.linkId(), tok.target(), null, widthOf(font, part, tok.bold()));
                        atom.x = x;
                        current.atoms.add(atom);
                        out.add(current);
                        current = new Line(kind, indent, true);
                        x = indent;
                        word = word.substring(cut);
                        continue;
                    }
                    Atom atom = new Atom(word, tok.bold(), tok.linkId(), tok.target(), null, w);
                    atom.x = x;
                    current.atoms.add(atom);
                    x += w;
                    word = "";
                }
            }
        }
        if (!current.atoms.isEmpty()) {
            out.add(current);
        }
    }

    // ----------------------------------------------------------- страницы

    /** Делит строки на страницы заданной высоты; заголовок не остаётся один в конце страницы. */
    public static List<List<Line>> paginate(List<Line> lines, int pageHeight) {
        List<List<Line>> pages = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        int used = 0;
        for (Line line : lines) {
            int h = line.height();
            if (current.isEmpty() && line.kind == Kind.SPACER) {
                continue;
            }
            int need = h + (line.kind == Kind.HEADING ? LINE_H : 0);
            if (used + need > pageHeight && !current.isEmpty()) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
                if (line.kind == Kind.SPACER) {
                    continue;
                }
            }
            current.add(line);
            used += h;
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private FolioText() {
    }
}
