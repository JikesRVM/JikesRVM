/*
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 *
 * @author Peter Sweeney
 * @date 6/27/2001
 */
static char *Sccs_id = "@(#)37  1.5  JNI2HPM.c, 06/27/2001 17:41:09";
/*
 * This file implements the native methods of the Java class Java2HPM.
 * These native methods are trampoline code to hpm.c methods.
 * The hpm.c methods provide access to the hardware performance monitors 
 * on the PowerPC archictecture by accessing the pmapi.h kernel extension.
 *
 */

#include <stdio.h>
#include "jni.h"
#include "hpm.h"

static int debug=0;
/*
 * This routine initializes the Performance Monitor APIs, and 
 * must be called before any other API calls can be made.
 * If filter == PM_UNVERIFIED, accept anything.  Other alternatives are:
 *	PM_VERIFIED, PM_CAVEAT
 * This routine sets the Myinfo data structure, which can then be 
 * referenced later to determine which events are accessible.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_init(JNIEnv *env,	/* interface pointer */
				    jclass cls		/* "this" pointer */
				    ) 
{
  int filter = PM_UNVERIFIED|PM_VERIFIED|PM_CAVEAT;

  if(debug>=1) fprintf(stdout, "Java2HPM.init() call hpm_init(%d)\n",filter);

  return hpm_init(filter);
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
jint 
Java_com_ibm_JikesRVM_Java2HPM_setEvent(JNIEnv *env,	/* interface pointer */
					jclass cls,	/* "this" pointer */
					jint e1, jint e2, jint e3, jint e4
					)
{
  if(debug>=1) fprintf(stdout,"Java2HPM_setEvent(%d,%d,%d,%d)\n",e1,e2,e3,e4);

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
jint 
Java_com_ibm_JikesRVM_Java2HPM_setEventX(JNIEnv *env,	/* interface pointer */
					 jclass cls,	/* "this" pointer */
					 jint e5, jint e6, jint e7, jint e8
					 )
{
  if(debug>=1) fprintf(stdout,"Java2HPM_setEventX(%d,%d,%d,%d)\n",e5,e6,e7,e8);

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
#define PM_USER		4	/* turns user mode counting on */
#define PM_KERNEL	8	/* turns kernel mode counting on */
jint 
Java_com_ibm_JikesRVM_Java2HPM_setModeUser(JNIEnv *env,	/* interface pointer */
					   jclass cls	/* "this" pointer */
					   ) 
{
  int mode = MODE_USER;
  if (hpm_isPower4()==1) {
    mode = mode|MODE_IS_GROUP;
  }
  if(debug>=1) fprintf(stdout,"Java2HPM_setModeUser() %d(0X%x)\n",mode,mode);

  return hpm_set_mode(MODE_USER);
}
jint 
Java_com_ibm_JikesRVM_Java2HPM_setModeKernel(JNIEnv *env,	/* interface pointer */
					     jclass cls		/* "this" pointer */
					     ) 
{
  int mode = MODE_KERNEL;
  if (hpm_isPower4()==1) {
    mode = mode|MODE_IS_GROUP;
  }
  if(debug>=1) fprintf(stdout,"Java2HPM_setModeKernel() %d(0X%x)\n",mode,mode);

  return hpm_set_mode(mode);
}
jint 
Java_com_ibm_JikesRVM_Java2HPM_setModeBoth(JNIEnv *env,		/* interface pointer */
					   jclass cls		/* "this" pointer */
					   ) 
{
  int mode = MODE_KERNEL|MODE_USER;
  if (hpm_isPower4()==1) {
    mode = mode|MODE_IS_GROUP;
  }
  if(debug>=1) fprintf(stdout,"Java2HPM_setModeBoth() %d(0X%x)\n",mode,mode);

  return hpm_set_mode(mode);
}
/*
 * Set mode to what parameter is.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_setMode(JNIEnv *env,		/* interface pointer */
				       jclass cls,		/* "this" pointer */
				       jint   mode
				       ) 
{
  if(debug>=1) fprintf(stdout,"Java2HPM_setModeBoth(%d(0X%x))\n",mode,mode);

  return hpm_set_mode(mode);
}
/*
 * After hpm_init is called, and events and modes are set, 
 * call this routine to set HPM settings.
 * May call this multiple times only after calling hpmDeleteSettings.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_setSettings(JNIEnv *env,	/* interface pointer */
					   jclass cls	/* "this" pointer */
					   )
{
  if(debug>=1) fprintf(stdout,"Java2HPM_setSettings()\n");

  return hpm_set_settings();
}
/*
 * This routine retrieves the HPM settings.
 * May be called only after a hpmSetSettings() is called.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_getSettings(JNIEnv *env,	/* interface pointer */
					   jclass cls	/* "this" pointer */
					   ) 
{
  if(debug>=1) fprintf(stdout,"Java2HPM_getSettings()\n");

  return hpm_get_settings();
}
/*
 * After hpmSetSettings is called, this routine unsets settings
 * making it possible to call hpmSetSettings again.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_deleteSettings(JNIEnv *env,	/* interface pointer */
					      jclass cls	/* "this" pointer */
					      )
{
  if(debug>=1) fprintf(stdout,"Java2HPM_deleteSettings()\n");

  return hpm_delete_settings();
}
/*
 * Assume events already set.
 *
 * Alternatively, could turn on counting by getting program_mythread, 
 * setprog.mode.b.count = 1, and setting program_mythread.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_startCounting(JNIEnv *env,	/* interface pointer */
					     jclass cls		/* "this" pointer */
					     )
{
  if(debug>=1) fprintf(stdout,"Java2HPM_startCounting()\n"); 

  return hpm_start_counting();
}
/*
 * Assumes that hpmStartCounting completed correctly.
 * After successful completion, counters no longer enabled.
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_stopCounting(JNIEnv *env,	/* interface pointer */
					    jclass cls		/* "this" pointer */
					    ) 
{
  if(debug>=1) fprintf(stdout,"Java2HPM_stopCounting()\n");

  return hpm_stop_counting();
}
/*
 * This routine is called to reset the counters to zero.
 * Must be called after hpm_init!
 */
