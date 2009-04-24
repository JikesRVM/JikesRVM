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
#include "TestGC.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     TestGC
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_TestGC_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}

/*
 * Class:     TestGC
 * Method:    testgc
 * Signature: (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_TestGC_testgc
  (JNIEnv * env, jclass cls, jobject obj1, jobject obj2) {

  jmethodID methodID;
  jclass expectCls;
  jboolean matchClass;
  jclass systemClass;

  systemClass = (*env) -> FindClass(env, "java/lang/System");
  if (systemClass == NULL) {
    if (verbose)
      printf("> FindClass: fail to get class for java/lang/System\n");
    return NULL;
  }

  methodID = (*env) -> GetStaticMethodID(env, systemClass, "gc", "()V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method System.gc\n");
    return NULL;
  }

  if (verbose)
    printf("about to do System.gc\n");

  (*env) -> CallStaticVoidMethodA(env, systemClass, methodID, NULL);

  if (verbose)
    printf("back from System.gc\n");


  /* Do a few operations on the moved object to see if it's still valid */
  if (verbose) printf("Calling FindClass\n");
  expectCls = (*env) -> FindClass(env, "java/lang/String");
  if (verbose) printf("Calling IsInstanceOf\n");
  matchClass = (*env) -> IsInstanceOf(env, obj1, expectCls);
  if (!matchClass)
    return NULL;
  if (verbose) printf("Calling IsInstanceOf\n");
  matchClass = (*env) -> IsInstanceOf(env, obj2, expectCls);
  if (!matchClass)
    return NULL;

  if (verbose) printf("Returning\n");

  return obj1;
}




