package kote.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KoteConfig {
    public static final String[] CATEGORIES = {"ИНФО", "ИГРОК", "ВИЗУАЛ"};
    public static final String[] ACCENT_NAMES = {"Фиолетовый", "Голубой", "Розовый", "Зелёный", "Жёлтый", "Радуга"};
    public static final int[] ACCENTS = {0xFFB388FF, 0xFF4FC3F7, 0xFFFF6E9C, 0xFF69F0AE, 0xFFFFD54F};

    public static class Setting {
        public final String id, name;
        public final boolean mode;
        public final String[] options;
        public final double min, max;
        public double value;

        Setting(String id, String name, double min, double max, double def) {
            this.id = id; this.name = name; this.mode = false; this.options = null;
            this.min = min; this.max = max; this.value = def;
        }

        Setting(String id, String name, String[] options, int def) {
            this.id = id; this.name = name; this.mode = true; this.options = options;
            this.min = 0; this.max = options.length - 1; this.value = def;
        }

        public String display() {
            return mode ? options[(int) value] : String.valueOf((int) Math.round(value));
        }
    }

    public static class Mod {
        public final String id, name, category;
        public final boolean alwaysOn;
        public String desc = "";
        public boolean enabled;
        public boolean expanded;
        public float anim;
        public final List<Setting> settings = new ArrayList<>();

        Mod(String id, String name, String category, boolean enabled, boolean alwaysOn) {
            this.id = id; this.name = name; this.category = category;
            this.enabled = enabled; this.alwaysOn = alwaysOn;
            this.anim = enabled ? 1f : 0f;
        }

        Mod desc(String d) { this.desc = d; return this; }

        Mod slider(String sid, String sname, double min, double max, double def) {
            settings.add(new Setting(sid, sname, min, max, def)); return this;
        }

        Mod mode(String sid, String sname, String[] options, int def) {
            settings.add(new Setting(sid, sname, options, def)); return this;
        }

        public Setting setting(String sid) {
            for (Setting s : settings) if (s.id.equals(sid)) return s;
            throw new IllegalArgumentException(sid);
        }
    }

    public static final List<Mod> ALL = new ArrayList<>();
    public static final Map<String, int[]> PANELS = new LinkedHashMap<>();

    private static Mod add(String id, String name, String cat, boolean def) {
        Mod m = new Mod(id, name, cat, def, false);
        ALL.add(m);
        return m;
    }

    // ИНФО
    public static final Mod WATERMARK = add("watermark", "Название", "ИНФО", true).desc("Плашка Kote Client и ник");
    public static final Mod FPS = add("fps", "FPS", "ИНФО", true).desc("Кадры в секунду");
    public static final Mod PING = add("ping", "Пинг", "ИНФО", true).desc("Задержка до сервера");
    public static final Mod TIME = add("time", "Время", "ИНФО", false).desc("Реальное время");
    public static final Mod COORDS = add("coords", "Координаты", "ИНФО", true).desc("XYZ игрока");
    public static final Mod DIRECTION = add("direction", "Направление", "ИНФО", false).desc("Сторона света");
    public static final Mod SPEED = add("speed", "Скорость", "ИНФО", false).desc("Блоков в секунду");
    public static final Mod CPS = add("cps", "CPS", "ИНФО", true).desc("Клики мышью в секунду");
    public static final Mod NETHER = add("nether", "Координаты Незера", "ИНФО", false).desc("X и Z, делённые на 8");
    public static final Mod RAM = add("ram", "Память", "ИНФО", false).desc("Занято ОЗУ игрой");
    public static final Mod SERVER = add("server", "Сервер", "ИНФО", false).desc("Адрес текущего сервера");
    // ИГРОК
    public static final Mod KEYS = add("keys", "Клавиши", "ИГРОК", true).desc("WASD, пробел, мышь");
    public static final Mod ARMOR = add("armor", "Броня", "ИГРОК", true).desc("Броня и прочность");
    public static final Mod EFFECTS = add("effects", "Эффекты", "ИГРОК", true).desc("Список эффектов");
    public static final Mod ITEMS = add("items", "Счётчик предметов", "ИГРОК", false).desc("Тотемы, яблоки, жемчуг");
    public static final Mod HELD = add("held", "Прочность в руке", "ИГРОК", false).desc("Прочность предмета в руке");
    public static final Mod ZOOM = add("zoom", "Зум (C)", "ИГРОК", true).desc("Приближение на кнопку C")
            .slider("fov", "Угол", 30, 60, 30);
    // ВИЗУАЛ
    public static final Mod ARRAYLIST = add("arraylist", "Список модулей", "ВИЗУАЛ", true).desc("Включённое справа");
    public static final Mod TARGET = add("target", "Цель (панель)", "ВИЗУАЛ", true).desc("Ник и здоровье цели");
    public static final Mod CROSSHAIR = add("crosshair", "Свой прицел", "ВИЗУАЛ", false).desc("Форма и цвет прицела")
            .mode("style", "Вид", new String[]{"Крест", "Точка", "Рамка"}, 0)
            .slider("size", "Размер", 2, 12, 5)
            .slider("gap", "Зазор", 0, 8, 2)
            .slider("thick", "Толщина", 1, 3, 1)
            .mode("color", "Цвет", new String[]{"Тема", "Белый"}, 0);
    public static final Mod BRIGHTNESS = add("brightness", "Макс. яркость", "ВИЗУАЛ", false).desc("Максимум из настроек");
    public static final Mod THEME;

    static {
        THEME = new Mod("theme", "Тема", "ВИЗУАЛ", true, true)
                .desc("Цвет и фон меню")
                .mode("color", "Цвет", ACCENT_NAMES, 0)
                .slider("bg", "Фон", 20, 100, 70);
        ALL.add(THEME);
    }

    private static long lastAnimNs = System.nanoTime();

    /** Вызывать один раз за кадр: плавные переключатели модулей. */
    public static void tickAnim() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastAnimNs) / 1_000_000_000f);
        lastAnimNs = now;
        for (Mod m : ALL) {
            float target = m.enabled ? 1f : 0f;
            m.anim += (target - m.anim) * Math.min(1f, dt * 12f);
            if (Math.abs(target - m.anim) < 0.005f) m.anim = target;
        }
    }

    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int hsv(float h) {
        float s = 0.55f, v = 1f;
        float r, g, b;
        int i = (int) (h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** Цвет оформления. offset сдвигает фазу радуги. */
    public static int accent(int offset) {
        int idx = (int) THEME.setting("color").value;
        if (idx >= ACCENTS.length) {
            float h = ((System.currentTimeMillis() + offset * 90L) % 6000L) / 6000f;
            return hsv(h);
        }
        return ACCENTS[idx];
    }

    public static int panelColor() {
        int a = (int) (THEME.setting("bg").value / 100.0 * 255);
        return (a << 24) | 0x100C1C;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("koteclient.properties");
    }

    public static void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file())) {
            p.load(in);
        } catch (Exception ignored) { }
        for (Mod m : ALL) {
            if (!m.alwaysOn) m.enabled = Boolean.parseBoolean(p.getProperty(m.id + ".on", String.valueOf(m.enabled)));
            m.anim = m.enabled ? 1f : 0f;
            for (Setting s : m.settings) {
                try {
                    s.value = Double.parseDouble(p.getProperty(m.id + "." + s.id, String.valueOf(s.value)));
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (Mod m : ALL) {
            p.setProperty(m.id + ".on", String.valueOf(m.enabled));
            for (Setting s : m.settings) p.setProperty(m.id + "." + s.id, String.valueOf(s.value));
        }
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Kote Client");
        } catch (Exception ignored) { }
    }
}
