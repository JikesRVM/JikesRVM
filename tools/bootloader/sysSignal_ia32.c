/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Architecture specific signal handling routines
 */
#include "sys.h"
#include <signal.h>

/* Macros to modify signal context */
#ifdef RVM_FOR_LINUX
#define __USE_GNU 1
 #include <sys/ucontext.h>

#define __MC(context) ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context) (__MC(context).gregs)

#ifndef __x86_64__
#  define IA32_EAX(context) (__GREGS(context)[REG_EAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_EBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_ECX])
#  define IA32_EDX(context) (__GREGS(context)[REG_EDX])
#  define IA32_EDI(context)  (__GREGS(context)[REG_EDI])
#  define IA32_ESI(context)  (__GREGS(context)[REG_ESI])
#  define IA32_EBP(context)  (__GREGS(context)[REG_EBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_ESP])
#  define IA32_EIP(context)  (__GREGS(context)[REG_EIP])

#  define IA32_CS(context)  (__GREGS(context)[REG_CS])
#  define IA32_DS(context)  (__GREGS(context)[REG_DS])
#  define IA32_ES(context)  (__GREGS(context)[REG_ES])
#  define IA32_FS(context)  (__GREGS(context)[REG_FS])
#  define IA32_GS(context)  (__GREGS(context)[REG_GS])
#  define IA32_SS(context)  (__GREGS(context)[REG_SS])

#  define IA32_OLDMASK(context) (__MC(context).oldmask)
#  define IA32_FPFAULTDATA(context)     (__MC(context).cr2)
#else
#  define IA32_EAX(context) (__GREGS(context)[REG_RAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_RBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_RCX])
#  define IA32_EDX(context) (__GREGS(context)[REG_RDX])
#  define IA32_EDI(context)  (__GREGS(context)[REG_RDI])
#  define IA32_ESI(context)  (__GREGS(context)[REG_RSI])
#  define IA32_EBP(context)  (__GREGS(context)[REG_RBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_RSP])
#  define IA32_R8(context)  (__GREGS(context)[REG_R8])
#  define IA32_R9(context)  (__GREGS(context)[REG_R9])
#  define IA32_R10(context)  (__GREGS(context)[REG_R10])
#  define IA32_R11(context)  (__GREGS(context)[REG_R11])
#  define IA32_R12(context) (__GREGS(context)[REG_R12])
#  define IA32_R13(context) (__GREGS(context)[REG_R13])
#  define IA32_R14(context) (__GREGS(context)[REG_R14])
#  define IA32_R15(context) (__GREGS(context)[REG_R15])
#  define IA32_EIP(context)  (__GREGS(context)[REG_RIP])
#  define IA32_CSGSFS(context) (__GREGS(context)[REG_CSGSFS])
#endif

#define IA32_EFLAGS(context)  (__GREGS(context)[REG_EFL])
#define IA32_TRAPNO(context) (__GREGS(context)[REG_TRAPNO])
#define IA32_ERR(context) (__GREGS(context)[REG_ERR])

#define IA32_FPREGS(context) (__MC(context).fpregs)

#endif

#ifdef RVM_FOR_OSX
#ifdef __DARWIN_UNIX03
#define DARWIN_PREFIX(x) __##x
#else
#define DARWIN_PREFIX(x) ##x
#endif

#define __MCSS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(ss)
#define __MCES(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(es)
#define __MCFS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(fs)

