#include "nemuproxy.h"
#include <assert.h>
#include <cstdio>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

extern char *difftest_ref_so;

NemuProxy::NemuProxy() {
  if (difftest_ref_so == NULL) {
    fprintf(stderr, "FATAL: difftest_ref_so is not set\n");
    exit(1);
  }
  printf("Using %s for difftest\n", difftest_ref_so);

  handle = dlmopen(LM_ID_NEWLM, difftest_ref_so, RTLD_LAZY | RTLD_DEEPBIND);
  if (!handle) {
    printf("%s\n", dlerror());
    assert(0);
  }

  this->memcpy =
      (void (*)(paddr_t, void *, size_t, bool))dlsym(handle, "difftest_memcpy");
  check_and_assert(this->memcpy);

  regcpy = (void (*)(void *, bool, bool))dlsym(handle, "difftest_regcpy");
  check_and_assert(regcpy);

  csrcpy = (void (*)(void *, bool))dlsym(handle, "difftest_csrcpy");
  check_and_assert(csrcpy);

  uarchstatus_cpy =
      (void (*)(void *, bool))dlsym(handle, "difftest_uarchstatus_cpy");
  check_and_assert(uarchstatus_cpy);

  exec = (void (*)(uint64_t))dlsym(handle, "difftest_exec");
  check_and_assert(exec);

  check_end = (int (*)(void))dlsym(handle, "difftest_cosim_end");
  check_and_assert(check_end);

  guided_exec = (vaddr_t (*)(void *))dlsym(handle, "difftest_guided_exec");
  check_and_assert(guided_exec);

  store_commit =
      (int (*)(uint64_t, uint64_t))dlsym(handle, "difftest_store_commit");
  check_and_assert(store_commit);

  raise_intr = (void (*)(uint64_t))dlsym(handle, "difftest_raise_intr");
  check_and_assert(raise_intr);

  isa_reg_display = (void (*)(void))dlsym(handle, "isa_reg_display");
  check_and_assert(isa_reg_display);

  tlbfill_index_set =
      (void (*)(uint32_t))dlsym(handle, "difftest_tlbfill_index_set");
  check_and_assert(tlbfill_index_set);

  timercpy = (void (*)(void *))dlsym(handle, "difftest_timercpy");
  check_and_assert(timercpy);

  estat_sync =
      (void (*)(uint32_t, uint32_t))dlsym(handle, "difftest_estat_sync");
  check_and_assert(estat_sync);

  auto nemu_init = (void (*)(void))dlsym(handle, "difftest_init");
  check_and_assert(nemu_init);

  nemu_init();
}

NemuProxy::~NemuProxy() {
  if (handle != NULL) {
    dlclose(handle);
  }
}
