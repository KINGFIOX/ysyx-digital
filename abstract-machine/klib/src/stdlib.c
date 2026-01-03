#include <am.h>
#include <klib-macros.h>
#include <klib.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)
static unsigned long int next = 1;

int rand(void) {
  // RAND_MAX assumed to be 32767
  next = next * 1103515245 + 12345;
  return (unsigned int)(next / 65536) % 32768;
}

void srand(unsigned int seed) { next = seed; }

int abs(int x) { return (x < 0 ? -x : x); }

int atoi(const char *nptr) {
  int x = 0;
  while (*nptr == ' ') {
    nptr++;
  }
  while (*nptr >= '0' && *nptr <= '9') {
    x = x * 10 + *nptr - '0';
    nptr++;
  }
  return x;
}

// On native, malloc() will be called during initializaion of C runtime.
// Therefore do not call panic() here, else it will yield a dead recursion:
//   panic() -> putchar() -> (glibc) -> malloc() -> panic()

#if !(defined(__ISA_NATIVE__) && defined(__NATIVE_USE_KLIB__))

extern Area heap;

static char * hbrk = 0;
static uintptr_t mlim = 0;

static void malloc_init() {
  hbrk = (void *)ROUNDUP(heap.start, 8);
  mlim = (uintptr_t)ROUNDDOWN(heap.end, 8) - (uintptr_t)hbrk;
}

void* malloc(size_t size) {
  if (hbrk == 0) malloc_init();
  size  = (size_t)ROUNDUP(size, 8);
  char *old = hbrk;
  hbrk += size;
  assert((uintptr_t)heap.start <= (uintptr_t)hbrk && (uintptr_t)hbrk < (uintptr_t)heap.end);
  for (uint64_t *p = (uint64_t *)old; p != (uint64_t *)hbrk; p ++) { *p = 0; } // bzero
  assert((uintptr_t)hbrk - (uintptr_t)heap.start <= mlim);
  return old;
}

void free(void *ptr) { }

#else

void *malloc(size_t size) { return NULL; }
void free(void *ptr) { (void)ptr; }

#endif

#endif
