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

#ifndef __RISCV_REG_H__
#define __RISCV_REG_H__

#include "debug.h"
#include "isa.h"
#include <common.h>

enum {
  MSTATUS = 0x0300,
  MTVEC = 0x0305,
  MEPC = 0x0341,
  MCAUSE = 0x0342,
  MCYCLE = 0x0B00,
  MCYCLEH = 0x0B80,
  MVENDORID = 0x0F11,
  MARCHID   = 0x0F12,
};

static inline int check_gpr_idx(int idx) {
  IFDEF(CONFIG_RT_CHECK, assert(idx >= 0 && idx < MUXDEF(CONFIG_RVE, 16, 32)));
  return idx;
}
#define gpr(idx) (cpu.gpr[check_gpr_idx(idx)])

static inline int check_csr_idx(int idx) {
  IFDEF(CONFIG_RT_CHECK, Assert(
    idx == MTVEC
    || idx == MSTATUS
    || idx == MEPC
    || idx == MCAUSE
    || idx == MVENDORID
    || idx == MARCHID
    || idx == MCYCLE
    || idx == MCYCLEH,
    "invalid csr index: %d", idx)
  );
  return idx;
}

static inline word_t csr_read(int idx) {
  idx &= 0xfff;
  if (idx == MSTATUS) { return 0x1800; }
  idx = check_csr_idx(idx);
  return cpu.csr[idx];
}

static inline void csr_write(int idx, word_t value) {
  idx &= 0xfff;
  if (idx == MSTATUS) { return; } // 只有机器模式
  if (idx == MVENDORID || idx == MARCHID) { return; } // 只读 csr
  idx = check_csr_idx(idx);
  cpu.csr[idx] = value;
}


static inline const char *reg_name(int idx) {
  extern const char *regs[];
  return regs[check_gpr_idx(idx)];
}

#endif
