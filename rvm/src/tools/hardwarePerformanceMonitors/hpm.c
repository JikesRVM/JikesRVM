/*
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 *
 * @author Peter Sweeney
 */

/*
 *   FUNCTIONS: Jikes RVM interface
 *		hpm_init
 *		hpm_set_settings
 *		hpm_delete_settings
 *		hpm_get_settings
 *		hpm_set_event
 *		hpm_set_event_X
 *		hpm_set_mode
 *		hpm_start_counting
 *		hpm_stop_counting
 *		hpm_reset_counters
 *		hpm_get_counter
 *		hpm_print
 *		hpm_test
 *		Other interface
 *              print_data
 *              print_header 
 *              print_events
 *
 * Description: 
 * This file provides an interface to the hardware performance monitor facilities 
 * on PowerPC architectures and are used by the adaptive optimization system of 
 * Jikes RVM.
 *
 * The inteface is based on "mythread" which creates a group for the calling
 * kernel thread.  The "mythread" interface does not require superuser privileges.
 * The "init" procedure must be invoked before all others.
 * 
 * An alternative inteface is based on "mygroup" which creates a group for the calling
 * kernel thread and any threads which it or its descendants create in the future.
 * The "mygroup" interface does not require superuser privileges.
 */
 
#include <stdio.h>
#include <string.h>
#include <sys/m_wait.h>
#include <sys/systemcfg.h>
#include <sys/processor.h>
#include "pmapi.h"

#define False	0
#define True	1
#define ERROR_CODE	-1
#define OK_CODE		 0

extern int errno;
pm_info_t Myinfo;	/* machine specific services */
pm_groups_info_t My_group_info;
pm_prog_t getprog;	/* storage to get events and mode */
pm_prog_t setprog;	/* storage to set events and mode */
pm_data_t mydata;
int threadapi = 0;
int filter = PM_UNVERIFIED;
/*
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * If filter == PM_UNVERIFIED, accept anything.  Other alternatives are:
 *	PM_VERIFIED, PM_CAVEAT
 * This routine sets the Myinfo data structure, which can then be referenced later.
 */
int
hpm_init(int my_filter) 
{
  int rc;
  int i;
  /* pm_init */ 
  filter = my_filter;
  if ( (rc = pm_init(filter, &Myinfo, &My_group_info)) != OK_CODE) {
    pm_error("pm_init", rc);
    exit(ERROR_CODE);
  }

  printf("hpm_init: processor name is %s\n",Myinfo.proc_name);
  // Set counter to count nothing
  for (i = 0; i < MAX_COUNTERS; i++) {
    setprog.events[i] = COUNT_NOTHING;
  }

  // Set mode to user only
  setprog.mode.w         = 0;		// start with clean mode
  setprog.mode.b.count   = 0;
  setprog.mode.b.user    = 1;
  setprog.mode.b.kernel  = 0;
  //  not used for mythread!
  // setprog.mode.b.process = 0;	// process level counting group
  setprog.mode.b.proctree= 1;	// count this process and its descendants events

  return(OK_CODE);
}

/*
 * After init is called, and events and modes are set in the local variable setprog, 
 * call this routine to set HPM settings.
 * May call this multiple times only after calling hpm_delete_settings.
 */
int
hpm_set_settings()
{
  int rc;
  if ( (rc = pm_set_program_mythread(&setprog)) != OK_CODE) {
    pm_error("hpm_set_settings: pm_set_program_mythread ", rc);
    exit(ERROR_CODE);
  }
  return (OK_CODE);
}
/*
 * After hpm_set_settings is called, this routine unsets settings
 * making it possible to call hpm_set_settings again.
 */
int
hpm_delete_settings()
{
  int rc;
  if ( (rc = pm_delete_program_mythread()) != OK_CODE) {
    pm_error("pm_delete_settings: pm_delete_program_mythread ", rc);
    exit(ERROR_CODE);
  }
  return (OK_CODE);
}

/*
 * This routine retrieves the HPM settings into the local variable setprog.
 * May be called only after a hpm_set_settings() is called.
 */
int 
hpm_get_settings() 
{
  int rc;
  if ( (rc = pm_get_program_mythread(&setprog)) != OK_CODE) {
    pm_error("hpm_get_settings: pm_get_program_mythread ", rc);
    exit(ERROR_CODE);
  }
  return (OK_CODE);
}


