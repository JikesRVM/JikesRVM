/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * @author Dave Toll
 */
/****************************************************************************/
/**                                                                        **/
/**        COPYRIGHT   I B M   CORPORATION  1983, 1984, 1985               **/
/**                                   1986, 1987, 1988, 1989, 2003         **/
/**                                                                        **/
/**        LICENSED MATERIAL  -  PROGRAM PROPERTY OF I B M                 **/
/**                                                                        **/
/**        REFER TO COPYRIGHT INSTRUCTIONS: FORM G120-2083                 **/
/**                                                                        **/
/****************************************************************************/
/*                                                                          */
/*                                                                          */
/*  MODULE-NAME      = ihnpdsm.cpp                                          */
/*                                                                          */
/*  DESCRIPTIVE-NAME = Disassembler for 80386/80486/Pentium/Pentium Pro     */
/*                                                                          */
/*  STATUS           = Version 1 Release 1                                  */
/*                                                                          */
/*  FUNCTION         = to disassemble one instruction into MASM             */
/*                     mnemonics and instruction format.                    */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*  Entry points:                                                           */
/*                                                                          */
/*  void p__DisAsm(PARLIST *parmptr,int print);                             */
/*                                                                          */
/*  parmptr - pointer to a PARLIST structure as defined in ihnpdsm.hpp      */
/*  print   - zero indicates that mnemonic, opcode and hex buffers are      */
/*            not to be loaded.  The disassembler is being called only      */
/*            for operand type and location of the next opcode.             */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*  Comments:                                                               */
/*                                                                          */
/*  This code will support the following:                                   */
/*   16 or 32 bit code segments                                             */
/*   All Pentium & Pentium Pro instructions                                 */
/*   All MMX extensions                                                     */
/*                                                                          */
/*  The disassembly format is, in most cases, valid for the ALP assemble    */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*  Conditionally Invalid instructions:                                     */
/*                                                                          */
/*  This section describes opcodes that are invalid depending on            */
/*  the compile switches that are set.                                      */
/*                                                                          */
/*  Opcode  Switch  Default   Description                                   */
/*  ------  ------  --------  --------------------------------------------- */
/*   82     OP82    Disabled  MOVB AL,immed8, reserved                      */
/*   0F24   OP0F24  Enabled   Move r32,TRn, valid on 80486 and earlier      */
/*   0F26   OP0F24  Enabled   Move TRn,r32, valid on 80486 and earlier      */
/*   0FA6   OP0FA6  Disabled  CMPXCHG opcode valid only on PentiumPro A step */
/*   0FA7   OP0FA6  Disabled  CMPXCHG opcode valid only on PentiumPro A step */
/*                                                                          */
/*  To change the state of the switch at compile time, define the switch    */
/*  to be 1 or 0 on the compile line.                                       */
/*       e.g.   /dOP82=1                                                    */
/*                                                                          */
/****************************************************************************/

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <limits.h>

#include "ihnpdsm.h"

#ifndef UNUSED                  // we're having trouble with some g++ versions
                                // doing "unused"
#define UNUSED __attribute__((unused))
/* In GNU C, __attribute__((unused)) really means "possibly unused". */
#define POSSIBLY_UNUSED UNUSED
#endif


/***************************************************************************/
/*       Set the default state of the conditionally invalid opcodes        */
/***************************************************************************/
#ifndef OP82
  #define OP82 0
#endif
#ifndef OP0F24
  #define OP0F24 1
#endif
#ifndef OP0FA6
  #define OP0FA6 0
#endif

/***************************************************************************/
/*                tables used for strings are in this form                 */
/***************************************************************************/
typedef struct {
    int length;
    const char *string;
} TABLE;
// macro for TABLE initialization, to include trailing null
#define Tinit(x) { sizeof(x), x }
// macro for TABLE initialization, not to include trailing null
#define TinitShort(x) { sizeof(x) - 1, x }

/****************************************************************************/
/* memory operand size prefixes                                             */
/****************************************************************************/
static const TABLE PTRsize[] = {
   TinitShort(""),              // dummy entry
   TinitShort("byte ptr "),
   TinitShort("word ptr "),
   TinitShort("dword ptr "),
   TinitShort("fword ptr "),
   TinitShort("qword ptr "),
   TinitShort("tbyte ptr "),
   TinitShort(""),
   { 0, NULL }
} ;
#define PTRbyte  1
#define PTRword  2
#define PTRdword 3
#define PTRfword 4
#define PTRqword 5
#define PTRtbyte 6
#define PTRnone  7

/****************************************************************************/
/* segment register names                                                   */
/****************************************************************************/
static const TABLE segreg[] =  {
    TinitShort("es"),  
    TinitShort("cs"),
    TinitShort("ss"),
    TinitShort("ds"),
    TinitShort("fs"),
    TinitShort("gs"),
    { 0, NULL }
} ;
#define ES 0
#define CS 1
#define SS 2
#define DS 3
#define FS 4
#define GS 5

/****************************************************************************/
/* 8-bit register names                                                     */
/****************************************************************************/
static const TABLE reg8[] =  {
    TinitShort("al"),
    TinitShort("cl"),
    TinitShort("dl"),
    TinitShort("bl"),
    TinitShort("ah"),
    TinitShort("ch"),
    TinitShort("dh"),
    TinitShort("bh"),
    { 0, NULL }
} ;
#define AL 0
#define CL 1
#define DL 2
#define BL 3
#define AH 4
#define CH 5
#define DH 6
#define BH 7

/****************************************************************************/
/* 16-bit register names                                                    */
/****************************************************************************/
static const TABLE reg16[] =  {
    TinitShort("ax"),
    TinitShort("cx"),
    TinitShort("dx"),
    TinitShort("bx"),
    TinitShort("sp"),
    TinitShort("bp"),
    TinitShort("si"),
    TinitShort("di"),
    { 0, NULL }
} ;
#define AX 0
#define CX 1
#define DX 2
#define BX 3
#define SP 4
#define BP 5
#define SI 6
#define DI 7

/****************************************************************************/
/* 32-bit register names                                                    */
/****************************************************************************/
static const TABLE reg32[] =  {
    TinitShort("eax"),
    TinitShort("ecx"),
    TinitShort("edx"),
    TinitShort("ebx"),
    TinitShort("esp"),
    TinitShort("ebp"),
    TinitShort("PR"),
    TinitShort("JTOC"),
    { 0, NULL }
} ;
#define EAX 0
#define ECX 1
#define EDX 2
#define EBX 3
#define ESP 4
#define EBP 5
#define ESI 6
#define EDI 7

/****************************************************************************/
/* MMX register names                                                     */
/****************************************************************************/
static const TABLE regMMX[] = {
    TinitShort("MM0"),
    TinitShort("MM1"),
    TinitShort("MM2"),
    TinitShort("MM3"),
    TinitShort("MM4"),
    TinitShort("MM5"),
    TinitShort("MM6"),
    TinitShort("MM7"),
    { 0, NULL }
} ;
#define MM0 0
#define MM1 1
#define MM2 2
#define MM3 3
#define MM4 4
#define MM5 5
#define MM6 6
#define MM7 7

