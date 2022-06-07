#include <stdio.h>
#include <jni.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
#include "energy_Scaler.h"
#include "arch_spec.h"
#include "msr.h"

rapl_msr_unit rapl_unit;
rapl_msr_parameter *parameters;
char *ener_info;
/*global variable*/
int *fd;

void copy_to_string(char *ener_info, char uncore_buffer[60], int uncore_num, char cpu_buffer[60], int cpu_num, char package_buffer[60], int package_num, int i, int *offset) {
	memcpy(ener_info + *offset, &uncore_buffer, uncore_num);
	//split sigh
	ener_info[*offset + uncore_num] = '#';
	memcpy(ener_info + *offset + uncore_num + 1, &cpu_buffer, cpu_num);
	ener_info[*offset + uncore_num + cpu_num + 1] = '#';
	if(i < num_pkg - 1) {
		memcpy(ener_info + *offset + uncore_num + cpu_num + 2, &package_buffer, package_num);
		offset += uncore_num + cpu_num + package_num + 2;
		if(num_pkg > 1) {
			ener_info[*offset] = '@';
			offset++;
		}
	} else {
		memcpy(ener_info + *offset + uncore_num + cpu_num + 2, &package_buffer, package_num + 1);
	}

}


JNIEXPORT jint JNICALL Java_EnergyCheckUtils_ProfileInit(JNIEnv *env, jclass jcls) {
	//kmahmou1: The code that reads msr sees num_pkgs as zero ... Trying to initialize here ...
	num_pkg = 1;
	jintArray result;
	int i;
	char msr_filename[BUFSIZ];

	//get_cpu_model();	
	//getSocketNum();

	jint wraparound_energy;

	/*only two domains are supported for parameters check*/
	parameters = (rapl_msr_parameter *)malloc(2 * sizeof(rapl_msr_parameter));
	fd = (int *) malloc(num_pkg * sizeof(int));

	for(i = 0; i < num_pkg; i++) {
		if(i > 0) {
			core += num_pkg_thread / 2; 	//measure the first core of each package
		}
		sprintf(msr_filename, "/dev/cpu/%d/msr", core);
		fd[i] = open(msr_filename, O_RDWR);
	}

	uint64_t unit_info= read_msr(fd[0], MSR_RAPL_POWER_UNIT);
	//printf("open core: %d\n", core);
	get_msr_unit(&rapl_unit, unit_info);
	get_wraparound_energy(rapl_unit.energy);
	wraparound_energy = (int)WRAPAROUND_VALUE;

	return wraparound_energy;
}

JNIEXPORT jint JNICALL Java_EnergyCheckUtils_GetSocketNum(JNIEnv *env, jclass jcls) {
	return (jint)getSocketNum();
}

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetPowerLimit
  (JNIEnv *env, jclass jcls, jint enable) {
	int i;
	//set power limit enable/disable
	for(i = 0; i < num_pkg; i++) {
		set_package_power_limit_enable(fd[i], enable, MSR_PKG_POWER_LIMIT);
		set_dram_power_limit_enable(fd[i], enable, MSR_DRAM_POWER_LIMIT);
	}

  }


JNIEXPORT jdoubleArray JNICALL Java_EnergyCheckUtils_GetPackagePowerSpec
  (JNIEnv *env, jclass jcls) {
	jdouble result[4];
	jdoubleArray specs = (*env)->NewDoubleArray(env, 4);	//TDP, min_power, max_power, max_time_window

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
	(*env)->SetDoubleArrayRegion(env, specs, 0, 4, result);
	/*
	rapl_msr_power_limit_t pkg_limit_info = get_specs(fd[0], MSR_PKG_POWER_LIMIT);
	printf("power limit: %f\n", pkg_limit_info.power_limit);
	printf("time window limit: %f\n", pkg_limit_info.time_window_limit);
	printf("clamp enable: %ld\n", pkg_limit_info.clamp_enable);
	printf("limit enable: %ld\n", pkg_limit_info.limit_enable);
	printf("lock enable: %ld\n", pkg_limit_info.lock_enable);
	*/

	return specs;
}

