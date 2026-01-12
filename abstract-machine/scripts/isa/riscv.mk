CROSS_COMPILE ?= riscv32-unknown-linux-gnu-

# 修复 nixpkgs 中缺少的 stubs-ilp32.h
ifneq ($(STUBS_ILP32_FIX),)
STUBS_FIX_FLAGS := -isystem $(STUBS_ILP32_FIX)
endif

COMMON_CFLAGS := -fno-pic -march=rv32im -mabi=ilp32 -mstrict-align $(STUBS_FIX_FLAGS)
CFLAGS        += $(COMMON_CFLAGS) -static
ASFLAGS       += $(COMMON_CFLAGS) -O0
LDFLAGS       += -melf32lriscv

# overwrite ARCH_H defined in $(AM_HOME)/Makefile
ARCH_H := arch/riscv.h