/*************************************************************************/
/*  The following table holds the MASM mnemonics:  these are accessed    */
/*  by mnemonic number, not by instruction value.                        */
/*************************************************************************/
/*  each entry is the size (including trailing '\0'), followed by        */
/*  the actual mnemonic name                                             */
/*************************************************************************/
/*  There is a corresponding #define for each index into the array       */
/*************************************************************************/
static const TABLE mnemstr[] =  {
    Tinit("0x"),                      //   0 - Invalid opcode
    Tinit("aaa"),                     //   1
    Tinit("aad"),                     //   2
    Tinit("aam"),                     //   3
    Tinit("aas"),                     //   4
    Tinit("adc"),                     //   5
    Tinit("add"),                     //   6
    Tinit("and"),                     //   7
    Tinit("arpl"),                    //   8
    Tinit("bound"),                   //   9
    Tinit("bsf"),                     //  10
    Tinit("bsr"),                     //  11
    Tinit("bswap"),                   //  12
    Tinit("bt"),                      //  13
    Tinit("btc"),                     //  14
    Tinit("btr"),                     //  15
    Tinit("bts"),                     //  16
    Tinit("call"),                    //  17
    Tinit("cbw"),                     //  18
    Tinit("cdq"),                     //  19
    Tinit("clc"),                     //  20
    Tinit("cld"),                     //  21
    Tinit("cli"),                     //  22
    Tinit("clts"),                    //  23
    Tinit("cmc"),                     //  24
    Tinit("cmova"),                   //  25
    Tinit("cmovae"),                  //  26
    Tinit("cmovb"),                   //  27
    Tinit("cmovbe"),                  //  28
    Tinit("cmove"),                   //  29
    Tinit("cmovg"),                   //  30
    Tinit("cmovge"),                  //  31
    Tinit("cmovl"),                   //  32
    Tinit("cmovle"),                  //  33
    Tinit("cmovne"),                  //  34
    Tinit("cmovno"),                  //  35
    Tinit("cmovns"),                  //  36
    Tinit("cmovo"),                   //  37
    Tinit("cmovpe"),                  //  38
    Tinit("cmovpo"),                  //  39
    Tinit("cmovs"),                   //  40
    Tinit("cmp"),                     //  41
    Tinit("cmpsb"),                   //  42
    Tinit("cmpsd"),                   //  43
    Tinit("cmpsw"),                   //  44
    Tinit("cmpxchg8b"),               //  45
    Tinit("cmpxchg"),                 //  46
    Tinit("cpuid"),                   //  47
    Tinit("cwd"),                     //  48
    Tinit("cwde"),                    //  49
    Tinit("daa"),                     //  50
    Tinit("das"),                     //  51
    Tinit("dec"),                     //  52
    Tinit("div"),                     //  53
    Tinit("emms"),                    //  54
    Tinit("enter"),                   //  55
    Tinit("esc"),                     //  56
    Tinit("f2xm1"),                   //  57
    Tinit("fabs"),                    //  58
    Tinit("fadd"),                    //  59
    Tinit("faddp"),                   //  60
    Tinit("fbld"),                    //  61
    Tinit("fbstp"),                   //  62
    Tinit("fchs"),                    //  63
    Tinit("fclex"),                   //  64
    Tinit("fcmovb"),                  //  65
    Tinit("fcmovbe"),                 //  66
    Tinit("fcmove"),                  //  67
    Tinit("fcmovnb"),                 //  68
    Tinit("fcmovnbe"),                //  69
    Tinit("fcmovne"),                 //  70
    Tinit("fcmovnu"),                 //  71
    Tinit("fcmovu"),                  //  72
    Tinit("fcom"),                    //  73
    Tinit("fcomi"),                   //  74
    Tinit("fcomip"),                  //  75
    Tinit("fcomp"),                   //  76
    Tinit("fcompp"),                  //  77
    Tinit("fcos"),                    //  78
    Tinit("fdecstp"),                 //  79
    Tinit("fdisi"),                   //  80
    Tinit("fdiv"),                    //  81
    Tinit("fdivp"),                   //  82
    Tinit("fdivr"),                   //  83
    Tinit("fdivrp"),                  //  84
    Tinit("feni"),                    //  85
    Tinit("ffree"),                   //  86
    Tinit("ffreep"),                  //  87
    Tinit("fiadd"),                   //  88
    Tinit("ficom"),                   //  89
    Tinit("ficomp"),                  //  90
    Tinit("fidiv"),                   //  91
    Tinit("fidivr"),                  //  92
    Tinit("fild"),                    //  93
    Tinit("fimul"),                   //  94
    Tinit("fincstp"),                 //  95
    Tinit("finit"),                   //  96
    Tinit("fist"),                    //  97
    Tinit("fistp"),                   //  98
    Tinit("fisub"),                   //  99
    Tinit("fisubr"),                  // 100
    Tinit("fld"),                     // 101
    Tinit("fld1"),                    // 102
    Tinit("fldcw"),                   // 103
    Tinit("fldenv"),                  // 104
    Tinit("fldl2e"),                  // 105
    Tinit("fldl2t"),                  // 106
    Tinit("fldlg2"),                  // 107
    Tinit("fldln2"),                  // 108
    Tinit("fldpi"),                   // 109
    Tinit("fldz"),                    // 110
    Tinit("fmul"),                    // 111
    Tinit("fmulp"),                   // 112
    Tinit("fnclex"),                  // 113
    Tinit("fndisi"),                  // 114
    Tinit("fneni"),                   // 115
    Tinit("fninit"),                  // 116
    Tinit("fnop"),                    // 117
    Tinit("fnsave"),                  // 118
    Tinit("fnstcw"),                  // 119
    Tinit("fnstenv"),                 // 120
    Tinit("fnstsw"),                  // 121
    Tinit("fpatan"),                  // 122
    Tinit("fprem"),                   // 123
    Tinit("fprem1"),                  // 124
    Tinit("fptan"),                   // 125
    Tinit("frndint"),                 // 126
    Tinit("frstor"),                  // 127
    Tinit("fsave"),                   // 128
    Tinit("fscale"),                  // 129
    Tinit("fsetpm"),                  // 130
    Tinit("fsin"),                    // 131
    Tinit("fsincos"),                 // 132
    Tinit("fsqrt"),                   // 133
    Tinit("fst"),                     // 134
    Tinit("fstcw"),                   // 135
    Tinit("fstenv"),                  // 136
    Tinit("fstp"),                    // 137
    Tinit("fstsw"),                   // 138
    Tinit("fsub"),                    // 139
    Tinit("fsubp"),                   // 140
    Tinit("fsubr"),                   // 141
    Tinit("fsubrp"),                  // 142
    Tinit("ftst"),                    // 143
    Tinit("fucom"),                   // 144
    Tinit("fucomi"),                  // 145
    Tinit("fucomip"),                 // 146
    Tinit("fucomp"),                  // 147
    Tinit("fucompp"),                 // 148
    Tinit("fxam"),                    // 149
    Tinit("fxch"),                    // 150
    Tinit("fxtract"),                 // 151
    Tinit("fyl2x"),                   // 152
    Tinit("fyl2xp1"),                 // 153
    Tinit("hlt"),                     // 154
    Tinit("idiv"),                    // 155
    Tinit("imul"),                    // 156
    Tinit("in"),                      // 157
    Tinit("inc"),                     // 158
    Tinit("insb"),                    // 159
    Tinit("insd"),                    // 160
    Tinit("insw"),                    // 161
    Tinit("int"),                     // 162
    Tinit("into"),                    // 163
    Tinit("invd"),                    // 164
    Tinit("invlpg"),                  // 165
    Tinit("iret"),                    // 166
    Tinit("iretd"),                   // 167
    Tinit("ja"),                      // 168
    Tinit("jb"),                      // 169
    Tinit("jcxz"),                    // 170
    Tinit("jecxz"),                   // 171
    Tinit("jg"),                      // 172
    Tinit("jl"),                      // 173
    Tinit("jmp"),                     // 174
    Tinit("jna"),                     // 175
    Tinit("jnb"),                     // 176
    Tinit("jng"),                     // 177
    Tinit("jnl"),                     // 178
    Tinit("jno"),                     // 179
    Tinit("jns"),                     // 180
    Tinit("jnz"),                     // 181
    Tinit("jo"),                      // 182
    Tinit("jpe"),                     // 183
    Tinit("jpo"),                     // 184
    Tinit("js"),                      // 185
    Tinit("jz"),                      // 186
    Tinit("lahf"),                    // 187
    Tinit("lar"),                     // 188
    Tinit("lds"),                     // 189
    Tinit("lea"),                     // 190
    Tinit("leave"),                   // 191
    Tinit("les"),                     // 192
    Tinit("lfs"),                     // 193
    Tinit("lgdt"),                    // 194
    Tinit("lgs"),                     // 195
    Tinit("lidt"),                    // 196
    Tinit("lldt"),                    // 197
    Tinit("lmsw"),                    // 198
    Tinit("lock"),                    // 199
    Tinit("lodsb"),                   // 200
    Tinit("lodsd"),                   // 201
    Tinit("lodsw"),                   // 202
    Tinit("loop"),                    // 203
    Tinit("loopnz"),                  // 204
    Tinit("loopz"),                   // 205
    Tinit("lsl"),                     // 206
    Tinit("lss"),                     // 207
    Tinit("ltr"),                     // 208
    Tinit("mov"),                     // 209
    Tinit("movb"),                    // 210
    Tinit("movd"),                    // 211
    Tinit("movq"),                    // 212
    Tinit("movsb"),                   // 213
    Tinit("movsd"),                   // 214
    Tinit("movsw"),                   // 215
    Tinit("movsx"),                   // 216
    Tinit("movzx"),                   // 217
    Tinit("mul"),                     // 218
    Tinit("neg"),                     // 219
    Tinit("nop"),                     // 220
    Tinit("not"),                     // 221
    Tinit("or"),                      // 222
    Tinit("out"),                     // 223
    Tinit("outsb"),                   // 224
    Tinit("outsd"),                   // 225
    Tinit("outsw"),                   // 226
    Tinit("packssdw"),                // 227
    Tinit("packsswb"),                // 228
    Tinit("packuswb"),                // 229
    Tinit("paddb"),                   // 230
    Tinit("paddd"),                   // 231
    Tinit("paddsb"),                  // 232
    Tinit("paddsw"),                  // 233
    Tinit("paddusb"),                 // 234
    Tinit("paddusw"),                 // 235
    Tinit("paddw"),                   // 236
    Tinit("pand"),                    // 237
    Tinit("pandn"),                   // 238
    Tinit("pcmpeqb"),                 // 239
    Tinit("pcmpeqd"),                 // 240
    Tinit("pcmpeqw"),                 // 241
    Tinit("pcmpgtb"),                 // 242
    Tinit("pcmpgtd"),                 // 243
    Tinit("pcmpgtw"),                 // 244
    Tinit("pmaddwd"),                 // 245
    Tinit("pmulhw"),                  // 246
    Tinit("pmullw"),                  // 247
    Tinit("pop"),                     // 248
    Tinit("popa"),                    // 249
    Tinit("popad"),                   // 250
    Tinit("popf"),                    // 251
    Tinit("popfd"),                   // 252
    Tinit("por"),                     // 253
    Tinit("pslld"),                   // 254
    Tinit("psllq"),                   // 255
    Tinit("psllw"),                   // 256
    Tinit("psrad"),                   // 257
    Tinit("psraw"),                   // 258
    Tinit("psrld"),                   // 259
    Tinit("psrlq"),                   // 260
    Tinit("psrlw"),                   // 261
    Tinit("psubb"),                   // 262
    Tinit("psubd"),                   // 263
    Tinit("psubsb"),                  // 264
    Tinit("psubsw"),                  // 265
    Tinit("psubusb"),                 // 266
    Tinit("psubusw"),                 // 267
    Tinit("psubw"),                   // 268
    Tinit("punpckhbw"),               // 269
    Tinit("punpckhdq"),               // 270
    Tinit("punpckhwd"),               // 271
    Tinit("punpcklbw"),               // 272
    Tinit("punpckldq"),               // 273
    Tinit("punpcklwd"),               // 274
    Tinit("push"),                    // 275
    Tinit("pusha"),                   // 276
    Tinit("pushad"),                  // 277
    Tinit("pushf"),                   // 278
    Tinit("pushfd"),                  // 279
    Tinit("pushi"),                   // 280
    Tinit("pxor"),                    // 281
    Tinit("rcl"),                     // 282
    Tinit("rcr"),                     // 283
    Tinit("rdmsr"),                   // 284
    Tinit("rdpmc"),                   // 285
    Tinit("rdtsc"),                   // 286
    Tinit("rep"),                     // 287
    Tinit("repnz"),                   // 288
    Tinit("repz"),                    // 289
    Tinit("retf"),                    // 290
    Tinit("retn"),                    // 291
    Tinit("rol"),                     // 292
    Tinit("ror"),                     // 293
    Tinit("rsm"),                     // 294
    Tinit("sahf"),                    // 295
    Tinit("sal"),                     // 296
    Tinit("sar"),                     // 297
    Tinit("sbb"),                     // 298
    Tinit("scasb"),                   // 299
    Tinit("scasd"),                   // 300
    Tinit("scasw"),                   // 301
    Tinit("seg"),                     // 302
    Tinit("seta"),                    // 303
    Tinit("setae"),                   // 304
    Tinit("setb"),                    // 305
    Tinit("setbe"),                   // 306
    Tinit("setg"),                    // 307
    Tinit("setge"),                   // 308
    Tinit("setl"),                    // 309
    Tinit("setle"),                   // 310
    Tinit("setno"),                   // 311
    Tinit("setns"),                   // 312
    Tinit("setnz"),                   // 313
    Tinit("seto"),                    // 314
    Tinit("setpe"),                   // 315
    Tinit("setpo"),                   // 316
    Tinit("sets"),                    // 317
    Tinit("setz"),                    // 318
    Tinit("sgdt"),                    // 319
    Tinit("shl"),                     // 320
    Tinit("shld"),                    // 321
    Tinit("shr"),                     // 322
    Tinit("shrd"),                    // 323
    Tinit("sidt"),                    // 324
    Tinit("sldt"),                    // 325
    Tinit("smsw"),                    // 326
    Tinit("stc"),                     // 327
    Tinit("std"),                     // 328
    Tinit("sti"),                     // 329
    Tinit("stosb"),                   // 330
    Tinit("stosd"),                   // 331
    Tinit("stosw"),                   // 332
    Tinit("str"),                     // 333
    Tinit("sub"),                     // 334
    Tinit("test"),                    // 335
    Tinit("ud2"),                     // 336
    Tinit("verr"),                    // 337
    Tinit("verw"),                    // 338
    Tinit("wait"),                    // 339
    Tinit("wbinvd"),                  // 340
    Tinit("wrmsr"),                   // 341
    Tinit("xadd"),                    // 342
    Tinit("xchg"),                    // 343
    Tinit("xlatb"),                   // 344
    Tinit("xor"),                     // 345
    { 0, NULL }
    // unused opcode
    // { 6, "FWAIT" } ,
} ;
// defines for the indexes to the mnemonic array
#define ILLEGAL       0  // Any illegal opcode
#define AAA           1  
#define AAD           2  
#define AAM           3  
#define AAS           4  
#define ADC           5  
#define ADD           6  
#define AND           7  
#define ARPL          8  
#define BOUND         9  
#define BSF          10  
#define BSR          11  
#define BSWAP        12  
#define BT           13  
#define BTC          14  
#define BTR          15  
#define BTS          16  
#define CALL         17  
#define CBW          18  
#define CDQ          19  
#define CLC          20  
#define CLD          21  
#define CLI          22  
#define CLTS         23  
#define CMC          24  
#define CMOVA        25  
#define CMOVAE       26  
#define CMOVB        27  
#define CMOVBE       28  
#define CMOVE        29  
#define CMOVG        30  
#define CMOVGE       31  
#define CMOVL        32  
#define CMOVLE       33  
#define CMOVNE       34  
#define CMOVNO       35  
#define CMOVNS       36  
#define CMOVO        37  
#define CMOVPE       38  
#define CMOVPO       39  
#define CMOVS        40  
#define CMP          41  
#define CMPSB        42  
#define CMPSD        43  
#define CMPSW        44  
#define CMPXCHG8B    45  
#define CMPXCHG      46  
#define CPUID        47  
#define CWD          48  
#define CWDE         49  
#define DAA          50  
#define DAS          51  
#define DEC          52  
#define DIV          53  
#define EMMS         54  
#define ENTER        55  
#define ESC          56  
#define F2XM1        57  
#define FABS         58  
#define FADD         59  
#define FADDP        60  
#define FBLD         61  
#define FBSTP        62  
#define FCHS         63  
#define FCLEX        64  
#define FCMOVB       65  
#define FCMOVBE      66  
#define FCMOVE       67  
#define FCMOVNB      68  
#define FCMOVNBE     69  
#define FCMOVNE      70  
#define FCMOVNU      71  
#define FCMOVU       72  
#define FCOM         73  
#define FCOMI        74  
#define FCOMIP       75  
#define FCOMP        76  
#define FCOMPP       77  
#define FCOS         78  
#define FDECSTP      79  
#define FDISI        80  
#define FDIV         81  
#define FDIVP        82  
#define FDIVR        83  
#define FDIVRP       84  
#define FENI         85  
#define FFREE        86  
#define FFREEP       87  
#define FIADD        88  
#define FICOM        89  
#define FICOMP       90  
#define FIDIV        91  
#define FIDIVR       92  
#define FILD         93  
#define FIMUL        94  
#define FINCSTP      95  
#define FINIT        96  
#define FIST         97  
#define FISTP        98  
#define FISUB        99  
#define FISUBR      100  
#define FLD         101  
#define FLD1        102  
#define FLDCW       103  
#define FLDENV      104  
#define FLDL2E      105  
#define FLDL2T      106  
#define FLDLG2      107  
#define FLDLN2      108  
#define FLDPI       109  
#define FLDZ        110  
#define FMUL        111  
#define FMULP       112  
#define FNCLEX      113  
#define FNDISI      114  
#define FNENI       115  
#define FNINIT      116  
#define FNOP        117  
#define FNSAVE      118  
#define FNSTCW      119  
#define FNSTENV     120  
#define FNSTSW      121  
#define FPATAN      122  
#define FPREM       123  
#define FPREM1      124  
#define FPTAN       125  
#define FRNDINT     126  
#define FRSTOR      127
#define FSAVE       128  
#define FSCALE      129  
#define FSETPM      130  
#define FSIN        131  
#define FSINCOS     132  
#define FSQRT       133  
#define FST         134  
#define FSTCW       135  
#define FSTENV      136  
#define FSTP        137  
#define FSTSW       138  
#define FSUB        139  
#define FSUBP       140  
#define FSUBR       141  
#define FSUBRP      142  
#define FTST        143  
#define FUCOM       144  
#define FUCOMI      145  
#define FUCOMIP     146  
#define FUCOMP      147  
#define FUCOMPP     148  
#define FXAM        149  
#define FXCH        150  
#define FXTRACT     151  
#define FYL2X       152  
#define FYL2XP1     153  
#define HLT         154  
#define IDIV        155  
#define IMUL        156  
#define IN          157  
#define INC         158  
#define INSB        159  
#define INSD        160  
#define INSW        161  
#define INT         162  
#define INTO        163  
#define INVD        164  
#define INVLPG      165  
#define IRET        166  
#define IRETD       167  
#define JA          168  
#define JB          169  
#define JCXZ        170  
#define JECXZ       171  
#define JG          172  
#define JL          173  
#define JMP         174  
#define JNA         175  
#define JNB         176  
#define JNG         177  
#define JNL         178  
#define JNO         179  
#define JNS         180  
#define JNZ         181  
#define JO          182  
#define JPE         183  
#define JPO         184  
#define JS          185  
#define JZ          186  
#define LAHF        187  
#define LAR         188  
#define LDS         189  
#define LEA         190  
#define LEAVE       191  
#define LES         192  
#define LFS         193  
#define LGDT        194  
#define LGS         195  
#define LIDT        196  
#define LLDT        197  
#define LMSW        198  
#define LOCK        199  
#define LODSB       200  
#define LODSD       201  
#define LODSW       202  
#define LOOP        203  
#define LOOPNZ      204  
#define LOOPZ       205  
#define LSL         206  
#define LSS         207  
#define LTR         208  
#define MOV         209  
#define MOVB        210
#define MOVD        211  
#define MOVQ        212  
#define MOVSB       213  
#define MOVSD       214  
#define MOVSW       215  
#define MOVSX       216  
#define MOVZX       217  
#define MUL         218  
#define NEG         219  
#define NOP         220  
#define NOT         221  
#define OR          222  
#define OUT         223  
#define OUTSB       224  
#define OUTSD       225  
#define OUTSW       226  
#define PACKSSDW    227  
#define PACKSSWB    228  
#define PACKUSWB    229  
#define PADDB       230  
#define PADDD       231  
#define PADDSB      232  
#define PADDSW      233  
#define PADDUSB     234  
#define PADDUSW     235  
#define PADDW       236  
#define PAND        237  
#define PANDN       238  
#define PCMPEQB     239  
#define PCMPEQD     240  
#define PCMPEQW     241  
#define PCMPGTB     242  
#define PCMPGTD     243  
#define PCMPGTW     244  
#define PMADDWD     245  
#define PMULHW      246  
#define PMULLW      247  
#define POP         248  
#define POPA        249  
#define POPAD       250  
#define POPF        251  
#define POPFD       252  
#define POR         253  
#define PSLLD       254  
#define PSLLQ       255  
#define PSLLW       256  
#define PSRAD       257  
#define PSRAW       258  
#define PSRLD       259  
#define PSRLQ       260  
#define PSRLW       261  
#define PSUBB       262  
#define PSUBD       263  
#define PSUBSB      264  
#define PSUBSW      265  
#define PSUBUSB     266  
#define PSUBUSW     267  
#define PSUBW       268  
#define PUNPCKHBW   269  
#define PUNPCKHDQ   270  
#define PUNPCKHWD   271  
#define PUNPCKLBW   272  
#define PUNPCKLDQ   273  
#define PUNPCKLWD   274  
#define PUSH        275  
#define PUSHA       276  
#define PUSHAD      277  
#define PUSHF       278  
#define PUSHFD      279  
#define PUSHI       280  
#define PXOR        281  
#define RCL         282  
#define RCR         283  
#define RDMSR       284  
#define RDPMC       285  
#define RDTSC       286  
#define REP         287  
#define REPNZ       288  
#define REPZ        289  
#define RETF        290  
#define RETN        291  
#define ROL         292  
#define ROR         293  
#define RSM         294  
#define SAHF        295  
#define SAL         296  
#define SAR         297  
#define SBB         298  
#define SCASB       299  
#define SCASD       300  
#define SCASW       301  
#define SEG         302  
#define SETA        303  
#define SETAE       304  
#define SETB        305  
#define SETBE       306  
#define SETG        307  
#define SETGE       308  
#define SETL        309  
#define SETLE       310  
#define SETNO       311  
#define SETNS       312  
#define SETNZ       313  
#define SETO        314  
#define SETPE       315  
#define SETPO       316  
#define SETS        317  
#define SETZ        318  
#define SGDT        319  
#define SHL         320  
#define SHLD        321  
#define SHR         322  
#define SHRD        323  
#define SIDT        324  
#define SLDT        325  
#define SMSW        326  
#define STC         327  
#define STD         328  
#define STI         329  
#define STOSB       330  
#define STOSD       331  
#define STOSW       332  
#define STR         333  
#define SUB         334  
#define TEST        335  
#define UD2         336  
#define VERR        337  
#define VERW        338  
#define WAIT        339  
#define WBINVD      340  
#define WRMSR       341  
#define XADD        342  
#define XCHG        343  
#define XLATB       344  
#define XOR         345
               
/* mnemonic number to cause special action to be taken, */
/* usually because there are several mnemonics matching */
/* the opcode                                           */
#define odd         0xFFFF          

/***************************************************************************/
/*                         Control flags structure                         */
/***************************************************************************/
typedef struct {
   int Dbit:3;          // hold "d" bit from instruction.
       #define DBit_RegToMem  0         // register is second operand
       #define DBit_MemToReg  1         // register is first operand
       #define DBit_Mem1op    2         // only 1 (memory) operand
   int Wbit:1;          // holds "w" bit from instruction    
                            //    0 => 8 bit,
                            //    1 => 16 or 32 bit            
   int disppres:1;      // a displacement is present in this instruction
   int addrover:1;      // an address override prefix has been found
   int addroverUsed:1;  // address override has been used
   int addr32:1;        // address size is 32-bit
   int opsizeover:1;    // an operand size override prefix has been found
   int opsize32:1;      // operand size is 32-bit
   int prefix:1;        // prefix instruction - rerun opcode decoding
   int waitOp:1;        // a wait opcode that can be incorporated 
                        // into an NPX instruction was found 
   int replock:2;       // rep or lock prefix
       #define replockNone  0  // no prefix
       #define replockREPZ  1  // REPZ prefix
       #define replockREPNZ 2  // REPNZ prefix
       #define replockLOCK  3  // LOCK prefix
   int sizePrefix:3;    // Size prefix when creating output of a memory operand
       #define sizeWop   0     //  as defined by opsize and Wbit
                               //  (word or dword0
       #define sizeByte  PTRbyte       //  byte  (1 bytes)
       #define sizeWord  PTRword       //  word  (2 bytes)
       #define sizeDword PTRdword      //  dword (4 bytes)
       #define sizeFword PTRfword      //  fword (6 bytes)
       #define sizeQword PTRqword      //  qword (8 bytes)
       #define sizeTbyte PTRtbyte      //  tbyte (10 bytes)
       #define sizeNone  PTRnone       //  no opsize entry
   int regf:3;          // reg field from instruction
   int mod:2;           // mod field from instruction
   int rm:3;            // rm field from instruction
   int MMXop:1;         // MMX operation - use MMX registers
} FLAGS;

/* debug flag */
int debug = 0;

/****************************************************************************/
/*  Lookup table from opcode to mnemonic id.   A value of "odd" indicates   */
/*  that special processing is required, as there are several mnemonics     */
/*  used for the opcode.  Note that the opcode is provisional on the        */
/*  instruction not being subsequently determined to be illegal.            */
/*                                                                          */
/*  Two tables are provided.                                                */
/*                                                                          */
/*  Table mnem_16 is for 16 bit segments                                    */
/*  Table mnem_32 is for 32 bit segments                                    */
/*                                                                          */
/****************************************************************************/
const USHORT *mnemnum;          //nemonic number table pointer (set to either mnem_16 or mnem_32)
static const USHORT mnem_16[256] = {
//0      1       2       3       4       5       6       7       8       9       A       B       C       D       E       F
 ADD   , ADD   , ADD   , ADD   , ADD   , ADD   , PUSH  , POP   , OR    , OR    , OR    , OR    , OR    , OR    , PUSH  , odd   , // 0 
 ADC   , ADC   , ADC   , ADC   , ADC   , ADC   , PUSH  , POP   , SBB   , SBB   , SBB   , SBB   , SBB   , SBB   , PUSH  , POP   , // 1 
 AND   , AND   , AND   , AND   , AND   , AND   , odd   , DAA   , SUB   , SUB   , SUB   , SUB   , SUB   , SUB   , odd   , DAS   , // 2 
 XOR   , XOR   , XOR   , XOR   , XOR   , XOR   , odd   , AAA   , CMP   , CMP   , CMP   , CMP   , CMP   , CMP   , odd   , AAS   , // 3 
 INC   , INC   , INC   , INC   , INC   , INC   , INC   , INC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , // 4 
 PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , POP   , POP   , POP   , POP   , POP   , POP   , POP   , POP   , // 5 
 PUSHA , POPA  , BOUND , ARPL  , odd   , odd   , odd   , odd   , PUSH  , IMUL  , PUSH  , IMUL  , INSB  , INSW  , OUTSB , OUTSW , // 6 
 JO    , JNO   , JB    , JNB   , JZ    , JNZ   , JNA   , JA    , JS    , JNS   , JPE   , JPO   , JL    , JNL   , JNG   , JG    , // 7 
 odd   , odd   , MOVB  , odd   , TEST  , TEST  , XCHG  , XCHG  , MOV   , MOV   , MOV   , MOV   , MOV   , LEA   , MOV   , POP   , // 8
 NOP   , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , CBW   , CWD   , CALL  , WAIT  , PUSHF , POPF  , SAHF  , LAHF  , // 9 
 MOV   , MOV   , MOV   , MOV   , MOVSB , MOVSW , CMPSB , CMPSW , TEST  , TEST  , STOSB , STOSW , LODSB , LODSW , SCASB , SCASW , // A 
 MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , // B 
 odd   , odd   , RETN  , RETN  , LES   , LDS   , MOV   , MOV   , ENTER , LEAVE , RETF  , RETF  , INT   , INT   , INTO  , IRET  , // C
 odd   , odd   , odd   , odd   , AAM   , AAD   , odd   , XLATB , odd   , odd   , odd   , odd   , odd   , odd   , odd   , odd   , // D
 LOOPNZ, LOOPZ , LOOP  , JCXZ  , IN    , IN    , OUT   , OUT   , CALL  , JMP   , JMP   , JMP   , IN    , IN    , OUT   , OUT   , // E 
 LOCK  , odd   , odd   , odd   , HLT   , CMC   , odd   , odd   , CLC   , STC   , CLI   , STI   , CLD   , STD   , odd   , odd     // F 
};
static const USHORT mnem_32[256] =  {
//0      1       2       3       4       5       6       7       8       9       A       B       C       D       E       F
 ADD   , ADD   , ADD   , ADD   , ADD   , ADD   , PUSH  , POP   , OR    , OR    , OR    , OR    , OR    , OR    , PUSH  , odd   , // 0
 ADC   , ADC   , ADC   , ADC   , ADC   , ADC   , PUSH  , POP   , SBB   , SBB   , SBB   , SBB   , SBB   , SBB   , PUSH  , POP   , // 1
 AND   , AND   , AND   , AND   , AND   , AND   , odd   , DAA   , SUB   , SUB   , SUB   , SUB   , SUB   , SUB   , odd   , DAS   , // 2
 XOR   , XOR   , XOR   , XOR   , XOR   , XOR   , odd   , AAA   , CMP   , CMP   , CMP   , CMP   , CMP   , CMP   , odd   , AAS   , // 3
 INC   , INC   , INC   , INC   , INC   , INC   , INC   , INC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , DEC   , // 4
 PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , PUSH  , POP   , POP   , POP   , POP   , POP   , POP   , POP   , POP   , // 5
 PUSHAD, POPAD , BOUND , ARPL  , odd   , odd   , odd   , odd   , PUSH  , IMUL  , PUSH  , IMUL  , INSB  , INSD  , OUTSB , OUTSD , // 6
 JO    , JNO   , JB    , JNB   , JZ    , JNZ   , JNA   , JA    , JS    , JNS   , JPE   , JPO   , JL    , JNL   , JNG   , JG    , // 7
 odd   , odd   , MOVB  , odd   , TEST  , TEST  , XCHG  , XCHG  , MOV   , MOV   , MOV   , MOV   , MOV   , LEA   , MOV   , POP   , // 8
 NOP   , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , XCHG  , CWDE  , CDQ   , CALL  , WAIT  , PUSHFD, POPFD , SAHF  , LAHF  , // 9
 MOV   , MOV   , MOV   , MOV   , MOVSB , MOVSD , CMPSB , CMPSD , TEST  , TEST  , STOSB , STOSD , LODSB , LODSD , SCASB , SCASD , // A
 MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , MOV   , // B
 odd   , odd   , RETN  , RETN  , LES   , LDS   , MOV   , MOV   , ENTER , LEAVE , RETF  , RETF  , INT   , INT   , INTO  , IRET  , // C
 odd   , odd   , odd   , odd   , AAM   , AAD   , odd   , XLATB , odd   , odd   , odd   , odd   , odd   , odd   , odd   , odd   , // D
 LOOPNZ, LOOPZ , LOOP  , JECXZ , IN    , IN    , OUT   , OUT   , CALL  , JMP   , JMP   , JMP   , IN    , IN    , OUT   , OUT   , // E
 LOCK  , odd   , odd   , odd   , HLT   , CMC   , odd   , odd   , CLC   , STC   , CLI   , STI   , CLD   , STD   , odd   , odd     // F
} ;
                                    

