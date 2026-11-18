package kote.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class KoteHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFB9AFD6;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean leftWas = false, rightWas = false;

    private static int cps(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static String dirName(String d) {
        return switch (d) {
            case "north" -> "Север";
            case "south" -> "Юг";
            case "east" -> "Восток";
            case "west" -> "Запад";
            default -> d;
        };
    }

    /** Плашка с цветной линией сверху (как в визуальных клиентах). */
    private static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, KoteConfig.panelColor());
        g.fill(x, y, x + w, y + 1, accent);
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        KoteConfig.tickAnim();
        if (mc.player == null || mc.options.hideGui) return;
        int accent = KoteConfig.accent(0);
        int PANEL = KoteConfig.panelColor();
        long now = System.currentTimeMillis();

        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();
        if (left && !leftWas) leftClicks.addLast(now);
        if (right && !rightWas) rightClicks.addLast(now);
        leftWas = left; rightWas = right;
        int lcps = cps(leftClicks, now), rcps = cps(rightClicks, now);

        // ---- Верхняя плашка: Kote Client | ник | fps | пинг | время ----
        List<String> seg = new ArrayList<>();
        if (KoteConfig.WATERMARK.enabled) {
            seg.add("Kote Client");
            seg.add(mc.player.getName().getString());
        }
        if (KoteConfig.FPS.enabled) seg.add(mc.getFps() + " fps");
        if (KoteConfig.PING.enabled) {
            int ping = 0;
            if (mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
            seg.add(ping + " ms");
        }
        if (KoteConfig.TIME.enabled) seg.add(LocalTime.now().format(TIME_FMT));
        int infoY = 6;
        if (!seg.isEmpty()) {
            int total = 8;
            for (String s : seg) total += mc.font.width(s) + 9;
            int x = 6, y = 6;
            card(g, x, y, total, 16, accent);
            int tx = x + 6;
            for (int i = 0; i < seg.size(); i++) {
                String s = seg.get(i);
                g.drawString(mc.font, s, tx, y + 5, (i == 0 && KoteConfig.WATERMARK.enabled) ? accent : WHITE, true);
                tx += mc.font.width(s) + 4;
                if (i < seg.size() - 1) {
                    g.fill(tx, y + 4, tx + 1, y + 13, 0x55FFFFFF);
                    tx += 5;
                }
            }
            infoY = 6 + 16 + 4;
        }

        // ---- Блок под плашкой: координаты, направление, скорость, CPS ----
        List<String> info = new ArrayList<>();
        if (KoteConfig.COORDS.enabled) {
            info.add(String.format("XYZ  %d  %d  %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())));
        }
        if (KoteConfig.DIRECTION.enabled) info.add("Куда  " + dirName(mc.player.getDirection().getSerializedName()));
        if (KoteConfig.SPEED.enabled) {
            var v = mc.player.getDeltaMovement();
            info.add(String.format("Скорость  %.1f б/с", Math.sqrt(v.x * v.x + v.z * v.z) * 20.0));
        }
        if (KoteConfig.CPS.enabled) info.add("CPS  " + lcps + " | " + rcps);
        int belowY = infoY;
        if (!info.isEmpty()) {
            int w = 0;
            for (String s : info) w = Math.max(w, mc.font.width(s));
            w += 14;
            int h = info.size() * 11 + 6;
            card(g, 6, infoY, w, h, accent);
            int ty = infoY + 5;
            for (String s : info) {
                g.drawString(mc.font, s, 12, ty, WHITE, true);
                ty += 11;
            }
            belowY = infoY + h + 4;
        }

        // ---- Эффекты ----
        if (KoteConfig.EFFECTS.enabled) {
            int ey = belowY;
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                int amp = e.getAmplifier() + 1;
                int sec = e.getDuration() / 20;
                String text = name + (amp > 1 ? " " + amp : "") + "  " + (sec / 60) + ":" + String.format("%02d", sec % 60);
                int w = mc.font.width(text) + 12;
                g.fill(6, ey, 6 + w, ey + 13, PANEL);
                g.fill(6, ey, 8, ey + 13, accent);
                g.drawString(mc.font, text, 12, ey + 3, WHITE, true);
                ey += 15;
            }
        }

        // ---- Клавиши ----
        if (KoteConfig.KEYS.enabled) {
            int kx = 8, ky = g.guiHeight() / 2 - 20;
            key(g, mc, kx + 24, ky, 20, "W", mc.options.keyUp.isDown());
            key(g, mc, kx, ky + 22, 20, "A", mc.options.keyLeft.isDown());
            key(g, mc, kx + 24, ky + 22, 20, "S", mc.options.keyDown.isDown());
            key(g, mc, kx + 48, ky + 22, 20, "D", mc.options.keyRight.isDown());
            key(g, mc, kx, ky + 44, 32, "ЛКМ", left);
            key(g, mc, kx + 36, ky + 44, 32, "ПКМ", right);
            key(g, mc, kx, ky + 66, 68, "ПРОБЕЛ", mc.options.keyJump.isDown());
        }

        // ---- Панель цели ----
        if (KoteConfig.TARGET.enabled && mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity target) {
            int pw = 132, ph = 44;
            int px = g.guiWidth() / 2 + 14, py = g.guiHeight() / 2 + 14;
            card(g, px, py, pw, ph, accent);
            g.drawString(mc.font, target.getName().getString(), px + 7, py + 5, accent, true);
            float hp = target.getHealth(), maxHp = Math.max(1f, target.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, hp / maxHp));
            int barColor = ratio > 0.5f ? 0xFF69F0AE : ratio > 0.25f ? 0xFFFFD54F : 0xFFFF5252;
            g.fill(px + 7, py + 17, px + pw - 7, py + 23, 0xFF241B3A);
            g.fill(px + 7, py + 17, px + 7 + (int) ((pw - 14) * ratio), py + 23, barColor);
            g.drawString(mc.font, String.format("%.1f / %.0f   %.1f м", hp, maxHp, mc.player.distanceTo(target)),
                    px + 7, py + 28, WHITE, true);
        }

        // ---- Список модулей справа (с выезжающей анимацией) ----
        if (KoteConfig.ARRAYLIST.enabled) {
            List<KoteConfig.Mod> mods = new ArrayList<>();
            for (KoteConfig.Mod m : KoteConfig.ALL) {
                if (!m.alwaysOn && m != KoteConfig.ARRAYLIST && m.anim > 0.02f) mods.add(m);
            }
            mods.sort((a, b) -> Integer.compare(mc.font.width(b.name), mc.font.width(a.name)));
            int ly = 6, li = 0;
            for (KoteConfig.Mod m : mods) {
                int w = mc.font.width(m.name) + 10;
                int lx = g.guiWidth() - (int) (w * m.anim) - 2;
                int c = KoteConfig.accent(li);
                g.fill(lx, ly, lx + w, ly + 12, PANEL);
                g.fill(lx + w - 2, ly, lx + w, ly + 12, c);
                g.drawString(mc.font, m.name, lx + 4, ly + 3, WHITE, true);
                ly += 12; li++;
            }
        }

        // ---- Броня с прочностью ----
        if (KoteConfig.ARMOR.enabled) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int ax = g.guiWidth() - 22, ay = g.guiHeight() - 4 * 18 - 44;
            for (int i = 0; i < slots.length; i++) {
                ItemStack st = mc.player.getItemBySlot(slots[i]);
                if (st.isEmpty()) continue;
                int iy = ay + i * 18;
                g.renderItem(st, ax, iy);
                g.renderItemDecorations(mc.font, st, ax, iy);
                if (st.isDamageableItem()) {
                    int pct = 100 * (st.getMaxDamage() - st.getDamageValue()) / Math.max(1, st.getMaxDamage());
                    String t = pct + "%";
                    int col = pct > 50 ? WHITE : pct > 25 ? 0xFFFFD54F : 0xFFFF5252;
                    g.drawString(mc.font, t, ax - mc.font.width(t) - 4, iy + 5, col, true);
                }
            }
        }
    }

    private static void key(GuiGraphics g, Minecraft mc, int x, int y, int w, String label, boolean down) {
        int accent = KoteConfig.accent(0);
        int bg = down ? ((0xAA << 24) | (accent & 0xFFFFFF)) : KoteConfig.panelColor();
        g.fill(x, y, x + w, y + 20, bg);
        g.drawCenteredString(mc.font, label, x + w / 2, y + 6, WHITE);
    }

    /** Свой прицел. */
    public static void crosshair(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        int style = (int) KoteConfig.CROSSHAIR.setting("style").value;
        int size = (int) KoteConfig.CROSSHAIR.setting("size").value;
        int gap = (int) KoteConfig.CROSSHAIR.setting("gap").value;
        int t = (int) KoteConfig.CROSSHAIR.setting("thick").value;
        int col = ((int) KoteConfig.CROSSHAIR.setting("color").value == 0) ? KoteConfig.accent(0) : WHITE;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (style == 0) {
            g.fill(cx - gap - size, cy, cx - gap, cy + t, col);
            g.fill(cx + gap + 1, cy, cx + gap + 1 + size, cy + t, col);
            g.fill(cx, cy - gap - size, cx + t, cy - gap, col);
            g.fill(cx, cy + gap + 1, cx + t, cy + gap + 1 + size, col);
        } else if (style == 1) {
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
        } else {
            int s = size;
            g.fill(cx - s, cy - s, cx + s + 1, cy - s + t, col);
            g.fill(cx - s, cy + s + 1 - t, cx + s + 1, cy + s + 1, col);
            g.fill(cx - s, cy - s, cx - s + t, cy + s + 1, col);
            g.fill(cx + s + 1 - t, cy - s, cx + s + 1, cy + s + 1, col);
        }
    }
}
