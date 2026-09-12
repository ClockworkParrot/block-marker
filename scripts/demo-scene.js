// 误拆演示场景生成脚本（Mindustry 控制台 F8 执行）
// 用法：进关卡 → 设置启用控制台 → 按 F8 → 粘贴整段 → 回车

var t = Vars.player.team();
var unit = Vars.player.unit();
if (!unit) { print("请先控制一个单位再执行"); }
else {

  // === 定位：用玩家单位所在瓦片为原点 ===
  var core = unit.tile();
  if (!core) { print("当前不在瓦片上（在水上或无单位）"); }
  else {

    var cx = core.x, cy = core.y;

    function safe(block, x, y) {
      var tt = Tiles.get(x, y);
      if (!tt || tt.build) return;
      try { Tiles.create(block, x, y, t); }
      catch (e) {}
    }

    // === 主生产线（中央水平线，要标记保护）===
    safe(Block.titanium-melter,  cx + 4,  cy);
    safe(Block.surge-smelter,    cx + 8,  cy);
    safe(Block.aerator,          cx + 12, cy);
    safe(Block.cryofluid-mixer,  cx + 16, cy);

    // === 周围密集杂建筑（容易误触）===
    for (var dx = -4; dx <= 19; dx++) {
      for (var dy = -4; dy <= 4; dy++) {
        if (dy === 0 && (dx === 4 || dx === 8 || dx === 12 || dx === 16)) continue;
        var pick;
        if (dx < 2)       pick = Block.copper-wall;
        else if (dx < 4)  pick = Block.conveyor;
        else if (dx < 8)  pick = Block.sorter;
        else if (dx < 12) pick = Block.copper-wall;
        else if (dx < 16) pick = Block.distillery;
        else              pick = Block.copper-wall;
        safe(pick, cx + dx, cy + dy);
      }
    }

    print("OK: 场景已生成。");
    print("中央 4 台生产建筑要保护，周围密集杂建筑易误触。");
    print("先按 G 标记 4 台生产建筑，再框选拆除试试。");
  }
}
