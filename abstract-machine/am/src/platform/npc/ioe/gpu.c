#include <am.h>
#include <npc.h>
#include <string.h>

#define SYNC_ADDR (VGACTL_ADDR + 4)

void __am_gpu_init() {
}

/// @param cfg return the screen width and height
void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  *cfg = (AM_GPU_CONFIG_T){ .present = true, .has_accel = false, .width = 0, .height = 0, .vmemsz = 0 };
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