/*
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
int
hpm_set_event(int e1, int e2, int e3, int e4)
{
  int i;
  int rc;

  pm_events_t *evp;

  /* TODO: map enumeration to mnemonic and 
   * index mnemonic into Myinfo structure to 
   * find counter, event pair.
   */
  if (Myinfo.maxpmcs > 0) {
    setprog.events[0] = e1;
  }
  if (Myinfo.maxpmcs > 1) {
    setprog.events[1] = e2;
  }
  if (Myinfo.maxpmcs > 2) {
    setprog.events[2] = e3;
  }
  if (Myinfo.maxpmcs > 3) {
    setprog.events[3] = e4;
  }

  /* check validity of the event number */
  for (i = 0; i < Myinfo.maxpmcs; i++) {
    /* with the filter, it's possible that a counter has no
       matching event. 
    */
    if (Myinfo.maxevents[i] == 0)
      continue;
    
    evp = Myinfo.list_events[i];
    evp += Myinfo.maxevents[i] - 1;
    
    if ((setprog.events[i] < COUNT_NOTHING) || 
	(setprog.events[i] > evp->event_id)) {
      fprintf(stderr,"Event %d is invalid in counter %d\n", 
	      setprog.events[i], i+1);
      exit(ERROR_CODE);
    }		
  }

  return(OK_CODE);
}
/*
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
int
hpm_set_event_X(int e5, int e6, int e7, int e8)
{
  int i;
  int rc;

  pm_events_t *evp;

  /* TODO: map enumeration to mnemonic and 
   * index mnemonic into Myinfo structure to 
   * find counter, event pair.
   */
  if (Myinfo.maxpmcs > 4) {
    setprog.events[4] = e5;
  }
  if (Myinfo.maxpmcs > 5) {
    setprog.events[5] = e6;
  }
  if (Myinfo.maxpmcs > 6) {
    setprog.events[6] = e7;
  }
  if (Myinfo.maxpmcs > 7) {
    setprog.events[7] = e8;
  }

  /* turn counting on only for this process and all descendants.
  setprog.mode.w |= PM_PROCTREE;

  /* check validity of the event number */
  for (i = 0; i < Myinfo.maxpmcs; i++) {
    /* with the filter, it's possible that a counter has no
       matching event. 
    */
    if (Myinfo.maxevents[i] == 0)
      continue;
    
    evp = Myinfo.list_events[i];
    evp += Myinfo.maxevents[i] - 1;
    
    if ((setprog.events[i] < COUNT_NOTHING) || 
	(setprog.events[i] > evp->event_id)) {
      fprintf(stderr,"Event %d is invalid in counter %d\n", 
	      setprog.events[i], i+1);
      exit(ERROR_CODE);
    }		
  }

  return(OK_CODE);
}

/*
 * Set the mode, in local variable setprog.
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 *
 * Valid parameter values:
 *  PM_COUNT	2	turns counting on immediately
 *  PM_USER	4	turns user mode counting on
 *  PM_KERNEL	8	turns kernel mode counting on
 **** not used for mythread
 *  PM_PROCTREE	1	turns process tree counting on
 *  PM_PROCESS	16	creates a process level group
 * 
 */
int 
hpm_set_mode(int mode) 
{
  int rc;

  if ((mode & 0x01) || (mode & 0x001) || (mode & 0x0001)) {
    fprintf(stderr,"hpm_set_mode(%d): parameter has an illegal value!  Can have the value 2, 4, 8, or any combination\n", mode);
    return (ERROR_CODE);
  }
  setprog.mode.w = mode;
  return (OK_CODE);
}
/*
 * Assume events already set with call to hpm_set_settings.
 *
 * Alternatively, could turn on counting by getting program_mythread, 
 * setprog.mode.b.count = 1, and setting program_mythread.
 */
int
hpm_start_counting()
{
  int rc;

  if ( (rc = pm_start_mythread()) != OK_CODE) {
    pm_error("hpm_start_counting: pm_start_mythread ", rc);
  }

  return(OK_CODE);
}

/*
 * Assumes that hpm_start completed correctly.
 * After successful completion, counters no longer enabled.
 */
int 
hpm_stop_counting() 
{
  int rc;

  if ( (rc = pm_stop_mythread()) != OK_CODE) {
    pm_error("hpm_stop_counting: pm_stop_mythread ", rc);
    exit(ERROR_CODE);
  }
  /* counters disabled */
  return(OK_CODE);	
}

/*
 * This routine is called to reset the counters to zero.
 * Must be called after hpm_init!
 * Do the counters have to be stopped before they can be reset?
 */
