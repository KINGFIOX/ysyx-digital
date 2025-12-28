/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
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

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <assert.h>
#include <string.h>
#include <stdarg.h>
#include <stdbool.h>

// this should be enough
static char buf[65536] = {};
static char code_buf[65536 + 128] = {}; // a little larger than `buf`
static char *code_format =
"#include <stdio.h>\n"
"int main() { "
"  unsigned result = %s; "
"  printf(\"%%u\", result); "
"  return 0; "
"}";

// --- 随机表达式生成 ---
static size_t buf_len = 0;

// 安全追加到 buf，防止越界
static void buf_append(const char *fmt, ...) {
  if (buf_len >= sizeof(buf) - 1) return; // 越界
  va_list ap;
  va_start(ap, fmt);
  int n = vsnprintf(buf + buf_len /*写入位置*/, sizeof(buf) - buf_len, fmt, ap); // 返回写入的字符数
  va_end(ap);
  if (n < 0) return; // 写入失败
  if ((size_t)n >= sizeof(buf) - buf_len) { // 写入的字符数超过了剩余空间
    buf_len = sizeof(buf) - 1; // 截断
    buf[buf_len] = '\0'; // 添加结束符
  } else {
    buf_len += (size_t)n;
  }
}

static inline unsigned choose(unsigned n) {
  return rand() % n;
}

static void gen_blank(void) {
  int n = choose(3); // 0~2 个空格
  for (int i = 0; i < n; i++) buf_append(" ");
}

static void gen_num(void) {
  buf_append("%u", choose(1000)); // 0~999
}

static void gen_hex(void) {
  buf_append("0x%x", choose(0xffff));
}

static void gen_non_zero_num(void) {
  buf_append("%u", choose(999) + 1); // 1~999，避免除零
}

static void gen_binary(int depth);

// 生成一个“原子” (数字/括号/一元负号)
static void gen_uminus(int depth) {
  if (depth > 8) { // 深度太大直接收敛
    (choose(2) == 0) ? gen_num() : gen_hex();
    return;
  }

  switch (choose(4)) {
    case 0: gen_num(); break;
    case 1: gen_hex(); break;
    case 2:
      buf_append("("); gen_blank(); gen_binary(depth + 1); gen_blank(); buf_append(")");
      break;
    default: // 一元负号
      buf_append("-");
      gen_blank();
      gen_uminus(depth + 1);
      break;
  }
}

static const char *bin_ops[] = { "+", "-", "*", "/", "==", "!=", "&&", "||" };
static const int nr_bin_ops = sizeof(bin_ops) / sizeof(bin_ops[0]);

static void gen_binary(int depth) {
  if (depth > 10) { // 控制递归深度
    gen_uminus(depth);
    return;
  }

  switch (choose(3)) {
    case 0:
      gen_uminus(depth);
      break;
    case 1:
      buf_append("("); gen_blank(); gen_binary(depth + 1); gen_blank(); buf_append(")");
      break;
    default: { // 二元表达式
      gen_binary(depth + 1);
      gen_blank();
      const char *op = bin_ops[choose(nr_bin_ops)];
      buf_append("%s", op);
      gen_blank();
      if (strcmp(op, "/") == 0) {
        // 右操作数强制非 0，避免编译出的 C 程序出现除零 UB
        gen_non_zero_num();
      } else {
        gen_binary(depth + 1);
      }
      break;
    }
  }
}

static void gen_rand_expr() {
  buf[0] = '\0';
  buf_len = 0;
  gen_binary(0);
}

// --- 与被测求值器对接 ---
extern uint32_t expr_eval(const char *expr_str, bool *success);

int main(int argc, char *argv[]) {
  int seed = time(0);
  srand(seed);
  int loop = 1; // 缺省值为1
  if (argc > 1) {
    sscanf(argv[1], "%d", &loop);
  }
  for (int i = 0; i < loop; i ++) {
    gen_rand_expr();

    sprintf(code_buf, code_format, buf);

    FILE *fp = fopen("/tmp/.code.c", "w");
    assert(fp != NULL);
    fputs(code_buf, fp);
    fclose(fp);

    // 如何验证生成的表达式是正确的? 如果能过 gcc 编译, 那么生成的表达式就是正确的
    int ret = system("gcc /tmp/.code.c -o /tmp/.expr");
    if (ret != 0) continue;

    fp = popen("/tmp/.expr", "r");
    assert(fp != NULL);

    int result;
    ret = fscanf(fp, "%d", &result); // golden model
    pclose(fp);

    bool ok = false;
    uint32_t got = expr_eval(buf, &ok); // 自己的
    if (!ok || got != (uint32_t)result) {
      fprintf(stderr, "Mismatch! expect=%u got=%u expr=\"%s\"\n",
              (uint32_t)result, got, buf);
      return 1;
    }
    printf("Match! expect=%u got=%u expr=\"%s\"\n", result, got, buf);
  }
  return 0;
}
