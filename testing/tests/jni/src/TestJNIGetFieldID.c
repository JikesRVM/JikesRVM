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

#include <jni.h>

JNIEXPORT jint JNICALL Java_TestJNIGetFieldID_getInstanceFieldA(JNIEnv *env, jclass clazz, jobject o)
{
  jclass o_clazz = (*env)->GetObjectClass(env, o);
  jfieldID fid = (*env)->GetFieldID(env, o_clazz, "a", "I");
  if (fid == NULL) {return 0;}
  return (*env)->GetIntField(env, o, fid);
}

JNIEXPORT jint JNICALL Java_TestJNIGetFieldID_getStaticFieldS(JNIEnv *env, jclass clazz, jclass b_clazz)
{
  jfieldID fid = (*env)->GetStaticFieldID(env, b_clazz, "s", "I");
  if (fid == NULL) {return 0;}
  return (*env)->GetStaticIntField(env, b_clazz, fid);
}

JNIEXPORT jint JNICALL Java_TestJNIGetFieldID_getStaticFinalF(JNIEnv *env, jclass clazz, jclass b_clazz)
{
  jfieldID fid = (*env)->GetStaticFieldID(env, b_clazz, "f", "I");
  if (fid == NULL) {return 0;}
  return (*env)->GetStaticIntField(env, b_clazz, fid);
}
