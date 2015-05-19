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

/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception handling.
 * The file "sys.cpp" contains the o/s support services required by the java class libraries.
 *
 * IA32 version for Linux
 */

#ifdef HARMONY
#define LINUX
#include "hythread.h"
#endif

#ifndef _GNU_SOURCE
#define _GNU_SOURCE             // so that string.h will include strsignal()
#endif
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <sys/mman.h>
#include <errno.h>
#include <string.h>
#include <stdarg.h>             // va_list, va_start(), va_end()
#ifndef __USE_GNU         // Deal with current ucontext ugliness.  The current
#define __USE_GNU         // mess of asm/ucontext.h & sys/ucontext.h and their
#include <sys/ucontext.h> // mutual exclusion on more recent versions of gcc
#undef __USE_GNU          // dictates this ugly hack.
#else
#include <sys/ucontext.h>
#endif
#include <assert.h>

#ifdef __APPLE__
#include "osx_ucontext.h"
#endif

#ifdef __linux__
#include "linux_ucontext.h"
#endif

#if defined (__SVR4) && defined (__sun)
typedef unsigned int u_int32_t;
#include "solaris_ucontext.h"
#endif

#define __STDC_FORMAT_MACROS    // include PRIxPTR
#include <inttypes.h>           // PRIxPTR, uintptr_t

#ifndef __SIZEOF_POINTER__
#  ifdef __x86_64__
#    define __SIZEOF_POINTER__ 8
#  else
#    define __SIZEOF_POINTER__ 4
#  endif
#endif

/* Interface to virtual machine data structures. */
#define NEED_EXIT_STATUS_CODES
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_BOOT_RECORD_INITIALIZATION
#define NEED_MEMORY_MANAGER_DECLARATIONS
#include <sys.h>

extern void setLinkage(struct BootRecord* br);

#include "../bootImageRunner.h" // In tools/bootImageRunner

// These are definitions of items declared in bootImageRunner.h

/* jump buffer for primordial thread */
jmp_buf primordial_jb;

/* Command line arguments to be passed to virtual machine. */
const char **JavaArgs;
int JavaArgc;

/* global; startup configuration option with default values */
const char *bootCodeFilename = 0;

/* global; startup configuration option with default values */
const char *bootDataFilename = 0;

/* global; startup configuration option with default values */
const char *bootRMapFilename = 0;

/* Emit trace information? */
int lib_verbose = 0;

/* Location of jtoc within virtual machine image. */
static Address VmToc;

/* TOC offset of Scheduler.dumpStackAndDie */
static Offset DumpStackAndDieOffset;

/* TOC offset of Scheduler.debugRequested */
static Offset DebugRequestedOffset;

/* name of program that will load and run RVM */
char *Me;

static struct BootRecord *bootRecord;

/*
 * Bootimage is loaded and ready for execution:
 * you can set a breakpoint here with gdb.
 */
int
bootThread (void *ip, void *tr, void *sp)
{
  static void *saved_esp;
  void *saved_ebp;
  asm (
#ifndef __x86_64__
    "mov   %%ebp, %0     \n"
    "mov   %%esp, %%ebp  \n"
    "mov   %4, %%esp     \n"
    "push  %%ebp         \n"
    "call  *%%eax        \n"
    "pop   %%esp         \n"
    "mov   %0, %%ebp     \n"
#else
    "mov   %%rbp, %0     \n"
    "mov   %%rsp, %1     \n"
    "mov   %4, %%rsp     \n"
    "call  *%%rax        \n"
    "mov   %1, %%rsp     \n"
    "mov   %0, %%rbp     \n"
#endif
    : "=m"(saved_ebp),
    "=m"(saved_esp)
    : "a"(ip), // EAX = Instruction Pointer
    "S"(tr), // ESI = Thread Register
    "r"(sp)
  );
}


int
inRVMAddressSpace(Address addr)
{
  int which;
  /* get the boot record */
  Address *heapRanges = bootRecord->heapRanges;
  for (which = 0; which < MAXHEAPS; which++) {
    Address start = heapRanges[2 * which];
    Address end = heapRanges[2 * which + 1];
    // Test against sentinel.
    if (start == ~(Address) 0 && end == ~ (Address) 0) break;
    if (start <= addr  && addr < end) {
      return 1;
    }
  }
  return 0;
}

/**
 * Compute the number of bytes used to encode the given modrm part of
 * an Intel instruction
 *
 * @param modrm [in] value to decode
 * @return number of bytes used to encode modrm and optionally an SIB
 * byte and displacement
 */
static int decodeModRMLength(Address modrmAddress)
{
  unsigned char sib;
  unsigned char tmp;
  unsigned char modrm =  ((unsigned char*)modrmAddress)[0];
  unsigned char mod = (modrm >> 6) & 3;
  unsigned char r_m = (modrm & 7);
  switch (mod) {
    case 0: // reg, [reg]
      switch (r_m) {
        case 4: // SIB byte
          // Need to decode SIB byte because mod 0 can have displacement
          sib = ((unsigned char*)modrmAddress)[1];
          tmp = sib & 7;
          if (tmp == 5) {
            return 6; // mod + sib + 4 byte displacement
          } else {
            return 2; // mod + sib
          }
        case 5: // disp32
          return 5;
        default:
          return 1;
      }
    case 1: // reg, [reg+disp8]
      switch (r_m) {
        case 4: // SIB byte
          return 3;
        default:
          return 2;
      }
    case 2: // reg, [reg+disp32]
      switch (r_m) {
        case 4: // SIB byte
          return 6;
        default:
          return 5;
      }
    default: // case 3 - reg, reg
      return 1;
  }
}

static unsigned char decodeOpcodeFromModrm(Address modrmAddr) {
  unsigned char modrm = ((unsigned char*)modrmAddr)[0];
  return (modrm >> 3) & 7;
}

/**
 * So stack maps can treat faults as call-return we must be able to
 * determine the address of the next instruction. This isn't easy on
 * Intel with variable length instructions.
 *
 * This function also handles instructions that cannot cause faults. The advantage
 * of this approach is that it is possible to test this function by applying it
 * to compiled methods (e.g. from the bootimage or compiled at runtime). This can
 * help with sanity checking on the instruction sizes, e.g. by comparing the
 * results with those of a disassembler.
 *
 * TODO this function is known to be incomplete. However, it's
 * still better than the old disassembler which considered some valid instructions
 * to be illegal.
 */
