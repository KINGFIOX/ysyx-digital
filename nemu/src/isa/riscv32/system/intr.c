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

#include "../local-include/reg.h"
#include "common.h"
#include <isa.h>
#ifdef CONFIG_ETRACE
#include <cpu/cpu.h>
#endif

#ifdef CONFIG_ETRACE
#include <utils/ringbuf.h>

#define ETRACE_BUF_SIZE 16

#define LogExc(format, ...)                                                   \
  _Log(ANSI_FMT(format, ANSI_FG_BLUE) "\n", ##__VA_ARGS__)

typedef struct {
  word_t cause;
  vaddr_t epc;
  vaddr_t handler;
  char type; // 'E' exception, 'I' interrupt, 'R' return
} EtraceItem;

static RINGBUF_DEFINE(EtraceItem, ETRACE_BUF_SIZE) etrace_buf = RINGBUF_INIT;

static const char *get_exception_name(word_t cause) {
  // RISC-V exception codes
  switch (cause) {
    case 0:  return "instruction_address_misaligned";
    case 1:  return "instruction_access_fault";
    case 2:  return "illegal_instruction";
    case 3:  return "breakpoint";
    case 4:  return "load_address_misaligned";
    case 5:  return "load_access_fault";
    case 6:  return "store_address_misaligned";
    case 7:  return "store_access_fault";
    case 8:  return "user_ecall";
    case 9:  return "supervisor_ecall";
    case 10: return "virtual_supervisor_ecall";
    case 11: return "machine_ecall";
    case 12: return "instruction_page_fault";
    case 13: return "load_page_fault";
    case 15: return "store_page_fault";
    default: return "unknown";
  }
}

static void etrace_push(char type, word_t cause, vaddr_t epc, vaddr_t handler) {
  RINGBUF_PUSH(etrace_buf, ETRACE_BUF_SIZE,
      ((EtraceItem){.cause = cause, .epc = epc, .handler = handler, .type = type}));
}

void etrace_dump(void) {
  if (RINGBUF_EMPTY(etrace_buf)) {
    return;
  }

  LogExc("Last %d exceptions/interrupts:", ETRACE_BUF_SIZE);
  RINGBUF_FOREACH(etrace_buf, ETRACE_BUF_SIZE, idx, pos) {
    const EtraceItem *it = RINGBUF_GET(etrace_buf, pos);
    if (it->type == 'R') {
      LogExc("    %c epc=" FMT_WORD " (return from exception/interrupt)",
          it->type, it->epc);
    } else {
      const char *name = get_exception_name(it->cause);
      LogExc("    %c cause=%d (%s) epc=" FMT_WORD " handler=" FMT_WORD,
          it->type, it->cause, name, it->epc, it->handler);
    }
  }
}
#endif

word_t isa_raise_intr(word_t NO, vaddr_t epc) {
#ifdef CONFIG_TRACE
  if ((sword_t)NO < 0) { // interrupt
    TODO();
  } else { // exception
#ifdef CONFIG_ETRACE
    etrace_push('E', NO, epc, csr(MTVEC));
#endif
  }
#endif
  /* Trigger an interrupt/exception with ``NO''.
   * Then return the address of the interrupt/exception vector.
   */
  csr(MCAUSE) = NO;
  csr(MEPC) = epc;
  return csr(MTVEC);
}

word_t isa_return_intr(void) {
  word_t mepc = csr(MEPC);
#ifdef CONFIG_TRACE
  if ((sword_t)csr(MCAUSE) < 0) { // interrupt
    TODO();
  } else { // exception
#ifdef CONFIG_ETRACE
    etrace_push('R', csr(MCAUSE), mepc, 0);
#endif
  }
#endif
  // TODO: mstatus
  return mepc;
}

word_t isa_query_intr() { return INTR_EMPTY; }
