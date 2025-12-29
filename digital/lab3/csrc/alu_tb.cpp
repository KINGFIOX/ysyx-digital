#include "verilated.h"
#include "verilated_vcd_c.h"
#include "Valu.h"

#include <iostream>

VerilatedContext *contextp = nullptr;
VerilatedVcdC *tfp = nullptr;
static Valu *top = nullptr;

static inline int sat4(int v) {
  v &= 0xF;            // 保留低 4 位
  if (v & 0x8) v -= 16; // 还原为有符号范围 [-8, 7], 8 -> -8, 9 -> -7, ...
  return v;
}

static void step_and_dump_wave() {
  top->eval();
  contextp->timeInc(1);
  tfp->dump(contextp->time());
}

static void sim_init() {
  contextp = new VerilatedContext;
  tfp = new VerilatedVcdC;
  top = new Valu;
  contextp->traceEverOn(true);
  top->trace(tfp, 0);
  tfp->open("alu_dump.vcd");
}

static void sim_exit() {
  step_and_dump_wave();
  tfp->close();
  delete top;
  delete tfp;
  delete contextp;
}

struct Expect {
  int y;
  int of;
  int zf;
};

// golden model
static Expect model_eval(int op, int a, int b) {
  Expect e{};
  a = sat4(a);
  b = sat4(b);
  switch (op) {
  case 0b000: { // add
    int sum = a + b;
    e.y = sat4(sum);
    e.of = ((a >= 0 && b >= 0 && e.y < 0) || (a < 0 && b < 0 && e.y >= 0));
    e.zf = (e.y == 0);
    break;
  }
  case 0b001: { // sub
    int diff = a - b;
    e.y = sat4(diff);
    e.of = ((a >= 0 && b < 0 && e.y < 0) || (a < 0 && b >= 0 && e.y >= 0));
    e.zf = (e.y == 0);
    break;
  }
  case 0b010:
    e.y = sat4(~a);
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  case 0b011:
    e.y = sat4(a & b);
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  case 0b100:
    e.y = sat4(a | b);
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  case 0b101:
    e.y = sat4(a ^ b);
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  case 0b110:
    e.y = (a < b) ? 1 : 0;
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  case 0b111:
    e.y = (a == b) ? 1 : 0;
    e.of = 0;
    e.zf = (e.y == 0);
    break;
  default:
    e.y = 0;
    e.of = 0;
    e.zf = 1;
    break;
  }
  return e;
}

int main() {
  sim_init();

  int errors = 0;

  for (int op = 0; op < 8; ++op) {
    for (int a = -8; a <= 7; ++a) {
      for (int b = -8; b <= 7; ++b) {
        Expect exp = model_eval(op, a, b);
        top->OP = op;
        top->A = a;
        top->B = b;
        step_and_dump_wave();

        int got_y = sat4(top->Y);
        int got_of = top->OF;
        int got_zf = top->ZF;

        if (got_y != exp.y || got_of != exp.of || got_zf != exp.zf) {
          ++errors;
          std::cout << "[FAIL] op=" << op << " a=" << a << " b=" << b
                    << " exp(y,of,zf)=" << exp.y << "," << exp.of << ","
                    << exp.zf << " got=" << got_y << "," << got_of << ","
                    << got_zf << "\n";
        }
      }
    }
  }

  if (errors == 0) {
    std::cout << "ALU all test vectors pass.\n";
  } else {
    std::cout << "ALU failed cases: " << errors << "\n";
  }

  sim_exit();
  return errors == 0 ? 0 : 1;
}

