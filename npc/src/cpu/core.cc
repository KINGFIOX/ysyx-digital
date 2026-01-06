#include <cpu/core.h>

extern "C" {
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/decode.h>
#include <isa.h>
}

// 默认使用解释器后端, 便于与 Verilator 后端切换
#ifndef CONFIG_VERILATOR_CORE

extern "C" bool npc_core_init(void) { return true; }

extern "C" void npc_core_fini(void) {}

extern "C" bool npc_core_step(Decode *s) {
  s->pc = cpu.pc;
  s->snpc = cpu.pc;
  isa_exec_once(s);
  cpu.pc = s->dnpc;
  return true;
}

#else

// 占位: 预留给 Verilator 后端, 当前仍使用解释器以保持可编译
static bool g_warned = false;

extern "C" bool npc_core_init(void) {
  g_warned = false;
  return true;
}

extern "C" void npc_core_fini(void) {}

extern "C" bool npc_core_step(Decode *s) {
  if (!g_warned) {
    Log("CONFIG_VERILATOR_CORE 已启用, 但 Verilator 后端尚未接好, 暂时退回解释器执行.");
    g_warned = true;
  }
  s->pc = cpu.pc;
  s->snpc = cpu.pc;
  isa_exec_once(s);
  cpu.pc = s->dnpc;
  return true;
}

#endif

