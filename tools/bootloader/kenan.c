#include <perfmon/pfmlib_perf_event.h>
#include <err.h>
#include "sys.h"
#include "msr.h"
#include <fcntl.h>    /* For O_RDWR */
#include <unistd.h>   /* For open(), creat() */
#include "energy.c"



#define IVYBRIDGE            0x3AU
#define SANDYBRIDGE          0x2AU
#define SANDYBRIDGE_EP       0x2DU
#define SKYLAKE1             0x4EU
#define SKYLAKE2             0x5EU
#define HASWELL1	     0x3CU
#define HASWELL2	     0x45U
#define HASWELL3	     0x46U
#define HASWELL_EP	     0x3FU
#define BROADWELL	     0xD4U
#define BROADWELL2	     0x4FU

//extern char *ener_info;
extern rapl_msr_unit rapl_unit;
extern int *fd;
extern double WRAPAROUND_VALUE;
extern rapl_msr_parameter *parameters;

//int num_pkg = 1;
//int num_core;
//int num_pkg_thread = 1;
//uint32_t num_core_thread = 2;
//uint32_t num_pkg_core = 2;
//uint32_t cpu_model = SANDYBRIDGE_EP;

FILE  **gov_file;
FILE  **scale_file;
int num_core;

EXTERNAL int ProfileInit() {
//	jintArray result;
	int measure_core = 0;
	int i;
	char msr_filename[BUFSIZ];

//	get_cpu_model();
//	getSocketNum();
	int wraparound_energy;

	//get the number of cores
	//TODO: Not sure if it get the total number of cores or only
	//the num of cores for each socket.
	num_core = get_nprocs();
	/*only two domains are supported for parameters check*/
	parameters = (rapl_msr_parameter *)malloc(2 * sizeof(rapl_msr_parameter));
	fd = (int *) malloc(num_pkg * sizeof(int));


	for(i = 0; i < num_pkg; i++) {
		if(i > 0) {
			measure_core += num_pkg_thread / 2; 	//measure the first core of each package
		}
		sprintf(msr_filename, "/dev/cpu/%d/msr", measure_core);
		fd[i] = open(msr_filename, O_RDWR);
	}

	printf("Profileinit read_msr\n");
	uint64_t unit_info= read_msr(fd[0], MSR_RAPL_POWER_UNIT);
	get_msr_unit(&rapl_unit, unit_info);
	get_wraparound_energy(rapl_unit.energy);
	wraparound_energy = (int)WRAPAROUND_VALUE;

	return wraparound_energy;
}

EXTERNAL int GetSocketNum() {
	//TODO:
//	return (int)getSocketNum();
	return 2;
}

EXTERNAL void SetPowerLimit (int enable) {
	int i;
	//set power limit enable/disable
	for(i = 0; i < num_pkg; i++) {
		set_package_power_limit_enable(fd[i], enable, MSR_PKG_POWER_LIMIT);
		set_dram_power_limit_enable(fd[i], enable, MSR_DRAM_POWER_LIMIT);
	}

  }

EXTERNAL double * GetPackagePowerSpec() {
	double result[4];	//TDP, min_power, max_power, max_time_window

	switch(cpu_model) {
		case SANDYBRIDGE_EP:
		case IVYBRIDGE:
		case SANDYBRIDGE:
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(0);
			break;
	}

	get_rapl_pkg_parameters(fd[0], &rapl_unit, &parameters[PKG_DOMAIN]);
	getPowerSpec(result, parameters, PKG_DOMAIN);
	/*
	rapl_msr_power_limit_t pkg_limit_info = get_specs(fd[0], MSR_PKG_POWER_LIMIT);
	printf("power limit: %f\n", pkg_limit_info.power_limit);
	printf("time window limit: %f\n", pkg_limit_info.time_window_limit);
	printf("clamp enable: %ld\n", pkg_limit_info.clamp_enable);
	printf("limit enable: %ld\n", pkg_limit_info.limit_enable);
	printf("lock enable: %ld\n", pkg_limit_info.lock_enable);
	*/

	return result;
}

