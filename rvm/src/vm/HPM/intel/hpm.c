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
 * facilities on PowerPC architectures, which is used by Jikes RVM both
 * through sysCalls and JNI.
 * Before the JNI environment is initialized, this functionality must be accessed
 * via sysCalls.
 *
 * The PowerPC HPM facilities provide multiple APIs to access the HPM counters.
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

#if (!defined COUNT_NOTHING)
#define COUNT_NOTHING 0
#endif
long long *values;
struct program_data{
	int* events;
	int number_of_events;
};
int EventSet;
PAPI_option_t options;
PAPI_option_t get_options;

const PAPI_hw_info_t *hwinfo = NULL;	/* machine specific services */
struct program_data get_program;
struct program_data set_program;
long long* mydata;
int mxpmcs;

int threadapi = 0;

int init_enabled      = 0;	/* 1 after hpm_init is called and MyInfo initialized */
int get_data_enabled  = 0;	/* 1 after call to get_data */
int set_event_enabled = 0;	/* 1 after hpm_set_program_mythread is called, 0 after hpm_delete_program_mythread is called */


int debug = 0;
/*
 * This routine replicates sys_hpm.sys_hpm_init() functionality to provide JNI
 * access when not used through Jikes RVM, but by application code for example.
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * If filter == PM_UNVERIFIED, accept anything.  Other alternatives are:
 *	PM_VERIFIED, PM_CAVEAT
 * This routine sets the Myinfo data structure, which can then be referenced later.
 * Called once per machine.
 * Sets fields of setprog to default values
 */
int
hpm_init(int my_filter) 
{
  int rc, i;

  if(debug>1)fprintf(stdout,"hpm.hpm_init(%d)\n", my_filter);

  if (PAPI_library_init(PAPI_VER_CURRENT)!=PAPI_VER_CURRENT) {
    perror("***hpm.hpm_init() PAPI_library_init failed!***\n");
    exit(ERROR_CODE);
  }

  /*
   *should i create the eventset here, or should i wait until 
   *set_settings?
   *do we really need set_settings, and delete_settings if we use PAPI???
   */
  if ( (rc = PAPI_create_eventset(&EventSet) ) != PAPI_OK ){
    perror("***hpm.hpm_init() PAPI_create_event_set failed!***\n");
    exit(ERROR_CODE);
  }
  if(!memset(&options,0x0,sizeof(options))){
    perror("***hpm.hpm_init() memset failed!***\n");
    exit(ERROR_CODE);
  }
  if((hwinfo=PAPI_get_hardware_info())==NULL){
    perror("***hpm.hpm_init() PAPI_get_hardware_info() failed!***\n");
    exit(ERROR_CODE);
  }

  mxpmcs = PAPI_num_counters();

  values = (long long*) malloc(sizeof(long long)*mxpmcs);
  if(!(set_program.events =(int *) malloc(sizeof(int)*mxpmcs))){
    perror("***hpm.hpm_init() malloc set_program.events failed!***\n");
    exit(ERROR_CODE);
  }
  for(i=0;i<mxpmcs;i++) set_program.events[i]=0;
  if(!(mydata =(long long *) malloc(sizeof(long long)*mxpmcs))){
    perror("***hpm.hpm_init() malloc mydata failed!***\n");
    exit(ERROR_CODE);
  }

  init_enabled = 1;
  // Set counter to count nothing
  for (i = 0; i < mxpmcs; i++) {
    set_program.events[i] = COUNT_NOTHING;
  }

  options.domain.domain = PAPI_DOM_USER;
  options.domain.eventset = EventSet; 
  

  return(OK_CODE);
}

/***********************************************************************************
 * CONSTRAINT: the following functions may be called only after hpm_init 
 * has been called and they accesses the static structure Myinfo
 ************************************************************************************/

/*
 * How many counters available?
 */
int 
hpm_get_number_of_counters() 
{
  if(debug>1)printf("hpm.hpm_number_of_counters()\n");
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_number_of_counters() called before hpm_init()!***");
    exit(-1);
  }
  return mxpmcs;
}

/*
 * Return processor name.  Assume that hpm_init is called.
 */
