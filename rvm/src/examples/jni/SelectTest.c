/*
 * (C) Copyright IBM Corp. 2003
 *
 * $Id$
 *
 * Simple test for sys call interception.
 * 
 * @author David Grove
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