JNIEXPORT jdoubleArray JNICALL Java_EnergyCheckUtils_GetDramPowerSpec
  (JNIEnv *env, jclass jcls) {
	jdouble result[4];
	jdoubleArray specs = (*env)->NewDoubleArray(env, 4);	//TDP, min_power, max_power, max_time_window

	
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
	(*env)->SetDoubleArrayRegion(env, specs, 0, 4, result);
	return specs;
  }

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetPackagePowerLimit
  (JNIEnv *env, jclass jcls, jint socketId, jint level, jdouble custm_power) {

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

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetPackageTimeWindowLimit
  (JNIEnv *env, jclass jcls, jint socketId, jint level, jdouble custm_time_window) {
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

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetDramTimeWindowLimit
  (JNIEnv *env, jclass jcls, jint socketId, jint level, jdouble custm_time_window) {
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

JNIEXPORT void JNICALL Java_EnergyCheckUtils_SetDramPowerLimit
  (JNIEnv *env, jclass jcls, jint socketId, jint level, jdouble custm_power) {
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

JNIEXPORT jstring JNICALL Java_EnergyCheckUtils_EnergyStatCheck(JNIEnv *env,
		jclass jcls) {
	jstring ener_string;
	printf("kmahmou1 [EnergyStatCheck Begin  ....] \n");
	num_pkg=1;
	double result = 0.0;
	double package[num_pkg];
	double pp0[num_pkg];
	double pp1[num_pkg];
	double dram[num_pkg];
	//construct a string
	//char *ener_info;
	long dram_num = 0L; 
	long cpu_num = 0L; 
	long gpu_num = 0L; 
	long package_num = 0L;
	int info_size;
	int i;
	int offset = 0;

	printf("Pacakge Number %d \n", num_pkg);
	num_pkg=1;

	for(i = 0; i < num_pkg; i++) {
		char gpu_buffer[60]; 
		char dram_buffer[60]; 
		char cpu_buffer[60]; 
		char package_buffer[60];
		printf("Reading msr \n");
		result = read_msr(fd[i], MSR_PKG_ENERGY_STATUS);	//First 32 bits so don't need shift bits.
		printf("After reading msr .... %d \n", result);	
		package[i] = (double) result * rapl_unit.energy;

		result = read_msr(fd[i], MSR_PP0_ENERGY_STATUS);
		pp0[i] = (double) result * rapl_unit.energy;

		//printf("package energy: %f\n", package[i]);

		sprintf(package_buffer, "%f", package[i]);
		sprintf(cpu_buffer, "%f", pp0[i]);
		
		//allocate space for string
		switch(cpu_model) {
			case SANDYBRIDGE_EP:
	
				result = read_msr(fd[i],MSR_DRAM_ENERGY_STATUS);
				dram[i] =(double)result*rapl_unit.energy;

				sprintf(dram_buffer, "%f", dram[i]);

				package_num = strlen(package_buffer);
				dram_num = strlen(dram_buffer);
				cpu_num = strlen(cpu_buffer);

				if(i == 0) {
					info_size = num_pkg * (dram_num + cpu_num + package_num + 4);	
					ener_info = (char *) malloc(info_size);
				}

				//copy_to_string(ener_info, dram_buffer, dram_num, cpu_buffer, cpu_num, package_buffer, package_num, i, &offset);
				/*Insert socket number*/	
				
				memcpy(ener_info + offset, &dram_buffer, dram_num);
				//split sigh
				ener_info[offset + dram_num] = '#';
				memcpy(ener_info + offset + dram_num + 1, &cpu_buffer, cpu_num);
				ener_info[offset + dram_num + cpu_num + 1] = '#';
				if(i < num_pkg - 1) {
					memcpy(ener_info + offset + dram_num + cpu_num + 2, &package_buffer, package_num);
					offset += dram_num + cpu_num + package_num + 2;
					if(num_pkg > 1) {
						ener_info[offset] = '@';
						offset++;
					}
				} else {
					memcpy(ener_info + offset + dram_num + cpu_num + 2, &package_buffer, package_num + 1);
				}
				
				break;	
			case SANDYBRIDGE:
			case IVYBRIDGE:


				result = read_msr(fd[i],MSR_PP1_ENERGY_STATUS);
				pp1[i] = (double) result *rapl_unit.energy;

				sprintf(gpu_buffer, "%f", pp1[i]);

				package_num = strlen(package_buffer);
				gpu_num = strlen(gpu_buffer);		
				cpu_num = strlen(cpu_buffer);
				if(i == 0) {
					info_size = num_pkg * (gpu_num + cpu_num + package_num + 4);	
					ener_info = (char *) malloc(info_size);
				}

				//copy_to_string(ener_info, gpu_buffer, gpu_num, cpu_buffer, cpu_num, package_buffer, package_num, i, &offset);
				
				memcpy(ener_info + offset, &gpu_buffer, gpu_num);
				//split sign
				ener_info[offset + gpu_num] = '#';
				memcpy(ener_info + offset + gpu_num + 1, &cpu_buffer, cpu_num);
				ener_info[offset + gpu_num + cpu_num + 1] = '#';
				if(i < num_pkg - 1) {
					memcpy(ener_info + offset + gpu_num + cpu_num + 2, &package_buffer, package_num);
					offset += gpu_num + cpu_num + package_num + 2;
					if(num_pkg > 1) {
						ener_info[offset] = '@';
						offset++;
					}
				} else {
					memcpy(ener_info + offset + gpu_num + cpu_num + 2, &package_buffer,
							package_num + 1);
				}
				
				break;

		}
	}

	ener_string = (*env)->NewStringUTF(env, ener_info);	
	free(ener_info);
	return ener_string;

}
JNIEXPORT void JNICALL Java_EnergyCheckUtils_ProfileDealloc
   (JNIEnv * env, jclass jcls) {
	int i;
	free(fd);	
	free(parameters);
}

/*Change the system to be default governor*/
JNIEXPORT void JNICALL Java_energy_Scaler_SetGovernor
  (JNIEnv * env, jclass jcls, jstring name){
	const char *gov = (*env)->GetStringUTFChars(env, name, 0);
	int cores = core_num();
	int i;
	char govFile[cores][60];

	for(i = 0; i < cores; i++) {
		sprintf(govFile[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
	}
	check_write_gov(cores, govFile, gov);
}


JNIEXPORT jint JNICALL Java_energy_Scaler_scale
  (JNIEnv * env, jclass jcls, jint name){
	jboolean iscopy;
	int cores = core_num();
	int i;
	char govFile[cores][60];
	char filename[cores][60];

	const char *usrSpace = "userspace";
	const char *cur_freq = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq";
	const char *scal_freq = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
	const int freq = name;
	
	//Read govenor
	for(i = 0; i < cores; i++) {
		sprintf(govFile[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
		sprintf(filename[i], "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed", i);
	}

	check_write_gov(cores, govFile, usrSpace);

	//free memory
	//free (string);
		//Write frequency
	write_freq_all_cores(cores, filename, cur_freq, scal_freq, freq);

    return 0;
}

int get_pos_intnum(int value) {
	int num = 1;
	while(value > 9) {
		num++;
		value /= 10;
	}
	return num;
}

JNIEXPORT jintArray JNICALL JNICALL Java_energy_Scaler_freqAvailable
  (JNIEnv * env, jclass jcls){
	const char *filename = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    int i;
	
	FILE *f;
    int rc;
    size_t data_length, data_written;

		f = fopen(filename, "r");
		if (f == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
		}
	
	int freq[20];
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
    jintArray ret = (*env)->NewIntArray(env, count);

    for(i = 0; i < count; i++) {
    	(*env)->SetIntArrayRegion(env, ret, 0, count, freq);
    }

    return(ret);
}
