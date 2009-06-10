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
#include "TestJNIDirectBuffers.h"

int verbose=1;

/*
 * Class:     FieldAccess
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_TestJNIDirectBuffers_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}

#define SIZE 10

static jbyte native_bytes[SIZE];

JNIEXPORT void JNICALL Java_TestJNIDirectBuffers_putByte(JNIEnv *env, jclass clazz, jobject buffer, jint index, jbyte b) {
	jbyte *bytes = (jbyte *)(*env)->GetDirectBufferAddress(env, buffer);
	bytes[index] = b;
}

JNIEXPORT jbyte JNICALL Java_TestJNIDirectBuffers_getByte(JNIEnv *env, jclass clazz, jobject buffer, jint index) {
	jbyte *bytes = (jbyte *)(*env)->GetDirectBufferAddress(env, buffer);
	return bytes[index];
}

JNIEXPORT jlong JNICALL Java_TestJNIDirectBuffers_getStaticNativeCapacity(JNIEnv *env, jclass clazz) {
	return SIZE;
}

JNIEXPORT jlong JNICALL Java_TestJNIDirectBuffers_getStaticNativeAddress(JNIEnv *env, jclass clazz) {
	return (jlong)(intptr_t)native_bytes;
}

JNIEXPORT jlong JNICALL Java_TestJNIDirectBuffers_getAddress(JNIEnv *env, jclass clazz, jobject buffer) {
	void *address = (*env)->GetDirectBufferAddress(env, buffer);
	return (jlong)(intptr_t)address;
}

JNIEXPORT jobject JNICALL Java_TestJNIDirectBuffers_newByteBuffer(JNIEnv *env, jclass clazz, jlong address, jlong capacity) {
	return (*env)->NewDirectByteBuffer(env, (void *)(intptr_t)address, capacity);
}
