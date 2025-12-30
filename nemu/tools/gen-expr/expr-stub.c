#include <stdbool.h>
#include <stdint.h>
#include <assert.h>

// 引入 NEMU 类型定义
#include <common.h>
#include <isa.h>
#include <memory/vaddr.h>

// 这些是用来骗过编译器的
word_t isa_reg_str2val(const char *s, bool *success) {
  if (success) { *success = false; }
  return 0;
}

word_t vaddr_read(vaddr_t addr, int len) {
  (void)addr; (void)len;
  return 0;
}

void vaddr_write(vaddr_t addr, int len, word_t data) {
  (void)addr; (void)len; (void)data;
}

// 防止某些头文件期望的符号缺失
void rtl_trap(int a, vaddr_t b) {
  (void)a; (void)b;
  assert(0 && "rtl_trap should not be called in expr tester");
}

