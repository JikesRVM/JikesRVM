/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Elias Naur 2006
 *
 *
 * @author Elias Naur
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
