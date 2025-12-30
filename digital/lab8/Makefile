PROJ_PATH ?= $(shell pwd)

# 共用设置
VERILATOR ?= verilator
BUILD_DIR ?= $(PROJ_PATH)/build

# ============================ NVBOARD （nvboard） ============================
TOPNAME     = top
NXDC_FILES  = constr/top.nxdc
INC_PATH   ?=

VERILATOR_CFLAGS += -MMD --build -cc -O3 --x-assign fast --x-initial fast --noassert

NV_OBJ_DIR  = $(BUILD_DIR)/obj_dir_nvboard
BIN         = $(BUILD_DIR)/$(TOPNAME)

$(shell mkdir -p $(BUILD_DIR))
# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# project source
VSRCS = $(shell find $(abspath $(PROJ_PATH)/vsrc) -name "*.v")
CSRCS = $(abspath $(PROJ_PATH)/csrc/main.cpp) $(SRC_AUTO_BIND)

# rules for NVBoard
include $(NVBOARD_HOME)/scripts/nvboard.mk

# rules for verilator
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""

# 上板构建
$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(NV_OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(NV_OBJ_DIR) --exe -o $(abspath $(BIN))

nvboard: $(BIN)

# 通用目标
default: ps2_tb
all: ps2_tb nvboard

clean:
	rm -rf $(BUILD_DIR)
	rm -rf $(RESULT_DIR)

# ============================ yosys-sta ============================

# 与具体电路相关
O ?= $(PROJ_PATH)/result
RTL_FILES ?= $(shell find $(PROJ_PATH)/vsrc -name "*.v")
RESULT_DIR = $(O)/$(TOPNAME)-$(CLK_FREQ_MHZ)MHz
export CLK_FREQ_MHZ ?= 500
export CLK_PORT_NAME ?= clk
NETLIST_SYN_V = $(RESULT_DIR)/$(TOPNAME).netlist.v
TIMING_RPT = $(RESULT_DIR)/$(TOPNAME).rpt

# 一些脚本
SDC_FILE ?= $(YOSYS_STA_HOME)/scripts/default.sdc #
SCRIPT_DIR = $(YOSYS_STA_HOME)/scripts
PDK = icsprout55

syn: $(NETLIST_SYN_V)
$(NETLIST_SYN_V): $(RTL_FILES) $(SCRIPT_DIR)/yosys.tcl
	mkdir -p $(@D)
	echo tcl $(SCRIPT_DIR)/yosys.tcl $(TOPNAME) $(PDK) \"$(RTL_FILES)\" $@ | yosys -g -l $(@D)/yosys.log -s -


sta: $(TIMING_RPT)
$(TIMING_RPT): $(SCRIPT_DIR)/sta.tcl $(SDC_FILE) $(NETLIST_SYN_V)
	set -o pipefail && $(YOSYS_STA_HOME)/bin/iEDA -script $^ $(TOPNAME) $(PDK) 2>&1 | tee $(RESULT_DIR)/sta.log


.PHONY: default all clean ps2_tb nvboard syn sta clean
