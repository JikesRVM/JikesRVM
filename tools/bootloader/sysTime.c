/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include "sys.h"

#include <sys/time.h> // gettimeofday

#ifndef __MACH__
#include <time.h> // CLOCK_REALTIME
#endif

EXTERNAL long long sysCurrentTimeMillis()
{
  TRACE_PRINTF("%s: sysCurrentTimeMillis\n", Me);
  int rc;
  long long returnValue;
  struct timeval tv = {0};
  struct timezone tz = {0};

  returnValue = 0;

  rc = gettimeofday(&tv, &tz);
  if (rc != 0) {
    returnValue = rc;
  } else {
    returnValue = ((long long) tv.tv_sec * 1000) + tv.tv_usec/1000;
  }

  return returnValue;
}

EXTERNAL long long sysNanoTime()
{
  TRACE_PRINTF("%s: sysNanoTime\n", Me);
  long long retVal;
#ifndef __MACH__
  struct timespec tp = {0};
  int rc = clock_gettime(CLOCK_REALTIME, &tp);
  if (rc != 0) {
    retVal = rc;
    ERROR_PRINTF("sysNanoTime: Non-zero return code %d from clock_gettime\n", rc);
  } else {
    retVal = (((long long) tp.tv_sec) * 1000000000) + tp.tv_nsec;
  }
#else
  struct timeval tv = {0};

  gettimeofday(&tv,NULL);

  retVal=tv.tv_sec;
  retVal*=1000;
  retVal*=1000;
  retVal+=tv.tv_usec;
  retVal*=1000;
#endif
  return retVal;
}