jint 
Java_com_ibm_JikesRVM_Java2HPM_resetCounters(JNIEnv *env,	/* interface pointer */
					     jclass cls	/* "this" pointer */
					     )
{
  if(debug>=1) fprintf(stdout,"Java2HPM_resetCounters()\n");

  return hpm_reset_counters();
}
/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_getCounters(JNIEnv *env,	/* interface pointer */
					   jclass cls	/* "this" pointer */
					   )
{
  jint value = hpm_get_counters();

  if(debug>=1) fprintf(stdout,"Java2HPM_getCounters() returns %d\n",value);
  return value;
}
/*
 * Return number of countes available on this machine.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_getNumberOfCounters(JNIEnv *env,	/* interface pointer */
						   jclass cls	/* "this" pointer */
						   )
{
  jint value = hpm_number_of_counters();

  if(debug>=1){fprintf(stdout,"Java2HPM_getNumberOfCounters() returns %d\n",value);fflush(stdout);}
  return value;
}
/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
jlong
Java_com_ibm_JikesRVM_Java2HPM_getCounterValue(JNIEnv *env,	/* interface pointer */
					       jclass cls,	/* "this" pointer */
					       jint counter
					       )
{
  jlong value = hpm_get_counter(counter);

  if(debug>=1) fprintf(stdout,"Java2HPM_getCounter(%d) returns %d%d\n",counter,value);
  return value;
}

/*
 * Assume hpmInit is already called.
 */
void
Java_com_ibm_JikesRVM_Java2HPM_listAllEvents(JNIEnv *env,/* interface pointer */
					     jclass cls	 /* "this" pointer */
					     )
{
  hpm_list_all_events();
}

/*
 * Assume hpmInit is already called.
 */
void
Java_com_ibm_JikesRVM_Java2HPM_listSelectedEvents(JNIEnv *env,/* interface pointer */
						  jclass cls	/* "this" pointer */
						  )
{
  hpm_list_selected_events();
}

