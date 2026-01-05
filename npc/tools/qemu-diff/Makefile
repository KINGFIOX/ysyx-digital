#***************************************************************************************
# Copyright (c) 2014-2024 Zihao Yu, Nanjing University
#
# NPC is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
#
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
#
# See the Mulan PSL v2 for more details.
#**************************************************************************************/

NAME  = $(GUEST_ISA)-qemu
SRCS  = $(shell find src/ -name "*.c")

SHARE = 1
CFLAGS += -DNPC_HOME=\"$(NPC_HOME)\"

# qemu-diff uses CONFIG_ISA_riscv + CONFIG_RV64 to distinguish RV32/RV64
# while NPC uses GUEST_ISA=riscv32/riscv64 in many flows. Map them here.
ifeq ($(GUEST_ISA),riscv32)
  CFLAGS += -DCONFIG_ISA_riscv
else ifeq ($(GUEST_ISA),riscv64)
  CFLAGS += -DCONFIG_ISA_riscv -DCONFIG_RV64
else
  CFLAGS += -DCONFIG_ISA_$(GUEST_ISA)
endif
INC_PATH += $(NPC_HOME)/include

include $(NPC_HOME)/scripts/build.mk
