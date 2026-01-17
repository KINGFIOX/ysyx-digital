/**
 * NPC Core - Verilator 仿真驱动
 *
 * 职责:
 *   1. 初始化 Verilator 生成的 VNpcCoreTop 模型
 *   2. 每次 npc_core_step() 驱动时钟, 等待 debug.valid 有效
 *   3. 将 debug 信息写回 Decode 结构体, 供 itrace/difftest 使用
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
#include "../isa/riscv32/local-include/reg.h"
}

#include "VNPCSoC.h"
#include <verilated.h>

#ifdef CONFIG_VERILATOR_TRACE
#include <verilated_vcd_c.h>
#endif

// Verilator 模型实例
static VNPCSoC *top = nullptr;
static VerilatedContext *ctx = nullptr;
#ifdef CONFIG_VERILATOR_TRACE
static VerilatedVcdC *tfp = nullptr;
static uint64_t sim_time = 0;  // 仿真时间, 用于波形 dump
#endif

static uint64_t ncycles = 0;

/// @brief 打一拍(寄存器更新)
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
  ncycles++; // 统计
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
  top = new VNPCSoC(ctx);

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
  Log("total cycles: %lu", ncycles);
}

/// @brief 读取 debug 信息, 写入 Decode 结构体
static void read_debug_to_decode(Decode *s) {
  s->pc = top->io_debug_pc;
  s->dnpc = top->io_debug_dnpc;
  s->snpc = s->pc + 4; // 对于 RV32, 静态下一条指令地址
  s->isa.inst = top->io_debug_inst;
}

/// @brief 软件维护了硬件的状态, 主要是为了方便 difftest and tracee
static void sync_gpr_to_cpu() {
  cpu.gpr[0]  = top->io_debug_gpr_0;
  cpu.gpr[1]  = top->io_debug_gpr_1;
  cpu.gpr[2]  = top->io_debug_gpr_2;
  cpu.gpr[3]  = top->io_debug_gpr_3;
  cpu.gpr[4]  = top->io_debug_gpr_4;
  cpu.gpr[5]  = top->io_debug_gpr_5;
  cpu.gpr[6]  = top->io_debug_gpr_6;
  cpu.gpr[7]  = top->io_debug_gpr_7;
  cpu.gpr[8]  = top->io_debug_gpr_8;
  cpu.gpr[9]  = top->io_debug_gpr_9;
  cpu.gpr[10] = top->io_debug_gpr_10;
  cpu.gpr[11] = top->io_debug_gpr_11;
  cpu.gpr[12] = top->io_debug_gpr_12;
  cpu.gpr[13] = top->io_debug_gpr_13;
  cpu.gpr[14] = top->io_debug_gpr_14;
  cpu.gpr[15] = top->io_debug_gpr_15;
  cpu.gpr[16] = top->io_debug_gpr_16;
  cpu.gpr[17] = top->io_debug_gpr_17;
  cpu.gpr[18] = top->io_debug_gpr_18;
  cpu.gpr[19] = top->io_debug_gpr_19;
  cpu.gpr[20] = top->io_debug_gpr_20;
  cpu.gpr[21] = top->io_debug_gpr_21;
  cpu.gpr[22] = top->io_debug_gpr_22;
  cpu.gpr[23] = top->io_debug_gpr_23;
  cpu.gpr[24] = top->io_debug_gpr_24;
  cpu.gpr[25] = top->io_debug_gpr_25;
  cpu.gpr[26] = top->io_debug_gpr_26;
  cpu.gpr[27] = top->io_debug_gpr_27;
  cpu.gpr[28] = top->io_debug_gpr_28;
  cpu.gpr[29] = top->io_debug_gpr_29;
  cpu.gpr[30] = top->io_debug_gpr_30;
  cpu.gpr[31] = top->io_debug_gpr_31;
}

static void sync_csr_to_cpu() {
  cpu.csr[MSTATUS] = top->io_debug_csr_mstatus;
  cpu.csr[MTVEC] = top->io_debug_csr_mtvec;
  cpu.csr[MEPC] = top->io_debug_csr_mepc;
  cpu.csr[MCAUSE] = top->io_debug_csr_mcause;
}

// extern "C" bool npc_core_step(Decode *s) {
//   top->io_step = 1; top->eval(); // 拉高 step 信号
//   read_debug_to_decode(s); // 组合逻辑的求值
//   cpu.pc = s->dnpc; //
//   tick(); // 更新寄存器
//   sync_gpr_to_cpu(); // 读出写入后的寄存器
//   sync_csr_to_cpu(); // 读出写入后的 CSR
//   top->io_step = 0; top->eval(); // 拉低 step 信号
//   return true;
// }

extern "C" bool npc_core_step(Decode *s) {
  top->io_step = 1;

  // 运行直到 debug.valid 为真
  const int MAX_CYCLES = 1000; // 防止死循环
  int cycles = 0;
  do {
    tick();
    cycles++;
    if (cycles >= MAX_CYCLES) {
      Log("Warning: npc_core_step exceeded %d cycles without debug_commit", MAX_CYCLES);
      return false;
    }
  } while (!top->io_debug_valid);

  // 读取 commit 信息
  read_debug_to_decode(s);
  cpu.pc = s->dnpc;
  sync_gpr_to_cpu();
  sync_csr_to_cpu();

  top->io_step = 0;
  top->eval();
  return true;
}
