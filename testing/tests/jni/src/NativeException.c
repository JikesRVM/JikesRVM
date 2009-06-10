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
/* Test JNI Functions related to Exception
 * Implement native methods from NativeException.java
 */
#include <stdio.h>
#include <stdlib.h>             /* malloc() */
#include "NativeException.h"
#include <jni.h>

int verbose=1;
/*
 * Class:     NativeException
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_NativeException_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}


/*
 * Class:     NativeException
 * Method:    testPassThrough
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testPassThrough
  (JNIEnv *env, jclass cls, jintArray intArray) {

  int i;
  int size = 10;
  jint *buf = (jint *) malloc(sizeof(int) * size);
  for (i = 0; i<size; i++) {
    buf[i] = buf[i] + 1;
  }

  /* force an ArrayIndexOutOfBoundsException */
  (*env) -> SetIntArrayRegion(env, intArray, 5, 10, buf);

  return JNI_TRUE;    /* exception should occur in caller */

}


/*
 * Class:     NativeException
 * Method:    testExceptionOccured
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testExceptionOccured
  (JNIEnv *env, jclass cls, jintArray intArray) {

  int i;
  int size = 10;
  jthrowable e;

  jint *buf = (jint *) malloc(sizeof(int) * size);
  for (i = 0; i<size; i++) {
    buf[i] = buf[i] + 1;
  }

  /* force an ArrayIndexOutOfBoundsException */
  (*env) -> SetIntArrayRegion(env, intArray, 5, 10, buf);

  e = (*env) -> ExceptionOccurred(env);
  return JNI_FALSE;    /* exception should occur in caller */
}


/*
 * Class:     NativeException
 * Method:    testExceptionClear
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testExceptionClear
  (JNIEnv *env, jclass cls, jintArray intArray) {

  int i;
  int size = 10;
  jint *buf = (jint *) malloc(sizeof(int) * size);
  for (i = 0; i<size; i++) {
    buf[i] = buf[i] + 1;
  }

  /* force an ArrayIndexOutOfBoundsException */
  (*env) -> SetIntArrayRegion(env, intArray, 5, 10, buf);

  (*env) -> ExceptionClear(env);

  return JNI_TRUE;

}



/*
 * Class:     NativeException
 * Method:    testExceptionDescribe
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testExceptionDescribe
  (JNIEnv *env, jclass cls, jintArray intArray) {

  int i;
  int size = 10;
  jint *buf = (jint *) malloc(sizeof(int) * size);
  for (i = 0; i<size; i++) {
    buf[i] = buf[i] + 1;
  }

  /* force an ArrayIndexOutOfBoundsException */
  (*env) -> SetIntArrayRegion(env, intArray, 5, 10, buf);

  (*env) -> ExceptionDescribe(env); /* also clear exception */

  return JNI_TRUE;

}

/*
 * Class:     NativeException
 * Method:    testExceptionThrow
 * Signature: (Ljava/lang/Throwable;)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testExceptionThrow
  (JNIEnv *env, jclass cls, jthrowable e) {

  (*env) -> Throw(env, e);
  return JNI_FALSE;

}

/*
 * Class:     NativeException
 * Method:    testExceptionThrowNew
 * Signature: (Ljava/lang/Class;)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testExceptionThrowNew
  (JNIEnv *env, jclass cls, jclass eclass) {

  (*env) -> ThrowNew(env, eclass, "Test ThrowNew in native");
  return JNI_FALSE;

}

/*
 * Class:     NativeException
 * Method:    testFatalError
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_NativeException_testFatalError
  (JNIEnv *env, jclass cls, jboolean allTestPass, jintArray intArray) {

  if (allTestPass)
    (*env) -> FatalError(env, "PASS: FatalError\nPASS: NativeException.\n");

  return JNI_FALSE;

}
