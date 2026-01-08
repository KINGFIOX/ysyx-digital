#include <klib.h>
#include <klib-macros.h>
#include <stdint.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *string) {
  const char *p;

  assert(string != NULL);

  for (p = string; *p != '\0'; p++) continue;
  return p - string;
}
char *strcpy(char *dst, const char *src) {
  char *os = dst;
  while ((*dst++ = *src++) != 0);
  return os;
}

char *strncpy(char *dst, const char *src, size_t n) {
  char *os = dst;
  while (n-- > 0 && (*dst++ = *src++) != 0);
  while (n-- > 0) *dst++ = 0;
  return os;
}

char *strcat(char *dst, const char *src) {
  char *os = dst;
  while (*dst) {
    dst++;
  }
  while ((*dst++ = *src++) != 0);
  return os;
}

int strcmp(const char *a_, const char *b_) {
  const unsigned char *a = (const unsigned char *)a_;
  const unsigned char *b = (const unsigned char *)b_;

  assert(a != NULL);
  assert(b != NULL);

  while (*a != '\0' && *a == *b) {
    a++;
    b++;
  }

  return *a < *b ? -1 : *a > *b;
}

int strncmp(const char *p, const char *q, size_t n) {
  while (n > 0 && *p && *p == *q) n--, p++, q++;
  if (n == 0) return 0;
  return (uint8_t)*p - (uint8_t)*q;
}

void *memset(void *dst_, int value, size_t size) {
  unsigned char *dst = dst_;

  assert(dst != NULL || size == 0);

  while (size-- > 0) *dst++ = value;

  return dst_;
}

void *memmove(void *dst_, const void *src_, size_t size) {
  unsigned char *dst = dst_;
  const unsigned char *src = src_;

  assert(dst != NULL || size == 0);
  assert(src != NULL || size == 0);

  if (dst < src) {
    while (size-- > 0) *dst++ = *src++;
  } else {
    dst += size;
    src += size;
    while (size-- > 0) *--dst = *--src;
  }

  return dst;
}

void *memcpy(void *dst_, const void *src_, size_t size) {
  unsigned char *dst = dst_;
  const unsigned char *src = src_;

  assert(dst != NULL || size == 0);
  assert(src != NULL || size == 0);

  while (size-- > 0) *dst++ = *src++;

  return dst_;
}

int memcmp(const void *a_, const void *b_, size_t size) {
  const unsigned char *a = a_;
  const unsigned char *b = b_;

  assert(a != NULL || size == 0);
  assert(b != NULL || size == 0);

  for (; size-- > 0; a++, b++)
    if (*a != *b) return *a > *b ? +1 : -1;
  return 0;
}

/** If STRING is less than MAXLEN characters in length, returns
   its actual length.  Otherwise, returns MAXLEN. */
#ifndef __RTTHREAD__
size_t strnlen(const char *string, size_t maxlen) {
  size_t length;

  for (length = 0; string[length] != '\0' && length < maxlen; length++) continue;
  return length;
}
#endif

#endif