static Address getInstructionFollowing(Address faultingInstructionAddress) {
  unsigned char opcode;
  Address modrmAddr;
  int size = 0;
  int two_byte_opcode = 0;
  int prefixes_done;
  int has_variable_immediate = 0; // either 16 or 32 bits in non-x64 mode, 32 bits is default
  int operand_size_override = 0;
  if (!inRVMAddressSpace(faultingInstructionAddress)) {
    ERROR_PRINTF("%s: Failing instruction starting at %x wasn't in RVM address space\n",
        Me, faultingInstructionAddress);
    return (Address) -1;
  }
  opcode = ((unsigned char*)faultingInstructionAddress)[0];
  size++;
  /* Process prefix bytes */
  prefixes_done = 0;
  while (!prefixes_done) {
    switch (opcode) {
      case 0x66:            // operand size override or compulsory SSE prefix
        operand_size_override = 1;
        // fallthrough
      case 0x2E: case 0x3E: // CS or DS segment override, or branch likely hints
      case 0x26: case 0x36: // ES or SS segment override
      case 0x64: case 0x65: // FS or GS segment override
      case 0x67:            // address size prefix
      case 0xF0:            // lock prefix
      case 0xF2: case 0xF3: // rep prefixes or compulsory SSE prefix
        opcode = ((unsigned char*)faultingInstructionAddress)[size];
        size++;
        break;
      default:
        prefixes_done = 1;
        break;
    }
  }
#ifdef __x86_64__
  /* handle any REX prefix */
  if (opcode >= 0x40 && opcode <= 0x4F) {
    opcode = ((unsigned char*)faultingInstructionAddress)[size];
    size++;
  }
#endif __x86_64__

  /* One-byte opcodes */
  switch (opcode) {
    case 0x40: case 0x41: case 0x42: case 0x43: // inc using alternate encoding
    case 0x44: case 0x45: case 0x46: case 0x47:
    case 0x48: case 0x49: case 0x4A: case 0x4B: // dec using alternate encoding
    case 0x4C: case 0x4D: case 0x4E: case 0x4F:
    case 0x50: case 0x51: case 0x52: case 0x53: // push using alternate encoding
    case 0x54: case 0x55: case 0x56: case 0x57:
    case 0x58: case 0x59: case 0x5A: case 0x5B: // pop using alternate encoding
    case 0x5C: case 0x5D: case 0x5E: case 0x5F:
    case 0x90: // nop
    case 0x9E: // sahf
    case 0x98: // cbw, cwde
    case 0x99: // cdq
    case 0xC3: case 0xCB: // return from procedure (no argument)
    case 0xCC: // single-step interrupt 3
      // size was already increased for reading opcodes, so it's correct
      break;

    case 0x6A: // push imm8
    case 0x70: case 0x71: case 0x72: case 0x73: // conditional jumps
    case 0x74: case 0x75: case 0x76: case 0x77:
    case 0x78: case 0x79: case 0x7A: case 0x7B:
    case 0x7C: case 0x7D: case 0x7E: case 0x7F:
    case 0xA8: // test imm8
    case 0xB0: case 0xB1: case 0xB2: case 0xB3: // mov
    case 0xB4: case 0xB5: case 0xB6: case 0xB7:
    case 0xCD: // int imm8
    case 0xEB: // unconditional jump
      size++; // 1 byte immediate
      break;

    case 0xC2: case 0xCA: // return from procedure, 2 bytes immediate
      size += 2; // 2 bytes immediate
      break;

    case 0x05: // add imm16/32
    case 0x0D: // or imm16/32
    case 0x15: // adc imm16/32
    case 0x25: // and imm16/32
    case 0x35: // xor imm16/32
    case 0x3D: // cmp imm32
    case 0x68: // push imm16/32
    case 0xA9: // test imm16/32
    case 0xB8: case 0xB9: case 0xBA: case 0xBB: // mov imm16/32
    case 0xBC: case 0xBD: case 0xBE: case 0xBF:
    case 0xE8: // call imm16/32
    case 0xE9: // jmp imm16/32
      has_variable_immediate = 1;
      break;

    case 0x6B: // imul r r/m imm8
    case 0x80: // extended opcodes using r/m8 imm8
    case 0x82: // extended opcodes using r/m8 imm8
    case 0x83: // extended opcodes using r/m16/32, imm8
    case 0xC0: case 0xC1: // rotates and shifts with r/m16/32, imm8
    case 0xC6: // mov
      modrmAddr = faultingInstructionAddress + size;
      size++; // 1 byte immediate
      size += decodeModRMLength(modrmAddr); // account for the size of the modrm
      break;
    case 0x69: // imul r r/m imm32
    case 0x81: // extended opcodes using  r/m16/32, imm32
    case 0xC7:
      modrmAddr = faultingInstructionAddress + size;
      has_variable_immediate = 1;
      size += decodeModRMLength(modrmAddr); // account for the size of the modrm
      break;
    case 0xF6:
      modrmAddr = faultingInstructionAddress + size;
      opcode = decodeOpcodeFromModrm(modrmAddr);
      switch (opcode) {
        case 0x00: case 0x01:
          size++;
          break;
        default:
          break;
      }
      size += decodeModRMLength(modrmAddr); // account for the size of the modrm
      break;
    case 0xF7:
      modrmAddr = faultingInstructionAddress + size;
      opcode = decodeOpcodeFromModrm(modrmAddr);
      switch (opcode) {
        case 0x00: case 0x01:
          has_variable_immediate = 1;
          break;
        default:
          break;
      }
      size += decodeModRMLength(modrmAddr); // account for the size of the modrm
      break;
    case 0x0B: case 0x13: case 0x1B: case 0x23: // op r, r/m
    case 0x2B: case 0x33: case 0x3A: case 0x3B: case 0x8A:
    case 0x8B:

    case 0x00: case 0x01: case 0x02: case 0x03: // op r/m, r
    case 0x09: case 0x11: case 0x19: case 0x21:
    case 0x29: case 0x31: case 0x38: case 0x39:

    case 0x84: case 0x85: // test
    case 0x88: // mov
    case 0x89: // mov r/m, r
    case 0x8D: // lea
    case 0x8F: // pop r/m
    case 0xD1: // rotate r/m or shift r/m
    case 0xD3: // rotate r/m or shift r/m
    case 0xDD: case 0xDF:  // floating point
    case 0xD9: // floating point
    case 0xDB:
    case 0xFF: // push, jmp, jmpf, call, callf, inc, dec - with mod r/m
      modrmAddr = faultingInstructionAddress + size;
      size += decodeModRMLength(modrmAddr); // account for the size of the modrm
      break;
    case 0x0F: // two byte opcode
      two_byte_opcode = 1;
      opcode = ((unsigned char*)faultingInstructionAddress)[size];
      size++;
      break;
    default:
      ERROR_PRINTF("%s: Unhandled opcode 0x%x during decoding of instruction at %x, stopped decoding\n",
          Me, opcode, faultingInstructionAddress);
      return (Address) 0;
  }

  /* two byte opcodes */
  if (two_byte_opcode == 1) {
    switch (opcode) {
      case 0x31: // rdtsc - read time stamp counter
        break;

      case 0x80: case 0x81: case 0x82: case 0x83: // conditional jumps
      case 0x84: case 0x85: case 0x86: case 0x87:
      case 0x88: case 0x89: case 0x8A: case 0x8B:
      case 0x8C: case 0x8D: case 0x8E: case 0x8F:
        has_variable_immediate = 1;
        break;
      case 0x10: // movss
      case 0x11: // movsd
      case 0x12: // movlpd
      case 0x13: // movlpd
      case 0x18: // prefetch & hint nop
      case 0x19: case 0x1A: case 0x1B: case 0x1C: // hint nop
      case 0x1D: case 0x1E: case 0x1F:
      case 0x2A: // ctpi2ps, cvtsi2ss, cvtpi2pd, cvtsi2sd
      case 0x2C: // cvttps2pi, cvttss2si, cvttpd2pi, cvttsd2si
      case 0x2E: // ucomisd, ucomiss
      case 0x45: // cmovnz, cmovne
      case 0x51: // sqrtps, sqrtss, sqrtpd, sqrtsd
      case 0x54: // andps, andpd, andnps, andnpd
      case 0x57: // xorps, xorpd
      case 0x58: // addps, addss, addpd, addsd
      case 0x59: // mulps, mulss, mulpd, mulsd
      case 0x5A: // cvtss2sd, cvtsd2ss
      case 0x5C: // subps, subss, subpd, subsd
      case 0x5E: // divsd
      case 0x6E: // movd
      case 0x7E: // movd
      case 0x90: case 0x91: case 0x92: case 0x93: // set
      case 0x94: case 0x95: case 0x96: case 0x97:
      case 0x98: case 0x99: case 0x9A: case 0x9B:
      case 0x9C: case 0x9D: case 0x9E: case 0x9F:
      case 0xA3: // bt
      case 0xA5: // shld
      case 0xAD: // shrd
      case 0xAF: // imul
      case 0xB1: // cmpxchg
      case 0xB6: case 0xB7: // movzx
      case 0xBE: case 0xBF: // movsx
      case 0xC7: // cmpxchg8b
      case 0xD3: // psrlq
      case 0xD6: //movdq2q
      case 0xF3: // psllq
        modrmAddr = faultingInstructionAddress + size;
        size += decodeModRMLength(modrmAddr);
        break;
      default:
        ERROR_PRINTF("%s: Unhandled opcode 0x%x during decoding of second opcode byte of two-byte opcode from instruction at %x, stopped decoding\n",
            Me, opcode, faultingInstructionAddress);
        return (Address) 0;
      }
  }

  if (has_variable_immediate == 1) {
    if (operand_size_override == 1) {
      size += 2;
    } else {
      size += 4;
    }
  }

  return faultingInstructionAddress + size;
}

