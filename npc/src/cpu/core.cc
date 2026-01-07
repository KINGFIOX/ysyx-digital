/**
 * NPC Core - Verilator 仿真驱动
 *
 * 职责:
 *   1. 初始化 Verilator 生成的 VNpcCoreTop 模型
 *   2. 每次 npc_core_step() 驱动时钟, 等待 commit.valid 有效
 *   3. 将 commit 信息写回 Decode 结构体, 供 itrace/difftest 使用
 *   4. 同步寄存器状态到全局 cpu 结构体
 */

#include "debug.h"
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
#ifdef CONFIG_VERILATOR_TRACE
static VerilatedVcdC *tfp = nullptr;
static uint64_t sim_time = 0;  // 仿真时间, 用于波形 dump
#endif


/**
 * 拉一个时钟周期: clock 0->1 (上升沿触发)
 */
static void tick() {
  // 下降沿
  top->clock = 0;
  top->eval();
#ifdef CONFIG_VERILATOR_TRACE
  tfp->dump(sim_time++);
#endif

  // 上升沿 (Chisel 默认在上升沿触发)
  top->clock = 1;
  top->eval();
#ifdef CONFIG_VERILATOR_TRACE
  tfp->dump(sim_time++);
#endif
}

static void reset(int cycles = 5) {
  top->reset = 1;
  top->io_step = 0; // 拉低 step 信号
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
  top = new VNpcCoreTop(ctx);

// init trace
#if defined(CONFIG_VERILATOR_TRACE)
  Verilated::traceEverOn(true);
  tfp = new VerilatedVcdC;
  top->trace(tfp, 99);  // 99 levels: 追踪所有层级的信号
  tfp->open("build/npc_core.vcd");
  Log("VCD trace enabled: build/npc_core.vcd");
#endif

  reset(); // 执行复位
  Log("Verilator core initialized, reset complete");
  return true;
}

extern "C" void npc_core_flush_trace(void) {
#ifdef CONFIG_VERILATOR_TRACE
  if (tfp) {
    tfp->flush();
  }
#endif
}

extern "C" void npc_core_fini(void) {
#ifdef CONFIG_VERILATOR_TRACE
  if (tfp) {
    tfp->close();
    delete tfp;
    tfp = nullptr;
  }
#endif

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
  s->dnpc = top->io_commit_dnpc;
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
  // 单周期设计: step=1 时, 一个时钟周期内完成取指/译码/执行/写回

  // 拉高 step 信号
  top->io_step = 1;

  // eval() 让组合逻辑稳定, 此时可以读取当前 PC/指令
  top->eval();

  // 读取 commit 信息 (执行前的 PC/指令/GPR 状态)
  read_commit_to_decode(s);

  // 驱动一个时钟周期, 完成寄存器更新 (PC 和 GPR 写回)
  tick();

  // 同步写回后的 GPR 到 cpu 结构体
  sync_gpr_to_cpu();

  // 拉低 step 信号
  top->io_step = 0;

  // 更新全局 PC
  cpu.pc = s->dnpc;

  return true;
}
