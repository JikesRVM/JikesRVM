/*
 * (C) Copyright IBM Corp. 2001
 */
/* test native code for jni
 */

#include <stdio.h>
#include "tNative.h"
#include <jni.h>


JNIEXPORT jint JNICALL Java_tNative_nativeFoo
  (JNIEnv *env, jclass cls, jint value) {
  int localval = value + 15;
  jintArray myArray;

  printf("Java_tTango_nativeFoo: reached native code with 0x%X 0x%X %d \n", env, cls, value); 

  myArray = (*env) -> NewIntArray(env, 11);
  printf("Java_tTango_nativeFoo: JNI call returns 0x%X\n", myArray);

  return localval;
}