static int
isVmSignal(Address ip, Address vpAddress)
{
  return inRVMAddressSpace(ip) && inRVMAddressSpace(vpAddress);
}

// variables and helper methods for alignment checking

#ifdef RVM_WITH_ALIGNMENT_CHECKING

// these vars help implement the two-phase trap handler approach for alignment checking
static Address alignCheckHandlerJumpLocation = 0; //
static unsigned char alignCheckHandlerInstBuf[100]; // ought to be enough to hold two instructions :P

// if enabled, print a character for each alignment trap (whether or not we ignore it)
static int alignCheckVerbose = 0;

// statistics defined in sys.cpp
EXTERNAL volatile int numNativeAlignTraps;
EXTERNAL volatile int numEightByteAlignTraps;
EXTERNAL volatile int numBadAlignTraps;

// called by the hardware trap handler if we're dealing with an alignment error;
// returns true iff the trap handler should return immediately
int handleAlignmentTrap(int signo, void* context) {
  if (signo == SIGBUS) {
    // first turn off alignment checks for the handler so we can don't cause another one here
    __asm__ __volatile__("pushf\n\t"
                         "andl $0xfffbffff,(%esp)\n\t"
                         "popf");

    // get the structures we need
    ucontext_t *uc = (ucontext_t *) context;  // user context
    mcontext_t *mc = &(uc->uc_mcontext);      // machine context
    greg_t  *gregs = mc->gregs;               // general purpose registers
    // get the faulting IP
    Address localInstructionAddress     = gregs[REG_EIP];

    // decide what kind of alignment error this is and whether to ignore it;
    // if we ignore it, then the normal handler will take care of it
    int ignore = 0;
    if (!inRVMAddressSpace(localInstructionAddress)) {
      // the IP is outside the VM, so ignore it
      if (alignCheckVerbose) {
        CONSOLE_PRINTF("N"); // native code, apparently
      }
      ignore = 1;
      numNativeAlignTraps++;
    } else {
        // any unaligned access -- these are probably bad
        // FIXME there was previously code here to disassemble instructions and filter
        // 8-byte acceses. With the removal of the IA32 disassembler, the alignment checking
        // code needs an update. The alignment checking code also needs an update to
        // work with native threads.
        if (alignCheckVerbose) {
          CONSOLE_PRINTF("*"); // other
        }
        ignore = 0;
        numBadAlignTraps++;
    }

    if (ignore) {
      // we can ignore the exception by returning to a code block
      // that we create that consists of
      // (1) a copy of the faulting instruction
      // (2) a trap
      // we turn off alignment exceptions so we execute the faulting instruction and then trap;
      // the trap will end up in the "if alignCheckHandlerJumpLocation)" section below
      alignCheckHandlerJumpLocation = getInstructionFollowing(localInstructionAddress);
      int length = alignCheckHandlerJumpLocation - localInstructionAddress;
      if (!length) {
        CONSOLE_PRINTF("\nUnexpected zero length\n");
        exit(1);
      }
      for (int i = 0; i < length; i++) {
        alignCheckHandlerInstBuf[i] = ((unsigned char*)localInstructionAddress)[i];
      }
      alignCheckHandlerInstBuf[length] = 0xCD; // INT opcode
      alignCheckHandlerInstBuf[length + 1] = 0x43; // not sure which interrupt this is, but it works
      // save the next instruction
      gregs[REG_EFL] &= 0xfffbffff;
      gregs[REG_EIP] = (Address)(void*)alignCheckHandlerInstBuf;
      return 1;
    }
  }

  // alignment checking: handle the second phase of align traps that the code above decided to ignore
  if (alignCheckHandlerJumpLocation) {
    // get needed structures
    ucontext_t *uc = (ucontext_t *) context;  // user context
    mcontext_t *mc = &(uc->uc_mcontext);      // machine context
    greg_t  *gregs = mc->gregs;               // general purpose registers
    // turn alignment exceptions back on
    gregs[REG_EFL] |= 0x00040000;
    // jump to the instruction following the original faulting instruction
    gregs[REG_EIP] = alignCheckHandlerJumpLocation;
    // clear the location so we don't end up here on the next trap
    alignCheckHandlerJumpLocation = 0;
    return 1;
  }

  return 0;
}

