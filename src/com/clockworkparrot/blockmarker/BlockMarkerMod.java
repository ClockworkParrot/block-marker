package com.clockworkparrot.blockmarker;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.ui.TextButton;
import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.world.Tile;

import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * 方块标记保护 BlockMarker
 *
 * 功能：给建筑添加自定义标记（可重命名、可换色），被标记的建筑不会被自己误拆除。
 * - 默认 G 键：标记/取消光标处的建筑
 * - 默认 H 键：打开标记管理面板
 * - 按键可在 设置 -> 方块标记保护 中重新绑定
 * - 标记按地图自动保存，重新进入同一张地图时恢复
 */
public class BlockMarkerMod extends Mod {

    public static final String KEY_MARK = "blockmarker.keyMark";
    public static final String KEY_PANEL = "blockmarker.keyPanel";
    public static final KeyCode DEFAULT_MARK = KeyCode.g;
    public static final KeyCode DEFAULT_PANEL = KeyCode.h;

    private static MarkerDialog dialog;

    //快捷键重绑定
    private static String captureSetting;
    private static final ObjectMap<String, TextButton> captureButtons = new ObjectMap<>();

    //G 键按住连续标记（同格节流）
    private static long lastMarkMillis;
    private static Tile lastMarked;

    @Override
    public void init() {
        // 记录 mod.hjson 路径（实例方法在静态工具方法里用）
        setConfig(getConfig());
        MarkStore.init();
        Protector.init();
        MarkerRenderer.init();

        //主循环（键位监听、按键捕获）
        Events.run(EventType.Trigger.update, BlockMarkerMod::handleUpdate);

        if (!Vars.headless) {
            //ui 在内容初始化后才可用，这里延迟注册 HUD 按钮与设置分类
            Events.on(EventType.ContentInitEvent.class, e -> {
                if (ui == null || ui.settings == null) return;
                ui.hudGroup.fill(hud -> {
                    hud.table(btns -> {
                        btns.button(Icon.pencil, Styles.clearNonei, BlockMarkerMod::openPanel).size(44f);
                    }).bottom().left().pad(8f);
                });
                ui.settings.addCategory("方块标记保护", Icon.pencil, t -> {
                    t.add("标记保护可防止建筑被自己误拆除。点击下方按钮后按任意键重新绑定，Esc 取消。")
                        .color(Color.gray).growX().wrap().pad(4f).row();
                    captureButtons.clear();
                    keyRow(t, KEY_MARK, "标记/取消标记（光标处）");
                    keyRow(t, KEY_PANEL, "打开标记管理面板");
                    refreshCaptureButtons();
                    // 启动检查更新开关
                    t.check("启动时检查更新", MarkStore.checkUpdate(), v -> {
                        Core.settings.put(MarkStore.SET_CHECK_UPDATE, v);
                    }).left().padLeft(6f).row();
                });
            });

            // 启动时静默检查更新
            if (MarkStore.checkUpdate()) {
                checkUpdate();
            }
        }
    }

    //---------- 主循环 ----------

    private static void handleUpdate() {
        handleCapture();

        if (Vars.state == null || Vars.state.isMenu()) return;
        if (Core.scene == null || Core.scene.hasField() || Core.scene.hasDialog()) return;

        if (Core.input.keyTap(keyMark())) {
            Tile t = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            toggleTile(t, true);
        }
        //按住 G 连续标记：光标移动到新建筑时每帧标记（同格节流，避免疯狂提示）
        if (Core.input.keyDown(keyMark()) && !Core.input.keyTap(keyMark())) {
            Tile t = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            long now = Time.millis();
            if (t != lastMarked && t != null && t.build != null && t.build.team == Vars.player.team()
                && MarkStore.get(t.build) == null && now - lastMarkMillis >= 60) {
                lastMarked = t;
                lastMarkMillis = now;
                toggleTile(t, false);
            }
        } else if (!Core.input.keyDown(keyMark())) {
            lastMarked = null;
        }
        if (Core.input.keyTap(keyPanel())) {
            openPanel();
        }
    }

    //---------- 标记操作 ----------

    /** 切换某方块上建筑的标记状态。 */
    public static void toggleTile(Tile t, boolean sound) {
        if (t == null || t.build == null) {
            toast("此处没有建筑");
            return;
        }
        Building b = t.build;
        if (b.team != Vars.player.team()) {
            toast("只能标记本方建筑");
            return;
        }
        MarkStore.Mark old = MarkStore.get(b);
        MarkStore.Mark m = MarkStore.toggle(b);
        if (m != null) {
            toast("已标记保护：" + m.label);
        } else {
            toast("已取消保护：" + (old == null ? "" : old.label));
        }
        if (sound) Sounds.click.play(1f);
    }

