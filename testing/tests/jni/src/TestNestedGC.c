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
/* Test GC with Native frames on stack
 */

#include <stdio.h>
#include "TestNestedGC.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     TestNestedGC
 * Method:    level0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_TestNestedGC_level0
  (JNIEnv * env, jclass cls) {

  jmethodID methodID = (*env) -> GetStaticMethodID(env, cls, "level1", "()V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method level1()\n");
    return NULL;
  }

  (*env) -> CallStaticVoidMethodA(env, cls, methodID, NULL);
}

/*
 * Class:     TestNestedGC
 * Method:    level2
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_TestNestedGC_level2
  (JNIEnv * env, jclass cls) {

  jmethodID methodID = (*env) -> GetStaticMethodID(env, cls, "level3", "()V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method level3()\n");
    return NULL;
  }

  (*env) -> CallStaticVoidMethodA(env, cls, methodID, NULL);
}

