/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NPC is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "testbench.h"
#include <common.h>
#include <cstdlib>
#include <cstdio>
#include <sys/time.h>
#include <verilated.h>

#ifndef RESET_VAL
#define RESET_VAL 0
#endif

// 当前仿真时间
vluint64_t main_time = 0;
double sc_time_stamp() { return main_time; }
struct timeval start, end;
static CpuTestbench *tb = nullptr;

static void init_verilator(int argc, char **argv, char **env) {
  Verilated::debug(0);
  Verilated::randReset(RESET_VAL);
  Verilated::traceEverOn(true);
  Verilated::commandArgs(argc, argv);
  Verilated::mkdir("logs");
}

int main(int argc, char *argv[], char *env[]) {

  init_verilator(argc, argv, env);
  tb = new CpuTestbench(argc, argv, env, &main_time);

  gettimeofday(&start, nullptr);
  tb->simulate(main_time);
  gettimeofday(&end, nullptr);

  long long total_time =
      (end.tv_sec - start.tv_sec) * 1000000 + (end.tv_usec - start.tv_usec);
  printf("total time is %lld us\n", total_time);

  delete tb;
  return 0;
}

