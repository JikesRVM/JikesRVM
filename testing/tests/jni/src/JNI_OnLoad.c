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
#include <inttypes.h>
#include <jni.h>
#include "JNI_OnLoad.h"

static int verbose=1;
static jboolean onloadIsCalled = JNI_FALSE;

/*
 * Class:     FieldAccess
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_JNI_1OnLoad_setVerboseOff
(JNIEnv *env, jclass cls){
  verbose=0;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  onloadIsCalled = JNI_TRUE;
  return JNI_VERSION_1_4;
}

JNIEXPORT jint JNICALL Java_JNI_1OnLoad_testJNIOnLoad(JNIEnv *env, jclass clazz) {
  return onloadIsCalled ? 0 : 1;
}
