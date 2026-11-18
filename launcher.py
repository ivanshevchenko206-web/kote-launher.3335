import sys, json, os, shutil, subprocess, threading, traceback, urllib.parse, urllib.request, uuid, datetime
import tkinter as tk
from tkinter import ttk
import minecraft_launcher_lib as mll

MC_VERSION = "1.21.11"
BASE = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(os.path.abspath(__file__))
GAME_DIR = os.path.join(BASE, "game")
MODS_DIR = os.path.join(BASE, "mods")
CONFIG = os.path.join(BASE, "config.json")
LOG_FILE = os.path.join(BASE, "launcher.log")
OPT_MODS = ["fabric-api", "sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling"]

BG, CARD, FIELD = "#0f0b1a", "#181228", "#241b3a"
TXT, MUTED, ACCENT, ACCENT_HOVER = "#e8e0f8", "#8d82a8", "#b388ff", "#c9a8ff"
BTN, BTN_HOVER = "#7c4dff", "#9468ff"
DEFAULTS = {"nick": "Player", "ram": 4, "server": ""}


def load_cfg():
    cfg = dict(DEFAULTS)
    try:
        with open(CONFIG, encoding="utf-8") as f:
            cfg.update(json.load(f))
    except Exception:
        pass
    return cfg


def save_cfg(c):
    try:
        with open(CONFIG, "w", encoding="utf-8") as f:
            json.dump(c, f, ensure_ascii=False)
    except Exception:
        pass


def file_log(text):
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(f"[{datetime.datetime.now():%H:%M:%S}] {text}\n")
    except Exception:
        pass


def open_folder(path):
    os.makedirs(path, exist_ok=True)
    try:
        os.startfile(path)  # Windows
    except AttributeError:
        subprocess.Popen(["xdg-open", path])


