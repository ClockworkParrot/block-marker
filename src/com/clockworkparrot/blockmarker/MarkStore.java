package com.clockworkparrot.blockmarker;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.world.Tile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * 标记存储：按建筑“锚点方块”坐标（build.tileOn()）记录标记。
 * 标记与具体地图绑定，通过设置项持久化（存档/重启后自动恢复）。
 */
public class MarkStore {
    /** 单个标记。 */
    public static class Mark {
        public int x, y;
        public String label = "";
        public String colorHex = "#ffa500";
        public transient Color color = new Color(Color.orange);

        public Mark() {
        }

        Mark(int x, int y, String label, Color color) {
            this.x = x;
            this.y = y;
            this.label = label;
            setColor(color);
        }

        public void setColor(Color c) {
            this.color = c.cpy();
            this.colorHex = c.toString();
        }

        public Color color() {
            if (color == null) color = Color.valueOf(colorHex);
            return color;
        }
    }

    public static final Color[] palette = {
        Color.orange, Color.scarlet, Color.lime, Color.cyan,
        Color.purple, Color.yellow, Color.white, Color.pink
    };

    public static final IntMap<Mark> marks = new IntMap<>();

    private static int colorIndex;

    /** 初始化：挂接事件（读档加载、清理失效标记）。 */
    public static void init() {
        Events.on(EventType.WorldLoadEvent.class, e -> load());
        Events.on(EventType.ResetEvent.class, e -> marks.clear());
        //方块变化后清理悬空标记（建筑被炸毁/正常拆除时坐标上不再有建筑）
        Events.on(EventType.TileChangeEvent.class, e -> {
            if (marks.isEmpty()) return;
            Tile t = e.tile;
            if (t == null) return;
            Mark m = marks.get(Point2.pack(t.x, t.y));
            if (m != null && t.build == null) {
                marks.remove(Point2.pack(t.x, t.y));
                save();
            }
        });
    }

    /** 某建筑是否被标记保护（仅保护本方建筑，敌方标记不生效）。 */
    public static boolean isProtected(Building b) {
        Mark m = get(b);
        if (m == null) return false;
        //只拦截拆自己人的操作；战棋图中误标记的敌方建筑不受保护
        return b.team == Vars.player.team();
    }

    /** 获取建筑的标记，未标记返回 null。 */
    public static Mark get(Building b) {
        if (b == null) return null;
        Tile anchor = b.tileOn();
        return anchor == null ? null : marks.get(Point2.pack(anchor.x, anchor.y));
    }

    /** 标记/取消标记一个建筑，返回新标记（取消时返回 null）。 */
    public static Mark toggle(Building b) {
        Tile anchor = b.tileOn();
        if (anchor == null) return null;
        int key = Point2.pack(anchor.x, anchor.y);
        Mark existing = marks.get(key);
        if (existing != null) {
            marks.remove(key);
            save();
            return null;
        }
        colorIndex = (colorIndex + 1) % palette.length;
        Mark m = new Mark(anchor.x, anchor.y,
            b.block.localizedName + " #" + (marks.size + 1), palette[colorIndex]);
        marks.put(key, m);
        save();
        return m;
    }

    public static void remove(Mark m) {
        marks.remove(Point2.pack(m.x, m.y));
        save();
    }

    public static void clearAll() {
        marks.clear();
        save();
    }

    public static Seq<Mark> list() {
        Seq<Mark> out = new Seq<>();
        for (Mark m : marks.values()) out.add(m);
        return out;
    }

    private static String settingsKey() {
        //按地图名 + 世界尺寸区分，同一张图的标记自动恢复
        return "blockmarker.marks." + Vars.state.map.plainName() + "_" + Vars.world.width() + "x" + Vars.world.height();
    }

    /** 保存到设置文件（随存档无关的全局配置，键与地图绑定）。 */
    public static void save() {
        if (Vars.state == null || Vars.state.isMenu() || Vars.state.map == null) return;
        StringBuilder sb = new StringBuilder();
        for (Mark m : marks.values()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(m.x).append(',').append(m.y).append(',')
                .append(URLEncoder.encode(m.label == null ? "" : m.label, StandardCharsets.UTF_8))
                .append(',').append(m.colorHex);
        }
        Core.settings.put(settingsKey(), sb.toString());
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        marks.clear();
        String raw = Core.settings.getString(settingsKey(), "");
        if (raw.isEmpty()) return;
        for (String part : raw.split("\\|")) {
            String[] sp = part.split(",", 4);
            if (sp.length < 4) continue;
            try {
                int x = Integer.parseInt(sp[0]), y = Integer.parseInt(sp[1]);
                Tile t = world.tile(x, y);
                if (t == null || t.build == null) continue; //位置上已无建筑，丢弃
                Mark m = new Mark();
                m.x = x;
                m.y = y;
                m.label = java.net.URLDecoder.decode(sp[2], StandardCharsets.UTF_8);
                m.colorHex = sp[3];
                marks.put(Point2.pack(x, y), m);
            } catch (Exception ignored) {
            }
        }
    }

    /** 提示一条 HUD 消息（节流，避免刷屏）。 */
    static void warnBlocked() {
        long now = Time.millis();
        if (now - lastWarn < 2000) return;
        lastWarn = now;
        if (!Vars.headless && ui != null && ui.hudfrag != null) {
            ui.hudfrag.showToast(Icon.cancel, "已阻止拆除受保护的建筑（可在标记面板中取消保护）");
        }
        Sounds.uiNotify.play(1f);
    }

    private static long lastWarn;
}
