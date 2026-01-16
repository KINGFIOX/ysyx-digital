#include <memory/paddr.h>
#include <cpu/cpu.h>


int pmem_read_dpi(int en, int addr, int len) {
  if (!en) return 0;
  return (int)paddr_read((paddr_t)addr, len);
}

void pmem_write_dpi(int en, int addr, int strb, int data) {
  if (!en) return;
  for (int i = 0; i < 4; i++) {
    if ((strb >> i) & 1) {
      paddr_write((paddr_t)(addr + i), 1, (word_t)((data >> (i * 8)) & 0xFF));
    }
  }
}

void exception_dpi(int en, int pc, int mcause, int a0) {
  if (!en) return;
  switch (mcause) {
    case 2:
      INV((vaddr_t)pc);
      break;
    default:
      NPCTRAP((vaddr_t)pc, a0);
      break;
  }
}
