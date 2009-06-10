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
 
/*
 * Performance counter support using the 'perfctr' system.
 *  
 * Important notes to all users:
 *
 * This code will only work on machines with perfctr installed (which
 * requires a patched kernel).  Please refer to the perfctr installation
 * notes.
 *
 * It is essential that users of this system understand exactly what it is
 * that they are counting.  You should be familiar with the relevant 
 * sections of the vendor's reference manual.  
 * 
 * The authors of this code cannot take responsibility for the accuracy of
 * any numbers garnered through this tool.
 *
 * 1. Modern machines rarely present simple metrics such as L1 miss rate.
 *    Rather, one must read the fine print in the vendor's reference manual
 *    to understand exactly what is being counted. Predicated execution,
 *    hardware prefetching, etc all frequently cloud simple ideas of what 
 *    is happening. 
 * 2. Vendors frequently state that hardware performance counters may be
 *    very inaccurate.  This situation has improved greatly in recent times.
 *    Nonetheless users should ensure they understand the limits of the
 *    underlying hardware.  Consult the vendor's reference manual.
 * 3. Notwithstanding the above caveats, the authors cannot guarantee that
 *    they have correctly encoded the calls through the perfctr interface.
 */

#ifdef RVM_FOR_POWERPC
#  define PPC64 1
#else
#  define RVM_FOR_IA32 1
#endif

#include <stdio.h>
#include <stdlib.h>      // getenv() and others
#include <unistd.h>
#include <string.h>

extern "C" { 
#include <libperfctr.h>
}

#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In tools/bootImageRunner.
#include "perfctr.h"

#define RET_INST 0
#define L1D_MISS 1
#define L2_MISS 2
#define DTLB_L_MISS 3
#define ITLB_MISS 4
#define ITLB_HIT 5
#define BPU_TRACE_CACHE_MISS 6
#define TRACE_CACHE_FLUSH 7
#define L1I_MISS 8
#define BRANCHES 9
#define BRANCH_MISS 10

static struct vperfctr *pc_vpc;
static struct perfctr_info pc_info;
static struct vperfctr_control pc_control;
static struct perfctr_sum_ctrs pc_sum_a, pc_sum_b;
static int pc_sum_arity = 0;
static int pc_initialized = 0;
static long long basecycles = 0;
static long long basemetric = 0;

