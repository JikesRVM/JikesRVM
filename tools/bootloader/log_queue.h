#include <unistd.h> 
#include <sys/types.h>
#include <sys/syscall.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct {
	int thread_id;
	long long* timestamps;
	long int* cmdids;
	double *profile_attrs;
	long log_no;
	int* core_ids;
} thread_stats;


extern __thread long log_no;
extern __thread long allocated;
extern __thread long long *timestamps;
extern __thread int *cmids;
extern __thread double *profile_attrs;

extern int allocated_g;
extern thread_stats** thread_stats_g;
extern int number_of_threads;
extern int no_profile_attrs;

void handle_error(int);
void register_thread_stat();
void check_malloc();
void inc_number_of_threads();
void init_g();
void allocate_stats();
void print_counters_g();
void log(long,int,double*);
void init_log_queue(int no_events);
