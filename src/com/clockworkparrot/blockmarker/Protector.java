package com.clockworkparrot.blockmarker;

import arc.Events;
import arc.struct.Queue;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.input.InputHandler;
import mindustry.world.Tile;

/**
 * 防误拆核心逻辑：
 * 每帧扫描本地玩家的建造/拆除计划，凡是对“被标记建筑”的拆除计划都立即移除，
 * 使被标记的建筑无法被自己（拖拽框选拆除、点击拆除、排队拆除等常规途径）误删。
 */
public class Protector {

    public static void init() {
        Events.run(EventType.Trigger.update, Protector::update);
    }

    static void update() {
        if (Vars.state == null || Vars.state.isMenu()) return;
        InputHandler input = Vars.control.input;
        if (input != null) {
            filterSeq(input.selectPlans);
            filterSeq(input.linePlans);
        }
        if (Vars.player != null && Vars.player.unit() != null) {
            filterQueue(Vars.player.unit().plans());
        }
    }

    /** 该拆除计划是否命中被标记的建筑。 */
    private static boolean isBlockedPlan(BuildPlan p) {
        if (p == null || !p.breaking) return false;
        Tile t = Vars.world.tile(p.x, p.y);
        if (t == null || t.build == null) return false;
        return MarkStore.isProtected(t.build);
    }

    private static void filterSeq(Seq<BuildPlan> seq) {
        if (seq == null || seq.isEmpty()) return;
        boolean any = false;
        for (BuildPlan p : seq) {
            if (isBlockedPlan(p)) {
                any = true;
                break;
            }
        }
        if (!any) return;
        seq.removeAll(Protector::isBlockedPlan);
        MarkStore.warnBlocked();
    }

    private static void filterQueue(Queue<BuildPlan> q) {
        if (q == null || q.isEmpty()) return;
        boolean any = false;
        for (BuildPlan p : q) {
            if (isBlockedPlan(p)) {
                any = true;
                break;
            }
        }
        if (!any) return;
        Queue<BuildPlan> filtered = new Queue<>(Math.max(1, q.size));
        for (BuildPlan p : q) {
            if (!isBlockedPlan(p)) filtered.addLast(p);
        }
        Vars.player.unit().plans(filtered);
        MarkStore.warnBlocked();
    }
}