/****************************************************************************/
/*  mnemonic numbers for shift instructions                                 */
/*  used by C0 and D0 opcodes                                               */
/****************************************************************************/
static const USHORT shiftmnem[8] =  {
    ROL, ROR, RCL, RCR, SHL, SHR, ILLEGAL, SAR,
} ;


static UCHAR *startiptr;               // instruction stream pointer
static char *hbuff = 0;                // hex output buffer pointer
static char *mbuff = 0;                // mnemonic output buffer pointer
static char *ibuff = 0;                // operand output buffer pointer
static UCHAR instr = 0;                // holds the current instruction
static UCHAR ovseg = 0;                // non-zero if there is a current segment override instr pending
static UCHAR defseg = 0;               // default segment for operands (1-based)
static UCHAR basereg = 0;              // index into register names of the base register, 0 if none
static UCHAR indexreg = 0;             // index into register names of the index register, 0 if none
static UCHAR scalefactor = 0;          // scale factor, possible values are: 0 => none 1 => *2 2 => *4 3 => *8
static long opdisp = 0;                // operand displacement from instr

/****************************************************************************/
/* Get bytes, words, dwords from the instruction stream                     */
/****************************************************************************/
static UCHAR getNextByte(void);
static USHORT getNextWord(void);
static ULONG getNextDword(void);
inline long 
getDisplacement(FLAGS & flags)
{
  // get next 2 or 4 byte quantity based on address size and sign extend if required
  flags.addroverUsed = 1;
  return (flags.addr32) ? getNextDword() : ((short)getNextWord());
} 
inline ULONG 
getNextOperand(FLAGS flags)
{ 
  // get next 2 or 4 byte quantity based on operand size
  return (flags.opsize32) ? getNextDword() : getNextWord();
} 
static long getSignedImmediate(FLAGS flags);
static ULONG getImmediate(FLAGS flags);
static FLAGS getMemop(FLAGS flags);
static void getNormalMemop(FLAGS & flags);   

/****************************************************************************/
/* Put values into the operand output                                       */
/****************************************************************************/
static void operandHex(ULONG val, const char *suffix = 0);
static void operandSignedHex(long val, const char *suffix = 0);
static void operandDecimal(long val, const char *suffix = 0);
static void operandTableItem(ULONG item,const TABLE *ptr, const char *suffix = 0); 
static void operandString(const char *str);
static void operandRegister(UCHAR regnum, FLAGS flags, const char *suffix = 0); 
static void operandMemop(FLAGS & flags);
inline void
operandChar(char c)
{
   // output a single character
   if (ibuff)
      *ibuff++ = c;
}
inline void 
operandMMXreg(UCHAR regf, const char *suffix = NULL)
{
   // output an MMX register
   operandTableItem(regf, regMMX, suffix);
}
inline void 
operandSegRegister(UCHAR regnum, const char *suffix = 0)
{
   // output a segment register
   operandTableItem(regnum, segreg, suffix);
} 
static void operandRel(long disp);

/****************************************************************************/
/* put values into the mnemonic output                                      */
/****************************************************************************/
static void mnemonicStd(FLAGS & flags,USHORT mnem_num);

/****************************************************************************/
/* opcode processing functions                                              */
/****************************************************************************/
// These functions may modify the flags
typedef void opcodeFunction(FLAGS &);
static opcodeFunction op_IL;    // illegal opcodes
static opcodeFunction op_NL;    // opcodes with no special processing
static opcodeFunction op_00;
static opcodeFunction op_04;
static opcodeFunction op_06;
static opcodeFunction op_0C;
static opcodeFunction op_0F;
static opcodeFunction op_26;
static opcodeFunction op_40;
static opcodeFunction op_62;
static opcodeFunction op_63;
static opcodeFunction op_66;
static opcodeFunction op_67;
static opcodeFunction op_68;
static opcodeFunction op_69;
static opcodeFunction op_6A;
static opcodeFunction op_70;
static opcodeFunction op_80;
#if OP82
   static opcodefunction op_82;
#else
   #define op_82 op_IL
#endif
static opcodeFunction op_8C;
static opcodeFunction op_8D;
static opcodeFunction op_8F;
static opcodeFunction op_91;
static opcodeFunction op_9A;
static opcodeFunction op_9B;
static opcodeFunction op_A0;
static opcodeFunction op_A4;
static opcodeFunction op_AA;
static opcodeFunction op_B0;
static opcodeFunction op_B8;
static opcodeFunction op_C0;
static opcodeFunction op_C2;
static opcodeFunction op_C3;
static opcodeFunction op_C4;
static opcodeFunction op_C6;
static opcodeFunction op_C8;
static opcodeFunction op_CB;
static opcodeFunction op_CC;
static opcodeFunction op_CD;
static opcodeFunction op_CE;
static opcodeFunction op_CF;
static opcodeFunction op_D0;
static opcodeFunction op_D4;
static opcodeFunction op_D7;
static opcodeFunction op_D8;
static opcodeFunction op_E0;
static opcodeFunction op_E3;
static opcodeFunction op_E4;
static opcodeFunction op_E8;
static opcodeFunction op_EC;
static opcodeFunction op_F0;
static opcodeFunction op_F2;
static opcodeFunction op_F3;
static opcodeFunction op_F6;
static opcodeFunction op_FE;
static opcodeFunction op_0F00;
static opcodeFunction op_0F01;
static opcodeFunction op_0F02;
static opcodeFunction op_0F20;
#if OP0F24
   #define op_0F24 op_0F20
#else
   #define op_0F24 op_IL
#endif
static opcodeFunction op_0F40;
static opcodeFunction op_0F60;
static opcodeFunction op_0F64;
static opcodeFunction op_0F6E;
static opcodeFunction op_0F6F;
static opcodeFunction op_0F71;
static opcodeFunction op_0F80;
static opcodeFunction op_0F90;
static opcodeFunction op_0FA0;
static opcodeFunction op_0FA4;
#if OP0FA6
   #define op_0FA6 op_0FB0
#else
   #define op_0FA6 op_IL
#endif
static opcodeFunction op_0FA8;
static opcodeFunction op_0FAF;
static opcodeFunction op_0FB0;
static opcodeFunction op_0FB2;
static opcodeFunction op_0FB6;
static opcodeFunction op_0FB7;
static opcodeFunction op_0FBA;
static opcodeFunction op_0FBB;
static opcodeFunction op_0FC0;
static opcodeFunction op_0FC7;
static opcodeFunction op_0FC8;

static void initialize(FLAGS & flags, int print);
static void setdw(FLAGS & flags);
inline void 
setw(FLAGS & flags)
{
  flags.Wbit = instr & 1;      // set 8/16/32 bit marker: 0 => 8 bit, 1 => 16/32 bit.
}
static void getMod_rm_dw(FLAGS & flags);
static void memopSetParms(FLAGS flags);
static PARLIST *parm = 0;              // pointer to the parameter block

// string used for hexadecimal conversion
static const char hexConvVal[] = "0123456789ABCDEF";

// class to be thrown if an opcode is determined to be illegal
class IllegalOp {
public:
   IllegalOp(int x=0) { type = x; }
   int type;
};

/****************************************************************************/
/****************************** DisAsm **************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              main routine                                                */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              parmptr              Input/Output interface pointer         */
/*              print                Input        create man readable data  */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
void 
p__DisAsm(PARLIST *parmptr,int print)
{
  // Table of functions used for each opcode
  static opcodeFunction *const opcodeTable[] =  {
    //  0      1     2       3      4      5      6      7      8      9      A      B      C      D      E      F
    op_00, op_00, op_00, op_00, op_04, op_04, op_06, op_06, op_00, op_00, op_00, op_00, op_0C, op_0C, op_06, op_0F, // 0
    op_00, op_00, op_00, op_00, op_04, op_04, op_06, op_06, op_00, op_00, op_00, op_00, op_04, op_04, op_06, op_06, // 1
    op_00, op_00, op_00, op_00, op_0C, op_0C, op_26, op_NL, op_00, op_00, op_00, op_00, op_04, op_04, op_26, op_NL, // 2
    op_00, op_00, op_00, op_00, op_0C, op_0C, op_26, op_NL, op_00, op_00, op_00, op_00, op_04, op_04, op_26, op_NL, // 3
    op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, // 4
    op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, op_40, // 5
    op_NL, op_NL, op_62, op_63, op_26, op_26, op_66, op_67, op_68, op_69, op_6A, op_69, op_NL, op_NL, op_NL, op_NL, // 6
    op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, op_70, // 7
    op_80, op_80, op_82, op_80, op_00, op_00, op_00, op_00, op_00, op_00, op_00, op_00, op_8C, op_8D, op_8C, op_8F, // 8
    op_NL, op_91, op_91, op_91, op_91, op_91, op_91, op_91, op_NL, op_NL, op_9A, op_9B, op_NL, op_NL, op_NL, op_NL, // 9
    op_A0, op_A0, op_A0, op_A0, op_A4, op_A4, op_A4, op_A4, op_0C, op_0C, op_AA, op_AA, op_A4, op_A4, op_AA, op_AA, // A
    op_B0, op_B0, op_B0, op_B0, op_B0, op_B0, op_B0, op_B0, op_B8, op_B8, op_B8, op_B8, op_B8, op_B8, op_B8, op_B8, // B
    op_C0, op_C0, op_C2, op_C3, op_C4, op_C4, op_C6, op_C6, op_C8, op_NL, op_C2, op_CB, op_CC, op_CD, op_CE, op_CF, // C
    op_D0, op_D0, op_D0, op_D0, op_D4, op_D4, op_IL, op_D7, op_D8, op_D8, op_D8, op_D8, op_D8, op_D8, op_D8, op_D8, // D
    op_E0, op_E0, op_E0, op_E3, op_E4, op_E4, op_E4, op_E4, op_E8, op_E8, op_9A, op_70, op_EC, op_EC, op_EC, op_EC, // E
    op_F0, op_IL, op_F2, op_F3, op_NL, op_NL, op_F6, op_F6, op_NL, op_NL, op_NL, op_NL, op_NL, op_NL, op_FE, op_FE  // F
  } ;
  FLAGS flags;

  memset(&flags, 0, sizeof(flags));
  parm = parmptr;
  startiptr = parm->iptr;
  ovseg = 0;
  defseg = DS+1;                          // DS
  initialize(flags,print);

  try {
     /********************************************************************/
     /* loop only if we find an opcode prefix:                           */
     /*     operand size                                                 */
     /*     address size                                                 */
     /*     repeat                                                       */
     /*     segment override                                             */
     /********************************************************************/
     do {
       flags.prefix = 0;
       flags.sizePrefix = sizeWop;
       if (parm->flagbits & use32mask) {
          flags.opsize32 = !flags.opsizeover;
          flags.addr32 = !flags.addrover;
       } else {
          flags.opsize32 = flags.opsizeover;
          flags.addr32 = flags.addrover;
       }

       #if RETPARMS
         parm->retbits = 0;
         if (flags.opsize32)
           parm->retbits |= 1;
         if (flags.addr32)
           parm->retbits |= 2;         
       #endif

       // determine which opcode table is to be used
       mnemnum = (flags.opsize32) ? mnem_32 : mnem_16;

       instr = getNextByte();                     // get next byte of instruction
   
       // in many cases we can print the instruction mnemonic now
       mnemonicStd(flags,mnemnum[instr]);
   
       // call according to the instruction opcode
       opcodeTable[instr](flags);
     }  while (flags.prefix);              // enddo
   
     // check if overrides were used if they were present
     if (ovseg || (flags.addrover && !flags.addroverUsed)) {
        /****************************************************/
        /* we had an unused prefix.                         */
        /* backtrack, and reply with an illegal instruction */
        /****************************************************/
        initialize(flags,print);
        op_IL(flags);    
     } 
  } /* end try */
  catch ( const IllegalOp x ) {
     // there is something wrong with the instruction
     initialize(flags,print);
     op_IL(flags);           
  } /* end catch */

  /**************************************************************************/
  /* update the returned buffers and instruction length                     */
  /**************************************************************************/
  parm->retleng = parm->iptr-startiptr;
  if (hbuff) 
     *hbuff = 0;
  if (ibuff) 
     *ibuff = 0;
  parm->retregop = flags.regf;
  parm->retmod = flags.mod;
  parm->retrm = flags.rm;
  /* printf("ihnpdsm: mod=%x, reg/op=%x, r/m=%x\n", parm->retmod, parm->retregop, parm->retrm);  */

} 

/****************************************************************************/
/**************************** operandRel ************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              print the JMP/CALL operand relative displacement            */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              disp        Input: displacement from instruction            */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandRel(long disp)
{
   disp += parm->iptr - startiptr;
   if (parm->instr_EIP == 0xFFFFFFFF) {
      // display a relative displacement
      operandChar('$');
      operandSignedHex(disp);
   } else {
      // display an absolute displacement
      operandHex(parm->instr_EIP + disp);
   }
}

/****************************************************************************/
/**************************** operandSignedHex ******************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              print the specified dword in hex with a sign                */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              val         Input: value to be printed                      */
/*              suffix      Input: string to follow value                   */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandSignedHex(long val, const char *suffix)
{
   if (ibuff) {
      if (val < 0) {
         *ibuff++ = '-';
         val = -val;
      } else {
         *ibuff++ = '+';
      } // endif
      operandHex(val, suffix);
   }
}

/****************************************************************************/
/**************************** operandHex ************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              print the specified dword in hex (unsigned)                 */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              val         Input: value to be printed                      */
/*              suffix      Input: string to follow value                   */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/

/* rotate dword left logical 4 bits*/ 

ULONG _lrotl4(ULONG val)
{
    if (debug)
        printf("Calling _lrotl4: 0x%08lx\n", val);  

    ULONG tmp = (val >> 28) & 0x0f;
    return ((val << 4) | tmp);
}



static void 
operandHex(ULONG val, const char *suffix) 
{
   if (debug)
      printf("Calling operandHex: 0x%08lx\n", val);

   if (ibuff) {
      // there must always be at least one digit
      if (!val) {
         *ibuff++ = '0';
      } else {
         int i;
         for (i = 0; i < 8 ; i++ ) {
             /* val = _lrotl(val,4); */
            val = _lrotl4(val);   /* rotate left logical 4 bits */
            if (val & 0x0f ) {
               if ((val & 0x0f) >= 0x0a)
                  *ibuff++ = '0';      // leading zero required
               *ibuff++ = hexConvVal[val & 0x0f];
               for (; ++i < 8 ;  ) {
                   /* val = _lrotl(val,4); */ 
                  val = _lrotl4(val);
                  *ibuff++ = hexConvVal[val & 0x0f];
               } // endfor
               if (val >= 10) {
                  // hex number
                  *ibuff++ = 'H';
               } // endif
            } 
         } // endfor
      } // endif

      if (suffix)
         while (*suffix) {
            *ibuff++ = *suffix++;
         } // endwhile
   } // endif
} 


/****************************************************************************/
/**************************** operandDecimal ********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              print the specified dword in decimal                        */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              val         Input: value to be printed                      */
/*              suffix      Input: string to follow value                   */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandDecimal(long val, const char *suffix) 
{
   if (ibuff) {
      ibuff += sprintf(ibuff, "%ld%s",val, suffix ? suffix : "");
   } // endif
} 

