/*
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 *
 * @author Peter Sweeney
 * @date 6/27/2001
 */
static char *Sccs_id = "@(#)37  1.5  jnihpm.c, 06/27/2001 17:41:09";
/*
 * This file implements the native methods (of the Java class JNI2HPM)
 * that provide a path from Java to the hpm.c methods.
 * The hpm.c methods provide access to the hardware performance monitors 
 * on the PowerPC archictecture by accessing the pmtoolkit library.
 *
 *   FUNCTIONS: 
 *		hpmInit
 *		hpmSetSettings
 *		hpmDeleteSettings
 *		hpmGetSettings
 *		hpmSetEvent
 *		hpmSetEventX
 *		hpmSetModeUser
 *		hpmSetModeKernel
 *		hpmSetModeBoth
 *		hpmStartCounting
 *		hpmStopCounting
 *		hpmResetCounters
 *		hpmGetCounter
 *		hpmPrint
 *		hpmTest
 */

#include <stdio.h>
#include "jni.h"
#include "pmapi.h"

static int debug=1;
/*
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * If filter == PM_UNVERIFIED, accept anything.  Other alternatives are:
 *	PM_VERIFIED, PM_CAVEAT
 * This routine sets the Myinfo data structure, which can then be 
 * referenced later to determine which events are accessible.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmInit(JNIEnv *env,	/* interface pointer */
		     jclass cls		/* "this" pointer */
		     ) 
{
  if(debug>=1) printf("Java_JNI2HPM_hpmInit()");
  return hpm_init(PM_UNVERIFIED);
}
/*
 * After hpm_init is called, and events and modes are set, 
 * call this routine to set HPM settings.
 * May call this multiple times only after calling hpmDeleteSettings.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmSetSettings(JNIEnv *env,	/* interface pointer */
			    jclass cls		/* "this" pointer */
			    )
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetSettings()");
  return hpm_set_settings();
}
/*
 * This routine retrieves the HPM settings.
 * May be called only after a hpmSetSettings() is called.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmGetSettings(JNIEnv *env,	/* interface pointer */
			    jclass cls		/* "this" pointer */
			    ) 
{
  return hpm_get_settings();
}
/*
 * After hpmSetSettings is called, this routine unsets settings
 * making it possible to call hpmSetSettings again.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmDeleteSettings(JNIEnv *env,	/* interface pointer */
			       jclass cls	/* "this" pointer */
			       )
{
  return hpm_delete_settings();
}
/*
 * This routine is called to set the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpmSetSettings is called.
 * This interface is sufficient for the 604e microarchitecture.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmSetEvent(JNIEnv *env,	/* interface pointer */
			 jclass cls,	/* "this" pointer */
			 jint e1, jint e2, jint e3, jint e4)
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetEvent(%d,%d,%d,%d)",e1,e2,e3,e4);
  return hpm_set_event(e1, e2, e3, e4);
}
/*
 * This routine is called to set the events to watch.
 * Must be called after hpm_init!
 * The result of calling this routine only takes effect after
 * hpmSetSettings is called.
 * This interface is needed for the 630 microarchitecture which can
 * count 8 events simultaneously.
 * TODO:
 * Arguments correspond to an enumerated type which
 * is mapped into a mnemonic name that is used to 
 * index into the Myinfo structure to determine the
 * correct counter, event pair to be set.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmSetEventX(JNIEnv *env,	/* interface pointer */
			  jclass cls,	/* "this" pointer */
			  jint e5, jint e6, jint e7, jint e8)
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetEventX(%d,%d,%d,%d)",e5,e6,e7,e8);
  return hpm_set_event_X(e5, e6, e7, e8);
}
/*
 * Set the mode.
 * The result of calling this routine only takes effect after
 * hpmSetSettings is called.
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
JNIEXPORT jint 
Java_JNI2HPM_hpmSetModeUser(JNIEnv *env,	/* interface pointer */
			    jclass cls		/* "this" pointer */
			    ) 
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetModeUser(%d)\n",PM_USER);
  return hpm_set_mode(PM_USER);
}
JNIEXPORT jint 
Java_JNI2HPM_hpmSetModeKernel(JNIEnv *env,	/* interface pointer */
			      jclass cls	/* "this" pointer */
			      ) 
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetModeKernel(%d)\n",PM_KERNEL);
  return hpm_set_mode(PM_KERNEL);
}
JNIEXPORT jint 
Java_JNI2HPM_hpmSetModeBoth(JNIEnv *env,	/* interface pointer */
			    jclass cls		/* "this" pointer */
			    ) 
{
  if(debug>=1) printf("Java_JNI2HPM_hpmSetModeBoth(%d)\n",PM_USER|PM_KERNEL);
  return hpm_set_mode(PM_USER|PM_KERNEL);
}
/*
 * Assume events already set.
 *
 * Alternatively, could turn on counting by getting program_mythread, 
 * setprog.mode.b.count = 1, and setting program_mythread.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmStartCounting(JNIEnv *env,	/* interface pointer */
			      jclass cls	/* "this" pointer */
			      )
{
  if(debug>=1) printf("Java_JNI2HPM_hpmStartCounting()\n");
  return hpm_start_counting();
}
/*
 * Assumes that hpmStartCounting completed correctly.
 * After successful completion, counters no longer enabled.
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmStopCounting(JNIEnv *env,	/* interface pointer */
			     jclass cls		/* "this" pointer */
			     ) 
{
  if(debug>=1) printf("Java_JNI2HPM_hpmStopCounting()\n");
  return hpm_stop_counting();
}
/*
 * This routine is called to reset the counters to zero.
 * Must be called after hpm_init!
 */
JNIEXPORT jint 
Java_JNI2HPM_hpmResetCounters(JNIEnv *env,	/* interface pointer */
			      jclass cls	/* "this" pointer */
			      )
{
  if(debug>=1) printf("Java_JNI2HPM_hpmResetCounters()\n");
  return hpm_reset_counters();
}
/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
JNIEXPORT jlong 
Java_JNI2HPM_hpmGetCounter(JNIEnv *env,	/* interface pointer */
			   jclass cls,	/* "this" pointer */
			   jint counter
			   )
{
  if(debug>=1) printf("Java_JNI2HPM_hpmGetCounter(%d)\n",counter);
  return hpm_get_counter(counter);
}
/*
 * print hardware performance monitors
 * Assumes
 */
JNIEXPORT jint
Java_JNI2HPM_hpmPrint(JNIEnv *env,	/* interface pointer */
		      jclass cls	/* "this" pointer */
		      ) 
{  
  if(debug>=1) printf("Java_JNI2HPM_hpmPrint()\n");
  return hpm_print();
}
/*
 * test interface to HPM
 */
static int test_value = 0;

JNIEXPORT jint
Java_JNI2HPM_hpmTest(JNIEnv *env,	/* interface pointer */
		     jclass cls	/* "this" pointer */
		     ) 
{
  return test_value++;
}

