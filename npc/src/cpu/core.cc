/**
 * NPC Core - Verilator 仿真驱动
 *
 * 职责:
 *   1. 初始化 Verilator 生成的 VNpcCoreTop 模型
 *   2. 每次 npc_core_step() 驱动时钟, 等待 commit.valid 有效
 *   3. 将 commit 信息写回 Decode 结构体, 供 itrace/difftest 使用
 *   4. 同步寄存器状态到全局 cpu 结构体
 */

#include <cpu/core.h>

extern "C" {
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/decode.h>
#include <isa.h>
#include <memory/paddr.h>
}

#include "VNpcCoreTop.h"
#include <verilated.h>

#ifdef CONFIG_VERILATOR_TRACE
#include <verilated_vcd_c.h>
#endif

// Verilator 模型实例
static VNpcCoreTop *top = nullptr;
static VerilatedContext *ctx = nullptr;
IFDEF(CONFIG_VERILATOR_TRACE, static VerilatedVcdC* tfp = nullptr;)

// 最大时钟周期数 (防止死循环)
static constexpr uint64_t MAX_CYCLES_PER_STEP = 10000;

/**
 * 拉一个时钟周期: clock 0->1 (上升沿触发)
 */
static void tick() {
  // 下降沿
  top->clock = 0;
  top->eval();

  // 上升沿 (Chisel 默认在上升沿触发)
  top->clock = 1;
  top->eval();
}

/**
 * 复位序列: 拉高 reset 若干周期
 */
static void reset(int cycles = 5) {
  top->reset = 1;
  for (int i = 0; i < cycles; i++) {
    tick();
  }
  top->reset = 0;
}

extern "C" bool npc_core_init(int argc, char *argv[]) {
  // init VerilatedContext
  ctx = new VerilatedContext;
  ctx->commandArgs(argc, argv);

  // init top module
#if defined(CONFIG_VERILATOR_TRACE)
  Verilated::traceEverOn(true);
#endif
  top = new VNpcCoreTop(ctx);

#if defined(CONFIG_VERILATOR_TRACE)
  tfp = new VerilatedVcdC;
  top->trace(tfp, 99);  // 99 levels: 追踪所有层级的信号
  tfp->open("build/npc_core.vcd");
  Log("VCD trace enabled: build/npc_core.vcd");
#endif

  // 执行复位
  reset();
  Log("Verilator core initialized, reset complete");
  return true;
}

extern "C" void npc_core_fini(void) {
  if (top) {
    top->final();
    delete top;
    top = nullptr;
  }

  if (ctx) {
    delete ctx;
    ctx = nullptr;
  }

  Log("Verilator core finalized");
}

/**
 * 从 Verilator 模型读取 commit 信息, 写入 Decode 结构体
 */
static void read_commit_to_decode(Decode *s) {
  s->pc = top->io_commit_pc;
  s->dnpc = top->io_commit_nextPc;
  s->snpc = s->pc + 4; // 对于 RV32, 静态下一条指令地址
  s->isa.inst = top->io_commit_inst;
}

/**
 * 从 Verilator 模型同步 GPR 到全局 cpu 结构体
 */
static void sync_gpr_to_cpu() {
  // Verilator 生成的信号: io_commit_gpr_0 ~ io_commit_gpr_31
  cpu.gpr[0]  = top->io_commit_gpr_0;
  cpu.gpr[1]  = top->io_commit_gpr_1;
  cpu.gpr[2]  = top->io_commit_gpr_2;
  cpu.gpr[3]  = top->io_commit_gpr_3;
  cpu.gpr[4]  = top->io_commit_gpr_4;
  cpu.gpr[5]  = top->io_commit_gpr_5;
  cpu.gpr[6]  = top->io_commit_gpr_6;
  cpu.gpr[7]  = top->io_commit_gpr_7;
  cpu.gpr[8]  = top->io_commit_gpr_8;
  cpu.gpr[9]  = top->io_commit_gpr_9;
  cpu.gpr[10] = top->io_commit_gpr_10;
  cpu.gpr[11] = top->io_commit_gpr_11;
  cpu.gpr[12] = top->io_commit_gpr_12;
  cpu.gpr[13] = top->io_commit_gpr_13;
  cpu.gpr[14] = top->io_commit_gpr_14;
  cpu.gpr[15] = top->io_commit_gpr_15;
  cpu.gpr[16] = top->io_commit_gpr_16;
  cpu.gpr[17] = top->io_commit_gpr_17;
  cpu.gpr[18] = top->io_commit_gpr_18;
  cpu.gpr[19] = top->io_commit_gpr_19;
  cpu.gpr[20] = top->io_commit_gpr_20;
  cpu.gpr[21] = top->io_commit_gpr_21;
  cpu.gpr[22] = top->io_commit_gpr_22;
  cpu.gpr[23] = top->io_commit_gpr_23;
  cpu.gpr[24] = top->io_commit_gpr_24;
  cpu.gpr[25] = top->io_commit_gpr_25;
  cpu.gpr[26] = top->io_commit_gpr_26;
  cpu.gpr[27] = top->io_commit_gpr_27;
  cpu.gpr[28] = top->io_commit_gpr_28;
  cpu.gpr[29] = top->io_commit_gpr_29;
  cpu.gpr[30] = top->io_commit_gpr_30;
  cpu.gpr[31] = top->io_commit_gpr_31;
}

extern "C" bool npc_core_step(Decode *s) {
  if (!top) {
    return false;
  }

  // 拉高 step 信号, 触发单步执行
  top->io_step = 1;

  uint64_t cycles = 0;
  bool committed = false;

  // 驱动时钟, 等待 commit.valid
  while (!committed && cycles < MAX_CYCLES_PER_STEP) {
    tick();
    cycles++;

    if (top->io_commit_valid) {
      committed = true;
      read_commit_to_decode(s);
      sync_gpr_to_cpu();
    }
  }

  // 拉低 step
  top->io_step = 0;

  if (!committed) {
    Log("Core did not commit within %lu cycles", MAX_CYCLES_PER_STEP);
    return false;
  }

  // 更新全局 PC
  cpu.pc = s->dnpc;

  return true;
}
