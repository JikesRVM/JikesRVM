#include <stdint.h>
#include <unistd.h>
#include <stdio.h>

#ifndef _ARCH_SPEC_H
#define _ARCH_SPEC_H



#define SANDYBRIDGE          0x2AU
#define SANDYBRIDGE_EP       0x2DU
#define IVYBRIDGE            0x3AU
#define CPUID                              \
    __asm__ volatile ("cpuid"                             \
			: "=a" (eax),     \
			"=b" (ebx),     \
			"=c" (ecx),     \
			"=d" (edx)      \
			: "0" (eax), "2" (ecx))

typedef struct APIC_ID_t {
	uint64_t smt_id;
	uint64_t core_id;
	uint64_t pkg_id;
	uint64_t os_id;
} APIC_ID_t;

typedef struct cpuid_info_t {
	uint32_t eax;
	uint32_t ebx;
	uint32_t ecx;
	uint32_t edx;
} cpuid_info_t;

extern uint32_t eax, ebx, ecx, edx;
extern uint32_t cpu_model;
#define IVYBRIDGE            0x3AU
#define SANDYBRIDGE          0x2AU
#define SANDYBRIDGE_EP       0x2DU

extern FILE  **gov_file;
extern FILE  **scale_file;




//void
//get_cpu_model(void);

void 
parse_apic_id(cpuid_info_t info_l0, cpuid_info_t info_l1, APIC_ID_t *my_id);

void cpuid(uint32_t eax_in, uint32_t ecx_in, cpuid_info_t *ci);

cpuid_info_t getProcessorTopology(uint32_t level);

int getSocketNum();

#endif
