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