#endif // RVM_WITH_ALIGNMENT_CHECKING

static void
hardwareTrapHandler(int signo, siginfo_t *si, void *context)
{
  // alignment checking: handle hardware alignment exceptions
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  if (signo == SIGBUS || alignCheckHandlerJumpLocation) {
    int returnNow = handleAlignmentTrap(signo, context);
    if (returnNow) {
      return;
    }
  }
#endif // RVM_WITH_ALIGNMENT_CHECKING

  Address localInstructionAddress;

  if (lib_verbose)
    CONSOLE_PRINTF("hardwareTrapHandler: thread = %p\n", getThreadId());

  Address localNativeThreadAddress;
  Address localFrameAddress;
  Address localJTOC = VmToc;

  int isRecoverable;

  Address instructionFollowing;

  /*
   * fill local working variables from context saved by OS trap handler
   * mechanism
   */
  localInstructionAddress  = IA32_EIP(context);
  localNativeThreadAddress = IA32_ESI(context);

  // We are prepared to handle these kinds of "recoverable" traps:
  //
  //  1. SIGSEGV - a null object dereference of the form "obj[-fieldOffset]"
  //               that wraps around to segment 0xf0000000.
  //
  //  2. SIGFPE  - interger divide by zero trap
  //
  //  3. SIGTRAP - stack overflow trap
  //
  // Anything else indicates some sort of unrecoverable vm error.
  //
  isRecoverable = 0;

  if (isVmSignal(localInstructionAddress, localNativeThreadAddress))
  {
    if (lib_verbose) CONSOLE_PRINTF("it's a VM signal.\n");

    if (signo == SIGSEGV /*&& check the adddress TODO */)
      isRecoverable = 1;

    else if (signo == SIGFPE)
      isRecoverable = 1;

    else if (signo == SIGTRAP)
      isRecoverable = 0;

    // alignment checking: hardware alignment exceptions are recoverable (i.e., we want to jump to the Java handler)
#ifdef RVM_WITH_ALIGNMENT_CHECKING
    else if (signo == SIGBUS)
      isRecoverable = 1;
#endif // RVM_WITH_ALIGNMENT_CHECKING

    else
      ERROR_PRINTF("%s: WHOOPS.  Got a signal (%s; #%d) that the hardware signal handler wasn't prepared for.\n", Me,  strsignal(signo), signo);
  } else {
    ERROR_PRINTF("%s: TROUBLE.  Got a signal (%s; #%d) from outside the VM's address space in thread %p.\n", Me,  strsignal(signo), signo, getThreadId());
  }




  if (lib_verbose || !isRecoverable)
  {
    ERROR_PRINTF("%s:%s trapped signal %d (%s)\n", Me,
             isRecoverable? "" : " UNRECOVERABLE",
             signo, strsignal(signo));

    ERROR_PRINTF("handler stack %x\n", localInstructionAddress);
    if (signo == SIGSEGV)
      ERROR_PRINTF("si->si_addr   %p\n", si->si_addr);
#ifndef __x86_64__
    ERROR_PRINTF("cs            0x%08x\n", IA32_CS(context));
    ERROR_PRINTF("ds            0x%08x\n", IA32_DS(context));
    ERROR_PRINTF("es            0x%08x\n", IA32_ES(context));
    ERROR_PRINTF("fs            0x%08x\n", IA32_FS(context));
    ERROR_PRINTF("gs            0x%08x\n", IA32_GS(context));
    ERROR_PRINTF("ss            0x%08x\n", IA32_SS(context));
#else
    ERROR_PRINTF("r8           0x%08x\n", IA32_R8(context));
    ERROR_PRINTF("r9           0x%08x\n", IA32_R9(context));
    ERROR_PRINTF("r10           0x%08x\n", IA32_R10(context));
    ERROR_PRINTF("r11           0x%08x\n", IA32_R11(context));
    ERROR_PRINTF("r12           0x%08x\n", IA32_R12(context));
    ERROR_PRINTF("r13           0x%08x\n", IA32_R13(context));
    ERROR_PRINTF("r14           0x%08x\n", IA32_R14(context));
    ERROR_PRINTF("r15           0x%08x\n", IA32_R15(context));
#endif
    ERROR_PRINTF("edi           0x%08x\n", IA32_EDI(context));
    ERROR_PRINTF("esi -- PR/VP  0x%08x\n", IA32_ESI(context));
    ERROR_PRINTF("ebp           0x%08x\n", IA32_EBP(context));
    ERROR_PRINTF("esp -- SP     0x%08x\n", IA32_ESP(context));
    ERROR_PRINTF("ebx           0x%08x\n", IA32_EBX(context));
    ERROR_PRINTF("edx           0x%08x\n", IA32_EDX(context));
    ERROR_PRINTF("ecx           0x%08x\n", IA32_ECX(context));
    ERROR_PRINTF("eax           0x%08x\n", IA32_EAX(context));
    ERROR_PRINTF("eip           0x%08x\n", IA32_EIP(context));
    ERROR_PRINTF("trapno        0x%08x\n", IA32_TRAPNO(context));
    ERROR_PRINTF("err           0x%08x\n", IA32_ERR(context));
    ERROR_PRINTF("eflags        0x%08x\n", IA32_EFLAGS(context));
    // ERROR_PRINTF("esp_at_signal 0x%08x\n", IA32_UESP(context));
    /* null if fp registers haven't been used yet */
    ERROR_PRINTF("fpregs        %x\n", IA32_FPREGS(context));
#ifndef __x86_64__
    ERROR_PRINTF("oldmask       0x%08lx\n", (unsigned long) IA32_OLDMASK(context));
    ERROR_PRINTF("cr2           0x%08lx\n",
             /* seems to contain mem address that
              * faulting instruction was trying to
              * access */
             (unsigned long) IA32_FPFAULTDATA(context));
#endif
    /*
     * There are 8 floating point registers, each 10 bytes wide.
     * See /usr/include/asm/sigcontext.h
     */

//Solaris doesn't seem to support these
#if !(defined (__SVR4) && defined (__sun))
    if (IA32_FPREGS(context)) {
      ERROR_PRINTF("fp0 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 0, 0) & 0xffff,
               IA32_STMM(context, 0, 1) & 0xffff,
               IA32_STMM(context, 0, 2) & 0xffff,
               IA32_STMM(context, 0, 3) & 0xffff,
               IA32_STMMEXP(context, 0) & 0xffff);
      ERROR_PRINTF("fp1 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 1, 0) & 0xffff,
               IA32_STMM(context, 1, 1) & 0xffff,
               IA32_STMM(context, 1, 2) & 0xffff,
               IA32_STMM(context, 1, 3) & 0xffff,
               IA32_STMMEXP(context, 1) & 0xffff);
      ERROR_PRINTF("fp2 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 2, 0) & 0xffff,
               IA32_STMM(context, 2, 1) & 0xffff,
               IA32_STMM(context, 2, 2) & 0xffff,
               IA32_STMM(context, 2, 3) & 0xffff,
               IA32_STMMEXP(context, 2) & 0xffff);
      ERROR_PRINTF("fp3 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 3, 0) & 0xffff,
               IA32_STMM(context, 3, 1) & 0xffff,
               IA32_STMM(context, 3, 2) & 0xffff,
               IA32_STMM(context, 3, 3) & 0xffff,
               IA32_STMMEXP(context, 3) & 0xffff);
      ERROR_PRINTF("fp4 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 4, 0) & 0xffff,
               IA32_STMM(context, 4, 1) & 0xffff,
               IA32_STMM(context, 4, 2) & 0xffff,
               IA32_STMM(context, 4, 3) & 0xffff,
               IA32_STMMEXP(context, 4) & 0xffff);
      ERROR_PRINTF("fp5 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 5, 0) & 0xffff,
               IA32_STMM(context, 5, 1) & 0xffff,
               IA32_STMM(context, 5, 2) & 0xffff,
               IA32_STMM(context, 5, 3) & 0xffff,
               IA32_STMMEXP(context, 5) & 0xffff);
      ERROR_PRINTF("fp6 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 6, 0) & 0xffff,
               IA32_STMM(context, 6, 1) & 0xffff,
               IA32_STMM(context, 6, 2) & 0xffff,
               IA32_STMM(context, 6, 3) & 0xffff,
               IA32_STMMEXP(context, 6) & 0xffff);
      ERROR_PRINTF("fp7 0x%04x%04x%04x%04x%04x\n",
               IA32_STMM(context, 7, 0) & 0xffff,
               IA32_STMM(context, 7, 1) & 0xffff,
               IA32_STMM(context, 7, 2) & 0xffff,
               IA32_STMM(context, 7, 3) & 0xffff,
               IA32_STMMEXP(context, 7) & 0xffff);
    }
#endif
    if (isRecoverable) {
      CONSOLE_PRINTF("%s: normal trap\n", Me);
    } else {
      CONSOLE_PRINTF("%s: internal error\n", Me);
    }
  }

  /* test validity of native thread address */
  if (!inRVMAddressSpace(localNativeThreadAddress))
  {
    ERROR_PRINTF("invalid native thread address (not an address %x)\n",
             localNativeThreadAddress);
    abort();
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }

  /* get the frame pointer from thread object  */
  localFrameAddress =
    *(unsigned *) (localNativeThreadAddress + Thread_framePointer_offset);

  /* test validity of frame address */
  if (!inRVMAddressSpace(localFrameAddress))
  {
    ERROR_PRINTF("invalid frame address (not an address %x)\n",
             localFrameAddress);
    abort();
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }


  int HardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
  Address javaExceptionHandlerAddress =
    *(Address *) (localJTOC + bootRecord->deliverHardwareExceptionOffset);

  DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

  /* get the active thread id */
  Address threadObjectAddress = (Address)localNativeThreadAddress;

  /* then get its hardware exception registers */
  Address registers =
    *(Address *) (threadObjectAddress +
                  RVMThread_exceptionRegisters_offset);

  /* get the addresses of the gps and other fields in the Registers object */
  Address *vmr_gprs  = *(Address **) (registers + Registers_gprs_offset);
  Address vmr_ip     =  (Address)    (registers + Registers_ip_offset);
  Address vmr_fp     =  (Address)    (registers + Registers_fp_offset);
  Address *vmr_inuse =  (Address *)  (registers + Registers_inuse_offset);

  Address sp;
  Address fp;

  /* Test for recursive errors -- if so, take one final stacktrace and exit
   */
  if (*vmr_inuse || !isRecoverable) {
    if (*vmr_inuse)
      ERROR_PRINTF("%s: internal error: recursive use of hardware exception registers (exiting)\n", Me);
    /*
     * Things went badly wrong, so attempt to generate a useful error dump
     * before exiting by returning to Scheduler.dumpStackAndDie passing
     * it the fp of the offending thread.
     *
     * We could try to continue, but sometimes doing so results
     * in cascading failures
     * and it's hard to tell what the real problem was.
     */
    int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);

    /* setup stack frame to contain the frame pointer */
    sp = IA32_ESP(context);

    /* put fp as a  parameter on the stack  */
    IA32_ESP(context) = IA32_ESP(context) - __SIZEOF_POINTER__;
    sp = IA32_ESP(context);
    ((Address *)sp)[0] = localFrameAddress;
    IA32_EAX(context) = localFrameAddress; // must pass localFrameAddress in first param register!

    /* put a return address of zero on the stack */
    IA32_ESP(context) = IA32_ESP(context) - __SIZEOF_POINTER__;
    sp = IA32_ESP(context);
    ((Address*)sp)[0] = 0;

    /* set up to goto dumpStackAndDie routine ( in Scheduler) as if called */
    IA32_EIP(context) = dumpStack;
    *vmr_inuse = 0;

    return;
  }

  *vmr_inuse = 1;                     /* mark in use to avoid infinite loop */

  /* move gp registers to Registers object */
  vmr_gprs[Constants_EAX] = IA32_EAX(context);
  vmr_gprs[Constants_ECX] = IA32_ECX(context);
  vmr_gprs[Constants_EDX] = IA32_EDX(context);
  vmr_gprs[Constants_EBX] = IA32_EBX(context);
  vmr_gprs[Constants_ESP] = IA32_ESP(context);
  vmr_gprs[Constants_EBP] = IA32_EBP(context);
  vmr_gprs[Constants_ESI] = IA32_ESI(context);
  vmr_gprs[Constants_EDI] = IA32_EDI(context);