int
hpm_reset_counters()
{
  int rc;

  if ( (rc = pm_reset_data_mythread()) != OK_CODE) {
    pm_error("hpm_reset_counters: pm_reset_data_mythread ", rc);
    exit(ERROR_CODE);
  }
  return(OK_CODE);
}
/*
 * Assume events already set.
 * Assume called stopped counting previously (is this required?).
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
long long
hpm_get_counter(int counter)
{
  int rc;

  long long value;
  if ( (counter < 0) || (counter > Myinfo.maxpmcs) ) {
     fprintf(stderr, "hpm_get_counter(%d): Invalid counter.\n",counter);
     exit(ERROR_CODE);
  }
  if ( (rc = pm_get_data_mythread(&mydata)) != OK_CODE) {
    pm_error("hpm_get_counter: pm_get_data_mythread()", rc);
    exit(ERROR_CODE);
  }
  value = mydata.accu[counter-1];
  return value;
}

/*
 * print hardware performance monitors
 * Assumes
 */
int 
hpm_print() 
{
  int rc;
  
  /* do I need this? */
  if ( (rc = pm_get_program_mythread(&getprog)) != OK_CODE)
    pm_error("pm_get_program", rc);
  
  if ( (rc = pm_get_data_mythread(&mydata)) != OK_CODE) {
    pm_error("pm_get_data", rc);
    return(ERROR_CODE);
  }

  /* print the results */
  print_header(getprog.mode, 0);
  print_events(getprog.events);
  print_data(&mydata);
  
  return(OK_CODE);	
}
/*
 * test interface to HPM
 */
static int test_value = 0;
int
hpm_test() 
{
  return test_value++;
}

/*****************************************
 **	Don't modify below.
 *****************************************/
/* Function to convert filter of character form (entered from command line, 
 * e.g. -t v,u) to a numeric form.
 */
int
print_data(pm_data_t *data)
{
	int j;

	for (j=0; j<Myinfo.maxpmcs; j++) 
		fprintf(stderr, "%-8lld  ", data->accu[j]); 
	fprintf(stderr, "\n");
}


int
print_header(pm_mode_t mode, int threadapi)
{
	char mode_str[20];
	int thresh_value;

	fprintf(stderr,"*** Configuration :\n");
	if ( mode.b.user && mode.b.kernel )
		sprintf(mode_str, "%s", "kernel and user");
	else if (mode.b.user)
		sprintf(mode_str, "%s", "user only");
	else if (mode.b.kernel)
		sprintf(mode_str, "%s", "kernel only");

	fprintf(stderr, "Mode = %s; ", mode_str);

	if (!threadapi) {
		fprintf(stderr,"Process tree = ");
		if (mode.b.proctree)
			fprintf(stderr,"on; ");
		else
			fprintf(stderr,"off; ");
	}
	else {
		fprintf(stderr,"Process group = ");
		if (mode.b.process)
			fprintf(stderr,"on; ");
		else
			fprintf(stderr,"off; ");
	}

	fprintf(stderr,"Thresholding = ");
	if (mode.b.threshold > 0) 
		fprintf(stderr,"%d cycles (adjusted)\n", 
			mode.b.threshold * Myinfo.thresholdmult);
	else
		fprintf(stderr,"off \n"); 
}

int
print_events(int *ev_list)
{
	/* for each event (pmc), print short name, accu */
	int	pmcid;		/* which pmc */
	int	evid;		/* event id */
	pm_events_t *evp;
	char	str[100];
	int	len;
	int	i;

	/* go through evs, get sname from table of events, print it */	
	for (pmcid = 0; pmcid < Myinfo.maxpmcs; pmcid++) {
		fprintf(stderr,"Counter %2d, ", pmcid+1); 
		/* get the event id from the list */
		evid = ev_list[pmcid];
		if ( (evid == COUNT_NOTHING) || (Myinfo.maxevents[pmcid] == 0))
			fprintf(stderr,"event %2d: No event\n", evid);
		else {
			/* find pointer to the event */
			for (i = 0; i < Myinfo.maxevents[pmcid]; i++) {
				evp = Myinfo.list_events[pmcid]+i;
				if (evid == evp->event_id) {
					break;
				}
			}
			
			fprintf(stderr,"event %2d: %s\n", evid, evp->short_name);
		}
	}

	fprintf(stderr,"\n*** Results :\n");

	str[0] = '\0';
	for (pmcid=0; pmcid<Myinfo.maxpmcs; pmcid++) {
		fprintf(stderr,"PMC%2d     ", pmcid+1);
		len = strlen(str);
		str[len] = ' ';
		sprintf(str+len,"%s","=====     ");
	}
	fprintf(stderr,"\n%s\n", str);	
}
