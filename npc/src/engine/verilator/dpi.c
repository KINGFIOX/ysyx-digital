#include <memory/paddr.h>

int pmem_read_dpi(int en, int addr, int len) {
  if (!en)
    return 0;
  return (int)paddr_read((paddr_t)addr, len);
}

void pmem_write_dpi(int en, int addr, int len, int data) {
  if (!en)
    return;
  paddr_write((paddr_t)addr, len, (word_t)data);
}