extern "C" int
perfCtrInit(int metric)
{
  if( pc_initialized == 0 ) {
  /* basic initialization */
  pc_vpc = vperfctr_open();
  
  if(!pc_vpc) {
    perror("sysPerfCtrInit:vperfctr_open");
    exit(1);
  }
  if(vperfctr_info(pc_vpc, &pc_info) < 0 ) {
    perror("sysPerfCtrInit:vperfctr_info");
    exit(1);
  }
  }

  /* set up the control data structure */
  memset(&pc_control, 0, sizeof pc_control);

  pc_control.cpu_control.tsc_on = 1;
  pc_control.cpu_control.nractrs = 1;
  switch (metric) {
    
/*****************************************************************************
 *                        Retired Instructions                               *
 *****************************************************************************/
  case RET_INST:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_P6:
    case PERFCTR_X86_INTEL_PII:
    case PERFCTR_X86_INTEL_PIII:
    case PERFCTR_X86_INTEL_PENTM:
    case PERFCTR_X86_INTEL_CORE2:
    case PERFCTR_X86_AMD_K7:
    case PERFCTR_X86_AMD_K8:
    case PERFCTR_X86_AMD_K8C:
      /* event 0xC0 (INST_RETIRED), count at CPL > 0, Enable */
      pc_control.cpu_control.evntsel[0] = 0xC0 | (1 << 16) | (1 << 22);
      break;
      
    /* Pentium 4 family */
    case PERFCTR_X86_INTEL_P4:
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      /* PMC0: IQ_COUNTER0 with fast RDPMC */
      pc_control.cpu_control.pmc_map[0] =  0x0C | (1 << 31);
      /* IQ_CCCR0: required flags, ESCR 4 (CRU_ESCR0), Enable */
      pc_control.cpu_control.evntsel[0] = (0x3 << 16) | (4 << 13) | (1 << 12);
      /* CRU_ESCR0: event 2 (instr_retired), NBOGUSNTAG, CPL>0 */
      pc_control.cpu_control.p4.escr[0] = (2 << 25) | (1 << 9) | (1 << 2);
      break;
#endif
#if defined(__powerpc64__) || defined(PPC64)
    case PERFCTR_PPC64_970:
      pc_control.cpu_control.pmc_map[0] = 0;
      pc_control.cpu_control.ppc64.mmcr0 = 0x00000900L;
      pc_control.cpu_control.ppc64.mmcr1 = 0x4003001005F09000ULL;
      pc_control.cpu_control.ppc64.mmcra = 0x00002000ULL;
      break;
#elif defined(__powerpc__) && !(defined(PPC64) || defined(__powerpc64__))
    case PERFCTR_PPC_604:
    case PERFCTR_PPC_604e:
    case PERFCTR_PPC_750:
    case PERFCTR_PPC_7400:
    case PERFCTR_PPC_7450:
      pc_control.cpu_control.pmc_map[0] = 0;
      /* INSTRUCTIONS_COMPLETED */
      pc_control.cpu_control.evntsel[0] = 0x02;
      /* don't count in kernel mode */
      pc_control.cpu_control.ppc.mmcr0 = (1 << (31-1)); 
      break;    
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;
    
/*****************************************************************************
 *                        Trace Cache misses                                 *
 *****************************************************************************/
  case BPU_TRACE_CACHE_MISS:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_P4 :
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      /* PMC0: MSR_BPU_COUNTER0 with fast RDPMC */
      pc_control.cpu_control.pmc_map[0] =  0x00 | (1 << 31);
      /* IQ_CCCR0: cascade, required flags, ESCR 0 (MSR_BPU_ESCR0), Enable */
      pc_control.cpu_control.evntsel[0] = (1 << 25) | (0x3 << 16) | (0 << 13) | (1 << 12);
      /* CRU_ESCR0: event 3 (BPU_fetch_request), TCMISS, CPL>0 */
      pc_control.cpu_control.p4.escr[0] = (3 << 25) | (1 << 9) | (1 << 2);
      break;
#endif
    /* Trace cache is a P4-specific feature */
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;
      
/*****************************************************************************
 *                        ITLB Misses                                        *
 *****************************************************************************/
  case ITLB_MISS:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_P4:
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      /* PMC0: MSR_BPU_COUNTER0 with fast RDPMC */
      pc_control.cpu_control.pmc_map[0] =  0x00 | (1 << 31);
      /* IQ_CCCR0: required flags, ESCR 0 (MSR_ITLB_ESCR0), Enable */
      pc_control.cpu_control.evntsel[0] = (0x3 << 16) | (3 << 13) | (1 << 12);
      /* CRU_ESCR0: event 18H (ITLB_reference), MISS, CPL>0 */
      pc_control.cpu_control.p4.escr[0] = (0x18 << 25) | (2 << 9) | (1 << 2);
      break;
      
    case PERFCTR_X86_INTEL_PENTM:
      /* event 0x85 (ITLB_MISS), count at CPL > 0, Enable */
      pc_control.cpu_control.evntsel[0] = 0x85 | (1 << 16) | (1 << 22);
      break;
      
    case PERFCTR_X86_INTEL_CORE2:
      /* event 0x82 (ITLB_MISS), all cores,  */
      pc_control.cpu_control.evntsel[0] = 0x82 | (0x12 << 8) | (1 << 16) | (1 << 22);
      break;
#endif
#if defined(__powerpc64__) || defined(PPC64)
    case PERFCTR_PPC64_970:
      // oprofile event:0x49 mmcr0:0x0400D420 mmcr1:0x000B000004DE9000 mmcra:0x00002000
      pc_control.cpu_control.pmc_map[0] = 1;
      pc_control.cpu_control.ppc64.mmcr0 = 0x0000D420L;
      pc_control.cpu_control.ppc64.mmcr1 = 0x000B000004DE9000ULL;
      pc_control.cpu_control.ppc64.mmcra = 0x00002000ULL;
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;
    
/*****************************************************************************
 *                        ITLB HITS                                          *
 *****************************************************************************/
  case ITLB_HIT:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_P4:
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      /* PMC0: MSR_BPU_COUNTER0 with fast RDPMC */
      pc_control.cpu_control.pmc_map[0] =  0x00 | (1 << 31);
      /* IQ_CCCR0: required flags, ESCR 0 (MSR_ITLB_ESCR0), Enable */
      pc_control.cpu_control.evntsel[0] = (0x3 << 16) | (3 << 13) | (1 << 12);
      /* CRU_ESCR0: event 18H (ITLB_reference), HIT, CPL>0 */
      pc_control.cpu_control.p4.escr[0] = (0x18 << 25) | (1 << 9) | (1 << 2);
      break;
#endif
#if defined(__powerpc64__) || defined(PPC64)
    case PERFCTR_PPC64_970:
      fprintf(stderr,"Counter unimplemented on PPC 970\n");
      exit(1);
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;

/*****************************************************************************
 *                            ICache Misses                                  *
 *****************************************************************************/
  case L1I_MISS:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_CORE2:
      /* event 0x81 (L1I_MISSES), all cores,  */
      pc_control.cpu_control.evntsel[0] = 0x81 | (1 << 16) | (1 << 22);
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;

/*****************************************************************************
 *                              Branches                                     *
 *****************************************************************************/
  case BRANCHES:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_CORE2:
      /* event 0xC4 (Branch Instruction Retired), count at CPL > 0, Enable */
      pc_control.cpu_control.evntsel[0] = 0xC4 | (1 << 16) | (1 << 22);
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;

/*****************************************************************************
 *                         Branch Mispredicts                                *
 *****************************************************************************/
  case BRANCH_MISS:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_CORE2:
      /* event 0xC5 (Branch Misses Retired), count at CPL > 0, Enable */
      pc_control.cpu_control.evntsel[0] = 0xC5 | (1 << 16) | (1 << 22);
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;
    
/*****************************************************************************
 *                        Trace Cache Flushes                                *
 *****************************************************************************/
  case TRACE_CACHE_FLUSH:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_INTEL_P4:
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      /* PMC0: MSR_MS_COUNTER0 with fast RDPMC */
      pc_control.cpu_control.pmc_map[0] =  0x04 | (1 << 31);
      /* IQ_CCCR0: required flags, ESCR 0 (MSR_TC_ESCR0), Enable */
      pc_control.cpu_control.evntsel[0] = (0x3 << 16) | (1 << 13) | (1 << 12);
      /* CRU_ESCR0: event 06H (TC_misc), FLUSH, CPL>0 */
      pc_control.cpu_control.p4.escr[0] = (0x06 << 25) | (16 << 9) | (1 << 2);
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
    break;


/*****************************************************************************
 *                        Cache and DTLB Misses                              *
 *****************************************************************************/
  case L1D_MISS:
  case L2_MISS:
  case DTLB_L_MISS:
    switch (pc_info.cpu_type) {
#ifdef RVM_FOR_IA32
    case PERFCTR_X86_AMD_K7:
    case PERFCTR_X86_AMD_K8:
    case PERFCTR_X86_AMD_K8C:
      switch (metric) {
      case L1D_MISS:
        /* DATA_CACHE_MISSES */
        pc_control.cpu_control.evntsel[0] = 0x41 | (1 << 16) | (1 << 22);
        break;
      case L2_MISS:
        /* DATA_CACHE_REFILLS_FROM_SYSTEM (i.e. L2 data misses) */
        pc_control.cpu_control.evntsel[0] = 0x43 | (1 << 16) | (1 << 22) | (0x1f << 8);
        break;
      case DTLB_L_MISS:
        /* L1_AND_L2_DTLB_MISSES (i.e. missed in both L1 and L2 of DTLB*/
        pc_control.cpu_control.evntsel[0] = 0x46 | (1 << 16) | (1 << 22);
        break;
      }
      break;
    case PERFCTR_X86_INTEL_P4:
    case PERFCTR_X86_INTEL_P4M2:
    case PERFCTR_X86_INTEL_P4M3:
      pc_control.cpu_control.pmc_map[0] = 0x0C | (1 << 31);
      pc_control.cpu_control.evntsel[0] = 0x0003B000;
      pc_control.cpu_control.p4.escr[0] = 0x12000204;
      pc_control.cpu_control.ireset[0] = -25;
      pc_control.cpu_control.p4.pebs_matrix_vert = 0x1;
      switch (metric) {
      case L1D_MISS:
        pc_control.cpu_control.p4.pebs_enable = 0x01000001;
        break;
      case L2_MISS:
        pc_control.cpu_control.p4.pebs_enable = 0x01000002;
        break;
      case DTLB_L_MISS:
        pc_control.cpu_control.p4.pebs_enable = 0x01000004;
        break;
      }
      break;
    case PERFCTR_X86_INTEL_PENTM:
      switch (metric) {
      case L1D_MISS:
        /* event 0x03 (L2_RQSTS), MESI 0xF, count at CPL > 0, Enable */
        //pc_control.cpu_control.evntsel[0] = 0x2E  | (0xF00) | (1 << 16) | (1 << 22);
        /* event 0x03 (DCU_LINES_IN), MESI 0xF, count at CPL > 0, Enable */
        pc_control.cpu_control.evntsel[0] = 0x45 | (1 << 16) | (1 << 22);
        break;
      case L2_MISS:
        /* event 0x24 (L2_LINES_IN), count at CPL > 0, Enable */
        pc_control.cpu_control.evntsel[0] = 0x24 | (1 << 16) | (1 << 22);
        break;
      case DTLB_L_MISS:
        /* event 0x49 (undocumented), count at CPL > 0, Enable */
        pc_control.cpu_control.evntsel[0] = 0x49 | (1 << 16) | (1 << 22);
        break;
        break;
      }
      break;
    case PERFCTR_X86_INTEL_CORE2:
      switch (metric) {
      case L1D_MISS:
        /* event 0x45 (L1D_REPL), umask 0xf, count at CPL > 0, Enable */
        pc_control.cpu_control.evntsel[0] = 0x45 | (0xf<<8) | (1 << 16) | (1 << 22);
        break;
      case L2_MISS:
        /* event 0x24 (L2_LINES_IN), all cores, incl h/w prefetch, count at CPL > 0, Enable */
        pc_control.cpu_control.evntsel[0] = 0x24 | (0x3<<14)| (0x3<<12) | (1 << 16) | (1 << 22);
        break;
      case DTLB_L_MISS:
        /* event 0x08, umask 0x1 */
        pc_control.cpu_control.evntsel[0] = 0x08 | (1 << 8) | (1 << 16) | (1 << 22);
        break;
        break;
      }
      break;
      break;
#endif
#if defined(__powerpc64__) || defined(PPC64)
    case PERFCTR_PPC64_970:
      switch (metric) {
      case L1D_MISS:
        /* oprofile event 0x4a - result in PMC2 */
        pc_control.cpu_control.pmc_map[0] = 2;
        pc_control.cpu_control.ppc64.mmcr0 = 0x0000D420L;
        pc_control.cpu_control.ppc64.mmcr1 = 0x000B000004DE9000ULL;
        pc_control.cpu_control.ppc64.mmcra = 0x00002000ULL;
        break;
      case L2_MISS:
        /* PAPI says this - result in PMC2 */
        pc_control.cpu_control.pmc_map[0] = 2;
        pc_control.cpu_control.ppc64.mmcr0 = 0x04000000L;
        pc_control.cpu_control.ppc64.mmcr1 = 0x300E38000840ULL;
        pc_control.cpu_control.ppc64.mmcra = 0x00002000ULL;
        break;
      case DTLB_L_MISS:
        /* oprofile event 0x48 - result in PMC0 */
        pc_control.cpu_control.pmc_map[0] = 0;
        pc_control.cpu_control.ppc64.mmcr0 = 0x0000D420L;
        pc_control.cpu_control.ppc64.mmcr1 = 0x000B000004DE9000ULL;
        pc_control.cpu_control.ppc64.mmcra = 0x00002000ULL;
        break;
      }
      break;
#endif
    default:
      fprintf(stderr, "cpu type %u (%s) not supported\n",
	      pc_info.cpu_type, perfctr_info_cpu_name(&pc_info));
      exit(1);
    }
  }

  /* enable the perf counting */
  if(vperfctr_control(pc_vpc, &pc_control) < 0) {
    perror("sysPerfCtrInit:vperfctr_control");
    exit(1);
  }
  pc_initialized = 1;
  vperfctr_read_ctrs(pc_vpc, &pc_sum_a);
  basecycles = pc_sum_a.tsc;
  basemetric = pc_sum_a.pmc[0];
  return 0;
}

