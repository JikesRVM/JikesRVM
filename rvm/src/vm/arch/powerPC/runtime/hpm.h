/*
 * (C) Copyright IBM Corp. 2002
 *
 * $Id$
 */
#ifndef _H_HPM
#define _H_HPM
#include "pmapi.h"
/* defined for pmapi.h */
extern int errno;

/* 
 * Header for hpm.c file 
 * @author Peter F. Sweeney
 */

#define False	0
#define True	1
#define ERROR_CODE	-1
#define OK_CODE		 0

#define MODE_IS_GROUP     1	/* is an event group (only event 1 should be defined) */
#define MODE_PROCESS      2	/* process level group indicator */
#define MODE_KERNEL       4	/* turns kernel mode counting on */
#define MODE_USER         8	/* turns user mode counting on */
#define MODE_COUNT       16	/* counting state */
#define MODE_PROCTREE    32	/* turns process tree counting on */
#define MODE_UPPER_BOUND 63	/* upper bound */

/*
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * If filter == PM_UNVERIFIED, accept anything.  Other alternatives are:
 *	PM_VERIFIED, PM_CAVEAT
 */
extern int   hpm_init(int my_filter);
extern int   hpm_number_of_counters();
extern char *hpm_get_processor_name();
extern int   hpm_isPower4();
extern int   hpm_isPower3II();
extern int   hpm_isPower3();
extern int   hpm_isRS64III();
extern int   hpm_is604e();
extern int   hpm_get_event_id(        int counter);
extern char *hpm_get_event_short_name(int counter);
/*
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 */
extern int hpm_set_event(int e1, int e2, int e3, int e4);
/*
 * This routine is called to set, in local variable setprog, the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 */
extern int hpm_set_event_X(int e5, int e6, int e7, int e8);
/*
 * Set the mode, in local variable setprog.
 * The result of calling this routine only takes effect after
 * hpm_set_settings is called.
 *
 * Valid parameter values:
 *  PM_USER	4	turns user mode counting on
 *  PM_KERNEL	8	turns kernel mode counting on
 */
extern int hpm_set_mode(int mode);

/*
 * After init is called, and events and modes are set in the local variable setprog, 
 * call this routine to set HPM settings.
 * May not make two consecutive calls to this routine without an intervening call to
 * hpm_delete_settings.
 */
extern int hpm_set_settings();
/*
 * After hpm_set_settings is called, this routine unsets settings
 * making it possible to call hpm_set_settings again.
 */
extern int hpm_delete_settings();
/*
 * This routine retrieves the HPM settings into the local variable setprog.
 * May be called only after a hpm_set_settings() is called.
 */
extern int hpm_get_settings();
/*
 * Starts counting.
 */
extern int hpm_start_counting();
/*
 * Stop counting.
 * After successful completion, counters no longer enabled.
 * Assumes that hpm_start_countingx completed correctly.
 */
extern int hpm_stop_counting();
/*
 * Reset counters to zero.
 * Must be called after hpm_init!
 */
extern int hpm_reset_counters();
extern int hpm_get_counters();
/*
 * Get the value of a counter.
 * Assume events already set.
 * Assume called stopped counting previously (is this required?).
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
extern long long hpm_get_counter(int counter);
/*
 * print hardware performance monitors
 * Assumes
 */
extern int hpm_print();
/*
 * test interface to HPM
 */
extern int hpm_test();
/*
 * List the machines available events.
 */
extern void hpm_list_events();

/*
 * dump out the events.
 */
extern void hpm_dumpEvents();

#endif