#ifdef __x86_64__
  vmr_gprs[Constants_R8]  = IA32_R8(context);
  vmr_gprs[Constants_R9]  = IA32_R9(context);
  vmr_gprs[Constants_R10] = IA32_R10(context);
  vmr_gprs[Constants_R11] = IA32_R11(context);
  vmr_gprs[Constants_R12] = IA32_R12(context);
  vmr_gprs[Constants_R13] = IA32_R13(context);
  vmr_gprs[Constants_R14] = IA32_R14(context);
  vmr_gprs[Constants_R15] = IA32_R15(context);
#endif //__x86_64__

  /* set the next instruction for the failing frame */
  instructionFollowing = getInstructionFollowing(localInstructionAddress);
  if (instructionFollowing == 0 || instructionFollowing == -1) {
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }

  /*
   * Advance ESP to the guard region of the stack.
   * Enables opt compiler to have ESP point to somewhere
   * other than the bottom of the frame at a PEI (see bug 2570).
   *
   * We'll execute the entire code sequence for
   * Runtime.deliverHardwareException et al. in the guard region of the
   * stack to avoid bashing stuff in the bottom opt-frame.
   */
  sp = IA32_ESP(context);
  Address stackLimit
    = *(Address *)(threadObjectAddress + RVMThread_stackLimit_offset);
  if (sp <= stackLimit - 384) {
    ERROR_PRINTF("sp (%x)too far below stackLimit (%x)to recover\n", sp, stackLimit);
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  sp = stackLimit - 384;
  stackLimit -= Constants_STACK_SIZE_GUARD;
  *(Address *)(threadObjectAddress + RVMThread_stackLimit_offset) = stackLimit;

  /* Insert artificial stackframe at site of trap. */
  /* This frame marks the place where "hardware exception registers" were saved. */
  sp = sp - Constants_STACKFRAME_HEADER_SIZE;
  fp = sp - __SIZEOF_POINTER__ - Constants_STACKFRAME_BODY_OFFSET;
  /* fill in artificial stack frame */
  ((Address*)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET))[0]  = localFrameAddress;
  ((int *)   (fp + Constants_STACKFRAME_METHOD_ID_OFFSET))[0]      = HardwareTrapMethodId;
  ((Address*)(fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET))[0] = instructionFollowing;

  /* fill in call to "deliverHardwareException" */
  sp = sp - __SIZEOF_POINTER__; /* first parameter is type of trap */

  if (signo == SIGSEGV) {
    *(int *) sp = Runtime_TRAP_NULL_POINTER;

    /* an int immediate instruction produces a SIGSEGV signal.
       An int 3 instruction a trap fault */
    if (*(unsigned char *)(localInstructionAddress) == 0xCD) {
      // is INT imm instruction
      unsigned char code = *(unsigned char*)(localInstructionAddress+1);
      code -= Constants_RVM_TRAP_BASE;
      *(int *) sp = code;
    }
  }

  else if (signo == SIGFPE) {
    *(int *) sp = Runtime_TRAP_DIVIDE_BY_ZERO;
  }

  else if (signo == SIGTRAP) {
    *(int *) sp = Runtime_TRAP_UNKNOWN;

    //CONSOLE_PRINTF("op code is 0x%x",*(unsigned char *)(localInstructionAddress));
    //CONSOLE_PRINTF("next code is 0x%x",*(unsigned char *)(localInstructionAddress+1));
    if (*(unsigned char *)(localInstructionAddress - 1) == 0xCC) {
      // is INT 3 instruction
    }
  }
  else {
    *(int *) sp = Runtime_TRAP_UNKNOWN;
  }
  IA32_EAX(context) = *(Address *)sp; // also pass first param in EAX.
  if (lib_verbose)
    CONSOLE_PRINTF("Trap code is 0x%x\n", IA32_EAX(context));
  sp = sp - __SIZEOF_POINTER__; /* next parameter is info for array bounds trap */
  *(int *) sp = (Address)*(unsigned *) (localNativeThreadAddress + Thread_arrayIndexTrapParam_offset);
  IA32_EDX(context) = *(int *)sp; // also pass second param in EDX.
  sp = sp - __SIZEOF_POINTER__; /* return address - looks like called from failing instruction */
  *(Address *) sp = instructionFollowing;

  /* store instructionFollowing and fp in Registers,ip and Registers.fp */
  *(Address*)vmr_ip = instructionFollowing;
  *(Address*)vmr_fp = localFrameAddress;

  if (lib_verbose)
    CONSOLE_PRINTF("Set vmr_fp to 0x%x\n", localFrameAddress);

  /* set up context block to look like the artificial stack frame is
   * returning  */
  IA32_ESP(context) = sp;
  IA32_EBP(context) = fp;
  *(Address*) (localNativeThreadAddress + Thread_framePointer_offset) = fp;

  /* setup to return to deliver hardware exception routine */
  IA32_EIP(context) = javaExceptionHandlerAddress;

  if (lib_verbose)
    CONSOLE_PRINTF("exiting normally; the context will take care of the rest (or so we hope)\n");
}