EXTERNAL double * GetDramPowerSpec() {
	double result[4];	//TDP, min_power, max_power, max_time_window


	switch(cpu_model) {
		case SANDYBRIDGE_EP:

			get_rapl_dram_parameters(fd[0], &rapl_unit, &parameters[DRAM_DOMAIN]);
	/*Test Use*/
	/*
	printf("thermal specification power is: %f, minimum power limit is: %f, maximum power limit is: %f, maximum time window is: %f\n", parameters[DRAM_DOMAIN].thermal_spec_power, parameters[DRAM_DOMAIN].min_power, parameters[DRAM_DOMAIN].max_power, parameters[DRAM_DOMAIN].max_time_window);
	*/
			break;
		case IVYBRIDGE:
		case SANDYBRIDGE:
			printf("Current architecture cannot support Dram measurement...");
			exit(1);
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(1);
	}
	get_rapl_dram_parameters(fd[0], &rapl_unit, &parameters[DRAM_DOMAIN]);
	getPowerSpec(result, parameters, DRAM_DOMAIN);
	return result;
  }

EXTERNAL void SetPackagePowerLimit(int socketId, int level, double custm_power) {

	switch(cpu_model) {
		case SANDYBRIDGE_EP:
		case IVYBRIDGE:
		case SANDYBRIDGE:
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(0);
			break;
	}

	rapl_msr_power_limit_t current_pkg_limit_info = get_specs(fd[socketId], MSR_PKG_POWER_LIMIT);

	//printf("setting before: \n");
	//printf("power limit: %f\n", current_pkg_limit_info.power_limit);
	//set clamp enabled
	set_package_clamp_enable(fd[socketId], MSR_PKG_POWER_LIMIT);

	switch(level) {
		case MINIMUM_POWER_LIMIT:
			get_rapl_pkg_parameters(fd[socketId], &rapl_unit, &parameters[PKG_DOMAIN]);
	//		printf("minimum power limit: %f\n", parameters[PKG_DOMAIN].min_power);
			set_pkg_power_limit(fd[socketId], MSR_PKG_POWER_LIMIT, parameters[PKG_DOMAIN].min_power);
			break;
		case MAXIMUM_POWER_LIMIT:
			get_rapl_pkg_parameters(fd[socketId], &rapl_unit, &parameters[PKG_DOMAIN]);
			set_pkg_power_limit(fd[socketId], MSR_PKG_POWER_LIMIT, parameters[PKG_DOMAIN].max_power);
			break;
		case COSTOM_POWER_LIMIT:
			set_pkg_power_limit(fd[socketId], MSR_PKG_POWER_LIMIT, custm_power);
			break;
		default:
			printf("Cannot support such power limit level");
	}
	current_pkg_limit_info = get_specs(fd[socketId], MSR_PKG_POWER_LIMIT);

	//printf("setting after: \n");
	//printf("power limit: %f\n", current_pkg_limit_info.power_limit);
	//printf("power limit lock: %ld\n", current_pkg_limit_info.lock_enable);
	//printf("power limit clamping: %ld\n", current_pkg_limit_info.clamp_enable);
	//printf("power limit enable: %ld\n", current_pkg_limit_info.limit_enable);

}

EXTERNAL void SetPackageTimeWindowLimit(int socketId, int level, double custm_time_window) {
	switch(cpu_model) {
		case SANDYBRIDGE_EP:
		case IVYBRIDGE:
		case SANDYBRIDGE:
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(0);
			break;
	}

	rapl_msr_power_limit_t current_pkg_limit_info = get_specs(fd[socketId], MSR_PKG_POWER_LIMIT);

	//printf("setting before: \n");
	//printf("time window: %f\n", current_pkg_limit_info.time_window_limit);
	//set clamp enabled
	//set_package_clamp_enable(fd[socketId], MSR_PKG_POWER_LIMIT);

	switch(level) {
		case NA:
			printf("Does not have minimum time window.");
			break;
		case MAXIMUM_TIME_WINDOW:
			get_rapl_pkg_parameters(fd[socketId], &rapl_unit, &parameters[PKG_DOMAIN]);
			set_pkg_time_window_limit(fd[socketId], MSR_PKG_POWER_LIMIT, parameters[PKG_DOMAIN].max_time_window);
			break;
		case COSTOM_TIME_WINDOW:
			set_pkg_time_window_limit(fd[socketId], MSR_PKG_POWER_LIMIT, custm_time_window);
			break;
		default:
			printf("Cannot support such power limit level.");
	}
	current_pkg_limit_info = get_specs(fd[socketId], MSR_PKG_POWER_LIMIT);

	//printf("setting after: \n");
	//printf("time window: %f\n", current_pkg_limit_info.time_window_limit);

  }

