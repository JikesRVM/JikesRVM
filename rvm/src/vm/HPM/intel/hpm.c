/*
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 *
 * @author Peter F. Sweeney
 * @modified Peter F. Sweeney 11/25/2003 to support PAPI IA_32 on linux by
 * incorporating contribution from Kien Le, Tuan Phan and Stephen Smaldone at Rutgers University.
 */

/*
 * Description: 
 * This file provides access to the hardware performance monitor (HPM)
 * facilities on a microarchitectures via calls through PAPI.
 * These functions are used by Jikes RVM both through sysCalls and JNI.
 * Before the JNI environment is initialized, these functions must be accessed
 * via sysCalls.
 *
 * *** What follows is out-of-date and must be updated ***
 * The PAPI facilities provide multiple APIs to access the HPM counters.
 * Here we use only two of them: mythread (thread context) and 
 * mygroup (thread group context).
 * The mythread API creates a context for each kernel thread.  
 * The mygroup API creates a context for a group of kernel threads all created by 
 * a root thread.
 * The "init" procedure must be invoked before all others.
 * For each context the following functions are defined 
 * set_program, get_program, delete_program, stop, reset, and get functions.
 * 
 */
 
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hpm.h"
#define NEED_EXIT_STATUS_CODES
#include "InterfaceDeclarations.h"

#if (!defined COUNT_NOTHING)
#define COUNT_NOTHING 0
#endif
static int default_event = 0x8000003c;	// PAPI_TOT_CYC: total cycles
static int n_counters = 0;		// number of hardware counters
static long long *values;
// local data structure to keep track of event set's contents
struct program_data{
  int* events;
  int n_events;
  char **short_names;
};

// Assume only one event set valid at a time
static int EventSet = PAPI_NULL;

static PAPI_option_t options;
static PAPI_option_t get_options;

const PAPI_hw_info_t *hwinfo = NULL;	/* machine specific services */
struct program_data get_program = {0,0};
struct program_data set_program = {0,0};
static long long* mydata;
static int threadapi = 0;

static int init_enabled      = 0;	/* 1 after hpm_init is called and MyInfo initialized */
static int get_data_enabled  = 0;	/* 1 after call to get_data */
static int set_event_enabled = 0;	/* 1 after set_program is called, 0 after hpm_delete_program is called */

static char errstring[PAPI_MAX_STR_LEN];

static int debug = 0;
/*
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * This routine creates an initial PAPI event set and gets the hardware information.
 * Called once per machine.
 * Sets fields of set_program to default values.
 * Sets n_counters.
 */
