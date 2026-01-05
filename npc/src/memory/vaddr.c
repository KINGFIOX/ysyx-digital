/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NPC is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <isa.h>
#include <memory/paddr.h>
#ifdef CONFIG_MTRACE
#include <cpu/cpu.h>
#endif

#ifdef CONFIG_MTRACE
#define MTRACE_BUF_SIZE 16

#define LogMem(format, ...)                                                   \
  _Log(ANSI_FMT(format, ANSI_FG_BLUE) "\n", ##__VA_ARGS__)

typedef struct {
  vaddr_t addr;
  int len;
  word_t data;
  char type; // 'I' fetch, 'R' read, 'W' write
  word_t pc;
} MtraceItem;

static struct {
  MtraceItem items[MTRACE_BUF_SIZE];
  size_t ptr;
  size_t count;
} mtrace_buf = {.ptr = 0, .count = 0};

static void mtrace_push(char type, vaddr_t addr, int len, word_t data, word_t pc) {
  mtrace_buf.items[mtrace_buf.ptr] = (MtraceItem){.addr = addr, .len = len, .data = data, .type = type, .pc = pc};
  if (mtrace_buf.count < MTRACE_BUF_SIZE) {
    mtrace_buf.count++;
  }
  mtrace_buf.ptr = (mtrace_buf.ptr + 1) % MTRACE_BUF_SIZE;
}

void mtrace_dump(void) {
  if (mtrace_buf.count == 0) {
    return;
  }

  LogMem("Last %d memory accesses:", MTRACE_BUF_SIZE);
  const size_t valid = mtrace_buf.count;
  const size_t start = (mtrace_buf.ptr + MTRACE_BUF_SIZE - valid) % MTRACE_BUF_SIZE;

  for (size_t idx = 0; idx < valid; idx++) {
    size_t pos = (start + idx) % MTRACE_BUF_SIZE;
    const MtraceItem *it = &mtrace_buf.items[pos];
    LogMem("    %c pc=" FMT_WORD " addr=" FMT_WORD " len=%d data=" FMT_WORD,
        it->type, it->pc, it->addr, it->len, it->data);
  }
}
#endif

word_t vaddr_ifetch(vaddr_t addr, int len) {
  return paddr_read(addr, len);
}

word_t vaddr_read(vaddr_t addr, int len) {
  word_t data = paddr_read(addr, len);
#ifdef CONFIG_MTRACE
  if (CONFIG_MTRACE_COND) {
    mtrace_push('R', addr, len, data, cpu.pc);
  }
#endif
  return data;
}

void vaddr_write(vaddr_t addr, int len, word_t data) {
#ifdef CONFIG_MTRACE
  if (CONFIG_MTRACE_COND) {
    mtrace_push('W', addr, len, data, cpu.pc);
  }
#endif
  paddr_write(addr, len, data);
}