EXTERNAL void SetDramTimeWindowLimit(int socketId, int level, double custm_time_window) {
	switch(cpu_model) {
		case SANDYBRIDGE_EP:
		case IVYBRIDGE:
		case SANDYBRIDGE:
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(0);
			break;
	}

	rapl_msr_power_limit_t current_dram_limit_info = get_specs(fd[socketId], MSR_DRAM_POWER_LIMIT);

	//printf("setting before: \n");
	//printf("power limit: %f\n", current_dram_limit_info.time_window_limit);
	//set clamp enabled
	//set_package_clamp_enable(fd[socketId], MSR_PKG_POWER_LIMIT);

	switch(level) {
		case NA:
			printf("Does not have minimum time window.");
			break;
		case MAXIMUM_TIME_WINDOW:
			get_rapl_dram_parameters(fd[socketId], &rapl_unit, &parameters[DRAM_DOMAIN]);
			set_dram_time_window_limit(fd[socketId], MSR_DRAM_POWER_LIMIT, parameters[DRAM_DOMAIN].max_time_window);
			break;
		case COSTOM_TIME_WINDOW:
			set_dram_time_window_limit(fd[socketId], MSR_DRAM_POWER_LIMIT, custm_time_window);
			break;
		default:
			printf("Cannot support such power limit level.");
	}
	current_dram_limit_info = get_specs(fd[socketId], MSR_DRAM_POWER_LIMIT);

	//printf("setting after: \n");
	//printf("time window: %f\n", current_dram_limit_info.time_window_limit);
}

EXTERNAL void SetDramPowerLimit(int socketId, int level, double custm_power) {
	switch(cpu_model) {
		case SANDYBRIDGE_EP:
		case IVYBRIDGE:
		case SANDYBRIDGE:
			break;
		default:
			printf("Current architecture cannot be supported...");
			exit(0);
			break;
	}

	rapl_msr_power_limit_t current_dram_limit_info = get_specs(fd[socketId], MSR_DRAM_POWER_LIMIT);

	//printf("setting before: \n");
//	printf("power limit: %f\n", current_dram_limit_info.power_limit);

	switch(level) {
		case MINIMUM_POWER_LIMIT:
			get_rapl_dram_parameters(fd[socketId], &rapl_unit, &parameters[DRAM_DOMAIN]);
			set_dram_power_limit(fd[socketId], MSR_DRAM_POWER_LIMIT, parameters[DRAM_DOMAIN].min_power);
			break;
		case MAXIMUM_POWER_LIMIT:
			get_rapl_dram_parameters(fd[socketId], &rapl_unit, &parameters[DRAM_DOMAIN]);
			set_dram_power_limit(fd[socketId], MSR_DRAM_POWER_LIMIT, parameters[DRAM_DOMAIN].max_power);
			break;
		case COSTOM_POWER_LIMIT:
			set_dram_power_limit(fd[socketId], MSR_DRAM_POWER_LIMIT, custm_power);
			break;
		default:
			printf("Cannot support such power limit level");
	}
	current_dram_limit_info = get_specs(fd[socketId], MSR_DRAM_POWER_LIMIT);

	//printf("setting after: \n");
	//printf("power limit: %f\n", current_dram_limit_info.power_limit);
}


#define MSR_DRAM_ENERGY_UNIT 0.000015