/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_getEventId(JNIEnv *env,	/* interface pointer */
					  jclass cls,	/* "this" pointer */
					  jint counter
					  )
{
  jint value = hpm_get_event_id(counter);

  if(debug>=1){fprintf(stdout,"Java2HPM_getEventId(%d) returns %d\n",counter,value);fflush(stdout);}
  return value;
}
/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
jstring
Java_com_ibm_JikesRVM_Java2HPM_getEventShortName(JNIEnv *env,	/* interface pointer */
						 jclass cls,	/* "this" pointer */
						 jint counter
						 )
{
  jstring r_value;
  char * value = hpm_get_event_short_name(counter);

  if(debug>=1) fprintf(stdout,"Java2HPM_getEventShortName(%d) returns %s\n",counter,value);
  r_value = env->NewStringUTF(value);
  if(debug>=1){fprintf(stdout,"Java2HPM_getEventShortName(%d) r_value 0X%x\n",counter,r_value);fflush(stdout);}
  return r_value;
}
/*
 * print hardware performance monitors
 * Assumes
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_print(JNIEnv *env,	/* interface pointer */
				     jclass cls,	/* "this" pointer */
				     jint processId
				     ) 
{  
  if(debug>=1)fprintf(stdout,"Java2HPM_print(%d) dump HPM counter values\n",processId);

  return hpm_print();
}
/*
 * test interface to HPM
 */
static int test_value = 0;

jint
Java_com_ibm_JikesRVM_Java2HPM_test(JNIEnv *env,	/* interface pointer */
				    jclass cls		/* "this" pointer */
				    ) 
{
  if(debug>=1)fprintf(stdout,"Java2HPM_test()\n");

  return test_value++;
}

/*
 * Assume events already set.
 * Assume called stopped counting previously.
 * Only returns if value found.
 * specify counter in range [1..maxCounters].
 */
jstring
Java_com_ibm_JikesRVM_Java2HPM_getProcessorName(JNIEnv *env,	/* interface pointer */
						jclass cls	/* "this" pointer */
						)
{
  jstring r_value;
  char * value = hpm_get_processor_name();

  if(debug>=1) fprintf(stdout,"Java2HPM_getProcessorName() returns %s\n",value);
  r_value = env->NewStringUTF(value);
  if(debug>=1){fprintf(stdout,"Java2HPM_getProcessorName() r_value 0X%x\n",r_value);fflush(stdout);}
  return r_value;
}
/*
 * Is machine PowerPC Power4?
 * Assume hpm_init already called.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_isPower4(JNIEnv *env,	/* interface pointer */
					jclass cls	/* "this" pointer */
					)
{
  jint value;
  //  if(debug>=1) fprintf(stdout,"Java2HPM_isPower4()\n");
  value = hpm_isPower4();
  if(debug>=1) fprintf(stdout,"Java2HPM_isPower4() returns %d\n",value);
  return value;
}
/*
 * Is machine PowerPC Power3?
 * Assume hpm_init already called.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_isPower3(JNIEnv *env,	/* interface pointer */
					jclass cls	/* "this" pointer */
					)
{
  jint value;
  //  if(debug>=1) fprintf(stdout,"Java2HPM_isPower3()\n");
  value = hpm_isPower3();
  if(debug>=1) fprintf(stdout,"Java2HPM_isPower3() returns %d\n",value);
  return value;
}
/*
 * Is machine RS64-III?
 * Assume hpm_init already called.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_isRS64III(JNIEnv *env,	/* interface pointer */
					 jclass cls	/* "this" pointer */
					 )
{
  jint value;
  //  if(debug>=1) fprintf(stdout,"Java2HPM_isRS64III()\n");
  value = hpm_isRS64III();
  if(debug>=1) fprintf(stdout,"Java_com_ibm_JikesRVM_Java2HPM_isRS64III() returns %d\n",value);
  return value;
}
/*
 * Is machine 604e?
 * Assume hpm_init already called.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_is604e(JNIEnv *env,	/* interface pointer */
				      jclass cls	/* "this" pointer */
				      )
{
  jint value;
  //  if(debug>=1) fprintf(stdout,"Java2HPM_is604e()\n");
  value = hpm_is604e();
  if(debug>=1) fprintf(stdout,"Java2HPM_is604e() returns %d\n",value);
  return value;
}
/*
 * Is machine PowerPC Power3-II?
 * Assume hpm_init already called.
 */
jint
Java_com_ibm_JikesRVM_Java2HPM_isPower3II(JNIEnv *env,	/* interface pointer */
					  jclass cls	/* "this" pointer */
					  )
{
  jint value;
  /*  if(debug>=1) fprintf(stdout,"Java_com_ibm_JikesRVM_Java2HPM_isPower3II()\n"); */
  value = hpm_isPower3II();
  if(debug>=1) fprintf(stdout,"Java2HPM_isPower3II() returns %d\n",value);
  return value;
}
