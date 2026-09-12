package com.clockworkparrot.blockmarker;

import arc.Core;
import arc.graphics.Color;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import static mindustry.Vars.control;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/** 标记管理面板：查看/重命名/换色/跳转/删除标记。 */
public class MarkerDialog extends BaseDialog {

    public MarkerDialog() {
        super("blockmarker");
        // 底部按钮只在构造期初始化一次：BaseDialog 已加默认关闭按钮，
        // 这里只补一个「全部清除」，之后每次打开不再 clear buttons，否则会清掉默认关闭按钮
        buttons.button("全部清除", Icon.trash, () -> {
            MarkStore.clearAll();
            rebuild();
        }).size(150f, 44f);
    }

    /** 先 rebuild 再显示，保证列表始终最新。 */
    public void open() {
        rebuild();
        Sounds.click.play(1f);
        show();
    }

    public void rebuild() {
        // 只清 cont（列表区），buttons 保持默认关闭按钮 + 全部清除按钮
        cont.clear();

        cont.table(h -> {
            h.add("方块标记保护").color(Color.scarlet).left();
            h.add("  共 " + MarkStore.marks.size + " 处").color(Color.gray).left().padLeft(6f);
        }).growX().row();

        cont.add(BlockMarkerMod.hintText()).color(Color.gray).left().padTop(4f).row();

        cont.pane(p -> {
            p.top();
            if (MarkStore.marks.isEmpty()) {
                p.add("暂无标记。用快捷键标记光标处的建筑，或点击下方按钮标记视野中心建筑。")
                    .color(Color.gray).pad(12f);
                return;
            }
            for (MarkStore.Mark m : MarkStore.marks.values()) {
                Tile t = world.tile(m.x, m.y);
                Building b = t == null ? null : t.build;
                p.table(Tex.pane, row -> buildRow(row, m, b)).growX().pad(4f).row();
            }
        }).grow().maxHeight(360f).row();

        cont.button("标记视野中心建筑", Icon.pencil, () -> {
            Tile t = world.tileWorld(Core.camera.position.x, Core.camera.position.y);
            BlockMarkerMod.toggleTile(t, true);
            rebuild();
        }).size(220f, 44f).padTop(6f).row();

        // 显示文字开关
        cont.check("显示标记文字", MarkStore.showLabel(), v -> {
            MarkStore.setShowLabel(v);
        }).left().padLeft(6f).row();

        // 打开更新页（v8 内嵌浏览器不一定可用，回退到系统浏览器）
        cont.button("检查更新 / 打开 GitHub Release", Icon.refresh, () -> {
            openUrl("https://github.com/ClockworkParrot/block-marker/releases/latest");
        }).size(280f, 44f).row();
    }

    private void buildRow(Table row, MarkStore.Mark m, Building b) {
        //颜色块（点击换色）
        row.button("", () -> cycleColor(m)).size(36f).pad(4f).get().color.set(m.color());

        //自定义名称（点击重命名）
        row.button(m.label, () -> rename(m)).growX().minWidth(90f).maxWidth(160f).pad(4f);

        //建筑名与坐标
        row.table(info -> {
            info.add(b == null ? "已失效" : b.block.localizedName)
                .color(b == null ? Color.scarlet : Color.lightGray).left();
            info.add("  " + m.x + "," + m.y).color(Color.gray).left();
        }).growX().pad(4f);

        //跳转
        row.button(Icon.move, Styles.clearNonei, () -> {
            if (b != null) control.input.panCamera(new Vec2(b.x, b.y));
        }).size(36f).pad(4f).disabled(b == null);

        //删除标记
        row.button(Icon.trash, Styles.clearNonei, () -> {
            MarkStore.remove(m);
            rebuild();
        }).size(36f).pad(4f);
    }

    private void cycleColor(MarkStore.Mark m) {
        int idx = 0;
        for (int i = 0; i < MarkStore.palette.length; i++) {
            if (MarkStore.palette[i].equals(m.color())) {
                idx = i;
                break;
            }
        }
        m.setColor(MarkStore.palette[(idx + 1) % MarkStore.palette.length]);
        MarkStore.save();
        Sounds.click.play(1f);
        rebuild();
    }

    private void rename(MarkStore.Mark m) {
        ui.showTextInput("重命名标记", "名称（留空则不显示文字）", 16, m.label, text -> {
            m.label = text == null ? "" : text;
            MarkStore.save();
            rebuild();
        });
    }

    /** 打开外链（回退到系统浏览器）。 */
    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (IOException | java.net.URISyntaxException e) {
            BlockMarkerMod.toast("打开失败：" + url);
        }
    }
}
