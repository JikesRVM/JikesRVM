/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include "sys.h"

#include <stdlib.h> // malloc and others
#include <errno.h> // error numbers
#include <string.h> // memcpy & memmove
#include <sys/mman.h> // mmap

int inRVMAddressSpace(Address a);

void* checkMalloc(int length)
{
  void *result = malloc(length);
  if (result == NULL) {
    ERROR_PRINTF("%s: error while trying to allocate memory in checkMalloc\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  if (inRVMAddressSpace((Address)result)) {
    ERROR_PRINTF("malloc returned something that is in RVM address space: %p\n",result);
  }
  return result;
}

void* checkCalloc(int numElements, int sizeOfOneElement)
{
  void *result = calloc(numElements,sizeOfOneElement);
  if (result == NULL) {
    ERROR_PRINTF("%s: error while trying to allocate memory in checkCalloc\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  if (inRVMAddressSpace((Address)result)) {
    ERROR_PRINTF("calloc returned something that is in RVM address space: %p\n",result);
  }
  return result;
}

void checkFree(void* mem)
{
  free(mem);
}

/** Allocate memory. */
EXTERNAL void * sysMalloc(int length)
{
  TRACE_PRINTF("%s: sysMalloc %d\n", Me, length);
  return checkMalloc(length);
}

EXTERNAL void * sysCalloc(int length)
{
  TRACE_PRINTF("%s: sysCalloc %d\n", Me, length);
  return checkCalloc(1, length);
}

/** Release memory. */
EXTERNAL void sysFree(void *location)
{
  TRACE_PRINTF("%s: sysFree %p\n", Me, location);
  checkFree(location);
}

/* Zero a range of memory with non-temporal instructions on x86 */
EXTERNAL void sysZeroNT(void *dst, Extent cnt)
{
  TRACE_PRINTF("%s: sysZeroNT %p %zu\n", Me, dst, cnt);
#ifdef RVM_FOR_SSE2
  char *buf = (char *) dst;
  unsigned int len = cnt;

  __asm__ volatile (
    ".align 4 \n\t"
    "cmp $0x10, %%esi \n\t"
    "jl 0f \n\t"
    "pxor %%xmm0, %%xmm0 \n\t"
    "16: \n\t"
    "test $0xf, %%edi \n\t"
    "je 64f \n\t"
    "movb $0,(%%edi) \n\t"
    "inc %%edi \n\t"
    "dec %%esi \n\t"
    "jmp 16b \n\t"
    "64: \n\t"
    "cmp $128, %%esi \n\t"
    "jl 0f \n\t"
    "movntdq %%xmm0, 0x0(%%edi) \n\t"
    "movntdq %%xmm0, 0x10(%%edi) \n\t"
    "movntdq %%xmm0, 0x20(%%edi) \n\t"
    "movntdq %%xmm0, 0x30(%%edi) \n\t"
    "movntdq %%xmm0, 0x40(%%edi) \n\t"
    "movntdq %%xmm0, 0x50(%%edi) \n\t"
    "movntdq %%xmm0, 0x60(%%edi) \n\t"
    "movntdq %%xmm0, 0x70(%%edi) \n\t"

    "add $128, %%edi \n\t"
    "sub $128, %%esi \n\t"
    "jmp 64b \n\t"
    "0: \n\t"
    "sfence \n\t"
    : "+S"(len),"+D" ( buf ));

  while (__builtin_expect (len--, 0)) {
    *buf++ = 0;
  }
#else
  memset(dst, 0x00, cnt);
#endif
}

/** Zero a range of memory bytes. */
EXTERNAL void sysZero(void *dst, Extent cnt)
{
  TRACE_PRINTF("%s: sysZero %p %zu\n", Me, dst, cnt);
  memset(dst, 0x00, cnt);
}

/**
 * Zeros a range of memory pages.
 * Taken:     start of range (must be a page boundary)
 *            size of range, in bytes (must be multiple of page size, 4096)
 * Returned:  nothing
 */
EXTERNAL void sysZeroPages(void *dst, int cnt)
{
  // uncomment one of the following
  //
#define STRATEGY 1 /* for optimum pBOB numbers */
// #define STRATEGY 2 /* for more realistic workload */
// #define STRATEGY 3 /* as yet untested */

  TRACE_PRINTF("%s: sysZeroPages %p %d\n", Me, dst, cnt);

#if (STRATEGY == 1)
  // Zero memory by touching all the bytes.
  // Advantage:    fewer page faults during mutation
  // Disadvantage: more page faults during collection, at least until
  //               steady state working set is achieved
  //
  sysZero(dst, cnt);
#endif

#if (STRATEGY == 2)
  // Zero memory by using munmap() followed by mmap().
  // This assumes that bootImageRunner.C has used mmap()
  // to acquire memory for the VM bootimage and heap.
  // Advantage:    fewer page faults during collection
  // Disadvantage: more page faults during mutation
  //
  int rc = munmap(dst, cnt);
  if (rc != 0)
  {
    ERROR_PRINTF("%s: munmap failed (errno=%d): ", Me, errno);
    perror(NULL);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

#ifdef MAP_ANONYMOUS
  void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
#else
  void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANON | MAP_FIXED, -1, 0);
#endif

  if (addr == (void *)-1)
  {
    ERROR_PRINTF("%s: mmap failed (errno=%d): ", Me, errno);
    perror(NULL);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif

#if (STRATEGY == 3)
  // Zero memory by using disclaim().
  // This assumes that bootImageRunner.C has used malloc()
  // to acquire memory for the VM bootimage and heap and requires use of
  // the binder option -bmaxdata:0x80000000 which allows large malloc heaps
  // Advantage:    ? haven't tried this strategy yet
  // Disadvantage: ? haven't tried this strategy yet
  //
  int rc = disclaim((char *)dst, cnt, ZERO_MEM);
  if (rc != 0)
  {
    ERROR_PRINTF("%s: disclaim failed (errno=%d): ", Me, errno);
    perror(NULL);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif

#undef STRATEGY
}

/**
 * mmap - general case
 * Taken:     start address (Java ADDRESS)
 *            length of region (Java EXTENT)
 *            desired protection (Java int)
 *            flags (Java int)
 *            file descriptor (Java int)
 *            offset (Java long)  [to cover 64 bit file systems]
 * Returned:  address of region (or -1 on failure) (Java ADDRESS)
 */
EXTERNAL void * sysMMap(char *start , size_t length ,
                        int protection , int flags ,
                        int fd , Offset offset)
{
  void *result;
  TRACE_PRINTF("%s: sysMMap %p %zu %d %d %d %zu\n",
               Me, start, length, protection, flags, fd, offset);
  result = mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
  return result;
}

/**
 * Same as mmap, but with more debugging support.
 * Returned: address of region if successful; errno (1 to 127) otherwise
 */
EXTERNAL void * sysMMapErrno(char *start , size_t length ,
                             int protection , int flags ,
                             int fd , Offset offset)
{
  void* res;
  TRACE_PRINTF("%s: sysMMapErrno %p %zu %d %d %d %zu\n",
               Me, start, length, protection, flags, fd, offset);
  res = mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
  if (res == (void *) -1) {
    ERROR_PRINTF("%s: sysMMapErrno %p %zu %d %d %d %ld failed with %d.\n",
                 Me, start, length, protection, flags, fd, (long) offset, errno);
    return (void *)(intptr_t) errno;
  } else {
    TRACE_PRINTF("mmap succeeded- region = [%p ... %p]    size = %zu\n",
                 res, (void*)(((size_t)res) + length), length);
    return res;
  }
}

/**
 * mprotect.
 * Taken:     start address (Java ADDRESS)
 *            length of region (Java EXTENT)
 *            new protection (Java int)
 * Returned:  0 (success) or -1 (failure) (Java int)
 */
EXTERNAL int sysMProtect(char *start, size_t length, int prot)
{
  TRACE_PRINTF("%s: sysMProtect %p %zu %d\n",
               Me, start, length, prot);
  return mprotect(start, length, prot);
}

/** Memory to memory copy. Memory regions must not overlap. */
EXTERNAL void sysCopy(void *dst, const void *src, Extent cnt)
{
  TRACE_PRINTF("%s: sysCopy %p %p %zu\n", Me, dst, src, cnt);
  memcpy(dst, src, cnt);
}

/** Memory to memory copy. Memory regions may overlap. */
EXTERNAL void sysMemmove(void *dst, const void *src, Extent cnt)
{
  TRACE_PRINTF("%s: sysMemmove %p %p %zu\n", Me, dst, src, cnt);
  memmove(dst, src, cnt);
}




/**
 * Synchronize caches: force data in dcache to be written out to main memory
 * so that it will be seen by icache when instructions are fetched back.
 *
 * Note: If other processors need to execute isync (e.g. for code patching),
 * this has to be done via soft handshakes.
 *
 * Taken:     start of address range
 *            size of address range (bytes)
 * Returned:  nothing
 */
EXTERNAL void sysSyncCache(void *address, size_t size)
{
  TRACE_PRINTF("%s: sync %p %zu\n", Me, address, size);

#ifdef RVM_FOR_POWERPC
  if (size < 0) {
    ERROR_PRINTF("%s: tried to sync a region of negative size!\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

  /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
  uintptr_t start = (uintptr_t)address;
  uintptr_t end = start + size;
  uintptr_t addr;

  /* update storage */
  /* Note: if one knew the cache line size, one could write a better loop */
  for (addr=start; addr < end; ++addr)
    asm("dcbst 0,%0" : : "r" (addr) );

  /* wait for update to commit */
  asm("sync");

  /* invalidate icache */
  /* Note: if one knew the cache line size, one could write a better loop */
  for (addr=start; addr<end; ++addr)
    asm("icbi 0,%0" : : "r" (addr) );

  /* context synchronization */
  asm("isync");
#endif
}

//
// Sweep through memory to find which areas of memory are mappable.
// This is invoked from a command-line argument.
void findMappable()
{
  Address i;
  Address granularity = 1 << 22; // every 4 megabytes
  Address max = (1L << ((sizeof(Address)*8)-2)) / (granularity >> 2);
  CONSOLE_PRINTF("Attempting to find mappable blocks of size %zu\n", pageSize);
  for (i = 0; i < max; i++) {
    char *start = (char *) (i * granularity);
    int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
    int flag = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
    void *result = mmap (start, (size_t) pageSize, prot, flag, -1, 0);
    int fail = (result == (void *) -1) || (result != ((void *)start));
    if (fail) {
      CONSOLE_PRINTF( "%p FAILED with errno %d: %s\n", start, errno, strerror(errno));
    } else {
      CONSOLE_PRINTF( "%p SUCCESS\n", start);
      munmap(start, (size_t) pageSize);
    }
  }
}

EXTERNAL Extent pageRoundUp(Extent size, Extent pageSize)
{
  return ((size + pageSize - 1) / pageSize) * pageSize;
}