/****************************************************************************/
/**************************** operandTableItem ******************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              print the specified item from the table pointed by ptr      */
/*              item starts with index 0                                    */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              item        Input: item number in the table                 */
/*              ptr         Input: pointer to the table of items            */
/*              suffix      Input: string to follow value                   */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandTableItem(ULONG item,const TABLE *ptr, const char *suffix) 
{
   if (ibuff) {
      memcpy(ibuff, ptr[item].string, ptr[item].length);
      ibuff += ptr[item].length;
      if (suffix) 
         while (*suffix) {
            *ibuff++ = *suffix++;
         } // endwhile
   } // endif
} 

/****************************************************************************/
/**************************** mnemonicStd ***********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              prints a mnemonic, including the REP/LOCK prefixes          */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags       Input/Output: flags                             */
/*              mnem_num    Input: mnemonic number                          */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
mnemonicStd(FLAGS & flags, USHORT mnem_num)
{
   if (mbuff && mnem_num != odd) {
      // if this is the illegal opcode - ignore the REP/LOCK prefixes
      if (mnem_num == ILLEGAL) 
         flags.replock = replockNone;

      switch (flags.replock) {
      case replockREPZ:
         /*********************************************************************/
         /*  check for instructions for which REP or REPZ is a valid prefix   */
         /*********************************************************************/
         switch (mnem_num) {
         case INSB:
         case INSW:
         case INSD:
         case MOVSB:
         case MOVSW:
         case MOVSD:
         case OUTSB:
         case OUTSW:
         case OUTSD:
         case LODSB:
         case LODSW:
         case LODSD:
         case STOSB:
         case STOSW:
         case STOSD:
            memcpy(mbuff, mnemstr[REP].string, mnemstr[REP].length);
            mbuff[mnemstr[REP].length - 1] = ' ';
            memcpy(mbuff + mnemstr[REP].length, mnemstr[mnem_num].string, mnemstr[mnem_num].length);
            break;
         case CMPSB:
         case CMPSW:
         case CMPSD:
         case SCASB:
         case SCASW:
         case SCASD:
            memcpy(mbuff, mnemstr[REPZ].string, mnemstr[REPZ].length);
            mbuff[mnemstr[REPZ].length - 1] = ' ';
            memcpy(mbuff + mnemstr[REPZ].length, mnemstr[mnem_num].string, mnemstr[mnem_num].length);
            break;
         default:
            // REP not valid on other instructions
            throw IllegalOp();
            break;
         } /* endswitch */
         break;
      case replockREPNZ:
         /*******************************************************************/
         /*     check for instructions for which REPNZ is a valid prefix    */
         /*******************************************************************/
         switch (mnem_num) {
         case CMPSB:
         case CMPSW:
         case CMPSD:
         case SCASB:
         case SCASW:
         case SCASD:
            memcpy(mbuff, mnemstr[REPNZ].string, mnemstr[REPNZ].length);
            mbuff[mnemstr[REPNZ].length - 1] = ' ';
            memcpy(mbuff + mnemstr[REPNZ].length, mnemstr[mnem_num].string, mnemstr[mnem_num].length);
            break;
         default:
            throw IllegalOp();
            break;
         } /* endswitch */
         break;
      case replockLOCK:
         /*********************************************************************/
         /*      check for instructions for which LOCK is a valid prefix      */
         /*********************************************************************/
         switch (mnem_num) {
         case ADD:
         case ADC:
         case AND:
         case BTC:
         case BTR:
         case BTS:
         case CMPXCHG:
         case DEC:
         case INC:
         case NEG:
         case NOT:
         case OR:
         case SBB:
         case SUB:
         case XOR:
         case XADD:
         case XCHG:
            memcpy(mbuff, mnemstr[LOCK].string, mnemstr[LOCK].length);
            mbuff[mnemstr[LOCK].length - 1] = ' ';
            memcpy(mbuff + mnemstr[LOCK].length, mnemstr[mnem_num].string, mnemstr[mnem_num].length);
            break;
         default: 
            // LOCK not valid on other instructions
            throw IllegalOp();
            break;
         } /* endswitch */
         break;
      default:
         memcpy(mbuff, mnemstr[mnem_num].string, mnemstr[mnem_num].length);
      } /* endswitch */
   } // endif
} 


/****************************************************************************/
/****************************** operandRegister *****************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              prints an 8, 16 or 32 bit register name, according to wbit  */
/*              and the current mode                                        */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              regnum      Input: ID of the register                       */
/*              flags       Input: flags                                    */
/*              suffix      Input: string to follow register number         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandRegister(UCHAR regnum, FLAGS flags, const char * suffix)
{
  if (ibuff) {
    if (!flags.Wbit) {
      operandTableItem(regnum, reg8, suffix);
    } else if (flags.opsize32) {
      operandTableItem(regnum, reg32, suffix);
    } else {
      operandTableItem(regnum, reg16, suffix );
    }
  } // endif
} 

/****************************************************************************/
/****************************** getNextByte *********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              set ic to next byte of the instruction, and print it in hex */
/*                                                                          */
/****************************************************************************/
static UCHAR 
getNextByte(void)
{
  UCHAR ic = *(parm->iptr)++;
  if (hbuff) {
    *hbuff++ = hexConvVal[(ic >> 4)&0x0F];
    *hbuff++ = hexConvVal[ic&0x0F];
  } 
  if (debug)
      printf("getNextByte:  %s\n", hbuff);
  return ic;
} 

/****************************************************************************/
/****************************** getNextWord *********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get the next 2 bytes from the instruction stream, update    */
/*              the inttruction pointer and the hex data buffer             */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              none                                                        */
/*                                                                          */
/*  RETURNS:                                                                */
/*              the data retrieved (as a USHORT)                            */
/*                                                                          */
/****************************************************************************/
static USHORT 
getNextWord(void) 
{
  USHORT Dword1 = *(USHORT *)(parm->iptr);
  parm->iptr += sizeof(USHORT);
  if (hbuff) {
    for (unsigned i = 0; i < (8 * sizeof(USHORT)); i += 8) {
       unsigned x = Dword1 >> i;
       *hbuff++ = hexConvVal[( x >> 4)&0x0F];
       *hbuff++ = hexConvVal[x & 0x0F];
    } // endfor
  } 
  if (debug)
      printf("getNextWord:  %s\n", hbuff);
  return Dword1;
} 

/****************************************************************************/
/****************************** getNextDword ********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get the next 4 bytes from the instruction stream, update    */
/*              the instruction pointer and the hex data buffer             */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              none                                                        */
/*                                                                          */
/*  RETURNS:                                                                */
/*              the data retrieved (as a ULONG)                             */
/*                                                                          */
/*                                                                          */
/****************************************************************************/
static ULONG 
getNextDword(void) 
{
    ULONG Dword1 = *(ULONG *)(parm->iptr);
    parm->iptr += sizeof(ULONG);
    if (hbuff) {                         // Print the instruction word in hex
        for (unsigned i = 0; i < (8 * sizeof(ULONG)); i += 8) {
            unsigned x = Dword1 >> i;
            *hbuff++ = hexConvVal[( x >> 4)&0x0F];
            *hbuff++ = hexConvVal[x & 0x0F];
        } // endfor
    } 
    if (debug)
        printf("getNextDword:  0x%08lx\n", Dword1);
    return Dword1;
} 


/****************************************************************************/
/****************************** getImmediate ********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get the next 1, 2, or 4 bytes from the instruction stream,  */
/*              update the instruction pointer and the hex data buffer      */
/*              Quantity of data dependent on opsize and Wbit               */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input: the controlling flags               */
/*                                                                          */
/*  RETURNS:                                                                */
/*              the data retrieved (as a ULONG)                             */
/*                                                                          */
/****************************************************************************/
static ULONG 
getImmediate(FLAGS flags) 
{
    if (flags.Wbit == 0) { 
        // a byte operand
        return getNextByte();
    } else {
        // a 16 or 32 bit ooerand depending on instruction setting
        return getNextOperand(flags);
    } 
} 

/****************************************************************************/
/****************************** getSignedImmediate **************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get the next 1, 2, or 4 bytes from the instruction stream,  */
/*              update the instruction pointer and the hex data buffer      */
/*              Quantity of data dependent on opsize and Wbit               */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input: the controlling flags               */
/*                                                                          */
/*  RETURNS:                                                                */
/*              the data retrieved (sign extended to a long)                */
/*                                                                          */
/****************************************************************************/
static long 
getSignedImmediate(FLAGS flags) 
{
    if (flags.Wbit == 0) { 
        return (signed char)getNextByte();  // sign extended byte operand
    } else if (flags.opsize32) {
        return getNextDword();
    } else {
        return (short)getNextWord();        // sign extended word operand
    } 
} 

/****************************************************************************/
/****************************** operandString *******************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              output the specified string to the operand buffer, if one   */
/*              exists.                                                     */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              str              Input: the string to be outputted          */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandString(const char *str) 
{
    if (ibuff) {
        for (;*str ; ) {
            *ibuff++ = *str++;
        } // endfor
    } // endif
} 

/****************************************************************************/
/****************************** initialize **********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              init all parameters and assign buffer for output            */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*              print            Input:  Non-zero if buffers are present    */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
initialize(FLAGS & flags, int print) 
{
    if (!print) {
        hbuff = 0;
        ibuff = 0;
        mbuff = 0;
    } else {
        hbuff = (char *)parm->hbuffer;
        ibuff = (char *)parm->ibuffer;
        mbuff = (char *)parm->mbuffer;
    }
    flags.Dbit = DBit_RegToMem;
    parm->rettype = notype;
    parm->retoffset = 0;
#if RETPARMS
    parm->retbits = 0;
    parm->retreg = 0;
    parm->retseg = 0;
    parm->retscale = 0;
    parm->retbase = 255;
    parm->retindex = 255;
#endif
} 

/****************************************************************************/
/****************************** setdw ***************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              set the 'd' and 'w' bits from the instruction               */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
setdw(FLAGS & flags) 
{
    // set 8/16/32 bit marker: 0 => 8 bit, 1 => 16 or 32 bit
    setw(flags);

    // set direction bit: 2 => mem->reg 0 => reg->mem
    flags.Dbit = (instr & 2) ? DBit_MemToReg : DBit_RegToMem;
} 

/****************************************************************************/
/****************************** getMod_rm_dw ********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              set trm-regf-mod from byte after instruction                */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
getMod_rm_dw(FLAGS & flags) 
{
    setdw(flags);
    UCHAR ic = getNextByte();

    // disassemble the mod/rm byte
    flags.rm = ic & 0x07;
    flags.regf = (ic >> 3) & 0x07;
    flags.mod = (ic >> 6) & 0x03;

    if (flags.addr32) {                // interpret this as a 32-bit instr
        indexreg = 0;
        scalefactor = 0;
        if (flags.mod != 3) {
            flags.addroverUsed = 1;      // we made use of the address override
            basereg = (UCHAR)(flags.rm+1);
            if (flags.rm == 4) {         // we have a SIB byte
                ic = getNextByte();   // get it
                basereg = (ic & 0x07) + 1;
                indexreg = ((ic >> 3) & 0x07 ) + 1;
                scalefactor = (ic >> 6) & 0x03;
  
                if (indexreg == 5) {          // index=4 -> no index
                    indexreg = 0;
                    if (scalefactor != 0) {
                        // scale factor must be zero
                        throw IllegalOp();
                    } // endif
                } 
                if (flags.mod == 0 && basereg == 6) {  // a special case, no base
                    basereg = 0;
                    flags.disppres = 1;          // remember what we have done
                } 
            } else if (flags.mod == 0 && flags.rm == 5)
                basereg = 0;
        }
        if (basereg == (1 + EBP) || basereg == (1 + ESP))  // EBP or ESP
            defseg = SS+1;                      // SS
    } 
    else {                               // interpret this as a 16-bit instr
        // vectors to convert 16-bit format mod-r/m bytes to base and index register forms
        static const UCHAR basereg16[8] =  { BX, BX, BP, BP, SI, DI, BP, BX };
        static const UCHAR indexreg16[8] =  { SI + 1, DI + 1, SI + 1, DI + 1, 0, 0, 0, 0 } ;
    
        if (flags.mod != 3) 
            flags.addroverUsed = 1;      // we made use of the address override
        basereg = 1 + basereg16[flags.rm];
        indexreg = indexreg16[flags.rm];
        if (flags.mod == 0 && flags.rm == 6)
            basereg = 0;
        else if (basereg == (1 + BP))      // BP
            defseg = SS+1;                      // SS
        scalefactor = 0;
    } 
} 

/****************************************************************************/
/****************************** op_IL   *************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              handle illegal operations                                   */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
op_IL(FLAGS UNUSED & flags ) 
{
    parm->iptr = startiptr+1;
    if (mbuff) {
        /*********************************************************************/
        /* put the hexcode of the first byte of the instruction into mbuff   */
        /*********************************************************************/
        sprintf(mbuff, "%s%2.2X", mnemstr[ILLEGAL].string, *startiptr);
    } 
    parm->rettype = illegtype;
} 


/****************************************************************************/
/****************************** operandMemop ********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              Output the standard memory operands to the instruction      */
/*              buffer, if present.                                         */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
operandMemop(FLAGS & flags) 
{
    if (flags.Dbit == DBit_MemToReg) {
        if (flags.MMXop) 
            operandMMXreg(flags.regf, ",");
        else 
            operandRegister(flags.regf,flags, ",");
    }

    memopSetParms(flags);

    if (flags.mod == 3) {                    // operand is a register
        // it is illegal for a LOCK prefix to be present if there is no memory op
        if (flags.replock == replockLOCK) 
            throw IllegalOp();

        if (ibuff) {
            if (flags.MMXop)
                operandMMXreg(flags.rm);
            else
                operandRegister(flags.rm,flags);
        }
    } else if (ibuff) {
        // operand is a memory location
        if (flags.sizePrefix != sizeWop) {
            operandTableItem(flags.sizePrefix,PTRsize);
        } else if (flags.Wbit) {
            if (flags.opsize32)
                operandTableItem(PTRdword,PTRsize);
            else
                operandTableItem(PTRword,PTRsize);
        } else {
            operandTableItem(PTRbyte,PTRsize);
        }
 
        // must show displacement if base and index are not present,
        // even if the displacement is 0
        int mustShowDisp = !basereg && !indexreg;  

        char paren = '[';
 
        if (ovseg) {                      // override segment present
            operandSegRegister(ovseg-1, ":");
            ovseg = 0;                      // delete it - used
        } else if (mustShowDisp) {      // there is no base or index
            // need to explicitly show the segment register if displacement only
            operandSegRegister(defseg-1, ":");
        }
 
        if (basereg) {                   
            // there is a base register
            *ibuff++ = paren;
            paren = '+';
            if (flags.addr32)
                operandTableItem(basereg-1, reg32);
            else
                operandTableItem(basereg-1, reg16);
        } 
 
        if (indexreg) {             
            // there is an index register
            *ibuff++ = paren;
            paren = '+';
            if (flags.addr32)
                operandTableItem(indexreg-1, reg32);
            else
                operandTableItem(indexreg-1, reg16);
 
            switch (scalefactor) {
            case 1 :
                operandString("*2");
                break;
            case 2 :
                operandString("*4"); 
                break;
            case 3 :
                operandString("*8"); 
                break;
            } // endswitch
        } 
 
        if (opdisp || mustShowDisp) {   
            // put out a paren if we have not done so yet
            if (paren == '[') {
                *ibuff++ = '[';
            } // endif

            operandSignedHex(opdisp);
        } // endif
 
        // we have output a left parenthesis - output a right parenthesis to match
        *ibuff++ = ']';

    } // endif

    if (flags.Dbit == DBit_RegToMem) {
        /************************************************************************/
        /* register is second operand - print register name                     */
        /************************************************************************/
        operandChar(',');
        if (flags.MMXop) 
            operandMMXreg(flags.regf);
        else 
            operandRegister(flags.regf,flags);
    } // endif
} 


/****************************************************************************/
/****************************** getNormalMemop ******************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get memory operand from instruction stream                  */
/*              and output as an operand to ibuff                           */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
getNormalMemop(FLAGS & flags) 
{ 
    flags = getMemop(flags);
    operandMemop(flags);
}
   
/****************************************************************************/
/****************************** getMemop ************************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              get memory operand from instruction stream                  */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input/Output:  the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static FLAGS 
getMemop(FLAGS flags) 
{
    UCHAR ic;
    opdisp = 0;

    switch (flags.mod) {
    case 0:
        // there is a diplacement only for certain combinations
        if ((flags.rm == 6 && !flags.addr32)
            || (flags.rm == 5 && flags.addr32)
            || (flags.addr32 && flags.disppres)) {

            // we have a displacement
            opdisp = getDisplacement(flags);
            flags.disppres = 1;
        }
        break;
    case 1:
        // there is an 8 bit signed displacement
        ic = getNextByte();
        opdisp = (signed long)((signed char)ic); // sign extend operand
        flags.disppres = 1;
        break;
    case 2:
        // we have a 16/32 bit displacement
        opdisp = getDisplacement(flags);
        flags.disppres = 1;
        break;
        // case 3 is a register
    } // endswitch

    return flags;
}


