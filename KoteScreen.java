package kote.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class KoteScreen extends Screen {
    private static final int WW = 430, WH = 270, TITLE = 26, SIDE = 100;
    private static final int CARD_H = 30, SET_H = 17, GAP = 5;
    private static final int GREY = 0xFFB9AFD6, WHITE = 0xFFFFFFFF, DARK = 0xFF0B0813;

    private static int wx = Integer.MIN_VALUE, wy = 0;
    private static int selectedCat = 0;

    private record Hit(int x, int y, int w, int h, int type, int cat, KoteConfig.Mod m, KoteConfig.Setting s) { }

    private final List<Hit> hits = new ArrayList<>();
    private boolean dragWindow = false;
    private int dragOffX, dragOffY;
    private KoteConfig.Setting dragSlider = null;
    private int sliderX, sliderW;

    public KoteScreen() { super(Component.literal("Kote Client")); }

    @Override
    protected void init() {
        if (wx == Integer.MIN_VALUE) {
            wx = (this.width - WW) / 2;
            wy = (this.height - WH) / 2;
        }
    }

    private static int darken(int c, int pct) {
        int r = (c >> 16 & 0xFF) * pct / 100, g = (c >> 8 & 0xFF) * pct / 100, b = (c & 0xFF) * pct / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        hits.clear();
        wx = Math.max(0, Math.min(this.width - WW, wx));
        wy = Math.max(0, Math.min(this.height - WH, wy));
        int accent = KoteConfig.accent(0);

        // окно
        g.fill(wx, wy, wx + WW, wy + WH, 0xEE100C1C);
        g.fillGradient(wx, wy, wx + WW, wy + TITLE, accent, darken(accent, 50));
        g.drawString(this.font, "KOTE CLIENT", wx + 10, wy + 9, WHITE, true);
        String ver = "1.21.11  •  Fabric";
        g.drawString(this.font, ver, wx + WW - this.font.width(ver) - 10, wy + 9, 0xFFEFE6FF, false);
        hits.add(new Hit(wx, wy, WW, TITLE, 0, 0, null, null));

        // сайдбар
        g.fill(wx, wy + TITLE, wx + SIDE, wy + WH, 0xFF0D0916);
        for (int i = 0; i < KoteConfig.CATEGORIES.length; i++) {
            int cy = wy + TITLE + 8 + i * 26;
            boolean sel = i == selectedCat;
            boolean hov = in(mouseX, mouseY, wx, cy, SIDE, 24);
            if (sel) {
                g.fill(wx + 6, cy, wx + SIDE - 6, cy + 24, (0x55 << 24) | (accent & 0xFFFFFF));
                g.fill(wx + 6, cy, wx + 8, cy + 24, accent);
            } else if (hov) {
                g.fill(wx + 6, cy, wx + SIDE - 6, cy + 24, 0x22FFFFFF);
            }
            g.drawString(this.font, KoteConfig.CATEGORIES[i], wx + 18, cy + 8, sel ? WHITE : GREY, false);
            hits.add(new Hit(wx + 6, cy, SIDE - 12, 24, 1, i, null, null));
        }
        g.drawString(this.font, "Shift - закрыть", wx + 10, wy + WH - 14, 0xFF6F6590, false);

        // карточки модулей в две колонки
        int areaX = wx + SIDE + 10;
        int colW = (WW - SIDE - 10 - 10 - GAP) / 2;
        int[] colY = {wy + TITLE + 10, wy + TITLE + 10};
        String cat = KoteConfig.CATEGORIES[selectedCat];
        for (KoteConfig.Mod m : KoteConfig.ALL) {
            if (!m.category.equals(cat)) continue;
            int col = colY[0] <= colY[1] ? 0 : 1;
            int x = areaX + col * (colW + GAP);
            int y = colY[col];
            boolean hov = in(mouseX, mouseY, x, y, colW, CARD_H);

            int bg = hov ? 0xFF1E1633 : 0xFF181228;
            g.fill(x, y, x + colW, y + CARD_H, bg);
            if (m.anim > 0.02f) g.fill(x, y, x + 2, y + CARD_H, accent);
            g.drawString(this.font, m.name, x + 8, y + 6, WHITE, false);
            String d = m.desc;
            while (this.font.width(d) > colW - 44 && d.length() > 3) d = d.substring(0, d.length() - 1);
            g.drawString(this.font, d, x + 8, y + 18, 0xFF8D82A8, false);

            // переключатель
            int sx = x + colW - 30, sy = y + 10;
            int pill = KoteConfig.mix(0xFF3A2F55, accent, m.anim);
            g.fill(sx, sy, sx + 22, sy + 10, pill);
            int kx = sx + 1 + (int) (12 * m.anim);
            g.fill(kx, sy + 1, kx + 8, sy + 9, WHITE);
            if (!m.settings.isEmpty()) {
                g.drawString(this.font, m.expanded ? "-" : "+", x + colW - 40, y + 11, GREY, false);
            }
            hits.add(new Hit(x, y, colW, CARD_H, 2, 0, m, null));
            int ny = y + CARD_H;

            if (m.expanded) {
                for (KoteConfig.Setting s : m.settings) {
                    g.fill(x, ny, x + colW, ny + SET_H, 0xFF0F0B1A);
                    if (!s.mode) {
                        double t = (s.value - s.min) / (s.max - s.min);
                        g.fill(x + 6, ny + SET_H - 3, x + colW - 6, ny + SET_H - 1, 0xFF2A2142);
                        g.fill(x + 6, ny + SET_H - 3, x + 6 + (int) ((colW - 12) * t), ny + SET_H - 1, accent);
                    }
                    g.drawString(this.font, s.name + ": " + s.display(), x + 8, ny + 3, GREY, false);
                    hits.add(new Hit(x, ny, colW, SET_H, 3, 0, m, s));
                    ny += SET_H;
                }
            }
            colY[col] = ny + GAP;
        }
    }

    private void applySlider(double mx) {
        if (dragSlider == null) return;
        double t = Math.max(0, Math.min(1, (mx - sliderX - 6) / (double) (sliderW - 12)));
        dragSlider.value = Math.round(dragSlider.min + t * (dragSlider.max - dragSlider.min));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int b = event.button();
        // вложенные элементы проверяем раньше заголовка окна
        for (int pass = 0; pass < 2; pass++) {
            for (Hit h : hits) {
                if ((pass == 0) == (h.type() == 0)) continue;
                if (!in(mx, my, h.x(), h.y(), h.w(), h.h())) continue;
                switch (h.type()) {
                    case 0 -> {
                        if (b == 0) {
                            dragWindow = true;
                            dragOffX = (int) mx - wx;
                            dragOffY = (int) my - wy;
                        }
                    }
                    case 1 -> selectedCat = h.cat();
                    case 2 -> {
                        KoteConfig.Mod m = h.m();
                        if (b == 0 && !m.alwaysOn) m.enabled = !m.enabled;
                        else if (!m.settings.isEmpty()) m.expanded = !m.expanded;
                    }
                    default -> {
                        KoteConfig.Setting s = h.s();
                        if (s.mode) {
                            int n = s.options.length;
                            s.value = (s.value + (b == 1 ? n - 1 : 1)) % n;
                        } else {
                            dragSlider = s;
                            sliderX = h.x();
                            sliderW = h.w();
                            applySlider(mx);
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragWindow) {
            wx = (int) event.x() - dragOffX;
            wy = (int) event.y() - dragOffY;
            return true;
        }
        if (dragSlider != null) {
            applySlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragWindow = false;
        dragSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        KoteConfig.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
