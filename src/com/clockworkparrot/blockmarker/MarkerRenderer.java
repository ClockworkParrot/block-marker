package com.clockworkparrot.blockmarker;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;

/** 渲染：为被标记的建筑绘制脉冲描边、中心菱形与自定义名称。 */
public class MarkerRenderer {

    public static void init() {
        Events.run(EventType.Trigger.draw, MarkerRenderer::draw);
    }

    static void draw() {
        if (Vars.state == null || Vars.state.isMenu() || MarkStore.marks.isEmpty()) return;
        boolean showLabel = MarkStore.showLabel();

        for (MarkStore.Mark m : MarkStore.marks.values()) {
            Tile t = Vars.world.tile(m.x, m.y);
            if (t == null || t.build == null) continue;
            Building b = t.build;
            float s = b.block.size * Vars.tilesize;
            Color c = m.color();
            float alpha = 0.55f + 0.3f * Mathf.absin(Time.time, 4f, 1f);

            Draw.color(c.r, c.g, c.b, alpha);
            Lines.stroke(2.5f);
            Lines.rect(b.x - s / 2f, b.y - s / 2f, s, s);
            Fill.square(b.x, b.y, 3f, 45f);

            if (showLabel && m.label != null && !m.label.isEmpty()) {
                Drawf.text(m.label, b.x, b.y + s / 2f + 8f, c);
            }
        }
        Draw.reset();
    }
}