extern "C" long long
perfCtrReadCycles()
{
  if (pc_initialized == 0) {
    fprintf(SysTraceFile, "Tried to read perf ctrs before initializing them!\n");
    exit(1);
  }
  vperfctr_read_ctrs(pc_vpc, &pc_sum_a);
  return pc_sum_a.tsc;
}

extern "C" long long
perfCtrReadMetric()
{
  if (pc_initialized == 0) {
    fprintf(SysTraceFile, "Tried to read perf ctrs before initializing them!\n");
    exit(1);
  }
  vperfctr_read_ctrs(pc_vpc, &pc_sum_a);
  long long rtn = pc_sum_a.pmc[0];
//   fprintf(SysTraceFile, "<%lld>", rtn);
  return rtn;
}

/*
 * The following is unused at present
 */
extern "C" int
perfCtrRead(char *str)
{
  if (pc_initialized == 0) {
    fprintf(SysTraceFile, "Tried to read perf ctrs before initializing them!");
    exit(1);
  }
  perfctr_sum_ctrs *before, *after;
  if (pc_sum_arity == 0) {
    before = &pc_sum_a;
    after = &pc_sum_b;
    pc_sum_arity = 1;
  } else {
    before = &pc_sum_b;
    after = &pc_sum_a;
    pc_sum_arity = 0;
  }
  vperfctr_read_ctrs(pc_vpc, after);
//   if (after->tsc < before->tsc || after->pmc[0] < before->pmc[0]) {
//     printf("perfctr overflow.  Exiting.\n");
//     exit(1);
//   }
  fprintf(SysTraceFile, "[%s %lld %lld]\n", str, after->tsc - before->tsc, after->pmc[0] - before->pmc[0]);
}