char *
hpm_get_processor_name()
{

  char * retval =(char *) malloc(sizeof(char)*100);
  if(debug>1) printf("hpm.hpm_get_processor_name()\n");
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_get_processor_name() called before hpm_init()!***");
    exit(-1);
  }
  strcpy(retval,hwinfo->model_string);
  return retval;
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
 * has been called and before hpm_set_program_mythread has been called.
 ************************************************************************************/

/*
 * This routine replicates sys_hpm.sys_hpm_init() functionality to provide JNI
 * access when not used by Jikes RVM, but by application code for example.
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_program_mythread is called.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
int
hpm_set_event(int event1, int event2, int event3, int event4)
{
  //int i;
  int rc;
 
  /*this is due to the imcompetent between java int and c int
   *so in java we specify with int but for the value of the event
   *in papi is not enough the int in java is not enough, instead we'll
   *need a long ('ll try to change it later)
   */
  int e1=(event1-1)|0x80000000;
  int e2=(event2-1)|0x80000000;
  int e3=(event3-1)|0x80000000;
  int e4=(event4-1)|0x80000000;

  if(debug>=1){fprintf(stdout,"hpm.hpm_set_event(%d,%d,%d,%d)\n",e1,e2,e3,e4);fflush(stdout);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_event() called before hpm_init()!***");
    exit(-1);
  }
  /* TODO: map enumeration to mnemonic and 
   * index mnemonic into Myinfo structure to 
   * find counter, event pair.
   */
  if (mxpmcs > 0&&event1!=0) {
    if(PAPI_query_event(e1)!=PAPI_OK){
	perror("***hpm.hpm_set_event() PAPI_query_event(e1) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[0]=e1;
    set_program.number_of_events++;
  }
  if (mxpmcs > 1&&event2!=0) {
    if(PAPI_query_event(e2)!=PAPI_OK){
	perror("***hpm.hpm_set_event() PAPI_query_event(e2) failed!***");
        exit(ERROR_CODE);
    }
    set_program.events[1]=e2;
    set_program.number_of_events++;
  }

  if (mxpmcs > 2&&event3!=0) {
    if(PAPI_query_event(e3)!=PAPI_OK){
	perror("***hpm.hpm_set_event() PAPI_query_event(e3) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[2]=e3;
    set_program.number_of_events++;
  }
  if (mxpmcs > 3&&event4!=0) {
    if(PAPI_query_event(e4)!=PAPI_OK){
	perror("***hpm.hpm_set_event() PAPI_query_event(e4) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[3]=e4;
    set_program.number_of_events++;
  }
  set_event_enabled = 1;
  return(OK_CODE);
}
int
hpm_set_event_X(int event1, int event2, int event3, int event4)
{
  //int i;
  int rc;
 
  /*this is due to the imcompetent between java int and c int
   *so in java we specify with int but for the value of the event
   *in papi is not enough the int in java is not enough, instead we'll
   *need a long ('ll try to change it later)
   */
  int e1=(event1-1)|0x80000000;
  int e2=(event2-1)|0x80000000;
  int e3=(event3-1)|0x80000000;
  int e4=(event4-1)|0x80000000;

  if(debug>=1){fprintf(stdout,"hpm.hpm_set_event(%d,%d,%d,%d)\n",e1,e2,e3,e4);fflush(stdout);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_event() called before hpm_init()!***");
    exit(-1);
  }
  /* TODO: map enumeration to mnemonic and 
   * index mnemonic into Myinfo structure to 
   * find counter, event pair.
   */
  if (mxpmcs > 4&&event1!=0) {
    if(PAPI_query_event(e1)!=PAPI_OK){
	perror("***hpm.hpm_set_event_X() PAPI_query_event(e1) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[4]=e1;
    set_program.number_of_events++;
  }
  if (mxpmcs > 5&&event2!=0) {
    if(PAPI_query_event(e2)!=PAPI_OK){
	perror("***hpm.hpm_set_event_X() PAPI_query_event(e2) failed!***");
        exit(ERROR_CODE);
    }
    set_program.events[5]=e2;
    set_program.number_of_events++;
  }

  if (mxpmcs > 6&&event3!=0) {
    if(PAPI_query_event(e3)!=PAPI_OK){
	perror("***hpm.hpm_set_event_X() PAPI_query_event(e3) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[6]=e3;
    set_program.number_of_events++;
  }
  if (mxpmcs > 7&&event4!=0) {
    if(PAPI_query_event(e4)!=PAPI_OK){
	perror("***hpm.hpm_set_event_X() PAPI_query_event(e4) failed!***");
	exit(ERROR_CODE);
    }
    set_program.events[7]=e4;
    set_program.number_of_events++;
  }
  set_event_enabled = 1;
  return(OK_CODE);
}
/*
 * This routine replicates sys_hpm.sys_hpm_init() functionality to provide JNI
 * access when not used by Jikes RVM, but by application code for example.
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_program_mythread is called.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
/*int
hpm_set_event_X(int e5, int e6, int e7, int e8)
{
  if(debug>2) printf("calling set event 2\n");
  return OK_CODE;
}*/

/*
 * Set the mode, in local variable setprog.
 * The result of calling this routine only takes effect after
 * hpm_set_program_mythread is called.
 * The valid values for mode are defined in hpm.h.
 */
int 
hpm_set_mode(int mode) 
{
   /*right now set mode is just for set domain which
   *will be either user, kernel or both
   *there are much richer set mode for the power pc
   *also in the papi but will be explored later,
   *now just the set modes that are supported under intel
   */

 if(debug>1) printf("hpm.hpm_set_mode(%d)\n",mode);
  options.domain.domain = mode;
  return (OK_CODE);
}
/*
 * After init is called, and events and modes are set in the local variable setprog, 
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
  //printf("hpm.hpm_set_set_mythread() calling set settings\n");
  if(debug>=1){fprintf(stdout,"hpm.hpm_set_program_mythread()\n");fflush(stdout);}
  if(init_enabled==0) {
    fprintf(stderr,"***hpm.hpm_set_program_mythread() called before hpm_init()!***");
    exit(-1);
  }
  if((rc=PAPI_add_events(&EventSet,set_program.events,set_program.number_of_events))!=PAPI_OK){
    perror("***hpm.hpm_set_program_mythread() PAPI_add_events failed!***\n");
    exit(ERROR_CODE);
  }
  //options.domain.domain = 1;
  if((rc=PAPI_set_opt(PAPI_SET_DOMAIN,&options))!=PAPI_OK){
    perror("hpm.hpm_set_program_mythread() PAPI_set_opt failed!***\n");
    exit(ERROR_CODE);
  }
  return (OK_CODE);
  
}
/*
 * This supports the mygroup API.
 */
int 
hpm_set_program_mygroup() 
{
  if(debug>0) printf("hpm.hpm_set_program_mygroup() not implemented \n");
  exit(ERROR_CODE);
  return (OK_CODE);
}

/***********************************************************************************
 * CONSTRAINT: the following functions may be called only after 
 * hpm_set_program_mythread has been called.
 ************************************************************************************/

/*
 * Get counter's event
 * Constraint: setprog has been initialized by a call to set_program.
 */
int
hpm_get_event_id(int counter) 
{
  int evid;
  if(debug>=1) printf("hpm.hpm_get_event_id(%d)\n",counter);
  if (mxpmcs <= counter) {
    fprintf(stderr,"***hpm.hpm_get_event_id(%d) called with counter value %d > max counters %d!***",
	    counter, counter, mxpmcs);
    exit(-1);
  }
  evid = set_program.events[counter];  // event number
  if(evid==0){
	//printf("hpm.hpm_get_event_id(%d) evei is 0 %x\n",counter,evid);
	//fflush(stdout);
	 return evid;
  }
  if(debug>=1) printf("hpm.hpm_get_event_id(%d) in C%x\n",counter,evid);
  evid =  ((evid&0x0fffffff)+1);
  if(debug>=1) printf("hpm.hpm_get_event_id(%d) in java%x\n",counter,evid);
	fflush(stdout);
  return evid;
}

/*
 * Get short description name for the event a counter is set to.
 * Constraint: setprog has been initialized by a call to set_program and init.
 */
char *
hpm_get_event_short_name(int counter) 
{
  int evid;
  char * name = (char *)malloc(sizeof(char)*100);
  if (mxpmcs <= counter) {
    fprintf(stderr,"***hpm.hpm_get_event_short_name(%d) called with counter value %d > max counters %d!***",
	    counter, counter, mxpmcs);
    exit(-1);
  }
  if(debug>=1)fprintf(stdout,"hpm_get_event_short_name(%d)\n",counter);

    evid = set_program.events[counter];  // event number
    if(debug>=1)fprintf(stdout,"hpm_get_event_short_name(%d)       evid %d\n",counter, evid);

  if ( (evid == 0))
    fprintf(stdout,"hpm_get_event_short_name(%d) event %2d: No event\n", counter, evid);
  else {
    PAPI_event_code_to_name(evid,name);
    return name;
  }
  return "";
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
  if((rc=PAPI_cleanup_eventset(&EventSet))!=PAPI_OK){
    perror("***hpm.hpm_delete_program_mythread() PAPI_cleanup_eventset failed!***");
    exit(ERROR_CODE);
  }
  return (OK_CODE);
}
/*
 * This supports mygroup API.
 */
int 
hpm_delete_program_mygroup() 
{
  printf("hpm.hpm_delete_program_mygroup() not implemented\n");
  exit(ERROR_CODE);
  return OK_CODE;
}

/*
 * This routine retrieves the HPM settings into the local variable setprog.
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
  if((rc=PAPI_get_opt(PAPI_GET_DOMAIN,&get_options))!=PAPI_OK){
	perror("***hpm.hpm_get_program_mythread() PAPI_get_opt failed!***\n");
	exit(ERROR_CODE);
  }
  /*    int rc; */
  /*    if ( (rc = pm_get_program_mythread(&setprog)) != OK_CODE) { */
  /*      pm_error("hpm.hpm_get_settings: pm_get_program_mythread ", rc); */
  /*      exit(ERROR_CODE); */
  /*    } */
  return (OK_CODE);
 
}
/*
 * This supports mygroup API.
 */
int 
hpm_get_program_mygroup() 
{
  fprintf(stderr,"hpm.hpm_get_program_mygroup() not implemented");
  exit(-1);
  return (OK_CODE);
}

/*
 * Starts hpm counting for mythread. 
 * Alternatively, could turn on counting by getting program_mythread, 
 * setprog.mode.b.count = 1, and setting program_mythread.
 */
int
hpm_start_mythread()
{

  int rc;
  if(debug>=1)printf("hpm.hpm_start_mythread()\n");
  if((rc=PAPI_start(EventSet))!=PAPI_OK){
    perror("***hpm.hpm_start_mythread() PAPI_start failed!***");
    exit(ERROR_CODE);
  }
  get_data_enabled = 0;
  return(OK_CODE);
}
/*
 * Starts hpm counting for mygroup.
 * The group includes the calling thread and all the decendent kernel threads.
 * Alternatively, could turn on counting by getting program_mygroup, 
 * setprog.mode.b.count = 1, and setting program_mygroup.
 */
int 
hpm_start_mygroup() 
{
  fprintf(stderr,"***hpm.hpm_start_mygroup() not implemented!***\n");
  exit(-1);
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

  long long values[mxpmcs];
  if(debug>=1)printf("hpm.hpm_stop_mythread()\n");
  if ( (rc = PAPI_stop(EventSet,values)) != PAPI_OK) {
    perror("***hpm.hpm_stop_mythread() PAPI_stop failed!***\n");
    //exit(ERROR_CODE);
    return ERROR_CODE;
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
  exit(-1);
  return OK_CODE;
}

/*
 * This routine resets HPM counters to zero for mythread.
 * Do the counters have to be stopped before they can be reset?
 */
int
hpm_reset_mythread()
{
  int rc;

  if(debug>=2)fprintf(stdout,"hpm.hpm_reset_mythread()\n");
  if ( (rc = PAPI_reset(EventSet)) != PAPI_OK) {
    perror("***hpm.hpm_reset_mythread() PAPI_reset failed!***\n");
    exit(ERROR_CODE);
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
  exit(-1);
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
  if((rc=PAPI_read(EventSet,mydata))!=PAPI_OK){
    perror("***hpm.hpm_get_mythread() PAPI_read failed!***\n");
    exit(ERROR_CODE);
  }
  get_data_enabled = 1;
  return mxpmcs; 
}
int
hpm_get_mygroup()
{
  fprintf(stderr,"***hpm.hpm_get_mygroup() not implemented!***\n");
  exit(-1);
  return 0; 
}
int
hpm_get_counters()
{
  int rc;

  if(debug>=1) fprintf(stdout,"hpm.hpm_get_counters()\n");
  if((rc=PAPI_read(EventSet,mydata))!=PAPI_OK){
    perror("***hpm.hpm_get_counters() PAPI_read failed!***\n");
    exit(ERROR_CODE);
  }
  get_data_enabled = 1;
  return mxpmcs;
}


/*
 * Read an HPM counter value.
 * Specify counter in range [1..maxCounters].
 */
long long
hpm_get_counter_mythread(int counter)
{
  int rc;

  if(debug>=2)fprintf(stdout,"hpm.hpm_get_counter_mythread(%d)\n",counter);
  long long value;
  if ( (counter < 0) || (counter > mxpmcs) ) {
     fprintf(stderr, "***hpm.hpm_get_counter(%d): Invalid counter!***\n",counter);
     exit(ERROR_CODE);
  }
  /* eliminate caching for time experiment */
  if (get_data_enabled == 0) { 
    hpm_get_counters();
  }
  value = mydata[counter-1];
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
  exit(-1);
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
  const PAPI_preset_info_t *infostructs;
  if(debug>1);
  printf("calling all events\n ");
  infostructs = PAPI_query_all_events_verbose();
  printf("Name\t\tCode\t\t\tDescription\n");
  for(i=0;i,i<PAPI_MAX_PRESET_EVENTS;i++){
    if(infostructs[i].avail){
      printf("%s\t0x%x\t%s\n",infostructs[i].event_name,infostructs[i].event_code,infostructs[i].event_descr);
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
  
  for (j=0; j<mxpmcs; j++) 
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
    perror("***hpm.hpm_print_mythread() hpm_get_program_mythread failed!  Never expect to be here***");
    exit(-1);
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
  exit(-1);
  return(OK_CODE);	
}

void
hpm_print_header(int mode, int threadapi)
{
  char mode_str[20];
  
  fprintf(stderr,"*** Configuration :\n");
  if ( mode==PAPI_DOM_ALL )
    sprintf(mode_str, "%s", "kernel and user");
  else if (mode==PAPI_DOM_USER)
    sprintf(mode_str, "%s", "user only");
  else if (mode==PAPI_DOM_KERNEL)
    sprintf(mode_str, "%s", "kernel only");
  
  fprintf(stderr, "Mode = %s;\n", mode_str);
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
  exit(ERROR_CODE);
  return NULL;
}

/**
 * List each event that is selected for a counter.
 */
void
hpm_list_selected_events()
{
  int i;
  for(i=0;i<set_program.number_of_events;i++){
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
  exit(ERROR_CODE);
}

int
print_events(int *ev_list)
{
/* for each event (pmc), print short name, accu */
  int	pmcid;		/* which pmc */
  int	evid;		/* event id */
  int	len;
  char	str[100];
  char name[100];
  
  /* go through evs, get sname from table of events, print it */	
  for (pmcid = 0; pmcid < mxpmcs; pmcid++) {
    fprintf(stdout,"Counter %2d, ", pmcid+1); 
    /* get the event id from the list */
    evid = set_program.events[pmcid];
    if ( (pmcid>=set_program.number_of_events))
      fprintf(stdout,"event : No event\n");
    else {
      /* find pointer to the event */
      PAPI_event_code_to_name(evid,name);	
      
      fprintf(stdout,"event %2x: %s\n", evid,name);
    }
  }
  
  fprintf(stdout,"\n*** Results :\n");
  
  str[0] = '\0';
  for (pmcid=0; pmcid<mxpmcs; pmcid++) {
    fprintf(stdout,"PMC%2d     ", pmcid+1);
    len = strlen(str);
    str[len] = ' ';
    sprintf(str+len,"%s","=====     ");
  }
  fprintf(stdout,"\n%s\n", str);	
  return OK_CODE;
}


