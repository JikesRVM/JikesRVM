#ifndef MSR
#define MSR

#include <stdint.h>

#define MSR_RAPL_POWER_UNIT		0x606

/**Energy measurement**/
#define MSR_PP0_ENERGY_STATUS		0x639
#define MSR_PP1_ENERGY_STATUS       0x641
#define MSR_PKG_ENERGY_STATUS		0x611
#define MSR_DRAM_ENERGY_STATUS		0x619

/**Power/time window maximum/minimum information(Only support for PKG and DRAM **/
#define MSR_PKG_POWER_INFO		0x614
#define MSR_DRAM_POWER_INFO     0x61C

/**Power limit**/
#define MSR_PKG_POWER_LIMIT        0x610
#define MSR_DRAM_POWER_LIMIT        0x618
#define MSR_PP0_POWER_LIMIT        0x638
#define MSR_PP1_POWER_LIMIT        0x640

/*Power domains*/
#define PKG_DOMAIN	0
#define DRAM_DOMAIN	1

/*Power limit set*/
#define DISABLE 0
#define ENABLE 1

/***global variable***/
typedef struct rapl_msr_unit {
	double power;
	double energy;
	double time;
} rapl_msr_unit;

typedef struct rapl_msr_parameter {
	double thermal_spec_power;
	double min_power;
	double max_power;
	double max_time_window;
} rapl_msr_parameter; 

typedef struct rapl_msr_power_limit_t {
	double power_limit;
	/* time_window_limit = 2^Y*F 
	 * F(23:22) Y(21:17)
	 */
	double time_window_limit; 
	uint64_t clamp_enable;
	uint64_t limit_enable;
	uint64_t lock_enable; 
} rapl_msr_power_limit_t;


extern char *ener_info;
extern rapl_msr_unit rapl_unit;
extern int *fd;
extern double WRAPAROUND_VALUE;
extern rapl_msr_parameter *parameters;


typedef enum { 
	MINIMUM_POWER_LIMIT = 0,
	MAXIMUM_POWER_LIMIT,
	COSTOM_POWER_LIMIT
} msr_power_set;

typedef enum { 
	NA = 0,
	MAXIMUM_TIME_WINDOW,
	COSTOM_TIME_WINDOW
} msr_time_window_set;
#define _2POW(e)	\
((e == 0) ? 1 : (2 << (e - 1)))

rapl_msr_power_limit_t get_specs(int fd, uint64_t addr);

#endif