void
initialize_energy_info(char gpu_buffer[num_pkg][60], char dram_buffer[num_pkg][60], char cpu_buffer[num_pkg][60], char package_buffer[num_pkg][60]) {

	double package[num_pkg];
	double pp0[num_pkg];
	double pp1[num_pkg];
	double dram[num_pkg];
	uint64_t result = 0.0;
	int info_size = 0;
	int i = 0;
	for (; i < num_pkg; i++) {

		result = read_msr(fd[i], MSR_PKG_ENERGY_STATUS);	//First 32 bits so don't need shift bits.
		package[i] = (double) result * rapl_unit.energy;

//		if (result < 0.0) {
//			printf("intermediate result energy: %f\n", result);
//			//printf("package energy: %f\n", package[i]);
//		}
	
		result = read_msr(fd[i], MSR_PP0_ENERGY_STATUS);
		pp0[i] = (double) result * rapl_unit.energy;

	
		sprintf(package_buffer[i], "%f", package[i]);
		sprintf(cpu_buffer[i], "%f", pp0[i]);
//		if (result < 0.0) {
//			printf("intermediate result energy: %f\n", result);
//			//printf("cpu_buffer energy: %f\n", pp0[i]);
//		}
		
		//allocate space for string
		//printf("%" PRIu32 "\n", cpu_model);
		switch(cpu_model) {
			case SANDYBRIDGE_EP:
			case HASWELL1:
			case HASWELL2:
			case HASWELL3:
			case HASWELL_EP:
			case SKYLAKE1:
			case SKYLAKE2:
			case BROADWELL:
			case BROADWELL2:
	
				result = read_msr(fd[i],MSR_DRAM_ENERGY_STATUS);
				if (cpu_model == BROADWELL || cpu_model == BROADWELL2) {
					dram[i] =(double)result*MSR_DRAM_ENERGY_UNIT;
				} else {
					dram[i] =(double)result*rapl_unit.energy;
				}

				sprintf(dram_buffer[i], "%f", dram[i]);

				info_size += strlen(package_buffer[i]) + strlen(dram_buffer[i]) + strlen(cpu_buffer[i]) + 4;	

				/*Insert socket number*/	
				
				break;
			case SANDYBRIDGE:
			case IVYBRIDGE:


				result = read_msr(fd[i],MSR_PP1_ENERGY_STATUS);

				pp1[i] = (double) result *rapl_unit.energy;

				sprintf(gpu_buffer[i], "%f", pp1[i]);

				info_size += strlen(package_buffer[i]) + strlen(gpu_buffer[i]) + strlen(cpu_buffer[i]) + 4;	
				
		}

		//ener_info = (char *) malloc(info_size);
	}
}

EXTERNAL EnergyStatCheck(char *ener_info) {

	char gpu_buffer[num_pkg][60]; 
	char dram_buffer[num_pkg][60]; 
	char cpu_buffer[num_pkg][60]; 
	char package_buffer[num_pkg][60];
	int dram_num = 0L;
	int cpu_num = 0L;
	int package_num = 0L;
	int gpu_num = 0L;
	//construct a string
	//char *ener_info;
	int info_size;
	int i;
	int offset = 0;

	//bzero(ener_info, 512);
	initialize_energy_info(gpu_buffer, dram_buffer, cpu_buffer, package_buffer);
	//printf("dram is %s, cpu is %s, package is %s\n", dram_buffer, cpu_buffer, package_buffer);

	for(i = 0; i < num_pkg; i++) {
		//allocate space for string
		//printf("%" PRIu32 "\n", cpu_model);
		switch(cpu_model) {
			case SANDYBRIDGE_EP:
			case HASWELL1:
			case HASWELL2:
			case HASWELL3:
			case HASWELL_EP:
			case SKYLAKE1:
			case SKYLAKE2:
			case BROADWELL:
			case BROADWELL2:

				//copy_to_string(ener_info, dram_buffer, dram_num, cpu_buffer, cpu_num, package_buffer, package_num, i, &offset);
				/*Insert socket number*/	
				dram_num = strlen(dram_buffer[i]);
				cpu_num = strlen(cpu_buffer[i]);
				package_num = strlen(package_buffer[i]);
				
				memcpy(ener_info + offset, &dram_buffer[i], dram_num);
				//split sigh
				ener_info[offset + dram_num] = '#';
				memcpy(ener_info + offset + dram_num + 1, &cpu_buffer[i], cpu_num);
				ener_info[offset + dram_num + cpu_num + 1] = '#';
				if(i < num_pkg - 1) {
					memcpy(ener_info + offset + dram_num + cpu_num + 2, &package_buffer[i], package_num);
					offset += dram_num + cpu_num + package_num + 2;
					if(num_pkg > 1) {
						ener_info[offset] = '@';
						offset++;
					}
				} else {
					memcpy(ener_info + offset + dram_num + cpu_num + 2, &package_buffer[i], package_num + 1);
				}
				
				break;	
			case SANDYBRIDGE:
			case IVYBRIDGE:

				gpu_num = strlen(gpu_buffer[i]);		
				cpu_num = strlen(cpu_buffer[i]);
				package_num = strlen(package_buffer[i]);

				//copy_to_string(ener_info, gpu_buffer, gpu_num, cpu_buffer, cpu_num, package_buffer, package_num, i, &offset);
				memcpy(ener_info + offset, &gpu_buffer[i], gpu_num);
				//split sign
				ener_info[offset + gpu_num] = '#';
				memcpy(ener_info + offset + gpu_num + 1, &cpu_buffer[i], cpu_num);
				ener_info[offset + gpu_num + cpu_num + 1] = '#';
				if(i < num_pkg - 1) {
					memcpy(ener_info + offset + gpu_num + cpu_num + 2, &package_buffer[i], package_num);
					offset += gpu_num + cpu_num + package_num + 2;
					if(num_pkg > 1) {
						ener_info[offset] = '@';
						offset++;
					}
				} else {
					memcpy(ener_info + offset + gpu_num + cpu_num + 2, &package_buffer[i],
							package_num + 1);
				}
				
				break;
			default:
				printf("non of archtectures are detected\n");
				break;


		}
	}


}

