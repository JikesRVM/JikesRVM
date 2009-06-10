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
/*
 * Simple test for sys call interception.
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