#ifndef __x86_64__
#  define IA32_EAX(context)    (__MCSS(context).DARWIN_PREFIX(eax))
#  define IA32_EBX(context)    (__MCSS(context).DARWIN_PREFIX(ebx))
#  define IA32_ECX(context)    (__MCSS(context).DARWIN_PREFIX(ecx))
#  define IA32_EDX(context)    (__MCSS(context).DARWIN_PREFIX(edx))
#  define IA32_EDI(context)    (__MCSS(context).DARWIN_PREFIX(edi))
#  define IA32_ESI(context)    (__MCSS(context).DARWIN_PREFIX(esi))
#  define IA32_EBP(context)    (__MCSS(context).DARWIN_PREFIX(ebp))
#  define IA32_ESP(context)    (__MCSS(context).DARWIN_PREFIX(esp))
#  define IA32_EFLAGS(context) (__MCSS(context).DARWIN_PREFIX(eflags))
#  define IA32_EIP(context)    (__MCSS(context).DARWIN_PREFIX(eip))
#  define IA32_DS(context)     (__MCSS(context).DARWIN_PREFIX(ds))
#  define IA32_ES(context)     (__MCSS(context).DARWIN_PREFIX(es))
#  define IA32_SS(context)     (__MCSS(context).DARWIN_PREFIX(ss))
#else
#  define IA32_EAX(context)    (__MCSS(context).DARWIN_PREFIX(rax))
#  define IA32_EBX(context)    (__MCSS(context).DARWIN_PREFIX(rbx))
#  define IA32_ECX(context)    (__MCSS(context).DARWIN_PREFIX(rcx))
#  define IA32_EDX(context)    (__MCSS(context).DARWIN_PREFIX(rdx))
#  define IA32_EDI(context)    (__MCSS(context).DARWIN_PREFIX(rdi))
#  define IA32_ESI(context)    (__MCSS(context).DARWIN_PREFIX(rsi))
#  define IA32_EBP(context)    (__MCSS(context).DARWIN_PREFIX(rbp))
#  define IA32_ESP(context)    (__MCSS(context).DARWIN_PREFIX(rsp))
#  define IA32_EFLAGS(context) (__MCSS(context).DARWIN_PREFIX(rflags))
#  define IA32_EIP(context)    (__MCSS(context).DARWIN_PREFIX(rip))
#  define IA32_R8(context)     (__MCSS(context).DARWIN_PREFIX(r8))
#  define IA32_R9(context)     (__MCSS(context).DARWIN_PREFIX(r9))
#  define IA32_R10(context)    (__MCSS(context).DARWIN_PREFIX(r10))
#  define IA32_R11(context)    (__MCSS(context).DARWIN_PREFIX(r11))
#  define IA32_R12(context)    (__MCSS(context).DARWIN_PREFIX(r12))
#  define IA32_R13(context)    (__MCSS(context).DARWIN_PREFIX(r13))
#  define IA32_R14(context)    (__MCSS(context).DARWIN_PREFIX(r14))
#  define IA32_R15(context)    (__MCSS(context).DARWIN_PREFIX(r15))
#endif // __x86_64__

#define IA32_CS(context)  (__MCSS(context).DARWIN_PREFIX(cs))
#define IA32_FS(context)  (__MCSS(context).DARWIN_PREFIX(fs))
#define IA32_GS(context)  (__MCSS(context).DARWIN_PREFIX(gs))

#define IA32_TRAPNO(context) (__MCES(context).DARWIN_PREFIX(trapno))
#define IA32_ERR(context) (__MCES(context).DARWIN_PREFIX(err))

#endif

#ifdef RVM_FOR_SOLARIS
#define __MC(context) ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context) (__MC(context).gregs)

#define IA32_ESP(context) (__GREGS(context)[ESP])

#define IA32_EAX(context) (__GREGS(context)[EAX])
#define IA32_EBX(context) (__GREGS(context)[EBX])
#define IA32_ECX(context) (__GREGS(context)[ECX])
#define IA32_EDX(context) (__GREGS(context)[EDX])
#define IA32_EDI(context)  (__GREGS(context)[EDI])
#define IA32_ESI(context)  (__GREGS(context)[ESI])
#define IA32_EBP(context)  (__GREGS(context)[EBP])
#define IA32_ESP(context) (__GREGS(context)[ESP])
#define IA32_SS(context)  (__GREGS(context)[SS])
#define IA32_EFLAGS(context)  (__GREGS(context)[EFL])
#define IA32_EIP(context)  (__GREGS(context)[EIP])
#define IA32_CS(context)  (__GREGS(context)[CS])
#define IA32_DS(context)  (__GREGS(context)[DS])
#define IA32_ES(context)  (__GREGS(context)[ES])
#define IA32_FS(context)  (__GREGS(context)[FS])
#define IA32_GS(context)  (__GREGS(context)[GS])
#define IA32_TRAPNO(context) (__GREGS(context)[TRAPNO])
#define IA32_ERR(context) (__GREGS(context)[ERR])
#define IA32_FPREGS(context) (__MC(context).fpregs)
#endif

