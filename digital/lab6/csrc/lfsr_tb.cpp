#include "verilated.h"
#include "verilated_vcd_c.h"
#include "Vlfsr.h"

#include <cstdint>
#include <iostream>

// 全局仿真对象
static VerilatedContext *contextp = nullptr;
static VerilatedVcdC *tfp = nullptr;
static Vlfsr *top = nullptr;

// 时钟一步（0->1），并打出波形
static void tick() {
  top->clk = 0;
  top->eval();
  contextp->timeInc(1);
  tfp->dump(contextp->time());

  top->clk = 1;
  top->eval();
  contextp->timeInc(1);
  tfp->dump(contextp->time());
}

static void sim_init() {
  contextp = new VerilatedContext;
  tfp = new VerilatedVcdC;
  top = new Vlfsr;
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("lfsr_dump.vcd");
}

static void sim_exit() {
  tick(); // 留下最后状态
  tfp->close();
  delete top;
  delete tfp;
  delete contextp;
}

// 参考模型：与 lfsr.v 相同的更新逻辑
static uint8_t model_next(uint8_t q) {
  uint8_t x8 = ((q >> 4) & 1) ^ ((q >> 3) & 1) ^ ((q >> 2) & 1) ^ (q & 1);
  return static_cast<uint8_t>((x8 << 7) | (q >> 1));
}

int main() {
  sim_init();

  // 复位：置 rst=1，一个上升沿后 q 应为 0x01
  top->rst = 1;
  tick();
  if (top->q != 0x01) {
    std::cout << "[FAIL] reset q mismatch, got 0x" << std::hex << int(top->q)
              << " exp 0x01\n";
    sim_exit();
    return 1;
  }

  // 释放复位
  top->rst = 0;
  uint8_t model_q = 0x01; // 与硬件当前状态保持一致

  int errors = 0;
  const int cycles = 260; // 覆盖满周期(理论 255)外加若干余量

  for (int i = 0; i < cycles; ++i) {
    uint8_t exp_q = model_next(model_q); // model_q 表示: 上一周期的 q 值
    model_q = exp_q;

    tick();

    uint8_t got = static_cast<uint8_t>(top->q);
    if (got != exp_q) {
      ++errors;
      std::cout << "[FAIL] cycle=" << std::dec << (i + 1)
                << " exp=0x" << std::hex << int(exp_q)
                << " got=0x" << int(got) << "\n";
    }

    // 附加检查：序列不应进入全 0
    if (got == 0x00) {
      ++errors;
      std::cout << "[FAIL] sequence hit 0 at cycle=" << std::dec << (i + 1)
                << "\n";
      break;
    }
  }

  if (errors == 0) {
    std::cout << "LFSR test passed for " << cycles << " cycles.\n";
  } else {
    std::cout << "LFSR failed cases: " << errors << "\n";
  }

  sim_exit();
  return errors == 0 ? 0 : 1;
}