static void
softwareSignalHandler(int signo,
                      siginfo_t UNUSED *si,
                      void *context)
{
  // asynchronous signal used to awaken internal debugger
  if (signo == SIGQUIT) {
    // Turn on debug-request flag.
    // Note that "jtoc" is not necessarily valid, because we might have interrupted
    // C-library code, so we use boot image jtoc address (== VmToc) instead.
    // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
    // For now, we assume table is fixed in boot image and never moves.
    //
    unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
    if (*flag) {
      TRACE_PRINTF("%s: debug request already in progress, please wait\n", Me);
    } else {
      TRACE_PRINTF("%s: debug requested, waiting for a thread switch\n", Me);
      *flag = 1;
    }
    return;
  }

  /** We need to adapt this code so that we run the exit handlers
      appropriately. */

  if (signo == SIGTERM) {
    // Presumably we received this signal because someone wants us
    // to shut down.  Exit directly (unless the lib_verbose flag is set).
    // TODO: Run the shutdown hooks instead.
    if (!lib_verbose) {
      /* Now reraise the signal.  We reactivate the signal's
         default handling, which is to terminate the process.
         We could just call `exit' or `abort',
         but reraising the signal sets the return status
         from the process correctly.
         TODO: Go run shutdown hooks before we re-raise the signal. */
      signal(signo, SIG_DFL);
      raise(signo);
    }


    DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

    Address localJTOC = VmToc;
    int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);

    /* get the frame pointer from thread object  */
    Address localNativeThreadAddress       = IA32_ESI(context);
    Address localFrameAddress =
      *(Address *) (localNativeThreadAddress + Thread_framePointer_offset);

    /* setup stack frame to contain the frame pointer */
    Address *sp = (Address *) IA32_ESP(context);

    /* put fp as a  parameter on the stack  */
    IA32_ESP(context) = IA32_ESP(context) - __SIZEOF_POINTER__;
    sp = (Address *) IA32_ESP(context);
    *sp = localFrameAddress;
    // must pass localFrameAddress in first param register!
    IA32_EAX(context) = localFrameAddress;

    /* put a return address of zero on the stack */
    IA32_ESP(context) = IA32_ESP(context) - __SIZEOF_POINTER__;
    sp = (Address *) IA32_ESP(context);
    *sp = 0;

    /* goto dumpStackAndDie routine (in Scheduler) as if called */
    IA32_EIP(context) = dumpStack;
    return;
  }

  /* Default case. */
  CONSOLE_PRINTF("%s: got an unexpected software signal (# %d)", Me, signo);
