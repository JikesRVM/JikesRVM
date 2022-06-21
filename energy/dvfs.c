#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int check_write_gov(int cores, char govFile[cores][60], const char *target) {
	int i;
	int rc;
	FILE *f[cores];
    size_t data_length, data_written;
	char string[cores][25];

	for (i = 0; i < cores; i++) {
		f[i] = fopen(govFile[i], "r");
		if (f[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s", govFile[i]);
			return 1;
		}

		fscanf(f[i], "%s", string[i]);

		rc = fclose(f[i]);
		if (rc != 0) {
			//LOGI("Failed to close %s: %s", filename, strerror(rc));
			printf("Failed to close %s", govFile[i]);
			return 1;
		}

		if (strcmp(string[i], target) != 0) {
			//Write govenor
			f[i] = fopen(govFile[i], "w");
			if (f[i] == NULL) {
				//LOGI("Failed to open %s: %s", govFile, strerror(errno));
				printf("Failed to open %s", govFile[i]);
				return 1;
			}

			data_length = strlen(target);
			data_written = fwrite(target, 1, data_length, f[i]);
			if (data_length != data_written) {
				//LOGI("Failed to write to %s: %s", filename, strerror(errno));
				printf("Failed to write %s", target);
				return 1;
			}

			rc = fclose(f[i]);
			if (rc != 0) {
				//LOGI("Failed to close %s: %s", filename, strerror(rc));
				printf("Failed to close %s", govFile[i]);
				return 1;
			}
		}
	}
	return 0;

}

write_freq_all_cores(int cores, char filename[][60], const char *cur_freq, const char *scal_freq, int freq) {
	int i;
	FILE *f[cores];
    int rc;
    size_t data_length, data_written;
	int cpu_freq[cores];
	int scal_cpufreq[cores];

	for(i = 0; i < cores; i++) { 
		f[i] = fopen(filename[i], "w");
		if (f[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s\n", filename[i]);
			return 1;
		}

	//    data_length = strlen(freq);
	 //   data_written = fwrite(freq, 1, data_length, f); //For binary code
		data_length = get_pos_intnum(freq);
		data_written = fprintf(f[i], "%d", freq);  //For integer

		if (data_length != data_written) {
			//LOGI("Failed to write to %s: %s", filename, strerror(errno));
			printf("Failed to write %s\n", filename[i]);
			return 1;
		}

		rc = fclose(f[i]);
		if (rc != 0) {
			//LOGI("Failed to close %s: %s", filename, strerror(rc));
			printf("Failed to close %s\n", filename[i]);
			return 1;
		}
		f[i] = fopen(cur_freq, "r");
		if (f[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s\n", cur_freq);
			return 1;
		}
		
		fscanf(f[i], "%d", &cpu_freq[i]);
		rc= fclose(f[i]);
		f[i] = fopen(scal_freq, "r");
		if (f[i] == NULL) {
			//LOGI("Failed to open %s: %s", filename, strerror(errno));
			printf("Failed to open %s\n", scal_freq);
			return 1;
		}
		fscanf(f[i], "%d", &scal_cpufreq[i]);
		rc= fclose(f[i]);
	}

}
