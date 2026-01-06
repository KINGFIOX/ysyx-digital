#ifndef CHIPLAB_TESTBENCH_H
#define CHIPLAB_TESTBENCH_H

#include "common.h"
#include "emu.h"
#include "ram.h"
#include <chrono>
#include <verilated_fst_c.h>
#include <verilated_save.h>
#include <verilated_threads.h>
#include <verilated_vcd_c.h>

class CpuTestbench : CpuTool {
public:
  Emulator *emu; //
  CpuRam *ram;
  UARTSIM *uart;
  VerilatedVcdC *m_trace;

  /* control information to satisfy different waveform generation requirements
   */
  unsigned long dump_next_start;
  char break_once = 0;

  /* uart */
  unsigned int uart_config = 16;
  bool uart_div_set = false;
  bool div_reinit = false;
  unsigned int div_val_1 = 0;
  unsigned int div_val_2 = 0;
  unsigned int div_val_3 = 0;

  /*  */
  void save_model(vluint64_t main_time, const char *top_filename);
  void restore_model(vluint64_t *main_time, const char *top_filename);

  CpuTestbench(int argc, char **argv, char **env, vluint64_t *main_time);
  virtual ~CpuTestbench();

  /* called after simulation is over to display exit cause */
  void display_exit_cause(vluint64_t &main_time, int emask);

  /* simulate [significant function] */
  void simulate(vluint64_t &main_time);

  /* open waveform file */
  virtual void opentrace(const char *wavename);

  /* Close a trace file */
  virtual void close(void);

  std::chrono::nanoseconds total_nano_seconds = std::chrono::nanoseconds(0);

  /* Time passes */
  inline int eval(vluint64_t &main_time) {
    auto start = std::chrono::steady_clock::now();
    top->eval();
    auto end = std::chrono::steady_clock::now();
    std::chrono::nanoseconds elapsed_seconds =
        std::chrono::nanoseconds(end - start);
    total_nano_seconds += elapsed_seconds;

    char waveform_name[128];
    opentrace("./logs/simu_trace.vcd");
    printf("Dump Start at %ld ns\n", main_time);
    m_trace->dump(main_time);
    return Verilated::gotFinish();
  }
};

#endif // CHIPLAB_TESTBENCH_H