/**
 * Compute the number of bytes used to encode the given modrm part of
 * an Intel instruction
 *
 * Taken:      modrm [in] value to decode
 * Returned:   number of bytes used to encode modrm and optionally an SIB
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
  int has_rex_prefix = 0;
  // w field in rex byte. If 1, operand size is 64-bit and operand size overrides are ignored
  int rex_w_byte = 0;

  if (!inRVMAddressSpace(faultingInstructionAddress)) {
	ERROR_PRINTF("%s: Failing instruction starting at %zx wasn't in RVM address space\n",
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
    has_rex_prefix = 1;
    opcode = ((unsigned char*)faultingInstructionAddress)[size];
    size++;
    // extract w byte which is necessary to determine operand sizes
    rex_w_byte = (opcode >> 3) & 1;
  }
#endif

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
    case 0x9B: // fwait / wait
      // Note: it might also be possible to decode 0x9B as a prefix for certain floating
      // point instructions. However, that would be more complicated and this function
      // only cares about the instruction length (and not the actual instructions).
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
    case 0xE3: // jump if ecx is zero
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
    case 0x28: case 0x2B:
    case 0x33: case 0x3A: case 0x3B:
    case 0x8A: case 0x8B:

    case 0x00: case 0x01: case 0x02: case 0x03: // op r/m, r
    case 0x09: case 0x11: case 0x19: case 0x21:
    case 0x29: case 0x31: case 0x38: case 0x39:

    case 0x63: // movsxd (op code not used by our compiler for 32 bit)
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
      ERROR_PRINTF("%s: Unhandled opcode 0x%x during decoding of instruction at %zx, stopped decoding\n",
          Me, (unsigned int) opcode, faultingInstructionAddress);
      return (Address) 0;
  }

  /* two byte opcodes */
  if (two_byte_opcode == 1) {
    switch (opcode) {
      case 0x0B: // ud2 - undefined instruction
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
      case 0x28: case 0x29: // movaps / movapd
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
        ERROR_PRINTF("%s: Unhandled opcode 0x%x during decoding of second opcode byte of two-byte opcode from instruction at %zx, stopped decoding\n",
            Me, (unsigned int) opcode, faultingInstructionAddress);
        return (Address) 0;
      }
  }

  if (has_variable_immediate == 1) {
    if (has_rex_prefix == 1) { // 64 bit instruction
      if (rex_w_byte == 1) {
        size += 8; // all operand overrides are ignored, width is 64 bit
      } else if (operand_size_override == 1) {
        size += 2; // no w byte and operand size overrides means 16 bit operands
      } else {
        size += 4; // default operand size is always 32 bit
      }
    } else { // normal instruction
      if (operand_size_override == 1) {
        size += 2; // operand size override for non-64 bit instructions means 16 bit
      } else {
        size += 4; // default operand size is always 32 bit
      }
    }
  }

  return faultingInstructionAddress + size;
}

/**
 * Read the addresses of pointers for important values out of the context
 *
 * Taken: context [in] context to read from
 *        instructionPtr [out] pointer to instruction
 *        instructionFollowingPtr [out] pointer to instruction following this
 *        threadPtr [out] ptr to current VM thread object
 *        tocPtr [out] ptr to JTOC
 */
