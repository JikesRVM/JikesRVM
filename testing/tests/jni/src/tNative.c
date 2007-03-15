/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/* test native code for jni
 *
 * @author unascribed
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
