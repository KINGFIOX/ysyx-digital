/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NPC is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "common.h"

static struct gdb_conn *conn;

bool gdb_connect_qemu(int port) {
  // connect to gdbserver on localhost port 1234
  while ((conn = gdb_begin_inet("127.0.0.1", port)) == NULL) {
    usleep(1);
  }

  return true;
}

static bool gdb_memcpy_to_qemu_small(uint32_t dest, void *src, int len) {
  char *buf = malloc(len * 2 + 128);
  assert(buf != NULL);
  int p = sprintf(buf, "M0x%x,%x:", dest, len);
  int i;
  for (i = 0; i < len; i ++) {
    p += sprintf(buf + p, "%c%c", hex_encode(((uint8_t *)src)[i] >> 4), hex_encode(((uint8_t *)src)[i] & 0xf));
  }

  gdb_send(conn, (const uint8_t *)buf, strlen(buf));
  free(buf);

  size_t size;
  uint8_t *reply = gdb_recv(conn, &size);
  bool ok = !strcmp((const char*)reply, "OK");
  free(reply);

  return ok;
}

bool gdb_memcpy_to_qemu(uint32_t dest, void *src, int len) {
  const int mtu = 1500;
  bool ok = true;
  while (len > mtu) {
    ok &= gdb_memcpy_to_qemu_small(dest, src, mtu);
    dest += mtu;
    src += mtu;
    len -= mtu;
  }
  ok &= gdb_memcpy_to_qemu_small(dest, src, len);
  return ok;
}

bool gdb_getregs(union isa_gdb_regs *r) {
  gdb_send(conn, (const uint8_t *)"g", 1);
  size_t size;
  uint8_t *reply = gdb_recv(conn, &size);

  int i;
  uint8_t *p = reply;
  uint8_t c;
  for (i = 0; i < sizeof(union isa_gdb_regs) / sizeof(uint32_t); i ++) {
    c = p[8];
    p[8] = '\0';
    r->array[i] = gdb_decode_hex_str(p);
    p[8] = c;
    p += 8;
  }

  free(reply);

  return true;
}

bool gdb_setregs(union isa_gdb_regs *r) {
  int len = sizeof(union isa_gdb_regs);
  char *buf = malloc(len * 2 + 128);
  assert(buf != NULL);
  buf[0] = 'G';

  void *src = r;
  int p = 1;
  int i;
  for (i = 0; i < len; i ++) {
    p += sprintf(buf + p, "%c%c", hex_encode(((uint8_t *)src)[i] >> 4), hex_encode(((uint8_t *)src)[i] & 0xf));
  }

  gdb_send(conn, (const uint8_t *)buf, strlen(buf));
  free(buf);

  size_t size;
  uint8_t *reply = gdb_recv(conn, &size);
  bool ok = !strcmp((const char*)reply, "OK");
  free(reply);

  return ok;
}

bool gdb_si() {
  char buf[] = "vCont;s:1";
  gdb_send(conn, (const uint8_t *)buf, strlen(buf));
  size_t size;
  uint8_t *reply = gdb_recv(conn, &size);
  free(reply);
  return true;
}

bool gdb_memcpy_from_qemu(uint32_t src, void *dest, int len) {
  char *buf = malloc(128);
  assert(buf != NULL);
  sprintf(buf, "m0x%x,%x", src, len);
  
  gdb_send(conn, (const uint8_t *)buf, strlen(buf));
  free(buf);
  
  size_t size;
  uint8_t *reply = gdb_recv(conn, &size);
  
  // 解析十六进制响应
  for (int i = 0; i < len && i * 2 < (int)size; i++) {
    uint8_t hi = reply[i * 2];
    uint8_t lo = reply[i * 2 + 1];
    hi = (hi >= 'a') ? (hi - 'a' + 10) : (hi >= 'A') ? (hi - 'A' + 10) : (hi - '0');
    lo = (lo >= 'a') ? (lo - 'a' + 10) : (lo >= 'A') ? (lo - 'A' + 10) : (lo - '0');
    ((uint8_t *)dest)[i] = (hi << 4) | lo;
  }
  
  free(reply);
  return true;
}

void gdb_exit() {
  gdb_end(conn);
}