EXTERNAL void readContextInformation(void *context, Address *instructionPtr,
                                     Address *instructionFollowingPtr,
                                     Address *threadPtr, Address *jtocPtr)
{
  Address ip = IA32_EIP(context);
  *instructionPtr  = ip;
  *instructionFollowingPtr = getInstructionFollowing(ip);
  *threadPtr = IA32_ESI(context);
  *jtocPtr = bootRecord->tocRegister;
}

/**
 * Read frame pointer at point of the signal
 *
 * Taken:     context [in] context to read from
 *            threadPtr [in] address of thread information
 * Returned:  address of stack frame
 */
EXTERNAL Address readContextFramePointer(void UNUSED *context, Address threadPtr)
{
  return *(Address *)(threadPtr + Thread_framePointer_offset);
}

/**
 * Read trap code from context of signal
 *
 * Taken:     context   [in] context to read from
 *            threadPtr [in] address of thread information
 *            signo     [in] signal number
 *            instructionPtr [in] address of instruction
 *            trapInfo  [out] extra information about trap
 * Returned:  trap code
 */
EXTERNAL int readContextTrapCode(void UNUSED *context, Address threadPtr, int signo, Address instructionPtr, Word *trapInfo)
{
  switch(signo) {
    case SIGSEGV:
      if (*((unsigned char *)instructionPtr) == 0xCD) {
        /* int imm opcode */
        unsigned char code = *(unsigned char*)(instructionPtr+1);
        switch(code) {
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_NULL_POINTER:
            return Runtime_TRAP_NULL_POINTER;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_ARRAY_BOUNDS:
            *trapInfo = *(int *) (threadPtr + Thread_arrayIndexTrapParam_offset);
            return Runtime_TRAP_ARRAY_BOUNDS;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_DIVIDE_BY_ZERO:
            return Runtime_TRAP_DIVIDE_BY_ZERO;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_STACK_OVERFLOW:
            return Runtime_TRAP_STACK_OVERFLOW;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_CHECKCAST:
            return Runtime_TRAP_CHECKCAST;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_REGENERATE:
            return Runtime_TRAP_REGENERATE;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_JNI_STACK:
            return Runtime_TRAP_JNI_STACK;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_MUST_IMPLEMENT:
            return Runtime_TRAP_MUST_IMPLEMENT;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_STORE_CHECK:
            return Runtime_TRAP_STORE_CHECK;
          case Constants_RVM_TRAP_BASE + Runtime_TRAP_UNREACHABLE_BYTECODE:
            return Runtime_TRAP_UNREACHABLE_BYTECODE;
          default:
            ERROR_PRINTF("%s: Unexpected trap code in int imm instruction 0x%x\n", Me, (unsigned int) code);
            return Runtime_TRAP_UNKNOWN;
        }
      } else {
        return Runtime_TRAP_NULL_POINTER;
      }
    case SIGFPE:
      return Runtime_TRAP_DIVIDE_BY_ZERO;
  default:
    ERROR_PRINTF("%s: Unexpected hardware trap signal 0x%x\n", Me, (unsigned int) signo);
    return Runtime_TRAP_UNKNOWN;
  }
}


/**
 * Set up the context to invoke RuntimeEntrypoints.deliverHardwareException.
 *
 * Taken:   context  [in,out] registers at point of signal/trap
 *          vmRegisters [out]
 */