class Launcher:
    def __init__(self):
        self.cfg = load_cfg()
        self.root = tk.Tk()
        self.root.title("Kote Client Launcher")
        self.root.geometry("520x640")
        self.root.resizable(False, False)
        self.root.configure(bg=BG)
        self.running = False
        self.build_ui()

    # ---------- UI ----------
    def button(self, parent, text, cmd, big=False):
        b = tk.Button(parent, text=text, command=cmd, bd=0, cursor="hand2", relief="flat",
                      bg=BTN if big else FIELD, fg="white", activebackground=BTN_HOVER if big else "#33284f",
                      activeforeground="white",
                      font=("Segoe UI", 14 if big else 10, "bold" if big else "normal"),
                      pady=12 if big else 6)
        base, hover = (BTN, BTN_HOVER) if big else (FIELD, "#33284f")
        b.bind("<Enter>", lambda e: b["state"] == "normal" and b.config(bg=hover))
        b.bind("<Leave>", lambda e: b["state"] == "normal" and b.config(bg=base))
        return b

    def field(self, parent, label, var):
        tk.Label(parent, text=label, fg=MUTED, bg=CARD, font=("Segoe UI", 9)).pack(anchor="w", padx=18, pady=(10, 2))
        e = tk.Entry(parent, textvariable=var, bg=FIELD, fg=TXT, insertbackground=TXT, bd=0,
                     font=("Segoe UI", 11), highlightthickness=1, highlightbackground=FIELD,
                     highlightcolor=ACCENT)
        e.pack(fill="x", padx=18, ipady=7)
        return e

    def build_ui(self):
        r = self.root
        # header
        head = tk.Frame(r, bg=BG); head.pack(pady=(22, 6))
        tk.Label(head, text="KOTE", font=("Segoe UI", 34, "bold"), fg=ACCENT, bg=BG).pack(side="left")
        tk.Label(head, text=" CLIENT", font=("Segoe UI", 34), fg=TXT, bg=BG).pack(side="left")
        tk.Label(r, text=f"Minecraft {MC_VERSION}  •  Fabric  •  Optimized", fg=MUTED, bg=BG,
                 font=("Segoe UI", 10)).pack()
        tk.Frame(r, bg=ACCENT, height=2, width=60).pack(pady=10)

        # settings card
        card = tk.Frame(r, bg=CARD); card.pack(fill="x", padx=24, pady=(4, 10))
        self.nick = tk.StringVar(value=self.cfg["nick"])
        self.server = tk.StringVar(value=self.cfg["server"])
        self.field(card, "НИК", self.nick)
        self.field(card, "СЕРВЕР (необязательно, например play.example.com)", self.server)

        row = tk.Frame(card, bg=CARD); row.pack(fill="x", padx=18, pady=(12, 14))
        tk.Label(row, text="ПАМЯТЬ (ОЗУ)", fg=MUTED, bg=CARD, font=("Segoe UI", 9)).pack(side="left")
        self.ram_label = tk.Label(row, text=f"{self.cfg['ram']} ГБ", fg=ACCENT, bg=CARD, font=("Segoe UI", 11, "bold"))
        self.ram_label.pack(side="right")
        self.ram = tk.Scale(card, from_=2, to=16, orient="horizontal", showvalue=False, bd=0, relief="flat",
                            bg=CARD, fg=TXT, troughcolor=FIELD, activebackground=ACCENT, highlightthickness=0,
                            sliderrelief="flat", command=lambda v: self.ram_label.config(text=f"{int(float(v))} ГБ"))
        self.ram.set(int(self.cfg["ram"]))
        self.ram.pack(fill="x", padx=18, pady=(0, 14))

        # progress
        st = ttk.Style(); st.theme_use("clam")
        st.configure("K.Horizontal.TProgressbar", troughcolor=FIELD, background=ACCENT, bordercolor=BG,
                     lightcolor=ACCENT, darkcolor=ACCENT, thickness=10)
        self.progress = ttk.Progressbar(r, style="K.Horizontal.TProgressbar", mode="determinate", maximum=100)
        self.progress.pack(fill="x", padx=24, pady=(4, 4))
        self.status = tk.Label(r, text="Готов к запуску", fg=MUTED, bg=BG, font=("Segoe UI", 10))
        self.status.pack()

        # play
        self.play_btn = self.button(r, "ИГРАТЬ", self.on_play, big=True)
        self.play_btn.pack(fill="x", padx=24, pady=(12, 8))

        # bottom buttons
        bar = tk.Frame(r, bg=BG); bar.pack(fill="x", padx=24)
        self.button(bar, "Папка игры", lambda: open_folder(GAME_DIR)).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self.button(bar, "Папка модов", lambda: open_folder(MODS_DIR)).pack(side="left", expand=True, fill="x", padx=4)
        self.button(bar, "Копировать лог", self.copy_log).pack(side="left", expand=True, fill="x", padx=(4, 0))

        # log
        self.logbox = tk.Text(r, height=6, bg="#0b0813", fg="#b9afd6", bd=0, font=("Consolas", 9),
                              wrap="word", highlightthickness=0)
        self.logbox.pack(fill="both", expand=True, padx=24, pady=(10, 14))

    # ---------- thread-safe UI helpers ----------
    def ui(self, fn):
        self.root.after(0, fn)

    def log(self, text):
        file_log(text)
        def f():
            self.logbox.insert("end", text + "\n"); self.logbox.see("end")
        self.ui(f)

    def set_status(self, text):
        self.ui(lambda: self.status.config(text=text))

    def set_progress(self, value, maximum):
        def f():
            self.progress.config(maximum=max(1, maximum)); self.progress["value"] = value
        self.ui(f)

    def copy_log(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.logbox.get("1.0", "end"))
        self.status.config(text="Лог скопирован, его можно вставить в чат")

    # ---------- actions ----------
    def on_play(self):
        if self.running:
            return
        nick = "".join(c for c in self.nick.get().strip() if c.isalnum() or c == "_")[:16] or "Player"
        self.nick.set(nick)
        self.cfg = {"nick": nick, "ram": int(self.ram.get()), "server": self.server.get().strip()}
        save_cfg(self.cfg)
        self.running = True
        self.play_btn.config(state="disabled", text="ЗАПУСК...", bg=FIELD)
        self.logbox.delete("1.0", "end")
        threading.Thread(target=self.run_game, daemon=True).start()

    def finish(self, ok):
        def f():
            self.running = False
            self.play_btn.config(state="normal", text="ИГРАТЬ", bg=BTN)
            self.status.config(text="Игра запущена. Приятной игры!" if ok else "Ошибка, смотри лог ниже")
        self.ui(f)

    def make_callback(self):
        state = {"max": 1}
        def set_max(m): state["max"] = m
        return {"setStatus": lambda s: self.set_status(s),
                "setMax": set_max,
                "setProgress": lambda p: self.set_progress(p, state["max"])}

    def download_opt_mods(self):
        mods_dir = os.path.join(GAME_DIR, "mods")
        os.makedirs(mods_dir, exist_ok=True)
        for i, slug in enumerate(OPT_MODS, 1):
            self.set_status(f"Моды: {slug} ({i}/{len(OPT_MODS)})")
            try:
                q = urllib.parse.urlencode({"game_versions": json.dumps([MC_VERSION]),
                                            "loaders": json.dumps(["fabric"])})
                req = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{q}",
                                             headers={"User-Agent": "KoteLauncher/0.2"})
                versions = json.load(urllib.request.urlopen(req, timeout=25))
                if not versions:
                    self.log(f"{slug}: нет версии для {MC_VERSION}, пропускаю"); continue
                f = next((x for x in versions[0]["files"] if x.get("primary")), versions[0]["files"][0])
                path = os.path.join(mods_dir, f["filename"])
                if not os.path.exists(path):
                    self.log(f"Скачиваю {slug}")
                    urllib.request.urlretrieve(f["url"], path)
            except Exception as e:
                self.log(f"{slug}: не скачался ({e})")

    def copy_kote_mod(self):
        mods_dir = os.path.join(GAME_DIR, "mods")
        os.makedirs(mods_dir, exist_ok=True)
        os.makedirs(MODS_DIR, exist_ok=True)
        # убираем старые копии нашего мода, чтобы не было дублей
        for n in os.listdir(mods_dir):
            if n.startswith("kote-client") and n.endswith(".jar"):
                os.remove(os.path.join(mods_dir, n))
        found = False
        for name in os.listdir(MODS_DIR):
            if name.endswith(".jar"):
                shutil.copy(os.path.join(MODS_DIR, name), mods_dir)
                self.log(f"Мод Kote Client: {name}")
                found = True
        if not found:
            self.log("Мод Kote Client не найден в папке mods рядом с лаунчером (запуск без него)")

    def run_game(self):
        try:
            os.makedirs(GAME_DIR, exist_ok=True)
            cb = self.make_callback()
            ram = self.cfg["ram"]

            # 1) Java. Нужна уже для установки Fabric, поэтому первой.
            self.set_status("Ставлю Java 21...")
            self.log("Java 21: установка (в первый раз долго)")
            mll.runtime.install_jvm_runtime("java-runtime-delta", GAME_DIR, callback=cb)
            java_exe = mll.runtime.get_executable_path("java-runtime-delta", GAME_DIR)
            if not java_exe or not os.path.exists(java_exe):
                raise RuntimeError(f"Java не найдена после установки: {java_exe}")
            os.environ["PATH"] = os.path.dirname(java_exe) + os.pathsep + os.environ.get("PATH", "")
            self.log(f"Java: {java_exe}")

            # 2) Fabric (скачает и саму игру)
            self.set_status(f"Ставлю Minecraft {MC_VERSION} и Fabric...")
            self.log("Minecraft и Fabric: установка")
            mll.fabric.install_fabric(MC_VERSION, GAME_DIR, callback=cb)
            vid = next(v["id"] for v in mll.utils.get_installed_versions(GAME_DIR)
                       if "fabric-loader" in v["id"] and v["id"].endswith(MC_VERSION))
            self.log(f"Версия: {vid}")

            # 3) Моды
            self.download_opt_mods()
            self.copy_kote_mod()

            # 4) Запуск
            opts = {"username": self.cfg["nick"],
                    "uuid": str(uuid.uuid3(uuid.NAMESPACE_DNS, "OfflinePlayer:" + self.cfg["nick"])),
                    "token": "", "executablePath": java_exe,
                    "jvmArguments": [f"-Xmx{ram}G", f"-Xms{max(1, ram // 2)}G"]}
            srv = self.cfg["server"]
            if srv:
                host, _, port = srv.partition(":")
                opts["server"] = host
                if port.isdigit():
                    opts["port"] = port
            cmd = mll.command.get_minecraft_command(vid, GAME_DIR, opts)
            self.set_status("Запускаю игру...")
            self.set_progress(100, 100)
            self.log("Запуск игры")
            subprocess.Popen(cmd, cwd=GAME_DIR, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            self.finish(True)
        except Exception as e:
            self.log(f"ОШИБКА: {e!r}")
            self.log(traceback.format_exc()[-1200:])
            self.finish(False)

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    Launcher().run()
