/*
 * (C) Copyright IBM Corp. 2001
 *$Id$
 */
/* test native code for jni
 *
 * @author unascribed
 */

#include <stdio.h>
#include "TestDeadVPQueueWorker.h"
#include <jni.h>


JNIEXPORT jint JNICALL Java_TestDeadVPQueueWorker_nativeFoo
  (JNIEnv *env, jclass cls, jint value) {
  int localval = value + 15;
  jintArray myArray;

  printf("Java_TestDeadVPQueueWorker_nativeFoo: reached native code with 0x%X 0x%X %d \n", env, cls, value); 

  myArray = (*env) -> NewIntArray(env, 11);
  printf("Java_TestDeadVPQueueWorker_nativeFoo: JNI call returns 0x%X\n", myArray);

  return localval;
}
