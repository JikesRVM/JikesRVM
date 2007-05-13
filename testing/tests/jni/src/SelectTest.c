/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2003
 *
 *
 * Simple test for sys call interception.
 * 
 */

#include <stdio.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#include "SelectTest.h"
#include <jni.h>

extern void directCallMe();
extern void sysWrite(int,int);

JNIEXPORT void JNICALL Java_SelectTest_doit(JNIEnv *env, jclass cls){
  fd_set rfds;
  struct timeval tv;
  int retval;
  FD_ZERO(&rfds);
  FD_SET(0, &rfds);
  tv.tv_sec = 5;
  tv.tv_usec = 0;
  retval = select(1, &rfds, NULL, NULL, &tv);
  if (retval)
    printf("Data is available now.\n");
  else
    printf("No data within five seconds.\n");
}