EXTERNAL void setupDeliverHardwareException(void *context, Address vmRegisters,
             int trapCode, Word trapInfo,
             Address instructionPtr UNUSED,
             Address instructionFollowingPtr,
             Address threadPtr, Address jtocPtr,
             Address framePtr, int signo)
{
  Address sp, stackLimit, fp;
  Address *vmr_gprs  = *(Address **) (vmRegisters + Registers_gprs_offset);
  Address vmr_ip     =  (Address)    (vmRegisters + Registers_ip_offset);
  Address vmr_fp     =  (Address)    (vmRegisters + Registers_fp_offset);

  /* move gp registers to Registers object */
  vmr_gprs[Constants_EAX] = IA32_EAX(context);
  vmr_gprs[Constants_ECX] = IA32_ECX(context);
  vmr_gprs[Constants_EDX] = IA32_EDX(context);
  vmr_gprs[Constants_EBX] = IA32_EBX(context);
  vmr_gprs[Constants_ESI] = IA32_ESI(context);
  vmr_gprs[Constants_EDI] = IA32_EDI(context);
  vmr_gprs[Constants_ESP] = IA32_ESP(context);
  vmr_gprs[Constants_EBP] = IA32_EBP(context);
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
  stackLimit = *(Address *)(threadPtr + RVMThread_stackLimit_offset);
  if (sp <= stackLimit - Constants_MAX_DIFFERENCE_TO_STACK_LIMIT) {
    ERROR_PRINTF("sp (%p) too far below stackLimit (%p) to recover\n", (void*)sp, (void*)stackLimit);
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  sp = stackLimit - Constants_MAX_DIFFERENCE_TO_STACK_LIMIT - Constants_RED_ZONE_SIZE;
  stackLimit -= Constants_STACK_SIZE_GUARD;
  *(Address *)(threadPtr + RVMThread_stackLimit_offset) = stackLimit;

  /* Insert artificial stackframe at site of trap. */
  /* This frame marks the place where "hardware exception registers" were saved. */
  sp = sp - Constants_STACKFRAME_HEADER_SIZE;
  fp = sp - __SIZEOF_POINTER__ - Constants_STACKFRAME_BODY_OFFSET;
  /* fill in artificial stack frame */
  ((Address*)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET))[0]  = framePtr;
  ((int *)   (fp + Constants_STACKFRAME_METHOD_ID_OFFSET))[0]      = bootRecord->hardwareTrapMethodId;
  ((Address*)(fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET))[0] = instructionFollowingPtr;

  /* fill in call to "deliverHardwareException" */
  sp = sp - __SIZEOF_POINTER__; /* first parameter is trap code */
  ((int *)sp)[0] = trapCode;
  IA32_EAX(context) = trapCode;
  VERBOSE_SIGNALS_PRINTF("%s: trap code is %d\n", Me, trapCode);

  sp = sp - __SIZEOF_POINTER__; /* next parameter is trap info */
  ((Word *)sp)[0] = trapInfo;
  IA32_EDX(context) = trapInfo;
  VERBOSE_SIGNALS_PRINTF("%s: trap info is %zx\n", Me, trapInfo);

  sp = sp - __SIZEOF_POINTER__; /* return address - looks like called from failing instruction */
  *(Address *) sp = instructionFollowingPtr;

  /* store instructionFollowing and fp in Registers.ip and Registers.fp */
  *(Address*)vmr_ip = instructionFollowingPtr;
  VERBOSE_SIGNALS_PRINTF("%s: set vmr_ip to %p\n", Me, (void*)instructionFollowingPtr);
  *(Address*)vmr_fp = framePtr;
  VERBOSE_SIGNALS_PRINTF("%s: set vmr_fp to %p\n", Me, (void*)framePtr);

  /* set up context block to look like the artificial stack frame is
   * returning
   */
  IA32_ESP(context) = sp;
  IA32_EBP(context) = fp;
  *(Address*) (threadPtr + Thread_framePointer_offset) = fp;

  /* setup to return to deliver hardware exception routine */
  IA32_EIP(context) = *(Address*)(jtocPtr + bootRecord->deliverHardwareExceptionOffset);
}

/**
 * Set up the context to invoke RVMThread.dumpStackAndDie
 *
 * Taken:   context [in,out] registers at point of signal/trap
 */
