#***************************************************************************************
# Copyright (c) 2014-2024 Zihao Yu, Nanjing University
#
# NEMU is licensed under Mulan PSL v2.
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

NAME = gen-expr
# 直接复用 sdb 的 lexer/parser 与 stub，将 expr_eval 链接进来对比验证
SRCS = gen-expr.c ../../src/monitor/sdb/expr.lex.c ../../src/monitor/sdb/expr.tab.c expr-stub.c
# 需要头文件：把 NEMU 顶层 include 目录加入搜索路径
INC_PATH += $(NEMU_HOME)/include
include $(NEMU_HOME)/scripts/build.mk
