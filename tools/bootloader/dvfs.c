#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "msr.h"
#include <errno.h>

int *fd;
char *ener_info;
rapl_msr_unit rapl_unit;
rapl_msr_parameter *parameters;


int get_pos_intnum(int value) {
	int num = 1;
	while(value > 9) {
		num++;
		value /= 10;
	}
	
	return num;
}


int check_write_gov(FILE* gov_file, const char *target, int core_id) {
    	size_t data_length;
	size_t data_written;
	char string[25];
	//char* string; 
	//string = (char *)malloc(50 * sizeof(char));
	//memset(string, 0, sizeof(char)*50);
	fseek(gov_file,0,SEEK_SET);
//	int rv = fscanf(gov_file, "%s", string);
//	printf("Before writting governor-----write the governor %s on core id: %d, actual read from the governor file is %s, and fscanf return value is: %d\n, error:%s \n", target, core_id, string, rv, strerror(errno));
//
//	if (strcmp(string, target) != 0) {
		data_length = strlen(target);
		data_written = fwrite(target, 1, data_length, gov_file);

//		fseek(gov_file,0,SEEK_SET);
//		fscanf(gov_file, "%s", string);
//		printf("After writting governor-----write the governor %s, actual read from the governor file is %s, error: %s\n", target, string, strerror(errno));

		if (data_length != data_written) {
			//LOGI("Failed to write to %s: %s", filename, strerror(errno));
			printf("Failed to write %s\n, the core id is: %d, error: %s\n", target, core_id, strerror(errno));
			return 1;
		}
//	}
	return 0;

}

int write_freq_coreId(FILE* scale_file, int freq, int core_id) {
        size_t data_length;
	size_t data_written;

	//printf("write the frequency\n");
	fseek(scale_file,0,SEEK_SET);
//	fscanf(scale_file, "%d", &freq);
//	printf("Before write frequency-----The current frequency is: %d\n", freq);


	data_length = get_pos_intnum(freq);
	data_written = fprintf(scale_file, "%d", freq);  //For integer

//	fseek(scale_file,0,SEEK_SET);
//	fscanf(scale_file, "%d", &freq);
//	printf("After write frequency-----Frequency just wrote to the system: %d, error: %s\n", freq, strerror(errno));

	if (data_length != data_written) {
		//LOGI("Failed to write to %s: %s", filename, strerror(errno));
		printf("Failed to write %d to scaling_governor file, data written: %d, the core id is: %d, error: %s \n", freq, data_written, core_id, strerror(errno));
		return 1;
	}
	return 0;
}
