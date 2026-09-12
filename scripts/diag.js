// 诊断脚本：逐个测试 API，输出哪个存在
print("[1] Vars.player=" + Vars.player);
print("[2] Vars.player.unit()=" + Vars.player.unit());
print("[3] Vars.control=" + Vars.control);
print("[4] Vars.control.player=" + Vars.control.player);
print("[5] Vars.control.playerUnit=" + Vars.control.playerUnit);
print("[6] Block.titanium-melter=" + Block.titanium-melter);
print("[7] Block.copper-wall=" + Block.copper-wall);
print("[8] Tiles.get type=" + typeof Tiles.get);
print("[9] Vars.state.teams=" + Vars.state.teams);
print("[10] Vars.state.teams.get(0)=" + Vars.state.teams.get(0));

if (Vars.player.unit()) {
  print("[11] unit.tile()=" + Vars.player.unit().tile());
  print("[12] unit.tileOn()=" + Vars.player.unit().tileOn());
  print("[13] unit.x=" + Vars.player.unit().x);
}
if (Vars.control.player) {
  print("[14] control.player.unit()=" + Vars.control.player.unit());
}
