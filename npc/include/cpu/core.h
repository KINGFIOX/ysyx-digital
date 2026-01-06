#include <stdbool.h>
#ifndef __CPU_CORE_H__
#define __CPU_CORE_H__

struct Decode;

#ifdef __cplusplus
extern "C" {
#endif

// 初始化/析构 Verilator 或解释器执行后端
bool npc_core_init(void);
void npc_core_fini(void);

// 执行一条指令, 并将提交后的寄存器/pc 写回全局 cpu
// 返回 false 表示后端无法继续执行
bool npc_core_step(struct Decode *s);

#ifdef __cplusplus
}
#endif

#endif

