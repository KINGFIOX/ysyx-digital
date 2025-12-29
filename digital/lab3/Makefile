TOPNAME = top
NXDC_FILES = constr/top.nxdc
INC_PATH ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert

BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)

# ========== ALU 独立仿真配置 ==========
ALU_TOP     = alu
ALU_BUILD   = ./build_alu
ALU_OBJDIR  = $(ALU_BUILD)/obj_dir
ALU_BIN     = $(ALU_BUILD)/alu_sim
ALU_VSRCS   = $(abspath ./vsrc/alu.v)
ALU_CSRCS   = $(abspath ./csrc/alu_tb.cpp)

default: $(BIN)

$(shell mkdir -p $(BUILD_DIR))
$(shell mkdir -p $(ALU_BUILD))

# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# project source
VSRCS = $(shell find $(abspath ./vsrc) -name "*.v")
CSRCS = $(abspath ./csrc/main.cpp) $(SRC_AUTO_BIND)

# rules for NVBoard
include $(NVBOARD_HOME)/scripts/nvboard.mk

# rules for verilator
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""

$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

# ALU 仿真构建
$(ALU_BIN): $(ALU_VSRCS) $(ALU_CSRCS)
	@rm -rf $(ALU_OBJDIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(ALU_TOP) $^ \
		--trace \
		--Mdir $(ALU_OBJDIR) --exe -o $(abspath $(ALU_BIN))

all: default

run: $(BIN)
	@$^

alu-sim: $(ALU_BIN)
	@$(ALU_BIN)

clean:
	rm -rf $(BUILD_DIR) $(ALU_BUILD)

.PHONY: default all clean run alu-sim