#if defined __GLIBC__ && defined _GNU_SOURCE
  CONSOLE_PRINTF(" %s", strsignal(signo));
#endif
  CONSOLE_PRINTF("; ignoring it.\n");
}

static void*
mapImageFile(const char *fileName, const void *targetAddress, int prot,
             unsigned *roundedImageSize) {

  /* open and mmap the image file.
   * create bootRegion
   */
  FILE *fin = fopen (fileName, "r");
  if (!fin) {
    ERROR_PRINTF("%s: can't find bootimage file\"%s\"\n", Me, fileName);
    return 0;
  }

  /* measure image size */
  if (lib_verbose)
    CONSOLE_PRINTF("%s: loading from \"%s\"\n", Me, fileName);
  fseek (fin, 0L, SEEK_END);
  unsigned actualImageSize = ftell(fin);
  *roundedImageSize = pageRoundUp((uint64_t) actualImageSize, pageSize);
  fseek (fin, 0L, SEEK_SET);

  void *bootRegion = 0;
  bootRegion = mmap((void*)targetAddress, *roundedImageSize,
                    prot,
                    MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE,
                    fileno(fin), 0);
  if (bootRegion == (void *) MAP_FAILED) {
    ERROR_PRINTF("%s: mmap failed (errno=%d): %s\n",
            Me, errno, strerror(errno));
    return 0;
  }
  if (bootRegion != targetAddress) {
    ERROR_PRINTF("%s: Attempted to mmap in the address %p; "
            " got %p instead.  This should never happen.",
            Me, bootRegion, targetAddress);
    /* Don't check the return value.  This is insane already.
     * If we weren't part of a larger runtime system, I'd abort at this
     * point.  */
    (void) munmap(bootRegion, *roundedImageSize);
    return 0;
  }

  /* Quoting from the Linux mmap(2) manual page:
     "closing the file descriptor does not unmap the region."
  */
  if (fclose (fin) != 0) {
    ERROR_PRINTF("%s: close failed (errno=%d)\n", Me, errno);
    return 0;
  }
  return bootRegion;
}

