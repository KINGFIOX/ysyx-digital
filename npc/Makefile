BUILD_DIR = ./build
NPC_HOME := $(abspath .)

PRJ = playground
VERILATOR ?= verilator
VERILATOR_TOP ?= NpcCoreTop
VERILATOR_SV  := $(BUILD_DIR)/$(VERILATOR_TOP).sv
VERILATOR_MDIR := $(BUILD_DIR)/obj-verilator
VERILATOR_MK  := $(VERILATOR_MDIR)/V$(VERILATOR_TOP).mk
VERILATOR_LIB := $(VERILATOR_MDIR)/V$(VERILATOR_TOP)__ALL.a

# =============================== 构建 NPC 可执行文件 ===============================
# 完整构建: Chisel -> Verilog -> Verilator -> C++ 链接
build-npc: verilate
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME)

# =============================== Chisel -> Verilog ===============================
test:
	mill -i $(PRJ).test

verilog: $(VERILATOR_SV)

$(VERILATOR_SV):
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i $(PRJ).runMain Elaborate --target-dir $(BUILD_DIR)

# =============================== Verilog -> Verilator C++ ===============================
verilate: $(VERILATOR_LIB)

$(VERILATOR_LIB): $(VERILATOR_SV)
	@echo "=== Verilating $(VERILATOR_TOP) ==="
	$(VERILATOR) --cc $(VERILATOR_SV) \
		--Mdir $(VERILATOR_MDIR) \
		--top-module $(VERILATOR_TOP) \
		--trace \
		-O3 -Wall -Wno-fatal \
		-CFLAGS "-std=c++17 -O2"
	$(MAKE) -C $(VERILATOR_MDIR) -f V$(VERILATOR_TOP).mk

help:
	mill -i $(PRJ).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog verilate help reformat checkformat clean build-npc menuconfig run gdb

menuconfig:
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME) menuconfig

run:
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME) run IMG=$(IMG)

gdb:
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME) gdb IMG=$(IMG)

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
