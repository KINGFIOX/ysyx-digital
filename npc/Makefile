BUILD_DIR = ./build
NPC_HOME := $(abspath .)

MILL := mill

PRJ = playground

VERILATOR ?= verilator
VERILATOR_TOP ?= NpcCoreTop
VERILATOR_SV  := $(BUILD_DIR)/$(VERILATOR_TOP).sv
VERILATOR_FILELIST := $(BUILD_DIR)/filelist.f
VERILATOR_MDIR := $(BUILD_DIR)/obj-verilator
VERILATOR_MK  := $(VERILATOR_MDIR)/V$(VERILATOR_TOP).mk
VERILATOR_LIB := $(VERILATOR_MDIR)/V$(VERILATOR_TOP)__ALL.a

# =============================== 构建 NPC 可执行文件 ===============================
# 完整构建: Chisel -> Verilog -> Verilator -> C++ 链接
build-npc: verilate
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME)

# =============================== Chisel -> Verilog ===============================
test:
	$(MILL) -i $(PRJ).test

verilog: $(VERILATOR_SV)

# 这个 sed 命令会删除 NpcCoreTop.sv 中从 // ----- 8< ----- FILE "firrtl_black_box_resource_files.f" 开始到文件末尾的所有内容
$(VERILATOR_SV):
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	$(MILL) -i $(PRJ).runMain Elaborate --target-dir $(BUILD_DIR)

# =============================== Verilog -> Verilator C++ ===============================

verilate: $(VERILATOR_LIB)

$(VERILATOR_LIB): $(VERILATOR_SV) $(VERILATOR_FILELIST)
	@echo "=== Verilating $(VERILATOR_TOP) ==="
	cd $(BUILD_DIR) && $(VERILATOR) --cc \
		-f filelist.f \
		-y verification \
		--Mdir obj-verilator \
		--top-module $(VERILATOR_TOP) \
		--trace \
		-O3 -Wall -Wno-fatal \
		-CFLAGS "-std=c++17 -O2"
	$(MAKE) -C $(VERILATOR_MDIR) -f V$(VERILATOR_TOP).mk


# =============================== commands ===============================

help:
	$(MILL) -i $(PRJ).runMain Elaborate --help

reformat:
	$(MILL) -i __.reformat

checkformat:
	$(MILL) -i __.checkFormat

bsp:
	$(MILL) -i mill.bsp.BSP/install

idea:
	$(MILL) -i mill.idea.GenIdea/idea

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
	@gtkwave $(BUILD_DIR)/npc_core.vcd

-include ../Makefile
