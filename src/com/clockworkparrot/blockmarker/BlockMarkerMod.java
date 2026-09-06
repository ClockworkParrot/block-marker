package com.clockworkparrot.blockmarker;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.ui.TextButton;
import arc.struct.ObjectMap;
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

    @Override
    public void init() {
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
                });
            });
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