int
hpm_init(int my_filter) 
{
  int rc, i;

  if(debug>1)fprintf(stdout,"hpm.hpm_init(%d)\n", my_filter);

  if (rc = PAPI_library_init(PAPI_VER_CURRENT) != PAPI_VER_CURRENT) {
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr, "***hpm.hpm_init() PAPI_library_init failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }

  if(!memset(&options,0x0,sizeof(options))){
    perror("***hpm.hpm_init() memset failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if((hwinfo = PAPI_get_hardware_info()) == NULL){
    perror("***hpm.hpm_init() PAPI_get_hardware_info() failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }

  if ((n_counters = PAPI_get_opt(PAPI_GET_MAX_HWCTRS,NULL)) <= 0) {
    fprintf(stderr,"***hpm_int() PAPI_get_opt(PAPI_GET_MAX_HWCTRS) failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(debug>=1)printf("hpm.hpm_init() %d = PAPI_num_counters()\n",n_counters);


  // this could be moved to set_program!
  if ( (rc = PAPI_create_eventset(&EventSet) ) != PAPI_OK ){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_init() PAPI_create_eventset failed: $s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(debug>=1)printf("hpm.hpm_init() eventSet = %d\n",EventSet);

  values = (long long*) malloc(sizeof(long long)*n_counters);
  if(!(set_program.events =(int *) malloc(sizeof(int)*n_counters))){
    perror("***hpm.hpm_init() malloc set_program.events failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(!(set_program.short_names = (char **)malloc(sizeof(char *)*n_counters))){
    perror("***hpm.hpm_init() malloc set_program.short_names failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(!(mydata =(long long *) malloc(sizeof(long long)*n_counters))){
    perror("***hpm.hpm_init() malloc mydata failed!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }

  // Set counter to count nothing
  for (i = 0; i < n_counters; i++) {
    set_program.events[i] = COUNT_NOTHING;
    set_program.short_names[i] = NULL;
  }

  // Domain definition is mode and is handled by command line options
  //  options.domain.domain = PAPI_DOM_USER;
  if(debug>=1)printf("hpm.hpm_init() set options.domain.eventset = EventSet\n");
  options.domain.eventset = EventSet; 

  init_enabled = 1;
  if(debug>=1)printf("hpm.hpm_init() init_enabled %d()\n",init_enabled);

  return(OK_CODE);
}

/*
 * Return processor name.  Assume that hpm_init is called.
 */
char *
hpm_get_processor_name()
{

  if(debug>1) printf("hpm.hpm_get_processor_name()\n");
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_get_processor_name() called before hpm_init()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(hwinfo == NULL) {
    fprintf(stderr,"***hpm.hpm_get_processor_name() PAPI_hw_info_t is NULL!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  // dump hardware information
  if (debug>=1) {
    fprintf(stdout,"hardware information:\n\tvendor %d:%s\n\tmodel  %d:%s\n\tCPUs %d, nodes %d, total CPUs %d, mhz %f\n",hwinfo->vendor,hwinfo->vendor_string,hwinfo->model,hwinfo->model_string,hwinfo->ncpu,hwinfo->nnodes,hwinfo->totalcpus,hwinfo->mhz);
  }
  return (char *)&hwinfo->model_string;
}

/*
 * return little-endian
 */
int
hpm_is_big_endian() {
  return 1;
}
/*
 * Is this a Power PC Power4 machine?
 */
int 
hpm_isPower4() 
{
  return 0;
}
/*
 * Is this a Power PC Power3-II machine?
 */
int 
hpm_isPower3II() 
{
  return 0;
}
/*
 * Is this a Power PC Power3 machine?
 */
int 
hpm_isPower3() 
{
  return 0;
}
/*
 * Is this a Power PC RS64-III machine?
 */
int 
hpm_isRS64III() 
{
  return 0;
}
/*
 * Is this a Power PC RS64-III machine?
 */
int 
hpm_is604e() 
{
  return 0;
}

/***********************************************************************************
 * CONSTRAINT: the following functions may be called only after hpm_init 
 * has been called and they accesses the static structure set_program
 ************************************************************************************/

/*
 * How many counters available?
 * Must be called after hpm_init!
 * Expected to be used in C code for correctness.
 */
int 
hpm_get_number_of_counters() 
{
  if(debug>1)printf("hpm.hpm_number_of_counters() returns %d\n", n_counters);
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_get_number_of_counters() called before hpm_init()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  return n_counters;
}
/*
 * How many counters available?
 * Must be called after hpm_set_program!
 * Expected to be used in Java code.
 */
int 
hpm_get_number_of_events() 
{
  if(set_event_enabled==0) {
    fprintf(stderr,"***hpm.hpm_get_number_of_events() called before set_program()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(debug>1)printf("hpm.hpm_number_of_events() returns %d\n", set_program.n_events);
  return set_program.n_events;
}

/***********************************************************************************
 * CONSTRAINT: the following functions may be called only after hpm_init 
 * has been called and before hpm_set_program_mythread has been called.
 ************************************************************************************/

static void set_event(int, int);
/*
 * This routine provides support to set the first four events to be counted.
 * This routine is called to set, in local variable set_program, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_program_mythread is called.
 */
int
hpm_set_event(int event1, int event2, int event3, int event4)
{
  int event = 0;
  int rc;
  char *name = NULL;

  if(debug>=1){fprintf(stdout,"hpm.hpm_set_event(%d,%d,%d,%d)\n",event1,event2,event3,event4);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_event() called before hpm_init()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if (n_counters > set_program.n_events && event1 >= 0) {
    set_event(event1, 1);
  }
  if (n_counters > set_program.n_events && event2 >= 0) {
    set_event(event2, 2);
  }

  if (n_counters > set_program.n_events && event3 >= 0) {
    set_event(event3, 3);
  }
  if (n_counters > set_program.n_events && event4 >= 0) {
    set_event(event4, 4);
  }
  return(OK_CODE);
}

/*
 * This routine allows more than 4 counter
 *
 */
int
hpm_set_event_X(int event5, int event6, int event7, int event8)
{
 
  if(debug>=1){fprintf(stdout,"hpm.hpm_set_event_X(%d,%d,%d,%d)\n",event5,event6,event7,event8);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_event_X() called before hpm_init()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if (n_counters > set_program.n_events && event5 >= 0) {
    set_event(event5, 5);
  }
  if (n_counters > set_program.n_events && event6 >= 0) {
    set_event(event6, 6);
  }
  if (n_counters > set_program.n_events && event7 >= 0) {
    set_event(event7, 7);
  }
  if (n_counters > set_program.n_events && event8 >= 0) {
    set_event(event8, 8);
  }
  return(OK_CODE);
}

/*
 * Do all the work of setting an event.
 */
static void
set_event(int event_id, int event_number) 
{
  int rc;
  char *name = NULL;

  int event = event_id|0x80000000;
  if(rc = PAPI_query_event(event) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_set_event%d(%d) PAPI_query_event(%x) failed: %s!***\n",
            event_number,event_id,event,errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  } else {
    if(debug>=1) printf("hpm.hpm_set_event1 = %x from %d\n",event,event_id);
    set_program.events[set_program.n_events]=event;
    name = (char *)malloc(sizeof(char)*PAPI_MAX_STR_LEN);
    if((rc = PAPI_event_code_to_name(event, name)) != PAPI_OK) {
      PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
      fprintf(stderr,
              "***hpm.set_event(%d, %d) PAPI_event_code_to_name(%x) failed: %s!***\n",
              event_id, event_number, event, errstring);
    } else {
      set_program.short_names[set_program.n_events++]=name;
    }
  }
}

/*
 * Set the mode.
 * The result of calling this routine only takes effect after
 * hpm_set_program_mythread is called.
 * The valid values for mode are defined in hpm.h, and are translated to
 * valid PAPI domain values.
 */
int 
hpm_set_mode(int mode) 
{
  int domain = PAPI_DOM_MIN;

  if(debug>1) printf("hpm.hpm_set_mode(%d)",mode);
  if ( mode & MODE_KERNEL ) {
    if(debug>=1)fprintf(stdout," PAPI_DOM_KERNEL");
    domain |= PAPI_DOM_KERNEL;
  }
  if ( mode & MODE_USER ) {
    if(debug>=1)fprintf(stdout," PAPI_DOM_USER");
    domain |= PAPI_DOM_USER;
  }
  if ( mode & MODE_ALL ) {
    if(debug>=1)fprintf(stdout," PAPI_DOM_ALL");
    domain |= PAPI_DOM_ALL;
  }
  if ( mode & MODE_IS_GROUP ) {
    fprintf(stderr,"\n***hpm.hpm_set_mode(%d) MODE_IS_GROUP %d does not apply!***\n",mode, MODE_IS_GROUP);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if ( mode & MODE_PROCTREE ) {
    fprintf(stderr,"\n***hpm.hpm_set_mode(%d) MODE_PROCTREE %d does not apply!***\n",mode, MODE_PROCTREE);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if ( mode & MODE_COUNT ) {
    fprintf(stderr,"\n***hpm.hpm_set_mode(%d) MODE_COUNT %d does not apply!***\n",mode, MODE_PROCTREE);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(debug>=1){ fprintf(stdout," has domain %d\n", domain); fflush(stdout); }
  options.domain.domain = domain;

  return (OK_CODE);
}
/*
 * After init is called, and events and modes are set in the local variable set_program, 
 * call this routine to set HPM settings.
 * May not make two consecutive calls to this routine without an intervening call to
 * hpm_delete_settings.
 */
/*
 * This supports the mythread API.
 */
int
hpm_set_program_mythread()
{
 int rc;
  if(debug>=1){fprintf(stdout,"hpm.hpm_set_program_mythread()\n");fflush(stdout);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_program_mythread() called before hpm_init()!***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if((rc = PAPI_add_events(&EventSet,set_program.events,set_program.n_events)) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_set_program_mythread() PAPI_add_events failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if(debug>=1)printf("hpm.hpm_set_program_mythread() print options: event set %d, domain %d\n",
                     options.domain.eventset,options.domain.domain);

  set_event_enabled = 1;
  return (OK_CODE);
  
}
/*
 * This supports the mygroup API.
 */
int 
hpm_set_program_mygroup() 
{
  if(debug>0) printf("hpm.hpm_set_program_mygroup() not implemented \n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  set_event_enabled = 1;
  return (OK_CODE);
}

/***********************************************************************************
 * CONSTRAINT: the following functions may be called only after 
 * hpm_set_program_mythread has been called.
 ************************************************************************************/

/*
 * Get counter's event
 * Constraint: set_program has been initialized by a call to set_program.
 */
int
hpm_get_event_id(int counter) 
{
  int evid;
  if(debug>=1) printf("hpm.hpm_get_event_id(%d)",counter);
  if (set_program.n_events <= counter) {
    fprintf(stderr,
            "\n***hpm.hpm_get_event_id(%d) called with counter value %d > number of events %d!***\n",
	    counter, counter, set_program.n_events);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  evid = set_program.events[counter];  // event number
  if(evid==0){
	//printf("hpm.hpm_get_event_id(%d) evei is 0 %x\n",counter,evid);
	//fflush(stdout);
	 return evid;
  }
  if(debug>=1) printf(" = X%x",evid);
  evid =  ((evid&0x0fffffff));
  if(debug>=1) printf(", was %d\n",evid);

  return evid;
}

/*
 * Get short description name for the event a counter is set to.
 * Constraint: set_program has been initialized by a call to set_program and init.
 */
char *
hpm_get_event_short_name(int counter) 
{
  int evid, rc;
  char * name = NULL;
  if (counter >= set_program.n_events) {
    fprintf(stderr,"***hpm.hpm_get_event_short_name(%d) called with counter value %d > number of events %d!***\n",
	    counter, counter, set_program.n_events);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  if (counter >= set_program.n_events) {
    fprintf(stderr,"***hpm.hpm_get_event_short_name(%d) counter value %d > set_program.n_events %d!***\n",
	    counter, counter, set_program.n_events);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }

  name = set_program.short_names[counter]; 
  if(debug>=1)fprintf(stdout,"hpm_get_event_short_name(%d) evid %x, name %s\n",counter,evid,name);

  return name;
}

/*
 * After hpm_set_program_mythread is called, this routine unsets program settings
 * making it possible to call hpm_set_event, hpm_set_eventX, hpm_set_mode and then
 * call set_program again.
 */
/*
 * This supports mythread API.
 */
int
hpm_delete_program_mythread()
{
  int rc;
  if(debug>1) printf("hpm.hpm_delete_program_mythread()\n");
  if((rc = PAPI_cleanup_eventset(&EventSet)) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_delete_program_mythread() PAPI_cleanup_eventset failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  set_event_enabled = 0;
  return (OK_CODE);
}
/*
 * This supports mygroup API.
 */
int 
hpm_delete_program_mygroup() 
{
  printf("hpm.hpm_delete_program_mygroup() not implemented\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  set_event_enabled = 0;
  return OK_CODE;
}

/*
 * This routine retrieves the HPM settings into the local variable set_program.
 * May be called only after a hpm_set_program_mythread() is called.
 */
/*
 * This supports mythread API.
 */
int 
hpm_get_program_mythread() 
{
  int rc;
  if(debug>0) printf("hpm.hpm_get_program_mythread()\n");
  if((rc = PAPI_get_opt(PAPI_GET_DOMAIN,&get_options)) != PAPI_OK){
      PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
      fprintf(stderr,"***hpm.hpm_get_program_mythread() PAPI_get_opt failed: %s!***\n",errstring);
      exit(EXIT_STATUS_HPM_TROUBLE);
  }
  return (OK_CODE);
 
}
/*
 * This supports mygroup API.
 */
int 
hpm_get_program_mygroup() 
{
  fprintf(stderr,"hpm.hpm_get_program_mygroup() not implemented");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return (OK_CODE);
}

/*
 * Starts hpm counting for mythread. 
 */
int
hpm_start_mythread()
{

  int rc;
  if(debug>=1)printf("hpm.hpm_start_mythread()\n");
  if((rc = PAPI_start(EventSet)) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_start_mythread() PAPI_start failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  get_data_enabled = 0;
  return(OK_CODE);
}
/*
 * Starts hpm counting for mygroup.
 * The group includes the calling thread and all the decendent kernel threads.
 */
int 
hpm_start_mygroup() 
{
  fprintf(stderr,"***hpm.hpm_start_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return OK_CODE;
}

/*
 * Stops hpm counting for mythread.
 * Assumes that hpm_start completed correctly.
 * After successful completion, counters no longer enabled.
 */
int 
hpm_stop_mythread() {

  int rc;

  if(debug>=1)printf("hpm.hpm_stop_mythread()\n");
  if ( (rc = PAPI_stop(EventSet,values)) != PAPI_OK) {
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_stop_mythread() PAPI_stop failed: %s!***\n",errstring);
    //    exit(EXIT_STATUS_HPM_TROUBLE);
    return(ERROR_CODE);
  }
  return OK_CODE;

}
/*
 * Stops hpm counting for mygroup.
 * The group includes the calling thread and all the decendent kernel threads.
 * After successful completion, counters no longer enabled.
 * Assumes that counters were started correctly.
 */
int 
hpm_stop_mygroup() 
{
  fprintf(stdout,"***hpm.hpm_stop_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return OK_CODE;
}

/*
 * This routine resets HPM counters to zero for mythread.
 * Do the counters have to be stopped before they can be reset?
 * Only if want all the counters to appear to be reset simultaneously.
 */
int
hpm_reset_mythread()
{
  int rc;

  if(debug>=2)fprintf(stdout,"hpm.hpm_reset_mythread()\n");
  if ( (rc = PAPI_reset(EventSet)) != PAPI_OK) {
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_reset_mythread() PAPI_reset failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
	return OK_CODE;

}
/*
 * This routine resets HPM counters to zero for mygroup.
 * Do the counters have to be stopped before they can be reset?
 */
int
hpm_reset_mygroup()
{
  fprintf(stderr,"***hpm.hpm_reset_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return(OK_CODE);
}
/*
 * Read the values of the counters
 * Cache counter values.
 * Return the number of counters.
 */
int
hpm_get_mythread()
{
  int rc;

  if(debug>=1) fprintf(stdout,"hpm.hpm_get_mythread()\n");
  if((rc = PAPI_read(EventSet,mydata)) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_get_mythread() PAPI_read failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  get_data_enabled = 1;
  return n_counters; 
}
int
hpm_get_mygroup()
{
  fprintf(stderr,"***hpm.hpm_get_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return 0; 
}
int
hpm_get_counters()
{
  int rc;

  if(debug>=1) fprintf(stdout,"hpm.hpm_get_counters()\n");
  if((rc = PAPI_read(EventSet,mydata)) != PAPI_OK){
    PAPI_perror(rc, errstring, PAPI_MAX_STR_LEN);
    fprintf(stderr,"***hpm.hpm_get_counters() PAPI_read failed: %s!***\n",errstring);
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  get_data_enabled = 1;
  return n_counters;
}


/*
 * Read an HPM counter value.
 * Specify counter in range [1..maxCounters].
 */
long long
hpm_get_counter_mythread(int counter)
{
  int rc;

  if(debug>=1)fprintf(stdout,"hpm.hpm_get_counter_mythread(%d)\n",counter);
  long long value;
  if ( (counter < 0) || (counter > set_program.n_events) ) {
     fprintf(stderr, "\n***hpm.hpm_get_counter(%d): Invalid counter!***\n",counter);
     exit(EXIT_STATUS_HPM_TROUBLE);
  }
  /* eliminate caching for time experiment */
  if (get_data_enabled == 0) { 
    hpm_get_counters();
  }
  value = mydata[counter-1];
  if(debug>=2)fprintf(stdout," returns %d%d\n",value);
  return value;
}

/*
 * Read an HPM counter value.
 * Specify counter in range [1..maxCounters].
 */
long long
hpm_get_counter_mygroup(int counter)
{
  fprintf(stderr,"***hpm.hpm_get_counter_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return 0;
}

/*
 * Test interface to HPM.
 */
static int test_value = 0;
int
hpm_test() 
{
  return test_value++;
}

/**
 * List all events associated with each counter.
 */
void
hpm_list_all_events()
{
  int i;
  const PAPI_preset_info_t *info;
  if(debug>1);
  if (debug>=0)printf("hpm.hpm_list_all_events()\n ");
  if ((info = PAPI_query_all_events_verbose()) == NULL) {
    ;
  }
  printf("Name\t\tCode\t\t\tDescription\n");
  for(i=0;i,i<PAPI_MAX_PRESET_EVENTS;i++){
    if(info[i].avail){
      printf("%d: %s\t0x%x\t%s\n",i,info[i].event_name,info[i].event_code,info[i].event_descr);
    }
  }
  //  printf("ADD 1 TO THE VALUE OF CODE!!!!!\n");
  
}

/* 
 * Function to convert filter of character form (entered from command line, 
 * e.g. -t v,u) to a numeric form.
 */
void
hpm_print_data(long long *data)
{
  int j;
  
  for (j=0; j<set_program.n_events; j++) 
    fprintf(stdout, "%-8lld  ", data[j]); 
  fprintf(stdout, "\n");
}


/*
 * Print hardware performance monitors values for mythread.
 */
int 
hpm_print_mythread() 
{
  PAPI_read(EventSet,values);
  /* print the results */
  if(hpm_get_program_mythread()==ERROR_CODE){
    perror("***hpm.hpm_print_mythread() hpm_get_program_mythread failed!  Never expect to be here***\n");
    exit(EXIT_STATUS_HPM_TROUBLE);
  }
  hpm_print_header(get_options.domain.domain, 0);
  //print_header(options.domain.domain, 0);
  print_events(set_program.events);
  //printf("d cache1:%ld\n",values[0]);printf("d cache1:%ld\n",values[1]);
  hpm_print_data(values);
  return(OK_CODE);
}
/*
 * Print hardware performance monitors values for mygroup.
 */
int 
hpm_print_mygroup() 
{
  fprintf(stderr,"***hpm.hpm_print_mygroup() not implemented!***\n");
  exit(EXIT_STATUS_HPM_TROUBLE);
  return(OK_CODE);	
}

void
hpm_print_header(int mode, int threadapi)
{
  fprintf(stderr,"*** Configuration :\n"
          "Mode= ");
  if ( mode==PAPI_DOM_ALL )
    fprintf(stderr, "kernel and user");
  else if (mode==PAPI_DOM_USER)
    fprintf(stderr, "user only");
  else if (mode==PAPI_DOM_KERNEL)
    fprintf(stderr, "kernel only");
  fprintf(stderr, ";\n");
}

/*
 * Return list of event for a group
 * Required for Power4
 * @param group_num   group number
 */
int*
hpm_get_group_event_list(int group_num)
{
  fprintf(stderr,"***hpm.hpm_get_group_event_list(%d) not implemented!***\n",group_num);
  exit(EXIT_STATUS_HPM_TROUBLE);
  return NULL;
}

/**
 * List each event that is selected for a counter.
 */
void
hpm_list_selected_events()
{
  int i;
  if(debug>=0)printf("hpm.hpm_list_selected_events()\n");
  for(i=0;i<set_program.n_events;i++){
    printf("counter %d:	%s\n",(i+1),hpm_get_event_short_name(set_program.events[i]));
    
  }
}

/*
 * Required for Power4
 */
void 
hpm_print_group_events(int group_num)
{
  fprintf(stderr,"hpm.hpm_print_group_events(%d) not implemeted~***\n",group_num);
  exit(EXIT_STATUS_HPM_TROUBLE);
}

int
print_events(int *ev_list)
{
/* for each event (pmc), print short name, accu */
  int	pmcid;		/* which pmc */
  int	evid;		/* event id */
  int	len;
  char name[100];
  
  /* go through evs, get sname from table of events, print it */	
  for (pmcid = 0; pmcid < set_program.n_events; pmcid++) {
    fprintf(stdout,"Counter %2d, ", pmcid+1); 
    /* get the event id from the list */
    evid = set_program.events[pmcid];
    if ( (pmcid>=set_program.n_events))
      fprintf(stdout,"event : No event\n");
    else {
      /* find pointer to the event */
      PAPI_event_code_to_name(evid,name);	
      
      fprintf(stdout,"event %2x: %s\n", evid,name);
    }
  }
  
  fprintf(stdout,"\n*** Results :\n");
  
  for (pmcid=0; pmcid<set_program.n_events; pmcid++) {
      fprintf(stdout, "PMC%2d     ", pmcid+1);
  }
  fprintf(stdout, "\n");

  for (pmcid=0; pmcid<set_program.n_events; pmcid++) {
      fprintf(stdout, "=====     ");
  }
  fprintf(stdout, "\n");

  return OK_CODE;
}
