/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Elias Naur 2006
 *
 * $Id$
 *
 * @author Elias Naur
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
