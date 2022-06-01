#include <stdio.h>
#include <math.h>


#include "msr.h"
typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;
typedef jint jsize;

double WRAPAROUND_VALUE;
//factor of F for time_window_limit. It represents these four value.
double F_arr[4] = {1.0, 1.1, 1.2, 1.3};

void
putBitField(uint64_t inField, uint64_t *data, uint64_t width, uint64_t offset)
{
	uint64_t mask = ~0;
	uint64_t bitMask;

	/*The bits to be overwritten are located in the leftmost part.*/
	if ((offset+width) == 64) 
    {
        bitMask = (mask<<offset);
    } else {
		bitMask = (mask<<offset) ^ (mask<<(offset + width));
	}
	/*Reset the bits that will be overwritten to be 0, and keep other bits the same.*/
	*data = ~bitMask & *data;	
	*data = *data | (inField<<offset);
}

uint64_t
extractBitField(uint64_t inField, uint64_t width, uint64_t offset)
{
	uint64_t mask = ~0;
	uint64_t bitMask;
	uint64_t outField;

	if ((offset+width) == 64) 
	{
		bitMask = (mask<<offset);
	}
	else 
	{
		bitMask = (mask<<offset) ^ (mask<<(offset+width));

	}

	outField = (inField & bitMask) >> offset;
	return outField;
}

uint64_t read_msr(int fd, uint64_t which) {

	uint64_t data = 0;
	uint64_t mask = 1;
	mask <<= 31;

	if ( pread(fd, &data, sizeof data, which) != sizeof data ) {
	  printf("pread error!\n");
	}

	/*
	if (data & mask != 0) {
		if (which == MSR_DRAM_ENERGY_STATUS) {
			printf("-----------------DRAM energy mask starts------------\n");
		}
		printf("negative!!!!%ld\n", data);
		data ^= mask;	
		printf("after mask!!!!%ld\n", data);
		if (which == MSR_DRAM_ENERGY_STATUS) {
			printf("-----------------DRAM energy mask ends------------\n");
		}
	}
	*/
	
	return data;
}

void write_msr(int fd, uint64_t which, uint64_t limit_info) {
	if ( pwrite(fd, &limit_info , sizeof limit_info, which) != sizeof limit_info) 
	  printf("pwrite error!\n");
}

double calc_time_window(uint64_t Y, uint64_t F) {
	return _2POW(Y) * F_arr[F] * rapl_unit.time;
}

void 
calc_y(uint64_t *Y, double F, jdouble custm_time) {
	*Y = log2(custm_time / rapl_unit.time / F);
}

rapl_msr_power_limit_t
get_specs(int fd, uint64_t addr) {
	uint64_t msr;
	rapl_msr_power_limit_t limit_info;
	msr = read_msr(fd, addr);	
	limit_info.power_limit = rapl_unit.power * extractBitField(msr, 14, 0);
	limit_info.time_window_limit = calc_time_window(extractBitField(msr, 5, 17), extractBitField(msr, 2, 22));
	limit_info.clamp_enable = extractBitField(msr, 1, 16);
	limit_info.limit_enable = extractBitField(msr, 1, 15);
	limit_info.lock_enable = extractBitField(msr, 1, 63);
	return limit_info;
}

void
set_package_power_limit_enable(int fd, uint64_t setting, uint64_t addr) {
	uint64_t msr;
	msr = read_msr(fd, addr);	

	//enable set #1
	putBitField(setting, &msr, 1, 15);
	//enable set #2
	putBitField(setting, &msr, 1, 47);
	write_msr(fd, addr, msr);

}

void
set_dram_power_limit_enable(int fd, uint64_t setting, uint64_t addr) {
	uint64_t msr;
	msr = read_msr(fd, addr);	

	//enable set
	putBitField(setting, &msr, 1, 15);

	write_msr(fd, addr, msr);

}

void
set_package_clamp_enable(int fd, uint64_t addr) {
	uint64_t msr;
	msr = read_msr(fd, addr);	

	//clamp set #1
	putBitField(0, &msr, 1, 16);
	//clamp set #2
	putBitField(0, &msr, 1, 48);
	//putBitField(power_limit, &msr, 15, 32);

	write_msr(fd, addr, msr);

}

//This idea is loop four possible sets of Y and F, and in return to get 
//the time window, then use the set of Y and F that is smaller than but 
//closest to the customized time.
void 
convert_optimal_yf_from_time(uint64_t *Y, uint64_t *F, jdouble custm_time) {
	uint64_t temp_y;
	double time_window = 0.0;
	double delta = 0.0;
	double smal_delta = 5000000000.0;
	int i = 0;
	for(i = 0; i < 4; i++) {
		calc_y(&temp_y, F_arr[i], custm_time);		
		time_window = calc_time_window(temp_y, i);
		delta = custm_time -time_window;
		//printf("Y is: %ld, F is: %d, time window: %f\n", temp_y, i, time_window);
		//printf("delta is: %f\n", delta);
		if(delta > 0 && delta < smal_delta) {
			smal_delta = delta;
			*Y = temp_y;
			*F = i;
		}
	}
}