EXTERNAL void ProfileDealloc() {
	free(fd);
	//free(ener_info);
	free(parameters);
}

/*Change governor*/
EXTERNAL int SetGovernor(const char* name) {
	int core_id = getCurrentCpu();
	return check_write_gov(gov_file[core_id], name, core_id);
}

EXTERNAL GetGovernor(char* name) {
	FILE* f[4];
	char govFile[4][60];
	int rc;
	int current_core = sched_getcpu();
	char output[20];
	fseek(gov_file[current_core], 0, SEEK_SET);
	fscanf(gov_file[current_core], "%s", name);
	return strlen(name);
}

EXTERNAL int getCpuNum() {
	return get_nprocs();
}


EXTERNAL int getCurrentCpu(){
	return sched_getcpu();
}

//Kenan: tempararily add
EXTERNAL void openDVFSFiles() {
	int i;

	char filename[num_core][60];
	scale_file = (FILE **)malloc(num_core * sizeof(FILE *));
	gov_file = (FILE **)malloc(num_core * sizeof(FILE *));
	for (i = 0; i < num_core; i++) {
		//printf("open DVFS files: core id is: %d\n", i);
		/*Open the Scaling files*/
		sprintf(filename[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed", i);
		scale_file[i] = fopen(filename[i], "r+");
//		printf("kenan: open the scaling file: %d!!!!!!\n", i);
		if (scale_file[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s\n", filename[i]);
//			return 1;
		}

		/*Open the governor files*/
		sprintf(filename[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
		gov_file[i] = fopen(filename[i], "r+");
		if (gov_file[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s", filename[i]);
//			return 1;
		}



	}
}

//Kenan: tempararily add
EXTERNAL void closeDVFSFiles() {
	int i;
	int rc;
	char filename[num_core][60];
	for (i = 0; i < num_core; i++) {

		/*Close scaler files*/
		sprintf(filename[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed", i);
		rc= fclose(scale_file[i]);
		if (rc != 0) {
			printf("Failed to close %s\n", filename[i]);
//			return 1;
		}
		/*Close governor files*/
		sprintf(filename[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
		rc = fclose(gov_file[i]);
		if (rc != 0) {
			//LOGI("Failed to close %s: %s", filename, strerror(rc));
			printf("Failed to close %s", gov_file[i]);
	//		return 1;
		}
	}

	free(scale_file);
	free(gov_file);

}

EXTERNAL int Scale(int name) {
//	int num_core = core_num();
//	int num_core = get_nprocs() / 2;

	char set_gov_file[60];
	char set_scale_file[60];
	const char *usrSpace = "userspace";
	const int freq = name;
	const int core_id = sched_getcpu();

	//printf("begin to scale!!!!\n");
	sprintf(set_gov_file, "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", core_id);
	check_write_gov(gov_file[core_id], usrSpace, core_id);

	//free memory
	//free (string);
		//Write frequency
	sprintf(set_scale_file, "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed", core_id);
	write_freq_coreId(scale_file[core_id], freq, core_id);

    return 1;
}

EXTERNAL int checkFrequency() {
	const char *cur_freq = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq";

	FILE *f;
	f = fopen(cur_freq, "r");

	int freq;
	fscanf (f, "%d", &freq);

	fclose(f);
	return freq;
}

EXTERNAL void FreqAvailable(int *freq){
	const char *filename = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    int i;

	FILE *f;
    int rc;
    size_t data_length, data_written;

		f = fopen(filename, "r");
		if (f == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
		}

//	int freq[20];
	int string;
	int count = 0;
	int temp = 100;

	while (fscanf (f, "%d", &string) != EOF){
			freq[count] = string;
			count++;

	}
    rc = fclose(f);
    if (rc != 0) {
        //LOGI("Failed to close %s: %s", filename, strerror(rc));
    }


    return;
}
