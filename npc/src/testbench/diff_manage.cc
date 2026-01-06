#include "diff_manage.h"
#include "common.h"
#include <cstdio>

Difftest *difftest = NULL;

int DiffManage::init_difftest() {
  difftest = new Difftest();
  return 0;
}

int DiffManage::difftest_state() {
  /* trap as long as any core trapping */
  if (difftest->get_trap_valid()) {
    return difftest->get_trap_code();
  }
  return STATE_RUNNING;
}

int DiffManage::do_step(vluint64_t &main_time) {
  int ret = 0;
  ret = difftest->step(main_time);
  if (ret)
    return ret;
  return STATE_RUNNING;
}

int DiffManage::check_end() {
  int end = 0;
  end |= difftest->get_proxy_check_end();
  if (end) {
    printf("END by Syscall\n");
  }
  return end;
}

DiffManage::~DiffManage() {
  delete difftest;
  difftest = NULL;
}
