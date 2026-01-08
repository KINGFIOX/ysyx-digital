/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "../monitor/sdb/sdb.h"
#include "isa.h"
#include <cpu/cpu.h>
#include <cpu/decode.h>
#include <cpu/difftest.h>
#include <device/map.h>
#include <ftrace.h>
#include <locale.h>
#include <memory/vaddr.h>

/* The assembly code of instructions executed is only output to the screen
 * when the number of instructions executed is less than this value.
 * This is useful when you use the `si' command.
 * You can modify this value as you want.
 */
#define MAX_INST_TO_PRINT 10

CPU_state cpu = {};
uint64_t g_nr_guest_inst = 0;
static uint64_t g_timer = 0; // unit: us
static bool g_print_step = false;

void device_update();

static void trace_and_difftest(Decode *_this, vaddr_t dnpc) {
#ifdef CONFIG_ITRACE_COND
  if (ITRACE_COND) {
    log_write("%s\n", _this->logbuf);
  }
#endif
  if (g_print_step) {
    IFDEF(CONFIG_ITRACE, puts(_this->logbuf));
  }

#ifdef CONFIG_DIFFTEST
  difftest_step(_this->pc, dnpc);
#endif

#ifdef CONFIG_WATCHPOINT
  check_watchpoints();
#endif
}

#ifdef CONFIG_ITRACE
bool gen_logbuf(char *logbuf, size_t size, vaddr_t pc, vaddr_t snpc, const ISADecodeInfo *isa) {
  // 效果:
  // 0x80000000: 00 00 02 97 auipc   t0, 0
  char *p = logbuf;
  p += snprintf(p, size, FMT_WORD ":", pc);
  int ilen = snpc - pc;
  uint8_t *inst = (uint8_t *)&isa->inst;
  for (int i = ilen - 1; i >= 0; i--) {
    p += snprintf(p, 4, " %02x", inst[i]);
  }
  int ilen_max = 4;
  int space_len = ilen_max - ilen;
  if (space_len < 0)
    space_len = 0;
  space_len = space_len * 3 + 1;
  memset(p, ' ', space_len); // 打印一些空格, 用来对齐的
  p += space_len;

  bool ret = disassemble(p, size - (p - logbuf), pc, (uint8_t *)&isa->inst, ilen);
  if (!ret) {
    logbuf[0] = '\0';
    return false;
  }
  return true;
}
#endif

#ifdef CONFIG_ITRACE

typedef struct {
  vaddr_t pc;
  vaddr_t snpc;
  ISADecodeInfo isa;
} ItraceItem;

static struct {
  ItraceItem items[CONFIG_IRINGBUF_SIZE];
  size_t ptr;
  size_t count;
} g_iringbuf = {.ptr = 0, .count = 0};

#define LogInst(format, ...)                                                   \
  _Log(ANSI_FMT(format, ANSI_FG_BLUE) "\n", ##__VA_ARGS__)

static void dump_iringbuf(void) {
  if (g_iringbuf.count == 0)
    return;

  Log("Last %d instructions:", CONFIG_IRINGBUF_SIZE);
  char logbuf[128];
  const size_t valid = g_iringbuf.count;
  const size_t start =
      (g_iringbuf.ptr + CONFIG_IRINGBUF_SIZE - valid) % CONFIG_IRINGBUF_SIZE;

  for (size_t idx = 0; idx < valid; idx++) {
    size_t pos = (start + idx) % CONFIG_IRINGBUF_SIZE;
    bool ret =
        gen_logbuf(logbuf, sizeof(logbuf), g_iringbuf.items[pos].pc,
                   g_iringbuf.items[pos].snpc, &g_iringbuf.items[pos].isa);
    if (!ret)
      continue; // 理论不会失败

    if (idx == valid - 1) {
      LogInst("--> %s", logbuf);
    } else {
      LogInst("    %s", logbuf);
    }
  }
}

#endif

static void exec_once(Decode *s, vaddr_t pc /*always pc = cpu.pc*/) {
  s->pc = pc;   // record
  s->snpc = pc; // static next pc
  isa_exec_once(s);
  cpu.pc = s->dnpc;
}

static void execute(uint64_t n) {
  Decode s;
  for (; n > 0; n--) {
    exec_once(&s, cpu.pc);

#ifdef CONFIG_ITRACE
    // 生成日志(完整)
    bool ret = gen_logbuf(s.logbuf, sizeof(s.logbuf), s.pc, s.snpc, &s.isa);
    Assert(ret, "disassemble failed"); // 不可能失败
    // 最近的 CONFIG_IRINGBUF_SIZE 条指令
    g_iringbuf.items[g_iringbuf.ptr].isa = s.isa;
    g_iringbuf.items[g_iringbuf.ptr].pc = s.pc;
    g_iringbuf.items[g_iringbuf.ptr].snpc = s.snpc;
    if (g_iringbuf.count < CONFIG_IRINGBUF_SIZE) {
      g_iringbuf.count++;
    }
    g_iringbuf.ptr = (g_iringbuf.ptr + 1) % CONFIG_IRINGBUF_SIZE; // loop back
#endif

    g_nr_guest_inst++;
    trace_and_difftest(&s, cpu.pc);
    if (nemu_state.state != NEMU_RUNNING)
      break;
    IFDEF(CONFIG_DEVICE, device_update());
  }
}

static void statistic() {
  IFNDEF(CONFIG_TARGET_AM, setlocale(LC_NUMERIC, ""));
#define NUMBERIC_FMT MUXDEF(CONFIG_TARGET_AM, "%", "%'") PRIu64
  Log("host time spent = " NUMBERIC_FMT " us", g_timer);
  Log("total guest instructions = " NUMBERIC_FMT, g_nr_guest_inst);
  if (g_timer > 0)
    Log("simulation frequency = " NUMBERIC_FMT " inst/s",
        g_nr_guest_inst * 1000000 / g_timer);
  else
    Log("Finish running in less than 1 us and can not calculate the simulation "
        "frequency");
}

void assert_fail_msg() {
  isa_reg_display();
#ifdef CONFIG_ITRACE
  dump_iringbuf();
#endif
#ifdef CONFIG_MTRACE
  mtrace_dump();
#endif
#ifdef CONFIG_DTRACE
  dtrace_dump();
#endif
#ifdef CONFIG_FTRACE
  ftrace_dump();
#endif
#ifdef CONFIG_ETRACE
  etrace_dump();
#endif
  statistic();
}

/* Simulate how the CPU works. */
void cpu_exec(uint64_t n) {
  g_print_step = (n < MAX_INST_TO_PRINT);
  switch (nemu_state.state) {
  case NEMU_END:
  case NEMU_ABORT:
  case NEMU_QUIT:
    printf("Program execution has ended. To restart the program, exit NEMU and "
           "run again.\n");
    return;
  default:
    nemu_state.state = NEMU_RUNNING;
  }

  uint64_t timer_start = get_time();

  execute(n);

  uint64_t timer_end = get_time();
  g_timer += timer_end - timer_start;

  switch (nemu_state.state) {
  case NEMU_RUNNING:
    nemu_state.state = NEMU_STOP;
    break;

  case NEMU_END:
  case NEMU_ABORT:
    Log("nemu: %s at pc = " FMT_WORD,
        (nemu_state.state == NEMU_ABORT
             ? ANSI_FMT("ABORT", ANSI_FG_RED)
             : (nemu_state.halt_ret == 0
                    ? ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN)
                    : ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED))),
        nemu_state.halt_pc);
    // fall through
  case NEMU_QUIT:
    statistic();
  }
}
