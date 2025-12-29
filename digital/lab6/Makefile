TOPNAME = top
NXDC_FILES = constr/top.nxdc
INC_PATH ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert

BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)

# ========== LFSR 独立仿真配置 ==========
LFSR_TOP     = lfsr
LFSR_BUILD   = ./build_lfsr
LFSR_OBJDIR  = $(LFSR_BUILD)/obj_dir
LFSR_BIN     = $(LFSR_BUILD)/lfsr_sim
LFSR_VSRCS   = $(abspath ./vsrc/lfsr.v)
LFSR_CSRCS   = $(abspath ./csrc/lfsr_tb.cpp)

default: $(BIN)

$(shell mkdir -p $(BUILD_DIR))
$(shell mkdir -p $(LFSR_BUILD))

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

# 上板
$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

# LFSR 仿真构建
$(LFSR_BIN): $(LFSR_VSRCS) $(LFSR_CSRCS)
	@rm -rf $(LFSR_OBJDIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(LFSR_TOP) $^ \
		--trace \
		--Mdir $(LFSR_OBJDIR) --exe -o $(abspath $(LFSR_BIN))

all: default

run: $(BIN)
	@$^

lfsr-sim: $(LFSR_BIN)
	@$(LFSR_BIN)

clean:
	rm -rf $(BUILD_DIR) $(LFSR_BUILD)

.PHONY: default all clean run lfsr-sim
