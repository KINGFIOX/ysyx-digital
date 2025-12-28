#pragma once
#include <stdint.h>

// 因为我的 expr_eval 其实包含了: $reg, *mem, 所以是需要包含 <isa.h> 的
// 又因为 isa.h 中有 __GUEST_ISA__ 宏, 所以需要定义一个宏来骗过编译器, 否则会报错
#define __GUEST_ISA__ dummy

typedef struct { uint32_t _; } dummy_CPU_state;
typedef struct { uint32_t _; } dummy_ISADecodeInfo;