EXTERNAL void setupDumpStackAndDie(void *context)
{
  Offset DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;
  Address localJTOC = bootRecord->tocRegister;
  Address dumpStack = *(Address *) ((char *) localJTOC + DumpStackAndDieOffset);

  /* get the frame pointer from thread object  */
  Address localNativeThreadAddress = IA32_ESI(context);
  Address localFrameAddress =  *(Address*)(localNativeThreadAddress + Thread_framePointer_offset);

  /* setup stack frame to contain the frame pointer */
  Address *sp = (Address*) IA32_ESP(context);

  /* put fp as a  parameter on the stack  */
  sp -= __SIZEOF_POINTER__;
  *sp = localFrameAddress;

  /* must pass localFrameAddress in first param register! */
  IA32_EAX(context) = localFrameAddress;

  /* put a return address of zero on the stack */
  sp -= __SIZEOF_POINTER__;
  *sp = 0;

  IA32_ESP(context) = (greg_t) sp;

  /* goto dumpStackAndDie routine (in Scheduler) as if called */
  IA32_EIP(context) = dumpStack;
}

/**
 * Print the contents of context to the screen
 *
 * Taken:   context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
  ERROR_PRINTF("eip           %p\n", (void*)IA32_EIP(context));
  ERROR_PRINTF("eax (T0)      %p\n", (void*)IA32_EAX(context));
  ERROR_PRINTF("ebx (ctrs)    %p\n", (void*)IA32_EBX(context));
  ERROR_PRINTF("ecx (S0)      %p\n", (void*)IA32_ECX(context));
  ERROR_PRINTF("edx (T1)      %p\n", (void*)IA32_EDX(context));
  ERROR_PRINTF("esi (TR)      %p\n", (void*)IA32_ESI(context));
  ERROR_PRINTF("edi (S1)      %p\n", (void*)IA32_EDI(context));
  ERROR_PRINTF("ebp           %p\n", (void*)IA32_EBP(context));
  ERROR_PRINTF("esp (SP)      %p\n", (void*)IA32_ESP(context));
#ifdef __x86_64__
  ERROR_PRINTF("r8            %p\n", (void*)IA32_R8(context));
  ERROR_PRINTF("r9            %p\n", (void*)IA32_R9(context));
  ERROR_PRINTF("r10           %p\n", (void*)IA32_R10(context));
  ERROR_PRINTF("r11           %p\n", (void*)IA32_R11(context));
  ERROR_PRINTF("r12           %p\n", (void*)IA32_R12(context));
  ERROR_PRINTF("r13           %p\n", (void*)IA32_R13(context));
  ERROR_PRINTF("r14           %p\n", (void*)IA32_R14(context));
  ERROR_PRINTF("r15           %p\n", (void*)IA32_R15(context));
#ifdef RVM_FOR_LINUX
  ERROR_PRINTF("cs, gs, fs    %p\n", (void*)IA32_CSGSFS(context));
#endif
#else
  ERROR_PRINTF("cs            %p\n", (void*)IA32_CS(context));
  ERROR_PRINTF("ds            %p\n", (void*)IA32_DS(context));
  ERROR_PRINTF("es            %p\n", (void*)IA32_ES(context));
  ERROR_PRINTF("fs            %p\n", (void*)IA32_FS(context));
  ERROR_PRINTF("gs            %p\n", (void*)IA32_GS(context));
  ERROR_PRINTF("ss            %p\n", (void*)IA32_SS(context));
#endif
  ERROR_PRINTF("trapno        0x%08x\n", (unsigned int)IA32_TRAPNO(context));
  ERROR_PRINTF("err           0x%08x\n", (unsigned int)IA32_ERR(context));
  ERROR_PRINTF("eflags        0x%08x\n", (int)IA32_EFLAGS(context));
  /* null if fp registers haven't been used yet */
#ifndef RVM_FOR_OSX
  ERROR_PRINTF("fpregs        %p\n", (void*)IA32_FPREGS(context));
#endif
#if !defined(__x86_64__) && defined(RVM_FOR_LINUX)
  ERROR_PRINTF("oldmask       0x%08lx\n", (unsigned long) IA32_OLDMASK(context));
  /* seems to contain mem address that faulting instruction was trying to access */
  ERROR_PRINTF("cr2           0x%08lx\n", (unsigned long) IA32_FPFAULTDATA(context));
#endif
}