    public static void openPanel() {
        if (dialog == null) dialog = new MarkerDialog();
        dialog.open();
    }

    /** 启动时异步检查更新（不阻塞主线程）。 */
    public static void checkUpdate() {
        try {
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(
                        "https://api.github.com/repos/ClockworkParrot/block-marker/releases/latest");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestProperty("User-Agent", "BlockMarker");
                    if (c.getResponseCode() != 200) return;
                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(c.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    String body = sb.toString();
                    int i = body.indexOf("\"tag_name\"");
                    if (i < 0) return;
                    int q1 = body.indexOf('"', i + 12), q2 = body.indexOf('"', q1 + 1);
                    if (q1 < 0 || q2 < 0) return;
                    String tag = body.substring(q1 + 1, q2);
                    String local = version();
                    if (local != null && tag.equals(local)) return;
                    if (!Vars.headless) {
                        Time.run(0f, () -> toast("发现新版本 " + tag + "（当前 " + local + "），可到 GitHub Release 下载"));
                    }
                } catch (Exception ignored) {
                }
            }, "blockmarker-update-check").start();
        } catch (Exception ignored) {
        }
    }

    /** 从 mod.hjson 读取当前版本。 */
    public static String version() {
        try {
            arc.files.Fi cfg = configFile;
            if (cfg != null && cfg.exists()) {
                String txt = cfg.readString("UTF-8");
                for (String line : txt.split("\\R")) {
                    String s = line.trim();
                    if (s.startsWith("version:")) {
                        return s.substring("version:".length()).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static arc.files.Fi configFile;

    /** 实例侧记录 mod.hjson 路径。 */
    static void setConfig(arc.files.Fi f) {
        configFile = f;
    }

    static void toast(String msg) {
        if (!Vars.headless && ui != null && ui.hudfrag != null) {
            ui.hudfrag.showToast(msg);
        }
    }

    //---------- 设置 ----------

    private static void keyRow(arc.scene.ui.layout.Table t, String setting, String name) {
        TextButton b = t.button(name + ": " + keyLabel(setting), () -> {
            captureSetting = setting.equals(captureSetting) ? null : setting;
            refreshCaptureButtons();
        }).growX().pad(4f).get();
        captureButtons.put(setting, b);
    }

    private static void handleCapture() {
        if (captureSetting == null) return;
        if (Core.input.keyTap(KeyCode.escape)) {
            captureSetting = null;
            refreshCaptureButtons();
            return;
        }
        for (KeyCode kc : KeyCode.values()) {
            if (Core.input.keyTap(kc)) {
                Core.settings.put(captureSetting, kc.name());
                Core.settings.forceSave();
                captureSetting = null;
                refreshCaptureButtons();
                break;
            }
        }
    }

    private static void refreshCaptureButtons() {
        for (ObjectMap.Entry<String, TextButton> e : captureButtons.entries()) {
            TextButton b = e.value;
            if (b == null) continue;
            if (captureSetting != null && captureSetting.equals(e.key)) {
                b.setText("按任意键…（Esc 取消）");
            } else {
                b.setText(settingName(e.key) + ": " + keyLabel(e.key));
            }
        }
    }

    //---------- 按键辅助 ----------

    private static final ObjectMap<String, String> settingNames = ObjectMap.of(
        KEY_MARK, "标记/取消标记（光标处）",
        KEY_PANEL, "打开标记管理面板"
    );

    private static String settingName(String setting) {
        return settingNames.get(setting, setting);
    }

    public static KeyCode keyMark() {
        return key(KEY_MARK, DEFAULT_MARK);
    }

    public static KeyCode keyPanel() {
        return key(KEY_PANEL, DEFAULT_PANEL);
    }

    private static KeyCode key(String setting, KeyCode def) {
        try {
            return KeyCode.valueOf(Core.settings.getString(setting, def.name()));
        } catch (Exception e) {
            return def;
        }
    }

    private static String keyLabel(String setting) {
        KeyCode def = setting.equals(KEY_MARK) ? DEFAULT_MARK : DEFAULT_PANEL;
        return keyName(key(setting, def));
    }

    public static String keyName(KeyCode kc) {
        String n = kc.name();
        return n.length() == 1 ? n.toUpperCase() : n;
    }

    public static String hintText() {
        return "快捷键：" + keyName(keyMark()) + " 标记/取消光标处建筑 · "
            + keyName(keyPanel()) + " 打开本面板（可在游戏设置中修改）";
    }
}