/****************************************************************************/
/**************************** memopSetParms *********************************/
/****************************************************************************/
/*                                                                          */
/*  DESCRIPTION:                                                            */
/*              set output parms from memory operation                      */
/*                                                                          */
/*  PARAMETERS:                                                             */
/*              flags            Input:         the flags structure         */
/*                                                                          */
/*  RETURNS:                                                                */
/*              none                                                        */
/*                                                                          */
/****************************************************************************/
static void 
memopSetParms(FLAGS flags) 
{
    if (flags.mod != 3) {
        if (flags.disppres)
            parm->retoffset = opdisp;
        if (parm->rettype == 0) {
            if (flags.Wbit == 0)
                parm->rettype = membtype;
            else
                parm->rettype = memwtype;
        } 
#if RETPARMS
        if (basereg != 0) {
            parm->retbase = (UCHAR)(basereg-1);
            if (flags.addr32)
                parm->retbase = (UCHAR)(parm->retbase+8);
        }
        if (indexreg != 0) {
            parm->retindex = (UCHAR)(indexreg-1);
            if (flags.addr32)
                parm->retindex = (UCHAR)(parm->retindex+8);
            parm->retscale = scalefactor;
        }
        if (ovseg == 0) {
            parm->retreg = defseg;
        } else {
            parm->retreg = ovseg;
        }
#endif
    } 
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     27 DAA                                                              */
/*     2F DAS                                                              */
/*     37 AAA                                                              */
/*     3F AAS                                                              */
/*     60 PUSHA                                                            */
/*     61 POPA                                                             */
/*     6C INSB                                                             */
/*     6D INSW/INSD                                                        */
/*     6E OUTSB                                                            */
/*     6F OUTSW/OUTSD                                                      */
/*     90 NOP                                                              */
/*     98 CBW                                                              */
/*     99 CWD                                                              */
/*     9B WAIT                                                             */
/*     9C PUSHF                                                            */
/*     9D POPF                                                             */
/*     9E SAHF                                                             */
/*     9F LAHF                                                             */
/*     C9 LEAVE                                                            */
/*     F4 HLT                                                              */
/*     F5 CMC                                                              */
/*     F8 CLC                                                              */
/*     F9 STC                                                              */
/*     FA CLI                                                              */
/*     FB STI                                                              */
/*     FC CLD                                                              */
/*     FD STC                                                              */
/*     OF06 CLTS                                                           */
/*     OF08 INVD                                                           */
/*     OF09 WBINVD                                                         */
/*     OF0B UD2                                                            */
/*     OF30 WRMSR                                                          */
/*     OF31 RDTSC                                                          */
/*     OF32 RDMSR                                                          */
/*     OF33 RDPMC                                                          */
/*     OF77 EMMS         MMX Extension                                     */
/*     OFA2 CPUID                                                          */
/*     OFAA RSM                                                            */
/***************************************************************************/
/*  operands with no arguments                                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_NL(FLAGS UNUSED & flags)
{
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*      00 01 02 03  ADD                                                   */
/*      08 09 0A 0B  OR                                                    */
/*      10 11 12 13  ADC                                                   */
/*      18 19 1A 1B  SBB                                                   */
/*      20 21 22 23  AND                                                   */
/*      28 29 2A 2B  SUB                                                   */
/*      30 31 32 33  XOR                                                   */
/*      38 39 3A 3B  CMP                                                   */
/*      84 85        TEST                                                  */
/*      86 87        XCHG                                                  */
/*      88 89 8A 8B  MOV                                                   */
/***************************************************************************/
/*  single byte instructions with mem-regf-r/m byte plus possible          */
/*  displacement bytes                                                     */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_00(FLAGS & flags)
{
    getMod_rm_dw(flags);
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*              04 05 ADD                                                  */
/*              14 15 ADC                                                  */
/*              1C 1D SBB                                                  */
/*              2C 2D SUB                                                  */
/*              3C 3D CMP                                                  */
/***************************************************************************/
/* arithmetic operations between AX/AL (EAX/AL in 32-bit mode) and         */
/* immediate operands                                                      */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_04(FLAGS & flags) 
{
    setw(flags);

    if (flags.replock == replockLOCK) 
        throw IllegalOp();

    flags.regf = 0;                            // the register is EAX/AX/AL
    operandRegister(flags.regf,flags, ",");
    parm->rettype = regimmedtype;

    long Dword1 = getSignedImmediate(flags);
    operandSignedHex(Dword1);

#if RETPARMS
    parm->retreg = flags.regf;
    if (!flags.Wbit)
        parm->retreg += 16;
    else if (flags.opsize32)
        parm->retreg += 8;
    parm->retimmed = Dword1;
#endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*              0C 0D OR                                                   */
/*              24 25 AND                                                  */
/*              34 35 XOR                                                  */
/*              A8 A9 TEST                                                 */
/***************************************************************************/
/* Logical operations between AX/AL (EAX/AL in 32-bit mode) and immediate  */
/* operands                                                                */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0C(FLAGS & flags)
{
    setw(flags);

    if (flags.replock == replockLOCK) 
        throw IllegalOp();

    flags.regf = 0;                            // the register is EAX/AX/AL
    operandRegister(flags.regf,flags, ",");
    parm->rettype = regimmedtype;

    ULONG Dword1 = getImmediate(flags);
    operandHex(Dword1);
#if RETPARMS
    parm->retreg = flags.regf;
    if (!flags.Wbit)
        parm->retreg += 16;
    else if (flags.opsize32)
        parm->retreg += 8;
    parm->retimmed = Dword1;
#endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     06 PUSH ES                                                          */
/*     07 POP ES                                                           */
/*     0E PUSH CS                                                          */
/*     16 PUSH SS                                                          */
/*     17 POP SS                                                           */
/*     1E PUSH DS                                                          */
/*     1F POP  DS                                                          */
/***************************************************************************/
/*  single byte segment register instructions                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_06(FLAGS UNUSED & flags) 
{
    int Dword1 = (instr & 0x18) >> 3;   // get register number
    operandSegRegister(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     0F Two byte escape                                                  */
/***************************************************************************/
/*  Since these are two-byte instructions, there is an additional          */
/*  function table here, used to handle the second byte                    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F(FLAGS & flags) 
{
    static opcodeFunction *const opcode0FTable[] =  {
        //  0        1        2        3        4        5        6        7        8        9        A        B        C        D        E        F    */
        op_0F00, op_0F01, op_0F02, op_0F02, op_IL  , op_IL  , op_NL  , op_IL  , op_NL  , op_NL  , op_IL  , op_NL  , op_IL  , op_IL  , op_IL  , op_IL  ,  // 0 
        op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  ,  // 1 
        op_0F20, op_0F20, op_0F20, op_0F20, op_0F24, op_IL  , op_0F24, op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  ,  // 2
        op_NL  , op_NL  , op_NL  , op_NL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  ,  // 3 
        op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40, op_0F40,  // 4 
        op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  ,  // 5 
        op_0F60, op_0F60, op_0F60, op_0F64, op_0F64, op_0F64, op_0F64, op_0F64, op_0F64, op_0F64, op_0F64, op_0F64, op_IL  , op_IL  , op_0F6E, op_0F6F,  // 6 
        op_IL  , op_0F71, op_0F71, op_0F71, op_0F64, op_0F64, op_0F64, op_NL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_0F6E, op_0F6F,  // 7 
        op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80, op_0F80,  // 8 
        op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90, op_0F90,  // 9 
        op_0FA0, op_0FA0, op_NL  , op_0FBB, op_0FA4, op_0FA4, op_0FA6, op_0FA6, op_0FA8, op_0FA8, op_NL  , op_0FBB, op_0FA4, op_0FA4, op_IL  , op_0FAF,  // A
        op_0FB0, op_0FB0, op_0FB2, op_0FBB, op_0FB2, op_0FB2, op_0FB6, op_0FB7, op_IL  , op_IL  , op_0FBA, op_0FBB, op_0FAF, op_0FAF, op_0FB6, op_0FB7,  // B 
        op_0FC0, op_0FC0, op_IL  , op_IL  , op_IL  , op_IL  , op_IL  , op_0FC7, op_0FC8, op_0FC8, op_0FC8, op_0FC8, op_0FC8, op_0FC8, op_0FC8, op_0FC8,  // C
        op_IL  , op_0F64, op_0F64, op_0F64, op_IL  , op_0F64, op_IL  , op_IL  , op_0F64, op_0F64, op_IL  , op_0F64, op_0F64, op_0F64, op_IL  , op_0F64,  // D 
        op_IL  , op_0F64, op_0F64, op_IL  , op_IL  , op_0F64, op_IL  , op_IL  , op_0F64, op_0F64, op_IL  , op_0F64, op_0F64, op_0F64, op_IL  , op_0F64,  // E 
        op_IL  , op_0F64, op_0F64, op_0F64, op_IL  , op_0F64, op_IL  , op_IL  , op_0F64, op_0F64, op_0F64, op_IL  , op_0F64, op_0F64, op_0F64, op_IL     // F 
    } ;

    /**************************************************************************/
    /* mnemonic numbers for 0F orders                                         */
    /**************************************************************************/
    static const USHORT mnem0F[256] =  {
        //0         1          2          3         4        5        6        7         8          9          A          B         C        D        E       F     
        odd      , odd      , LAR      , LSL     , ILLEGAL, ILLEGAL, CLTS   , ILLEGAL , INVD     , WBINVD   , ILLEGAL  , UD2     , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 0
        ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL , ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 1
        MOV      , MOV      , MOV      , MOV     , MOV    , ILLEGAL, MOV    , ILLEGAL , ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 2
        WRMSR    , RDTSC    , RDMSR    , RDPMC   , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL , ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 3
        CMOVO    , CMOVNO   , CMOVB    , CMOVAE  , CMOVE  , CMOVNE , CMOVBE , CMOVA   , CMOVS    , CMOVNS   , CMOVPE   , CMOVPO  , CMOVL  , CMOVGE , CMOVLE , CMOVG  , // 4
        ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL , ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 5
        PUNPCKLBW, PUNPCKLWD, PUNPCKLDQ, PACKSSWB, PCMPGTB, PCMPGTW, PCMPGTD, PACKUSWB, PUNPCKHBW, PUNPCKHWD, PUNPCKHDQ, PACKSSDW, ILLEGAL, ILLEGAL, MOVD   , MOVQ   , // 6
        ILLEGAL  , odd      , odd      , odd     , PCMPEQB, PCMPEQW, PCMPEQD, EMMS    , ILLEGAL  , ILLEGAL  , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, MOVD   , MOVQ   , // 7
        JO       , JNO      , JB       , JNB     , JZ     , JNZ    , JNA    , JA      , JS       , JNS      , JPE      , JPO     , JL     , JNL    , JNG    , JG     , // 8
        SETO     , SETNO    , SETB     , SETAE   , SETZ   , SETNZ  , SETBE  , SETA    , SETS     , SETNS    , SETPE    , SETPO   , SETL   , SETGE  , SETLE  , SETG   , // 9
        PUSH     , POP      , CPUID    , BT      , SHLD   , SHLD   , CMPXCHG, CMPXCHG , PUSH     , POP      , RSM      , BTS     , SHRD   , SHRD   , ILLEGAL, IMUL   , // A
        CMPXCHG  , CMPXCHG  , LSS      , BTR     , LFS    , LGS    , MOVZX  , MOVZX   , ILLEGAL  , ILLEGAL  , odd      , BTC     , BSF    , BSR    , MOVSX  , MOVSX  , // B
        XADD     , XADD     , ILLEGAL  , ILLEGAL , ILLEGAL, ILLEGAL, ILLEGAL, odd     , BSWAP    , BSWAP    , BSWAP    , BSWAP   , BSWAP  , BSWAP  , BSWAP  , BSWAP  , // C
        ILLEGAL  , PSRLW    , PSRLD    , PSRLQ   , ILLEGAL, PMULLW , ILLEGAL, ILLEGAL , PSUBUSB  , PSUBUSW  , ILLEGAL  , PAND    , PADDUSB, PADDUSW, ILLEGAL, PANDN  , // D
        ILLEGAL  , PSRAW    , PSRAD    , ILLEGAL , ILLEGAL, PMULHW , ILLEGAL, ILLEGAL , PSUBSB   , PSUBSW   , ILLEGAL  , POR     , PADDSB , PADDSW , ILLEGAL, PXOR   , // E
        ILLEGAL  , PSLLW    , PSLLD    , PSLLQ   , ILLEGAL, PMADDWD, ILLEGAL, ILLEGAL , PSUBB    , PSUBW    , PSUBD    , ILLEGAL , PADDB  , PADDW  , PADDD  , ILLEGAL  // F
    } ;

    instr = getNextByte();                   // get the second byte of the instr
    mnemonicStd(flags,mnem0F[instr]);              // dump the mnemonic, if known
    opcode0FTable[instr](flags);             // and process it
}


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     26 SEG = ES                                                         */
/*     2E SEG = CS                                                         */
/*     36 SEG = SS                                                         */
/*     3E SEG = DS                                                         */
/*     64 SEG = FS                                                         */
/*     65 SEG = GS                                                         */
/***************************************************************************/
/*  single byte segment override instructions                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_26(FLAGS & flags) 
{
    if (ovseg) 
        throw IllegalOp();             // two segment overrides indicates a problem

    switch (instr) {
    case 0x26:
        ovseg = ES + 1;
        break;
    case 0x2E:
        ovseg = CS + 1;
        break;
    case 0x36:
        ovseg = SS + 1;
        break;
    case 0x3E:
        ovseg = DS + 1;
        break;
    case 0x64:
        ovseg = FS + 1;
        break;
    case 0x65:
        ovseg = GS + 1;
        break;
    } /* endswitch */
    flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     40 - 47 INC                                                         */
/*     48 - 4F DEC                                                         */
/*     50 - 57 PUSH                                                        */
/*     58 - F5 POP                                                         */
/***************************************************************************/
/*  single byte instructions with implicit register operands               */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_40(FLAGS & flags) 
{
    if (flags.replock == replockLOCK) 
        throw IllegalOp();

    flags.regf = instr & 0x07;                 // get register number
    flags.Wbit = 1;                      // force 16 or 32 bit register
  
    operandRegister(flags.regf,flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     62 BOUND                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_62(FLAGS & flags) 
{
    getMod_rm_dw(flags);
    if (flags.mod == 3)
        throw IllegalOp();   // illegal if second operand would be a register 

    flags.Dbit = DBit_MemToReg;       // register is first operand
    flags.Wbit = 1;                   // word operation
    flags.sizePrefix = (flags.opsize32) ? sizeQword : sizeDword;       
    parm->rettype = BOUNDtype;
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     63 ARPL                                                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_63(FLAGS & flags)
{
    if (flags.opsizeover) 
        throw IllegalOp();

    getMod_rm_dw(flags);
    flags.Dbit = DBit_RegToMem;      // register is second operand
    flags.Wbit = 1;                  // word operand
    flags.opsize32 = 0;              // this is always a 16-bit operation

#if RETPARMS
    parm->retbits &= 0xFFFE;         // clear addr32 marker
#endif

    getNormalMemop(flags);
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     66 single byte operand size override prefix                         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_66(FLAGS & flags)
{
  if (flags.opsizeover) 
    throw IllegalOp();       // two opsize overrides indicates a problem

  flags.opsizeover = 1;                      // note we have had this
  flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     67 single byte address size override prefix                         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_67(FLAGS & flags)
{
  if (flags.addrover) 
    throw IllegalOp(); // two address size overrides indicates a problem

  flags.addrover = 1;                       // note we have had this
  flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     68 PUSH                                                             */
/***************************************************************************/
/*  push immediate, word operand                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_68(FLAGS & flags)
{
  flags.Wbit = 1;                     // always a word operation
  ULONG Dword1 = getImmediate(flags);
  operandHex(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     69 IMUL   r, r/m, imm                                               */
/*     69 IMUL   r, imm                                                    */
/*     6B IMUL   r, r/m, imm8                                              */
/*     6B IMUL   r, imm8                                                   */
/***************************************************************************/
/*  multiply immediate, 8/16/32 bit operands                               */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_69(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Wbit = 1;                  // word operation
  if (flags.mod == 3 && flags.regf == flags.rm) {
     operandRegister(flags.rm,flags);  // do not repeat the register number
  } else {
     flags.Dbit = DBit_MemToReg;   // register is first operand
     getNormalMemop(flags);
  } // endif
  if (instr == 0x6B) 
     flags.Wbit = 0;    // force a byte operation

  long Dword1 = getSignedImmediate(flags);  // 8, 16 or 32 bit
  operandChar(',');
  operandSignedHex(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     6A PUSH immediate 8 byte operand                                    */
/***************************************************************************/
/*  push immediate, sign extended byte operand                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_6A(FLAGS & flags)
{
  flags.Wbit = 0;                     // always a byte operation
  long Dword1 = getSignedImmediate(flags);
  operandHex(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     70 - 7F Jcc Short displacement                                      */
/*     E0 LOOPNE                                                           */
/*     E1 LOOPE                                                            */
/*     E2 LOOP                                                             */
/*     EB JMP                                                              */
/***************************************************************************/
/*  single byte jump instructions with single byte signed displacements    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_70(FLAGS UNUSED & flags)
{

  signed char ic = getNextByte();
  operandRel(ic);
  parm->rettype = jreltype;
  parm->retoffset = ic;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     80 Group 1 functions                                                */
/*     81 Group 1 functions                                                */
/*     83 Group 1 functions                                                */
/***************************************************************************/
/*  Group 1 functions:                                                     */
/*     0 - ADD                                                             */
/*     1 - OR                                                              */
/*     2 - ADC                                                             */
/*     3 - SBB                                                             */
/*     4 - AND                                                             */
/*     5 - SUB                                                             */
/*     6 - XOR                                                             */
/*     7 - CMP                                                             */
/***************************************************************************/
/*  a single byte instruction with mem-regf-r/m, followed by 1 or 2 byte   */
/*  immediate operand: the regf field further defines the operation        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_80(FLAGS UNUSED & flags)
{
  long Dword2;
//  ULONG Dword1;
  ULONG immmask = 0xFFFFFFFF;
  static const USHORT mnem8083[8] = { ADD, OR, ADC, SBB, AND, SUB, XOR, CMP } ;

  getMod_rm_dw(flags);

  if (!flags.Wbit) immmask = 0xFF;
  else if (!flags.opsize32) immmask = 0xFFFF;

  flags.Dbit = DBit_Mem1op;
  mnemonicStd(flags,mnem8083[flags.regf]);
  getNormalMemop(flags);

  if (instr == 0x83)
     flags.Wbit = 0;  // force immediate data to be a byte

  Dword2 = getSignedImmediate(flags);
  operandChar(',');

  switch (flags.regf) {
  case 0:
  case 2:
  case 3:
  case 5:
  case 7:
     // arithmetic
     operandSignedHex(Dword2);
     break;
  default: 
     // logical
     operandHex(Dword2 & immmask);
  } // endswitch
} 

#if OP82
   /***************************************************************************/
   /*  Opcodes handled by this function:                                      */
   /*     82 MOVB AL, imm8                                                    */
   /***************************************************************************/
   /*  This opcode is nominally reserved                                      */
   /***************************************************************************/
   /*                                                                         */
   /*  PARAMETERS:                                                            */
   /*              flags            Input/Output:  the flags structure        */
   /*                                                                         */
   /*  RETURNS:                                                               */
   /*              none                                                       */
   /*                                                                         */
   /***************************************************************************/
   static void op_82(FLAGS & flags) {
     flags.Wbit = 0;  // force immediate data to be a byte
   
     long Dword2 = getSignedImmediate(flags);
     operandRegister(AL,flags, ",");
     long Dword2 = getSignedImmediate(flags);
     operandSignedHex(Dword2);
   } 
#endif

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     8C MOV                                                              */
/*     8E MOV                                                              */
/***************************************************************************/
/*  load or store a segment register from or to memory or another          */
/*  register                                                               */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_8C(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.regf > 5 )             // register number must be from 0 to 5
     throw IllegalOp();

  flags.Wbit = 1;                  // force to a word operand
  flags.Dbit = DBit_Mem1op;
  #if RETPARMS
    parm->retbits &= 0xFE;           // clear 32-bit address marker
  #endif
   
  if (instr & 2) {
     operandSegRegister(flags.regf, ",");
     getNormalMemop(flags);
  } else {
     getNormalMemop(flags);
     operandChar(',');
     operandSegRegister(flags.regf);
  } // endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     8D LEA                                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_8D(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.mod == 3)            // must be a memory operand
    throw IllegalOp();

  flags.Dbit = DBit_MemToReg;     // register is first operand
  flags.Wbit = 1;                 // force a word operation
  parm->rettype = LEAtype;
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     8F POP                                                              */
/***************************************************************************/
/*  pop memory location (reg field = 0 only - others are illegal           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_8F(FLAGS & flags)
{
  
  getMod_rm_dw(flags);
  if (flags.regf != 0) 
    throw IllegalOp();

  flags.Dbit = DBit_Mem1op;           // no register operand
  flags.Wbit = 1;                     // force a word operation
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     91 - 97 XCKG eAX                                                    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_91(FLAGS & flags)
{
  if (flags.replock == replockLOCK) 
     throw IllegalOp();
  if (flags.opsize32) {
    operandTableItem(EAX, reg32, ",");
  } else {
    operandTableItem(AX, reg16, ",");
  } // endif
  flags.regf = (UCHAR)(instr&0x07);          // get register number
  flags.Wbit = 1;                            // force 16 or 32 bit register
  operandRegister(flags.regf,flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     9A CALL far immediate                                               */
/*     EA JMP  far immediate                                               */
/***************************************************************************/
/*  call instructions with 16:16 or 16:32 operands                         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_9A(FLAGS & flags)
{
  parm->rettype = (instr == 0x9A) ? cfarimmtype : jfarimmtype;
  flags.sizePrefix = sizeDword;
  ULONG Dword2 = getNextOperand(flags);
  ULONG Dword1 = getNextWord();       // get new CS value
  operandHex(Dword1, ":");
  operandHex(Dword2);
  parm->retoffset = Dword2;
  #if RETPARMS
    parm->retseg = (USHORT)Dword1;
  #endif
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     9B WAIT                                                             */
/***************************************************************************/
/*  Wait instruction                                                       */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_9B(FLAGS & flags)
{
   // check to see if the next instruction is an NPX instruction
   UCHAR ic1 = *(parm->iptr);
   if (ic1 < 0xD8 || ic1 > 0xDF ) 
      return;   // just a WAIT

   // we have a WAIT-modified NPX instruction
   flags.waitOp = 1;
   flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     A0 - A3 MOV                                                         */
/***************************************************************************/
/*  single byte MOV orders, with 16-bit displacement                       */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_A0(FLAGS & flags)
{
  setdw(flags);
  // flip the state of the dbit 
  flags.Dbit = (flags.Dbit == DBit_MemToReg) ? DBit_RegToMem : DBit_MemToReg;
  opdisp = getDisplacement(flags);
  flags.regf = 0;
  flags.mod = 0;
  flags.rm = 6;
  basereg = 0;
  indexreg = 0;
  scalefactor = 0;
  flags.disppres = 1;
  operandMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     A4 MOVSB                                                            */
/*     A5 MOVSW/MOVSD                                                      */
/*     A6 CMPSB                                                            */
/*     A7 CMPSW/CMPSD                                                      */
/*     AC LODSB                                                            */
/*     AD LODSW/LODSD                                                      */
/***************************************************************************/
/*  string orders                                                          */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_A4(FLAGS & flags)
{
  #if RETPARMS
    if (ovseg == 0) {
      parm->retreg = defseg;
    } else {
      parm->retreg = ovseg;
    }
    if (flags.addr32)
      parm->retbase = ESI+8;              // ESI
    else
      parm->retbase = SI;                 // SI
  #endif
  setdw(flags);
  if (flags.Wbit == 0)
    parm->rettype = strbtype;
  else
    parm->rettype = strwtype;

} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     AA STOSB                                                            */
/*     AB STOSW/STOSD                                                      */
/*     AE SCASB                                                            */
/*     AF SCASW/SCASD                                                      */
/***************************************************************************/
/*  store and scan string orders                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_AA(FLAGS & flags)
{
  #if RETPARMS
    if (ovseg == 0) {
      parm->retreg = ES;                  // ES
    } else {
      parm->retreg = ovseg;
    }
    if (flags.addr32)
      parm->retbase = EDI+8;                // EDI
    else
      parm->retbase = DI;                 // DI
  #endif

  setdw(flags);
  if (flags.Wbit == 0)
    parm->rettype = strbtype;
  else
    parm->rettype = strwtype;

} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     B0 - B7 MOV                                                         */
/***************************************************************************/
/*  MOV immediate to 8 bit register                                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_B0(FLAGS & flags)
{
  flags.Wbit = 0;          // 8 bit operation marker
  flags.regf = instr & 0x07;              // get register number
  operandRegister(flags.regf,flags, ",");
  parm->rettype = regimmedtype;
  UCHAR ic = getNextByte();           // get next byte of instruction
  operandHex(ic);
  #if RETPARMS
    parm->retreg = flags.regf + 16;
    parm->retimmed = ic;
  #endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     B8 - BF MOV                                                         */
/***************************************************************************/
/*  MOV immediate to 16/32 bit register                                    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_B8(FLAGS & flags)
{
  flags.Wbit = 1;                             // 8/16 bit operation marker
  flags.regf = instr & 0x07;                  // get register number
  operandRegister(flags.regf,flags, ",");
  parm->rettype = regimmedtype;
  ULONG Dword1 = getNextOperand(flags);
  operandHex(Dword1);
  #if RETPARMS
    parm->retreg = flags.regf;
    if (flags.opsize32)
        parm->retreg += 8;
    parm->retimmed = Dword1;
  #endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C0, C1 - Group 2                                                    */
/***************************************************************************/
/*  Group 2 functions:                                                     */
/*     0 - ROL                                                             */
/*     1 - ROR                                                             */
/*     2 - RCL                                                             */
/*     3 - RCR                                                             */
/*     4 - SHL                                                             */
/*     5 - SHR                                                             */
/*     6 - Illegal                                                         */
/*     7 - SAR                                                             */
/***************************************************************************/
/*  shift (rotate) operations                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C0(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.regf == 6) 
    throw IllegalOp();

  flags.Dbit = DBit_Mem1op; 
  ULONG Dword2 = flags.regf;
  mnemonicStd(flags,shiftmnem[Dword2]);
  getNormalMemop(flags);
  flags.Wbit = 0;                   // force to 8-bit operand
  ULONG Dword1 = getImmediate(flags);
  operandChar(',');
  operandDecimal(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C2 RETN n                                                           */
/*     CA RETF n                                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C2(FLAGS UNUSED & flags)
{
  USHORT Dword1 = getNextWord();
  operandDecimal(Dword1);
  parm->retoffset = Dword1;
  if (instr == 0xC2)                   // RET
    parm->rettype = retneartype;
  else                                 // assume instr = 0xCA - RETF/RET
    parm->rettype = retfartype;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C3 RETN                                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C3(FLAGS UNUSED & flags)
{
  parm->rettype = retneartype;
  parm->retoffset = 0;                 // it is RET 0
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C4 LES                                                              */
/*     C5 LDS                                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C4(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.mod == 3)            // must be a memory operand
    throw IllegalOp();

  flags.Dbit = DBit_MemToReg;     // register is first operand
  flags.Wbit = 1;                 // force a word operation
  parm->rettype = memwwtype;
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C6, C7 MOV                                                          */
/***************************************************************************/
/*  store immediate operations regf = 0 only - others are illegal)         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C6(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.regf != 0) 
    throw IllegalOp();

  flags.Dbit = DBit_Mem1op;         // immediate operand
  getNormalMemop(flags);
  ULONG Dword1 = getImmediate(flags);
  operandChar(',');
  operandHex(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     C8 ENTER                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_C8(FLAGS & flags)
{
  if (flags.opsizeover) 
     throw IllegalOp();

  flags.Wbit = 1;                     // a word immediate operand
  flags.opsize32 = 0;                 // this is always a 16-bit operation
  ULONG Dword1 = getImmediate(flags);
  operandDecimal(Dword1, ",");
  flags.Wbit = 0;                     // then a byte immediate operand
  Dword1 = getImmediate(flags);
  operandDecimal(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     CB RETF                                                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_CB(FLAGS UNUSED & flags)
{
  parm->rettype = retfartype;
  parm->retoffset = 0;                 // it is RETF 0
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     CC INT 3                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_CC(FLAGS UNUSED & flags)
{
  UCHAR ic = 3;
  operandHex(ic);
  parm->retoffset = ic;
  parm->rettype = intntype;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     CD INT                                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_CD(FLAGS UNUSED & flags)
{
  UCHAR ic = getNextByte();                       // get next byte of instruction
  operandHex(ic);
  parm->retoffset = ic;
  parm->rettype = intntype;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     CE INTO                                                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_CE(FLAGS UNUSED & flags)
{
  UCHAR ic = 4;
  parm->retoffset = ic;
  parm->rettype = intntype;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     CF IRET                                                             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_CF(FLAGS UNUSED & flags)
{
  parm->rettype = intrettype;
  parm->retoffset = 0;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     D0 - D3 - Group 2                                                   */
/***************************************************************************/
/*  Group 2 functions:                                                     */
/*     0 - ROL                                                             */
/*     1 - ROR                                                             */
/*     2 - RCL                                                             */
/*     3 - RCR                                                             */
/*     4 - SHL                                                             */
/*     5 - SHR                                                             */
/*     6 - Illegal                                                         */
/*     7 - SAR                                                             */
/***************************************************************************/
/*  shift (rotate) operations                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_D0(FLAGS & flags)
{
  getMod_rm_dw(flags);
  if (flags.regf == 6) 
    throw IllegalOp();

  int shift1 = (flags.Dbit == DBit_RegToMem);
  flags.Dbit = DBit_Mem1op;                 
  ULONG Dword2 = flags.regf;
  mnemonicStd(flags,shiftmnem[Dword2]);
  getNormalMemop(flags);
  if (shift1) {
     operandString(",1");
  } else {
     operandChar(',');
     operandTableItem(CL, reg8);
  } // endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     D4 AAM                                                              */
/*     D5 AAD                                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_D4(FLAGS UNUSED & flags)
{
  UCHAR ic = getNextByte();
  if (ic != 0x0A) 
    throw IllegalOp();
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     D7 XLATB                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_D7(FLAGS & flags)
{
  #if RETPARMS
    if (ovseg == 0) {
      parm->retreg = defseg;
    } else {
      parm->retreg = ovseg;
    }
    if (flags.addr32)
      parm->retbase = EBX+8;              // EBX
    else
      parm->retbase = BX;                 // BX
  #endif
  parm->rettype = xlattype;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     D8 - DF NPX instructions                                            */
/***************************************************************************/
/*  Since, strictly speaking, it is not illegal to do an NPX instruction   */
/*  without a preceeding WAIT, the code will not check for that condition  */
/*  with the intent of posting an illegal operation.  However, since some  */
/*  mnemonics have different versions depending on the presence of the     */
/*  WAIT instruction, it will be taken into account.                       */
/*                                                                         */
/*  The following opcodes have different versions with/without a WAIT:     */
/*     D9 /6     FSTENV/FNSTENV                                            */
/*     D9 /7     FSTCW/FNSTCW                                              */
/*     DB E0     FENI/FNENI                                                */
/*     DB E1     FDISI/FNDISI                                              */
/*     DB E2     FCLEX/FNCLEX                                              */
/*     DD /6     FSAVE/FNSAVE                                              */
/*     DD /7     FSTSW/FNSTSW  to memory                                   */
/*     DF E0     FSTSW/FNSTSW  to AX                                       */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_D8(FLAGS & flags)
{
  /*************************************************************************/
  /* the following table is the mnemonic table for 387 orders D9, mod = 3, */
  /* regf = 4-7 ( ESCInstr = 12 -> 15)                                     */
  /*************************************************************************/
  static const USHORT mnemD94[32] =  {
    FCHS,  FABS,    odd,    odd,     FTST,    FXAM,   odd,     odd,
    FLD1,  FLDL2T,  FLDL2E, FLDPI,   FLDLG2,  FLDLN2, FLDZ,    odd,
    F2XM1, FYL2X,   FPTAN,  FPATAN,  FXTRACT, FPREM1, FDECSTP, FINCSTP,
    FPREM, FYL2XP1, FSQRT,  FSINCOS, FRNDINT, FSCALE, FSIN,    FCOS
  } ;
  
  /**************************************************************************/
  /* the following table is the mnemonic table for memory access 387        */
  /* instructions not preceeded by a "WAIT" opcode                          */
  /**************************************************************************/
  static const USHORT m387mem[64] =  {
    FADD , FMUL   , FCOM , FCOMP , FSUB   , FSUBR  , FDIV   , FDIVR , // 00  0 (D8)  
    FLD  , ILLEGAL, FST  , FSTP  , FLDENV , FLDCW  , FNSTENV, FNSTCW, // 08  8 (D9)  
    FIADD, FIMUL  , FICOM, FICOMP, FISUB  , FISUBR , FIDIV  , FIDIVR, // 10 16 (DA)  
    FILD , ILLEGAL, FIST , FISTP , ILLEGAL, FLD    , ILLEGAL, FSTP  , // 18 24 (DB)  
    FADD , FMUL   , FCOM , FCOMP , FSUB   , FSUBR  , FDIV   , FDIVR , // 20 32 (DC)  
    FLD  , ILLEGAL, FST  , FSTP  , FRSTOR , ILLEGAL, FNSAVE , FNSTSW, // 28 40 (DD)
    FIADD, FIMUL  , FICOM, FICOMP, FISUB  , FISUBR , FIDIV  , FIDIVR, // 30 48 (DE)  
    FILD , ILLEGAL, FIST , FISTP , FBLD   , FILD   , FBSTP  , FISTP   // 38 56 (DF)
  } ;
  
  /**************************************************************************/
  /* the following table is the mnemonic table for memory access 387        */
  /* instructions preceeded by a "WAIT" opcode                              */
  /**************************************************************************/
  static const USHORT m387memW[64] =  {
    FADD , FMUL   , FCOM , FCOMP , FSUB   , FSUBR  , FDIV   , FDIVR , // 00  0 (D8)
    FLD  , ILLEGAL, FST  , FSTP  , FLDENV , FLDCW  , FSTENV , FSTCW , // 08  8 (D9)
    FIADD, FIMUL  , FICOM, FICOMP, FISUB  , FISUBR , FIDIV  , FIDIVR, // 10 16 (DA)
    FILD , ILLEGAL, FIST , FISTP , ILLEGAL, FLD    , ILLEGAL, FSTP  , // 18 24 (DB)
    FADD , FMUL   , FCOM , FCOMP , FSUB   , FSUBR  , FDIV   , FDIVR , // 20 32 (DC)
    FLD  , ILLEGAL, FST  , FSTP  , FRSTOR , ILLEGAL, FSAVE  , FSTSW , // 28 40 (DD)
    FIADD, FIMUL  , FICOM, FICOMP, FISUB  , FISUBR , FIDIV  , FIDIVR, // 30 48 (DE)
    FILD , ILLEGAL, FIST , FISTP , FBLD   , FILD   , FBSTP  , FISTP   // 38 56 (DF)
  } ;
  
  /**************************************************************************/
  /* the following table is the mnemonic table for 387 instructions with a  */
  /* reg field                                                              */
  /**************************************************************************/
  static const USHORT m387reg[64] =  {
    FADD,    FMUL,    FCOM,     FCOMP,   FSUB,    FSUBR,   FDIV,    FDIVR,   // 00  0 (D8)
    FLD,     FXCH,    odd,      FSTP,    odd,     odd,     odd,     odd,     // 08  8 (D9)
    FCMOVB,  FCMOVE,  FCMOVBE,  FCMOVU,  ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, // 10 16 (DA)
    FCMOVNB, FCMOVNE, FCMOVNBE, FCMOVNU, ILLEGAL, FUCOMI,  FCOMI,   ILLEGAL, // 18 24 (DB)
    FADD,    FMUL,    FCOM,     FCOMP,   FSUBR,   FSUB,    FDIVR,   FDIV,    // 20 32 (DC)
    FFREE,   FXCH,    FST,      FSTP,    FUCOM,   FUCOMP,  ILLEGAL, ILLEGAL, // 28 40 (DD)
    FADDP,   FMULP,   FCOMP,    odd,     FSUBRP,  FSUBP,   FDIVRP,  FDIVP,   // 30 48 (DE)
    FFREEP,  FXCH,    FSTP,     FSTP,    odd,     FUCOMIP, FCOMIP,  ILLEGAL  // 38 56 (DF)
  } ;

  /***************************************************************/
  /* the following table is the mnemonic table for 387 orders DB */
  /* regf = 4 ( ESCInstr = 28)                                   */
  /***************************************************************/
  static const USHORT mnemDB4[16] =  {
    FNENI, FNDISI, FNCLEX, FNINIT, FSETPM, ILLEGAL, ILLEGAL, ILLEGAL,
    FENI,  FDISI,  FCLEX,  FINIT,  FSETPM, ILLEGAL, ILLEGAL, ILLEGAL
  };

  UCHAR ESCinstr;

  if (flags.opsizeover) 
     throw IllegalOp();

  getMod_rm_dw(flags);
  flags.Wbit = 1;                     // force a word operation
  if (flags.mod == 3) {
    parm->rettype = escapetype;
    #if RETPARMS
      parm->retescape = (USHORT)(((((instr & 0x07) << 3) + flags.regf) << 3)+flags.rm);
    #endif
  } 
  else {
    parm->rettype = escmemtype;
    #if RETPARMS
      parm->retescape = (USHORT)(((instr&0x07) << 3)+flags.regf);
    #endif
  } 
  if ((parm->flagbits & N387mask) == 0) {// do not perform 287/387 decode
    mnemonicStd(flags,ESC);    // ESCAPE/ESC
    flags.Dbit = DBit_Mem1op;      // a special case
    flags = getMemop(flags);

    /************************************************************************/
    /* register field is printed as a number for the first operand          */
    /************************************************************************/
    if (flags.mod != 3) {
      operandHex(((instr&0x07) << 3)+flags.regf);
      operandMemop(flags);
    } else
      operandHex(((((instr&0x07) << 3)+flags.regf) << 3)+flags.rm);
  } else {  
    // we are to perform 287/387 decode

    ESCinstr = (UCHAR)(((instr&7) << 3)+flags.regf);
    if (flags.mod != 3) {                    // it is an operation that accesses memory
      int mnemNum = flags.waitOp ? m387memW[ESCinstr] : m387mem[ESCinstr];
      if (mnemNum == ILLEGAL) { // trap the illegal cases
        throw IllegalOp();
      }
      mnemonicStd(flags,mnemNum);
      /*********************************************************************/
      /* operand size table                                                */
      /*********************************************************************/
      static const char sizecode[64] = { 
      //     0          1          2          3          4          5          6          7          
         sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword,  // D8 
         sizeDword, sizeNone,  sizeDword, sizeDword, sizeNone,  sizeWord,  sizeNone,  sizeWord,   // D9
         sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword, sizeDword,  // DA 
         sizeDword, sizeNone,  sizeDword, sizeDword, sizeNone,  sizeTbyte, sizeNone,  sizeTbyte,  // DB 
         sizeQword, sizeQword, sizeQword, sizeQword, sizeQword, sizeQword, sizeQword, sizeQword,  // DC 
         sizeQword, sizeNone,  sizeQword, sizeQword, sizeNone,  sizeNone,  sizeNone,  sizeWord,   // DD
         sizeWord,  sizeWord,  sizeWord,  sizeWord,  sizeWord,  sizeWord,  sizeWord,  sizeWord,   // DE 
         sizeWord,  sizeNone,  sizeWord,  sizeWord,  sizeTbyte, sizeQword, sizeTbyte, sizeQword}; // DF 

      flags.sizePrefix = sizecode[ESCinstr];  // set operand size
      flags.Dbit = DBit_Mem1op;               // only 1 memory operand
      getNormalMemop(flags);
    } else {      
      // it is a non-memory operation - switch on Escape opcode

//      int ct = 0;
      switch (ESCinstr) {
      case 0 :                       // FADD     D8 C0/C7
      case 1 :                       // FMUL     D8 C8/CF
      case 4 :                       // FSUB     D8 E0/E7
      case 5 :                       // FSUBR    D8 E8/EF
      case 6 :                       // FDIV     D8 F0/F7
      case 7 :                       // FDIVR    D8 F8/FF
      case 16 :                      // FCMOVEB  DA C0/C7 (Pentium Pro)
      case 17 :                      // FCMOVE   DA C8/CF (Pentium Pro)
      case 18 :                      // FCMOVBE  DA D0/D7 (Pentium Pro)
      case 19 :                      // FCMOVU   DA D8/DF (Pentium Pro)
      case 24 :                      // FCMOVNB  DB C0/C7 (Pentium Pro)
      case 25 :                      // FCMOVNE  DB C8/CF (Pentium Pro)
      case 26 :                      // FCMOVNBE DB D0/D7 (Pentium Pro)
      case 27 :                      // FCMOVNU  DB D8/DF (Pentium Pro)
      case 29 :                      // FUCOMI   DB E8/EF (Pentium Pro)
      case 30 :                      // FCOMI    DB F0/F7 (Pentium Pro)
      case 61 :                      // FUCOMIP  DF E8/EF (Pentium Pro)
      case 62 :                      // FCOMIP   DF F0/F7 (Pentium Pro)
        /******************************************************************/
        /* operand is ST, ST(rm)                                          */
        /******************************************************************/
        mnemonicStd(flags,m387reg[ESCinstr]);
        operandString("ST,ST(");
        operandDecimal(flags.rm, ")");
        break;

      case 10 :                      // FNOP D9 D0
        if (flags.rm != 0)
          throw IllegalOp();
        mnemonicStd(flags,FNOP);
        break;

      case 12 :
        switch (flags.rm) {
        case 0:                      // FCHS D9 E0
        case 1:                      // FABS D9 E1
        case 4:                      // FTST D9 E4
        case 5:                      // FXAM D9 E5
           mnemonicStd(flags,mnemD94[flags.rm]);
           break;
        default: 
           throw IllegalOp();
        } // endswitch
        break;

      case 13 :
        switch (flags.rm) {
        case 0:                      // FLD1   D9 E8
        case 1:                      // FLDL2T D9 E9
        case 2:                      // FLDL2E D9 EA
        case 3:                      // FLDPI  D9 EB
        case 4:                      // FLDLG2 D9 EC
        case 5:                      // FLDLN2 D9 ED
        case 6:                      // FLDZ   D9 EE
           mnemonicStd(flags,mnemD94[8 + flags.rm]);
           break;
        default: 
           throw IllegalOp();
        } // endswitch
        break;

      case 14 :                      // F2XM1   D9 F0
                                     // FYL2X   D9 F1
                                     // FPTAN   D9 F2
                                     // FPATAN  D9 F3
                                     // FXTRACT D9 F4
                                     // FPREM1  D9 F5
                                     // FDECSTP D9 F6
                                     // FINCSTP D9 F7
        mnemonicStd(flags,mnemD94[16 + flags.rm]); 
        break;

      case 15 :                      // FPREM    D9 F8
                                     // FYL2XP1  D9 F9
                                     // FSQRT    D9 FA
                                     // FSINCOS  D9 FB
                                     // FRNDINT  D9 FC
                                     // FSCALE   D9 FD
                                     // FSIN     D9 FE
                                     // FCOS     D9 FF
        mnemonicStd(flags,mnemD94[24 + flags.rm]); 
        break;

      case 21 :                      // FUCOMPP DA E9
        if (flags.rm != 1) 
          throw IllegalOp();
        mnemonicStd(flags,FUCOMPP);
        break;

      case 28 :                     
        switch (flags.rm) {
        case 2:                      // FCLEX / FNCLEX  DB E2
        case 3:                      // FINIT / FNINIT  DB E3
           mnemonicStd(flags,mnemDB4[flags.rm + (flags.waitOp ? 8 : 0)]); 
           break;
        case 0:                      // FENI / FNENI    DB E0 - 8087 instruction only
        case 1:                      // FDISI / FNDISI  DB E1 - 8087 instruction only
        case 4:                      // FSETPM          DB E4 - 80287 instruction only
        default:
          throw IllegalOp();
        } // endswitch
        break;

      case 32 :                      // FADD   DC C0/C7
      case 33 :                      // FMUL   DC C8/CF
      case 36 :                      // FSUBR  DC E0/E7
      case 37 :                      // FSUB   DC E8/EF
      case 38 :                      // FDIVR  DC F0/F7
      case 39 :                      // FDIV   DC F8/FF
      case 48 :                      // FADDP  DE C0/C7
      case 49 :                      // FMULP  DE C8/CF
      case 52 :                      // FSUBRP DE E0/E7
      case 53 :                      // FSUBP  DE E8/EF
      case 54 :                      // FDIVRP DE F0/F7
      case 55 :                      // FDIVP  DE F8/FF
        /******************************************************************/
        /* operand is ST(rm), ST                                          */
        /******************************************************************/
        mnemonicStd(flags,m387reg[ESCinstr]);
        operandString("ST(");
        operandDecimal(flags.rm, "),ST"); // print register number
        break;
                                                                                                                                                                                                                                                            
      case 2 :                       // FCOM   D8 D0/D7
      case 3 :                       // FCOMP  D8 D8/DF
      case 8 :                       // FLD    D9 C0/C7
      case 9 :                       // FXCH   D9 C8/CF
      case 40 :                      // FFREE  DD C0/C7
      case 42 :                      // FST    DD D0/D7
      case 43 :                      // FSTP   DD D8/DF
      case 44 :                      // FUCOM  D9 E0/E7
      case 45 :                      // FUCOMP DD E8/EF
        /******************************************************************/
        /* operand is ST(rm)                                              */
        /******************************************************************/
        mnemonicStd(flags,m387reg[ESCinstr]);
        operandString("ST(");
        operandDecimal(flags.rm, ")"); // print register number
        break;

      case 51 :                      // FCOMPP DE D9
        if (flags.rm != 1) 
          throw IllegalOp();
        mnemonicStd(flags,FCOMPP);
        break;

      case 60 :                      // FSTSW AX  DF E0
        if (flags.rm != 0) 
          throw IllegalOp();

        // check the wait flag
        mnemonicStd(flags,flags.waitOp ? FSTSW : FNSTSW);
        operandTableItem(AX, reg16);
        break;

      case 11 :                     
      case 20 :                      
      case 22 :                      
      case 23 :                      
      case 31 :                     
      case 34 :
      case 35 :                     
      case 41 :
      case 46 :
      case 47 :                     
      case 50 :                     
      case 56 :
      case 57 :
      case 58 :
      case 59 :                     
      case 63 :                     
        throw IllegalOp();
      } // endswitch
    } // endif
  } // endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     E0 LOOPNE                                                           */
/*     E1 LOOPE                                                            */
/*     E2 LOOP                                                             */
/***************************************************************************/
/*  single byte jump instructions with single byte signed displacements    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_E0(FLAGS & flags)
{
   flags.addroverUsed = 1;  // the address override, if present, was used.
                            // It controls if CX or ECX is used - see Intel Docs.
   signed char ic = getNextByte();
   operandRel(ic);
   parm->rettype = jreltype;
   parm->retoffset = ic;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     E3 JCXZ /JECXZ                                                      */
/***************************************************************************/
/*  single byte jump instructions with single byte signed displacements    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_E3(FLAGS & flags)
{
   // The following looks decidedly odd, but the Intel processors actually
   // test CX/ECX based on the ADDRESS size, not the OPERAND size!
   // This behaviour is NOT in the Intel docs...
   if (flags.addr32) {
      mnemonicStd(flags,JECXZ);
   } else {
      mnemonicStd(flags,JCXZ);
   } /* endif */

   // otherwise this behaves like a LOOP instruction
   op_E0(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     E4, E5 IN                                                           */
/*     E6, E7 OUT                                                          */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_E4(FLAGS & flags)
{
  UCHAR ic = getNextByte();
  setdw(flags);
  if (flags.Dbit == DBit_RegToMem) {
    operandRegister(0,flags, ",");
    operandHex(ic);
  } else {
    operandHex(ic, ",");
    operandRegister(0,flags);
  } 
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     E8 CALL near relative                                               */
/*     E9 JMP near relative                                                */
/***************************************************************************/
/*  single byte instructions with 16/32 bit relative displacements         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_E8(FLAGS & flags)
{
  parm->rettype = (instr == 0xE8) ? creltype : jreltype;
  ULONG Dword1 = getNextOperand(flags);

  // sign extend the lower 16 bits
  long Dword2 = (flags.opsize32) ? Dword1 : (long)((short)Dword1);

  operandRel(Dword2);
  parm->retoffset = Dword1;
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     EC, ED IN                                                           */
/*     EE, EF OUT                                                          */
/***************************************************************************/
/*  IN DX and OUT DX instructions                                          */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_EC(FLAGS & flags)
{
  setdw(flags);

  if (flags.Dbit == DBit_RegToMem) {
    operandRegister(AX, flags, ",");
    operandTableItem(DX, reg16);
  } else {
    operandTableItem(DX, reg16, ",");
    operandRegister(AX, flags);
  }

} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     F0 LOCK Prefix                                                      */
/***************************************************************************/
/*  Mark the wall and look for the next instruction                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_F0(FLAGS & flags)
{
  if (flags.replock != replockNone)
     // two REP prefixes - this is an error
     throw IllegalOp();

  flags.replock = replockLOCK;
  flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     F2 REPNZ                                                            */
/***************************************************************************/
/*  single byte REP, REPZ, REPNZ prefixes                                  */
/*  Mark the wall and look for the next instruction                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_F2(FLAGS & flags)
{
  if (flags.replock != replockNone)
     // two REP prefixes - this is an error
     throw IllegalOp();


  flags.replock = replockREPNZ;
  flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     F3 REPZ, REP                                                        */
/***************************************************************************/
/*  single byte REP, REPZ, REPNZ prefixes                                  */
/*  Mark the wall and look for the next instruction                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_F3(FLAGS & flags)
{
  if (flags.replock != replockNone)
     // two REP prefixes - this is an error
     throw IllegalOp();

  flags.replock = replockREPZ;
  flags.prefix = 1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     F6, F7 Group 3                                                      */
/***************************************************************************/
/*  Group 3 functions:                                                     */
/*     0 - TEST                                                            */
/*     1 - Illegal                                                         */
/*     2 - NOT                                                             */
/*     3 - NEG                                                             */
/*     4 - MUL                                                             */
/*     5 - IMUL                                                            */
/*     6 - DIV                                                             */
/*     7 - IDIV                                                            */
/***************************************************************************/
/*  further memory operations: all have mod/rm byte and possible           */
/*  displacement or immediate operand                                      */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_F6(FLAGS & flags)
{
  /*************************************************************************/
  /*  mnemonic numbers for F6, F7 orders                                   */
  /*************************************************************************/
  static const USHORT mnemF6F7[8] = { 
     TEST, ILLEGAL, NOT, NEG, MUL, IMUL, DIV, IDIV } ;
  
  ULONG Dword1;

  getMod_rm_dw(flags);
  mnemonicStd(flags,mnemF6F7[flags.regf]);
  flags.Dbit = DBit_Mem1op;

  switch (flags.regf) {
  case 0:
     // the TEST op needs immediate data
     getNormalMemop(flags);
     Dword1 = getImmediate(flags);
     operandChar(',');
     operandHex(Dword1);
     break;
  case 1:
     throw IllegalOp();
  default:
     getNormalMemop(flags);
     break;
  } // endswitch
}


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     FE - Group 4                                                        */
/*     FF - Group 5                                                        */
/***************************************************************************/
/*  Group 4 functions:                                                     */
/*     0 - INC                                                             */
/*     1 - DEC                                                             */
/*     2 - 7 Illegal                                                       */
/***************************************************************************/
/*  Group 5 functions:                                                     */
/*     0 - INC                                                             */
/*     1 - DEC                                                             */
/*     2 - CALL near indirect                                              */
/*     3 - CALL far indirect                                               */
/*     4 - JMP  near indirect                                              */
/*     5 - JMP  far indirect                                               */
/*     6 - PUSH                                                            */
/*     7 - Illegal                                                         */
/***************************************************************************/
/*  miscellaneous operations: all have mod/rm byte and possible            */
/*  displacement bytes                                                     */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_FE(FLAGS & flags)
{
  static const USHORT mnemFF[7] =  { INC, DEC, CALL, CALL, JMP, JMP, PUSH } ;

  getMod_rm_dw(flags);
  if (flags.regf > 1 && instr == 0xFE) 
     throw IllegalOp();

  flags.Dbit = DBit_Mem1op;     // single operand
  mnemonicStd(flags,mnemFF[flags.regf]);

  switch (flags.regf) {
  case 0:  // INC
  case 1:  // DEC
     // May be a byte or word operation as set by W-bit
     break;
  case 2: // CALL near indirect
     flags.Wbit = 1;            // force a word operation
     parm->rettype = flags.mod ? cnearregtype : cnearmemtype;
     break;
  case 3: // CALL far indirect
     if (flags.mod == 3) 
        throw IllegalOp();
     flags.Wbit = 1;            // force a word operation
     flags.sizePrefix = flags.opsize32 ? sizeFword : sizeDword;
     parm->rettype = cfartype;
     break;
  case 4: // JMP near indirect
     flags.Wbit = 1;            // force a word operation
     parm->rettype = flags.mod ? jnearregtype : jnearmemtype;
     break;
  case 5: // JMP far indirect
     if (flags.mod == 3) 
        throw IllegalOp();
     flags.Wbit = 1;            // force a word operation
     flags.sizePrefix = flags.opsize32 ? sizeFword : sizeDword;
     parm->rettype = jfartype;
     break;
  case 6: // PUSH
     flags.Wbit = 1;            // force a word operation
     break;
  default: 
     throw IllegalOp();
  } // endswitch

  getNormalMemop(flags);
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF00 Group 6 opcode                                                 */
/***************************************************************************/
/*  Group 6 opcodes:                                                       */
/*     0 SLDT                                                              */
/*     1 STR                                                               */
/*     2 LLDT                                                              */
/*     3 LTR                                                               */
/*     4 VERR                                                              */
/*     5 VERW                                                              */
/*     6 Illegal                                                           */
/*     7 Illegal                                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F00(FLAGS & flags)
{
  /*************************************************************************/ 
  /* mnemonic numbers for 0F 00 orders                                     */
  /*************************************************************************/
  static const USHORT mnem0F00[6] =  { SLDT, STR, LLDT, LTR, VERR, VERW } ;

  if (flags.opsizeover) 
     throw IllegalOp();
  
  getMod_rm_dw(flags);
  if (flags.regf > 5) 
    throw IllegalOp();

  flags.Dbit = DBit_Mem1op;           // no register operand
  flags.Wbit = 1;                     // word operation
  flags.opsize32 = 0;                 // 16 bit opcodes
  #if RETPARMS
    parm->retbits &= 0xFE;              // clear 32 bit address marker
  #endif
  mnemonicStd(flags,mnem0F00[flags.regf]);
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF01 Group 7 opcode                                                 */
/***************************************************************************/
/*  Group 6 opcodes:                                                       */
/*     0 SGDT                                                              */
/*     1 SIDT                                                              */
/*     2 LGDT                                                              */
/*     3 LIDT                                                              */
/*     4 SMSW                                                              */
/*     5 Illegal                                                           */
/*     6 LMSW                                                              */
/*     7 INVLPG                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F01(FLAGS & flags)
{
  /*************************************************************************/
  /* mnemonic numbers for 0F 01 orders                                     */
  /*************************************************************************/
  static const USHORT mnem0F01[8] =  {
      SGDT, SIDT, LGDT, LIDT, SMSW, ILLEGAL, LMSW, INVLPG
  } ;

  getMod_rm_dw(flags);
  switch (flags.regf) {
  case 0:         // SGDT
  case 1:         // SIDT
  case 2:         // LGDT
  case 3:         // LIDT
     parm->rettype = LGDTtype;
     flags.sizePrefix = sizeFword;  // These are FWORD (48 bit) operations
     if (flags.mod == 3)            // memory operation only
        throw IllegalOp();
     break;
  case 7:         // INVLPG         
     flags.sizePrefix = sizeDword;
     if (flags.mod == 3)            // memory operation only
        throw IllegalOp();
     break;
  default:
     if (flags.mod == 3)            // memory operation only
        throw IllegalOp();
     break;
  case 5:          
     throw IllegalOp();
  case 4:          // SMSW
  case 6:          // LMSW
     // this is always a 16-bit operation
     flags.opsize32 = 0;
     #if RETPARMS
       parm->retbits &= 0xFE;       // clear 32 bit address marker
     #endif
     break;
  } // endswitch

  flags.Dbit = DBit_Mem1op;                  // no register operand
  flags.Wbit = 1;                            // word operation
  mnemonicStd(flags,mnem0F01[flags.regf]);
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF02 LAR                                                            */
/*     OF03 LSL                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F02(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Dbit = DBit_MemToReg;         // register is first operand
  flags.Wbit = 1;                     // word operation
  getNormalMemop(flags);              // and the source
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF20 MOV  r32,CRx                                                   */
/*     OF22 MOV  CRx,r32                                                   */
/*     OF21 MOV r32,DRn                                                    */
/*     OF23 MOV DRn,r32                                                    */
/*     OF24 MOV r32,TRn                                                    */
/*     OF26 MOV TRn,r32                                                    */
/***************************************************************************/
/*  Special Register to Register Move                                      */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F20(FLAGS & flags)
{
  // CRn register names
  static const TABLE controlreg[] =  {
      { 3, "CR0" } , { 3, "CR1" } , { 3, "CR2" } , { 3, "CR3" } ,
      { 3, "CR4" } , { 3, "CR5" } , { 3, "CR6" } , { 3, "CR7" },
      { 0, NULL }
  } ;
  // DRn register names
  static const TABLE debugreg[] =  {
      { 3, "DR0" } , { 3, "DR1" } , { 3, "DR2" } , { 3, "DR3" } ,
      { 3, "DR4" } , { 3, "DR5" } , { 3, "DR6" } , { 3, "DR7" } ,
      { 0, NULL }
  } ;

  #if OP0F24
     // TRn register names
     static const TABLE testreg[] =  {
         { 3, "TR0" } , { 3, "TR1" } , { 3, "TR2" } , { 3, "TR3" } ,
         { 3, "TR4" } , { 3, "TR5" } , { 3, "TR6" } , { 3, "TR7" } ,
         { 0, NULL }
     } ;
  #endif

  if (flags.opsizeover) 
     throw IllegalOp();

  getMod_rm_dw(flags);
  if (flags.mod != 3) 
     throw IllegalOp();

  flags.Wbit = 1;         // always a wide operation
  switch (instr) {
  case 0x20:
     // control register is second operand
     if (flags.regf != 1 && flags.regf < 5) {  
       // Control registers 0,2,3,4 permitted
       operandTableItem(flags.rm, reg32, ",");
       operandTableItem(flags.regf, controlreg);
       return;
     }
     break;
  case 0x21:
     // debug register is second operand
     operandTableItem(flags.rm, reg32, ",");
     operandTableItem(flags.regf, debugreg);
     return;
  case 0x22:
     // control register is first operand
     if (flags.regf != 1 && flags.regf < 5) {  
       // Control registers 0,2,3,4 permitted
       operandTableItem(flags.regf, controlreg, ",");
       operandTableItem(flags.rm, reg32);
       return;
     }
     break;
  case 0x23:
     // debug register is first operand
     operandTableItem(flags.regf, debugreg, ",");
     operandTableItem(flags.rm, reg32);
     return;

  #if OP0F24
     case 0x24:
        // test register is second operand
        if (flags.regf < 6) { 
          // Test regs 6 & 7 don't exist
          operandTableItem(flags.rm, reg32, ",");
          operandTableItem(flags.regf, testreg);
          return;
        }
        break;
     case 0x26:
        // test register is first operand
        if (flags.regf <= 6) { 
          // Test regs 6 & 7 don't exist
          operandTableItem(flags.regf, testreg, ", ");
          operandTableItem(flags.rm, reg32);
          return;
        }
        break;
  #endif

  } // endswitch 
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF40 - 0F4F CMOVcc - PentiumPro Support                             */
/***************************************************************************/
/*  Pentium Pro Conditional move instructions                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F40(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Dbit = DBit_MemToReg;  // always shows this way
  flags.Wbit = 1;              // always a word op
  getNormalMemop(flags);
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     0F60 PUNPCKLBW                                                      */
/*     0F61 PUNPCKLWD                                                      */
/*     0F62 PUNPCKLDQ                                                      */
/***************************************************************************/
/*  MMX support                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F60(FLAGS & flags)
{  
  getMod_rm_dw(flags);
  flags.Dbit = DBit_MemToReg;       // always a mem to Reg operation
  flags.sizePrefix = sizeDword;     // memory operands are Dwords
  flags.MMXop = 1;
  getNormalMemop(flags);
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     0F63 PACKSSWB                                                       */
/*     0F64 PCMPGTB                                                        */
/*     0F65 PCMPGTW                                                        */
/*     0F66 PCMPGTD                                                        */
/*     0F67 PACKUSWB                                                       */
/*     0F68 PUNPCKHBW                                                      */
/*     0F69 PUNPCKHWD                                                      */
/*     0F6A PUNPCKHDQ                                                      */
/*     0F6B PACKSSDW                                                       */
/*     0F74 PCMPEQB                                                        */
/*     0F75 PCMPEQW                                                        */
/*     0F76 PCMPEQD                                                        */
/*     0FD1 PSRLW                                                          */
/*     0FD2 PSRLD                                                          */
/*     0FD3 PSRLQ                                                          */
/*     0FD5 PMULLW                                                         */
/*     0FD8 PSUBUSB                                                        */
/*     0FD9 PSUMUSW                                                        */
/*     0FDB PAND                                                           */
/*     0FDC PADDUSB                                                        */
/*     0FDD PADDUSW                                                        */
/*     0FDF PANDN                                                          */
/*     0FE1 PSRAW                                                          */
/*     0FE2 PSRAD                                                          */
/*     0FE5 PMULHW                                                         */
/*     0FE8 PSUBSB                                                         */
/*     0FE9 PSUBSW                                                         */
/*     0FEB POR                                                            */
/*     0FEC PADDSB                                                         */
/*     0FED PADDSW                                                         */
/*     0FEF PXOR                                                           */
/*     0FF1 PSLLW                                                          */
/*     0FF2 PSLLD                                                          */
/*     0FF3 PSLLQ                                                          */
/*     0FF5 PMADDWD                                                        */
/*     0FF8 PSUBB                                                          */
/*     0FF9 PSUBW                                                          */
/*     0FFA PSUBD                                                          */
/*     0FFC PADDB                                                          */
/*     0FFD PADDW                                                          */
/*     0FFE PADDD                                                          */
/***************************************************************************/
/*  MMX support                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F64(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Dbit = DBit_MemToReg;       // always a mem to Reg operation
  flags.sizePrefix = sizeQword;     // memory operands are Qwords
  flags.MMXop = 1;
  getNormalMemop(flags);
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF6E MOVD MMX extension                                             */
/*     OF7E MOVD - MMX instruction                                         */
/***************************************************************************/
/*  This opcode uses both MMX and 32 bit registers as operands             */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F6E(FLAGS & flags)
{
  getMod_rm_dw(flags);

  flags.Dbit = DBit_Mem1op;    // force the display of only the memory operand
  flags.Wbit = 1;              // always a word op
  flags.opsize32 = 1;          // force the use of 32 bit operands

  if (instr == 0x6E) 
     operandMMXreg(flags.regf, ",");

  getNormalMemop(flags);

  if (instr == 0x7E) {
     operandChar(',');
     operandMMXreg(flags.regf);
  } /* endif */
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF6F MOVQ - MMX instruction                                         */
/*     OF7F MOVQ - MMX instruction                                         */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F6F(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Dbit = (instr & 0x10) ? DBit_RegToMem : DBit_MemToReg;
  flags.sizePrefix = sizeQword;     // memory operands are Qwords
  flags.MMXop = 1;
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF71 PSHIMW opcodes with immediate 8 bit data                       */
/*     OF71 /2 PSRLW                                                       */
/*     OF71 /4 PSRAW                                                       */
/*     OF71 /6 PSLLW                                                       */
/*     OF72 PSHIMD opcodes with immediate 8 bit data                       */
/*     OF72 /2 PSRLD                                                       */
/*     OF72 /4 PSRAD                                                       */
/*     OF72 /6 PSLLD                                                       */
/*     OF73 PSHIMQ opcodes with immediate 8 bit data                       */
/*     OF73 /2 PSRLQ                                                       */
/*     OF73 /6 PSLLQ                                                       */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F71(FLAGS & flags)
{
   static const USHORT mnem[3][8] =  {
      // 0F71
      { ILLEGAL, ILLEGAL, PSRLW, ILLEGAL, PSRAW,   ILLEGAL, PSLLW, ILLEGAL },
      // 0F72
      { ILLEGAL, ILLEGAL, PSRLD, ILLEGAL, PSRAD,   ILLEGAL, PSLLD, ILLEGAL },
      // 0F73
      { ILLEGAL, ILLEGAL, PSRLQ, ILLEGAL, ILLEGAL, ILLEGAL, PSLLQ, ILLEGAL }
   } ;

//   const USHORT * optbl;
   getMod_rm_dw(flags);
   int m = mnem[instr-0x71][flags.regf];
                 
   if (ILLEGAL == m)
      throw IllegalOp();

   mnemonicStd(flags,m);
   operandMMXreg(flags.rm, ",");
   int ic = getNextByte();
   operandDecimal(ic);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF80 JO                                                             */
/*     OF81 JNO                                                            */
/*     OF82 JB                                                             */
/*     OF83 JNB                                                            */
/*     OF84 JZ                                                             */
/*     OF85 JNZ                                                            */
/*     OF86 JBE                                                            */
/*     OF87 JNBE                                                           */
/*     OF88 JS                                                             */
/*     OF89 JNS                                                            */
/*     OF8A JP                                                             */
/*     OF8B JNP                                                            */
/*     OF8C JL                                                             */
/*     OF8D JNL                                                            */
/*     OF8E JLE                                                            */
/*     OF8F JNLE                                                           */
/***************************************************************************/
/*  Long displacement jump on condition                                    */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F80(FLAGS & flags)
{
  long Dword1 = getNextOperand(flags);

  if (!flags.opsize32) {   
    // it is a 2-byte operand - sign extend the lower 16 bits
    Dword1 = (short)Dword1;
  } 
  operandRel(Dword1);
  parm->rettype = jreltype;
  parm->retoffset = Dword1;
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OF90 SETO                                                           */
/*     OF91 SETNO                                                          */
/*     OF92 SETB                                                           */
/*     OF93 SETNB                                                          */
/*     OF94 SETZ                                                           */
/*     OF95 SETNZ                                                          */
/*     OF96 SETBE                                                          */
/*     OF97 SETNBE                                                         */
/*     OF98 SETS                                                           */
/*     OF99 SETNS                                                          */
/*     OF9A SETP                                                           */
/*     OF9B SETNP                                                          */
/*     OF9C SETL                                                           */
/*     OF9D SETNL                                                          */
/*     OF9E SETLE                                                          */
/*     OF9F SETNLE                                                         */
/***************************************************************************/
/*  Set byte on condition                                                  */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0F90(FLAGS & flags)
{
  getMod_rm_dw(flags);
  flags.Dbit = DBit_Mem1op;           // there is only 1 operand
  flags.Wbit = 0;                     // these are always byte operands
  getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFA0  PUSH FS                                                       */
/*     OFA1  POP FS                                                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0FA0(FLAGS UNUSED & flags)
{
  operandSegRegister(FS);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFA4 SHLD Imm                                                       */
/*     OFA5 SHLD CL                                                        */
/*     OFAC SHRD Imm                                                       */
/*     OFAD SHRD CL                                                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0FA4(FLAGS & flags)
{
  int post = instr & 1;
  getMod_rm_dw(flags);
  flags.Dbit = DBit_RegToMem;
  flags.Wbit = 1;
  getNormalMemop(flags);
  operandChar(',');
  if (!post) {
     // shift n instruction
     flags.Wbit = 0;                  // force to 8-bit operand
     ULONG Dword1 = getImmediate(flags);
     operandDecimal(Dword1);
  } else {
     // second (or third) operand is "CL" (for shifts)
     operandTableItem(CL, reg8);
  } // endif
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFA8  PUSH GS                                                       */
/*     OFA9  POP GS                                                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0FA8(FLAGS UNUSED & flags) 
{
    operandSegRegister(GS);
}

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFAF IMUL  multiply immediate, 16-bit operand                       */
/*     OFBC BSF                                                            */
/*     OFBD BSR                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void 
op_0FAF(FLAGS & flags)
{ 
    getMod_rm_dw(flags);
    flags.Dbit = DBit_MemToReg; // register is first operand
    flags.Wbit = 1;             // word operation
    getNormalMemop(flags);
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFA6 CMPXCHG                                                        */
/*     OFA7 CMPXCHG                                                        */
/*     OFB0 CMPXCHG                                                        */
/*     OFB1 CMPXCHG                                                        */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FB0(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.Dbit = DBit_RegToMem;
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFB2 LSS                                                            */
/*     OFB4 LFS                                                            */
/*     OFB5 LGS                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FB2(FLAGS & flags)
{
    getMod_rm_dw(flags);
    if (flags.mod == 3)              // memory operand required
        throw IllegalOp(); 

    flags.Dbit = DBit_MemToReg;      // register is first operand
    flags.Wbit = 1;                  // force a word operation
    parm->rettype = LEAtype;
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFB6 MOVZX                                                          */
/*     OFBE MOVSX                                                          */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FB6(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.Wbit = 1;                  //  always has a wide first operand
    operandRegister(flags.regf,flags, ",");
    if (flags.mod != 3)
        parm->rettype = membtype;
#if RETPARMS
    parm->retbits &= 0xFE;          // clear 32 bit address marker
#endif
    flags.Wbit = 0;                   // always has an 8 bit second operand
    flags.Dbit = DBit_Mem1op;   
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFB7 MOVZX                                                          */
/*     OFBF MOVSX                                                          */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FB7(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.opsize32 = 1;                 // always a 32-bit register
    flags.Wbit = 1;                     // always wide operands
    operandRegister(flags.regf,flags, ",");
    if (flags.mod != 3)
        parm->rettype = memwtype;
#if RETPARMS
    parm->retbits &= 0xFE;         // clear 32 bit address marker
#endif
    flags.opsize32 = 0;              // always has 16-bit size second operand
    flags.Dbit = DBit_Mem1op;   
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFBA BTR, BTC, BT, BTS                                              */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FBA(FLAGS & flags)
{

    /*************************************************************************/
    /* mnemonic numbers for 0F BA orders                                     */
    /*************************************************************************/
    static const USHORT mnem0FBA[8] =  {
        ILLEGAL, ILLEGAL, ILLEGAL, ILLEGAL, BT, BTS, BTR, BTC } ;

    getMod_rm_dw(flags);
    flags.Dbit = DBit_Mem1op;           // always an 8-bit immediate operand
    flags.Wbit = 1;                     // always a word/dword operation
    if (flags.regf < 4)  
        throw IllegalOp(); 

    mnemonicStd(flags,mnem0FBA[flags.regf]);
    getNormalMemop(flags);
    flags.Wbit = 0;                   // force to 8-bit operand
    operandChar(',');
    ULONG Dword1 = getImmediate(flags);
    operandDecimal(Dword1);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFA3 BT                                                             */
/*     OFAB BTS                                                            */
/*     OFB3 BTR                                                            */
/*     OFBB BTC                                                            */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FBB(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.Dbit = DBit_RegToMem;
    flags.Wbit = 1;
    getNormalMemop(flags);
} 


/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFC0 XADD                                                           */
/*     OFC1 XADD                                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FC0(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.Dbit = DBit_RegToMem;
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     OFC7                                                                */
/***************************************************************************/
/*  Group 9                                                                */
/*     0 Illegal                                                           */
/*     1 CMPXCHG8B                                                         */
/*     2 Illegal                                                           */
/*     3 Illegal                                                           */
/*     4 Illegal                                                           */
/*     5 Illegal                                                           */
/*     6 Illegal                                                           */
/*     7 Illegal                                                           */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FC7(FLAGS & flags)
{
    getMod_rm_dw(flags);
    flags.Dbit = DBit_Mem1op;          // no register operand
    flags.Wbit = 1;                    // word operation
    if (flags.regf != 1 || flags.mod == 3) 
        // Group 9 can only have CMPXCHG8B
        // This can only access memory
        throw IllegalOp(); 

#if RETPARMS
    parm->retbits &= 0xFE;   // clear 32 bit address marker
#endif
    mnemonicStd(flags,CMPXCHG8B);
    flags.sizePrefix=sizeQword;   // takes a QWORD
    getNormalMemop(flags);
} 

/***************************************************************************/
/*  Opcodes handled by this function:                                      */
/*     0FC8 BSWAP EAX                                                      */
/*     0FC9 BSWAP ECX                                                      */
/*     0FCA BSWAP EDX                                                      */
/*     0FCB BSWAP EBX                                                      */
/*     0FCC BSWAP ESP                                                      */
/*     0FCD BSWAP EBP                                                      */
/*     0FCE BSWAP ESI                                                      */
/*     0FCF BSWAP EDI                                                      */
/***************************************************************************/
/*                                                                         */
/*  PARAMETERS:                                                            */
/*              flags            Input/Output:  the flags structure        */
/*                                                                         */
/*  RETURNS:                                                               */
/*              none                                                       */
/*                                                                         */
/***************************************************************************/
static void
op_0FC8(FLAGS & flags)
{
    if (flags.opsizeover) 
        throw IllegalOp();

    flags.regf = instr & 0x07;         // get register number
    flags.Wbit = 1;                    // force 32 bit register
    flags.opsize32 = 1;
    operandRegister(flags.regf, flags);
} 
