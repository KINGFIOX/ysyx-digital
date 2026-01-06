# 默认目标为 app, 当运行 make 时, 会默认执行 app 这条规则
.DEFAULT_GOAL = app

# Add necessary options if the target is a shared library
ifeq ($(SHARE),1)
SO = -so
CFLAGS  += -fPIC -fvisibility=hidden
LDFLAGS += -shared -fPIC
endif

WORK_DIR  = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build

VERILATOR_ROOT ?= $(shell verilator --getenv VERILATOR_ROOT 2>/dev/null)
VERILATOR_INC_PATH ?= $(if $(VERILATOR_ROOT),$(VERILATOR_ROOT)/include $(VERILATOR_ROOT)/include/vltstd,)
VERILATOR_OBJ_DIR ?= $(BUILD_DIR)/verilator/obj_dir
VERILATOR_TOP ?= simu_top
VERILATOR_VSRCS ?= $(NPC_HOME)/src/testbench/simu_top.v $(NPC_HOME)/src/testbench/difftest.v $(NPC_HOME)/src/testbench/soc_top_stub.v
VERILATOR_LIB ?= $(VERILATOR_OBJ_DIR)/V$(VERILATOR_TOP)__ALL.a
VERILATOR_HDR ?= $(VERILATOR_OBJ_DIR)/V$(VERILATOR_TOP).h
VERILATOR_SUPPORT_OBJS ?= $(VERILATOR_OBJ_DIR)/verilated.o \
	$(VERILATOR_OBJ_DIR)/verilated_vcd_c.o \
	$(VERILATOR_OBJ_DIR)/verilated_threads.o \
	$(VERILATOR_OBJ_DIR)/verilated_dpi.o
VERILATOR_FLAGS ?= --trace

INC_PATH := $(WORK_DIR)/include $(INC_PATH)
INC_PATH += $(VERILATOR_INC_PATH) $(VERILATOR_OBJ_DIR)
# obj-riscv32-npc-interpreter
OBJ_DIR  = $(BUILD_DIR)/obj-$(NAME)$(SO)
BINARY   = $(BUILD_DIR)/$(NAME)$(SO)

# Compilation flags
ifeq ($(CC),clang)
CXX := clang++
else
CXX := g++
endif
CXXFLAGS += -std=c++17
CXXFLAGS += -pthread
CXXFLAGS += -Wno-error=sign-compare
CXXFLAGS += -Wno-error
LD := $(CXX)
INCLUDES = $(addprefix -I, $(INC_PATH))
CFLAGS  := -O2 -MMD -Wall -Werror $(INCLUDES) $(CFLAGS)
LDFLAGS := -O2 -pthread $(LDFLAGS)

# 将 SRCS 中的 .c 和 .cc 替换成 %(OBJ_DIR)/%.o, 字符串操作
OBJS = $(SRCS:%.c=$(OBJ_DIR)/%.o) $(CXXSRC:%.cc=$(OBJ_DIR)/%.o)
ARCHIVES += $(VERILATOR_LIB) $(VERILATOR_SUPPORT_OBJS)

# Compilation patterns

$(OBJ_DIR)/%.o: %.c
	@echo + CC $<
	@mkdir -p $(dir $@)
	@$(CC) $(CFLAGS) -c -o $@ $<
	$(call call_fixdep, $(@:.o=.d), $@)

$(OBJ_DIR)/%.o: %.cc | $(VERILATOR_HDR)
	@echo + CXX $<
	@mkdir -p $(dir $@)
	@$(CXX) $(CFLAGS) $(CXXFLAGS) -c -o $@ $<
	$(call call_fixdep, $(@:.o=.d), $@)

$(VERILATOR_LIB) $(VERILATOR_HDR): $(VERILATOR_VSRCS)
	@echo + VERILATOR $(VERILATOR_TOP)
	@mkdir -p $(VERILATOR_OBJ_DIR)
	@verilator -cc $(VERILATOR_VSRCS) --top-module $(VERILATOR_TOP) \
		--Mdir $(VERILATOR_OBJ_DIR) $(VERILATOR_FLAGS) --threads 4 -O2 \
		-CFLAGS "-std=c++17 -fPIC" -LDFLAGS "-fPIC"
	@$(MAKE) -C $(VERILATOR_OBJ_DIR) -f V$(VERILATOR_TOP).mk

# Depencies
# 将 .o 替换成 .d, 字符串操作.
# 再 include 进来, 里面里面有一些依赖关系
-include $(OBJS:.o=.d)

# Some convenient rules

.PHONY: app clean

app: $(BINARY)

# :: 避免与潜在的其他同名规则合并
$(BINARY):: $(OBJS) $(ARCHIVES)
	@echo + LD $@
	@$(LD) -o $@ $(OBJS) $(LDFLAGS) $(ARCHIVES) $(LIBS)

clean:
	-rm -rf $(BUILD_DIR)
