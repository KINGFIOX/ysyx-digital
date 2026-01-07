#include <memory/paddr.h>
#include <cpu/cpu.h>

// 检查地址是否在有效的物理内存范围内
static inline bool addr_in_pmem(paddr_t addr) {
  return addr >= CONFIG_MBASE && addr < CONFIG_MBASE + CONFIG_MSIZE;
}

int pmem_read_dpi(int en, int addr, int len) {
  // en=0 或地址无效时直接返回 0，避免 Verilator 初始化阶段的越界检查
  if (!en || !addr_in_pmem((paddr_t)addr))
    return 0;
  return (int)paddr_read((paddr_t)addr, len);
}

void pmem_write_dpi(int en, int addr, int len, int data) {
  // en=0 或地址无效时直接返回
  if (!en || !addr_in_pmem((paddr_t)addr))
    return;
  paddr_write((paddr_t)addr, len, (word_t)data);
}

void ebreak_dpi(int en, int pc, int a0) {
  if (!en)
    return;
  NPCTRAP((vaddr_t)pc, a0);
}

void invalid_inst_dpi(int en, int pc, int inst) {
  if (!en)
    return;
  INV((vaddr_t)pc);
}
