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
/* Test method invocation from native code
 * Implement native methods from Allocation.java
 */

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>             /* malloc() */

#include "Allocation.h"
#include <jni.h>

int verbose=1;

jstring testNewObjectV_part2(JNIEnv *env, jclass cls, jobject stringClass, ...);


/*
 * Class:     Allocation
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_Allocation_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;

}


/*
 * Class:     Allocation
 * Method:    testNewObjectA
 * Signature: (LClass;[C)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Allocation_testNewObjectA
  (JNIEnv *env, jclass cls, jobject stringClass, jcharArray inputChar) {

  jobject newObj;
  jmethodID methodID;
  jvalue *argA;

  argA = (jvalue *) malloc(sizeof(jvalue) * 2);
  (&argA[0])->l = inputChar;

  methodID = (*env) -> GetMethodID(env, stringClass, "<init>", "([C)V");

  newObj = (*env) -> NewObjectA(env, stringClass, methodID, argA);

  return newObj;

}


/*
 * Class:     Allocation
 * Method:    testNewObject
 * Signature: (LClass;[C)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Allocation_testNewObjectV
  (JNIEnv *env, jclass cls, jobject stringClass, jcharArray inputChar) {

  return testNewObjectV_part2(env, cls, stringClass, inputChar);

}


jstring testNewObjectV_part2(JNIEnv *env, jclass cls, jobject stringClass, ...) {

  jobject newObj;
  jmethodID methodID;

  va_list ap;
  va_start(ap, stringClass);

  methodID = (*env) -> GetMethodID(env, stringClass, "<init>", "([C)V");

  newObj = (*env) -> NewObjectV(env, stringClass, methodID, ap);

  va_end(ap);

  return newObj;

}



/*
 * Class:     Allocation
 * Method:    testNewObject
 * Signature: (LClass;[C)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_Allocation_testNewObject
  (JNIEnv *env, jclass cls, jobject stringClass, jcharArray inputChar) {

  jobject newObj;
  jmethodID methodID;

  methodID = (*env) -> GetMethodID(env, stringClass, "<init>", "([C)V");

  newObj = (*env) -> NewObject(env, stringClass, methodID, inputChar);

  return newObj;

}
