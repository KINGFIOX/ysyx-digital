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

INC_PATH += $(NPC_HOME)/src/testbench/include

# verilator 生成的头文件通常位于 $(VERILATOR_OBJ_DIR)
INC_PATH += $(VERILATOR_OBJ_DIR)

# testbench 相关 C++ 源文件(不包含 sim_main.cc, 入口改为 npc-main.cc)
CXXSRC += src/testbench/common.cc
CXXSRC += src/testbench/cpu_tool.cc
CXXSRC += src/testbench/devices.cc
CXXSRC += src/testbench/diff_manage.cc
CXXSRC += src/testbench/difftest.cc
CXXSRC += src/testbench/emu.cc
CXXSRC += src/testbench/interface.cc
CXXSRC += src/testbench/nemuproxy.cc
CXXSRC += src/testbench/ram.cc
CXXSRC += src/testbench/testbench.cc
CXXSRC += src/testbench/uart.cc

LIBS += -lpthread

