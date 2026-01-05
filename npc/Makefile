BUILD_DIR = ./build
NPC_HOME := $(abspath .)

PRJ = playground

test:
	mill -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i $(PRJ).runMain Elaborate --target-dir $(BUILD_DIR)

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

.PHONY: test verilog help reformat checkformat clean build-npc menuconfig run gdb

build-npc:
	$(MAKE) -f scripts/native.mk NPC_HOME=$(NPC_HOME)

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