/* Returns 1 upon any errors.   Never returns except to report an error. */
int
createVM(void)
{
  unsigned roundedDataRegionSize;
  void *bootDataRegion = mapImageFile(bootDataFilename,
                                      bootImageDataAddress,
                                      PROT_READ | PROT_WRITE | PROT_EXEC,
                                      &roundedDataRegionSize);
  if (bootDataRegion != bootImageDataAddress)
    return 1;

  unsigned roundedCodeRegionSize;
  void *bootCodeRegion = mapImageFile(bootCodeFilename,
                                      bootImageCodeAddress,
                                      PROT_READ | PROT_WRITE | PROT_EXEC,
                                      &roundedCodeRegionSize);
  if (bootCodeRegion != bootImageCodeAddress)
    return 1;

  unsigned roundedRMapRegionSize;
  void *bootRMapRegion = mapImageFile(bootRMapFilename,
                                      bootImageRMapAddress,
                                      PROT_READ,
                                      &roundedRMapRegionSize);
  if (bootRMapRegion != bootImageRMapAddress)
    return 1;


  /* validate contents of boot record */
  bootRecord = (struct BootRecord *) bootDataRegion;

  if (bootRecord->bootImageDataStart != (Address) bootDataRegion) {
    ERROR_PRINTF("%s: image load error: built for %x but loaded at %p\n",
            Me, bootRecord->bootImageDataStart, bootDataRegion);
    return 1;
  }

  if (bootRecord->bootImageCodeStart != (Address) bootCodeRegion) {
    ERROR_PRINTF("%s: image load error: built for %x but loaded at %p\n",
            Me, bootRecord->bootImageCodeStart, bootCodeRegion);
    return 1;
  }

  if (bootRecord->bootImageRMapStart != (Address) bootRMapRegion) {
    ERROR_PRINTF("%s: image load error: built for %x but loaded at %p\n",
            Me, bootRecord->bootImageRMapStart, bootRMapRegion);
    return 1;
  }

  if ((bootRecord->spRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: sp (%x) is not word aligned\n",
            Me, bootRecord->spRegister);
    return 1;
  }

  if ((bootRecord->ipRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: ip (%x) is not word aligned\n",
            Me, bootRecord->ipRegister);
    return 1;
  }

  if (((u_int32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
    ERROR_PRINTF("%s: image format error: missing stack sanity check marker (0x%08x)\n",
            Me, ((int *) bootRecord->spRegister)[-1]);
    return 1;
  }

  /* remember jtoc location for later use by trap handler */
  VmToc = bootRecord->tocRegister;

  // remember JTOC offset of Scheduler.DebugRequested
  //
  DebugRequestedOffset = bootRecord->debugRequestedOffset;

  /* write freespace information into boot record */
  bootRecord->initialHeapSize  = initialHeapSize;
  bootRecord->maximumHeapSize  = maximumHeapSize;
  bootRecord->bytesInPage = pageSize;
  bootRecord->bootImageDataStart   = (Address) bootDataRegion;
  bootRecord->bootImageDataEnd     = (Address) bootDataRegion + roundedDataRegionSize;
  bootRecord->bootImageCodeStart   = (Address) bootCodeRegion;
  bootRecord->bootImageCodeEnd     = (Address) bootCodeRegion + roundedCodeRegionSize;
  bootRecord->bootImageRMapStart   = (Address) bootRMapRegion;
  bootRecord->bootImageRMapEnd     = (Address) bootRMapRegion + roundedRMapRegionSize;
  bootRecord->verboseBoot      = verboseBoot;

  /* write sys.cpp linkage information into boot record */

  setLinkage(bootRecord);
  if (lib_verbose) {
    CONSOLE_PRINTF("%s: boot record contents:\n", Me);
    CONSOLE_PRINTF("   bootImageDataStart:   0x%08x\n",
            bootRecord->bootImageDataStart);
    CONSOLE_PRINTF("   bootImageDataEnd:     0x%08x\n",
            bootRecord->bootImageDataEnd);
    CONSOLE_PRINTF("   bootImageCodeStart:   0x%08x\n",
            bootRecord->bootImageCodeStart);
    CONSOLE_PRINTF("   bootImageCodeEnd:     0x%08x\n",
            bootRecord->bootImageCodeEnd);
    CONSOLE_PRINTF("   bootImageRMapStart:   0x%08x\n",
            bootRecord->bootImageRMapStart);
    CONSOLE_PRINTF("   bootImageRMapEnd:     0x%08x\n",
            bootRecord->bootImageRMapEnd);
    CONSOLE_PRINTF("   initialHeapSize:      0x%08x\n",
            bootRecord->initialHeapSize);
    CONSOLE_PRINTF("   maximumHeapSize:      0x%08x\n",
            bootRecord->maximumHeapSize);
    CONSOLE_PRINTF("   tiRegister:           0x%08x\n",
            bootRecord->tiRegister);
    CONSOLE_PRINTF("   spRegister:           0x%08x\n",
            bootRecord->spRegister);
    CONSOLE_PRINTF("   ipRegister:           0x%08x\n",
            bootRecord->ipRegister);
    CONSOLE_PRINTF("   tocRegister:          0x%08x\n",
            bootRecord->tocRegister);
    CONSOLE_PRINTF("   sysConsoleWriteCharIP:0x%08x\n",
            bootRecord->sysConsoleWriteCharIP);
    CONSOLE_PRINTF("   ...etc...                   \n");
  }

  /* install a stack for hardwareTrapHandler() to run on */
  stack_t stack;

  memset (&stack, 0, sizeof stack);
  stack.ss_sp = checkMalloc(SIGSTKSZ);

  stack.ss_size = SIGSTKSZ;
  if (sigaltstack (&stack, 0)) {
    ERROR_PRINTF("%s: sigaltstack failed (errno=%d)\n",
            Me, errno);
    return 1;
  }

  /* install hardware trap signal handler */
  struct sigaction action;

  memset (&action, 0, sizeof action);
  action.sa_sigaction = hardwareTrapHandler;
  /*
   * mask all signal from reaching the signal handler while the signal
   * handler is running
   */
  if (sigfillset(&(action.sa_mask))) {
    ERROR_PRINTF("%s: sigfillset failed (errno=%d)\n", Me, errno);
    return 1;
  }
  /*
   * exclude the signal used to wake up the daemons
   */
  if (sigdelset(&(action.sa_mask), SIGCONT)) {
    ERROR_PRINTF("%s: sigdelset failed (errno=%d)\n", Me, errno);
    return 1;
  }

  action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
  if (sigaction (SIGSEGV, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }
  if (sigaction (SIGFPE, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }
  if (sigaction (SIGTRAP, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }

  // alignment checking: we want the handler to handle alignment exceptions
  if (sigaction (SIGBUS, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }

  /* install software signal handler */
  action.sa_sigaction = &softwareSignalHandler;
  if (sigaction (SIGALRM, &action, 0)) {      /* catch timer ticks (so we can timeslice user level threads) */
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }
  if (sigaction (SIGQUIT, &action, 0)) {
    /* catch QUIT to invoke debugger
                                            * thread */
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }
  if (sigaction (SIGTERM, &action, 0)) { /* catch TERM to dump and die */
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }

  // Ignore "write (on a socket) with nobody to read it" signals so
  // that sysWriteBytes() will get an EPIPE return code instead of trapping.
  //
  memset (&action, 0, sizeof action);
  action.sa_handler = SIG_IGN;
  if (sigaction(SIGPIPE, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return 1;
  }

  /* set up initial stack frame */
  Address ip   = bootRecord->ipRegister;
  Address jtoc = bootRecord->tocRegister;
  Address tr;
  Address sp  = bootRecord->spRegister;

  tr = *(Address *) (bootRecord->tocRegister
                     + bootRecord->bootThreadOffset);

  /* initialize the thread id jtoc, and framepointer fields in the primordial
   * processor object.
   */
  *(Address *) (tr + Thread_framePointer_offset)
    = (Address)sp - 2*__SIZEOF_POINTER__;

  sysInitialize();

#ifdef HARMONY
  hythread_attach(NULL);
#endif

  // setup place that we'll return to when we're done
  if (setjmp(primordial_jb)) {
    *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;
    // cannot return or else the process will exit.  this is how pthreads
    // work on the platforms I've tried (OS X and Linux).  So, when the
    // primordial thread is done, we just have it idle.  When the process
    // is supposed to exit, it'll call exit().
#ifdef HARMONY
    hythread_detach(NULL);
#endif
    for (;;) pause();
  } else {
    sp -= __SIZEOF_POINTER__;
    *(uint32_t*)sp = 0xdeadbabe;         /* STACKFRAME_RETURN_ADDRESS_OFFSET */
    sp -= __SIZEOF_POINTER__;
    *(Address*)sp = Constants_STACKFRAME_SENTINEL_FP; /* STACKFRAME_FRAME_POINTER_OFFSET */
    sp -= __SIZEOF_POINTER__;
    ((Address *)sp)[0] = Constants_INVISIBLE_METHOD_ID;    /* STACKFRAME_METHOD_ID_OFFSET */

    // CONSOLE_PRINTF("%s: here goes...\n", Me);
    int rc = bootThread ((void*)ip, (void*)tr, (void*)sp);

    ERROR_PRINTF("%s: createVM(): boot() returned; failed to create a virtual machine.  rc=%d.  Bye.\n", Me, rc);
    return 1;
  }
}


// Get address of JTOC.
EXTERNAL void *
getJTOC(void)
{
  return (void*) VmToc;
}