void
set_pkg_time_window_limit(int fd, uint64_t addr, jdouble custm_time) {
	uint64_t msr;
	uint64_t Y;
	uint64_t F;
	msr = read_msr(fd, addr);	
	//Set the customized time window.
	convert_optimal_yf_from_time(&Y, &F, custm_time);

	//Keep everything else the same.
	//#1 time window bits
	putBitField(F, &msr, 2, 22);
	putBitField(Y, &msr, 5, 17);
	//#2 time window bits
	putBitField(F, &msr, 2, 54);
	putBitField(Y, &msr, 5, 49);

	write_msr(fd, addr, msr);

}

void
set_dram_time_window_limit(int fd, uint64_t addr, jdouble custm_time) {
	uint64_t msr;
	uint64_t Y;
	uint64_t F;
	msr = read_msr(fd, addr);	
	//Set the customized time window.
	convert_optimal_yf_from_time(&Y, &F, custm_time);

	//Keep everything else the same.
	//#1 time window bits
	putBitField(F, &msr, 2, 22);
	putBitField(Y, &msr, 5, 17);

	write_msr(fd, addr, msr);
}

void
set_pkg_power_limit(int fd, uint64_t addr, jdouble custm_power) {
	uint64_t msr;
	msr = read_msr(fd, addr);	
	//Set the customized power.
	uint64_t power_limit = custm_power / rapl_unit.power;
	//Keep everything else the same.
	putBitField(power_limit, &msr, 15, 0);
	putBitField(power_limit, &msr, 15, 32);

	write_msr(fd, addr, msr);

}

void
set_dram_power_limit(int fd, uint64_t addr, jdouble custm_power) {
	uint64_t msr;
	msr = read_msr(fd, addr);	
	//Set the customized power.
	uint64_t power_limit = custm_power / rapl_unit.power;
	//Keep everything else the same.
	putBitField(power_limit, &msr, 15, 0);
//	putBitField(power_limit, &msr, 15, 32);

	write_msr(fd, addr, msr);

}

/*Get unit information to be multiplied with */
void get_msr_unit(rapl_msr_unit *unit_obj, uint64_t data) {

	uint64_t power_bit = extractBitField(data, 4, 0);
	uint64_t energy_bit = extractBitField(data, 5, 8);
	uint64_t time_bit = extractBitField(data, 4, 16);

	unit_obj->power = (1.0 / _2POW(power_bit));	
	unit_obj->energy = (1.0 / _2POW(energy_bit));	
	unit_obj->time = (1.0 / _2POW(time_bit));	
}

/*Get wraparound value in order to prevent nagetive value*/
void 
get_wraparound_energy(double energy_unit) {
	WRAPAROUND_VALUE = 1.0 / energy_unit;
}

void
get_rapl_pkg_parameters(int fd, rapl_msr_unit *unit_obj, rapl_msr_parameter *paras) {
	get_rapl_parameters(fd, MSR_PKG_POWER_INFO, (rapl_msr_unit *)unit_obj, (rapl_msr_parameter *)paras);
}

void
get_rapl_dram_parameters(int fd, rapl_msr_unit *unit_obj, rapl_msr_parameter *paras) {
	get_rapl_parameters(fd, MSR_DRAM_POWER_INFO, (rapl_msr_unit *)unit_obj, (rapl_msr_parameter *)paras);
}



void 
get_rapl_parameters(int fd, uint64_t msr_addr, rapl_msr_unit *unit_obj, rapl_msr_parameter *paras) {
	uint64_t thermal_spec_power;
	uint64_t max_power;
	uint64_t min_power;
	uint64_t max_time_window;
	uint64_t power_info;

	power_info = read_msr(fd, msr_addr);

	thermal_spec_power = extractBitField(power_info, 15, 0);
	min_power = extractBitField(power_info, 15, 16);
	max_power = extractBitField(power_info, 15, 32);
	max_time_window = extractBitField(power_info, 6, 48);


	paras->thermal_spec_power = unit_obj->power * thermal_spec_power;
	paras->min_power = unit_obj->power * min_power;
	paras->max_power = unit_obj->power * max_power;
	paras->max_time_window = unit_obj->time * max_time_window;
}

void 
getPowerSpec(double result[4], rapl_msr_parameter *parameter, int domain) {

	int i;
	/*Test use*/
	/*
	printf("thermal specification power is: %f, minimum power limit is: %f, maximum power limit is: %f, maximum time window is: %f\n", parameters[domain].thermal_spec_power, parameters[domain].min_power, parameters[domain].max_power, parameters[domain].max_time_window);
		*/
	for(i = 0; i < 4; i++) {
		result[0] = parameters[domain].thermal_spec_power;	
		result[1] = parameters[domain].min_power;	
		result[2] = parameters[domain].max_power;	
		result[3] = parameters[domain].max_time_window;	
	}
}
