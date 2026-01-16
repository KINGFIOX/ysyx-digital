#include <memory/paddr.h>
#include <cpu/cpu.h>


int pmem_read_dpi(int en, int addr, int len) {
  if (!en) return 0;
  return (int)paddr_read((paddr_t)addr, len);
}

void pmem_write_dpi(int en, int addr, int len, int data) {
  if (!en) return;
  paddr_write((paddr_t)addr, len, (word_t)data);
}

void ebreak_dpi(int en, int pc, int a0) {
  if (!en) return;
  NPCTRAP((vaddr_t)pc, a0);
}

void invalid_inst_dpi(int en, int pc, int inst) {
  if (!en) return;
  INV((vaddr_t)pc);
}
