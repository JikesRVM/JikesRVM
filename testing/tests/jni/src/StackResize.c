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
/* Test stack resize with native methods
 * Implement native methods from StackResize.java
 */

#include <stdio.h>
#include "StackResize.h"
#include <jni.h>

int verbose=0;


/*
 * Class:     StackResize
 * Method:    expectResize
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_StackResize_expectResize
  (JNIEnv *env, jclass cls, jint previousStackSize){
  jboolean returnBooleanValue;
  jmethodID methodID;


  /* First check to see if the stack has been resized on the first
   * transition to native code
   */
  methodID = (*env) -> GetStaticMethodID(env, cls, "checkResizeOccurred", "(I)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method checkResizeOccurred\n");
    return JNI_FALSE;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethod(env, cls, methodID,
                                                         previousStackSize);
  if (returnBooleanValue == JNI_FALSE) {
    if (verbose)
      printf("> FAIL to resize stack on first native method\n");
    return JNI_FALSE;
  }

  /* Next call back to Java to make another native call */
  methodID = (*env) -> GetStaticMethodID(env, cls, "makeSecondNativeCall", "()Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method makeSecondNativeCall\n");
    return JNI_FALSE;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethod(env, cls, methodID);

  if (returnBooleanValue == JNI_FALSE) {
    if (verbose)
      printf("> Error: stack should not be resized on second native method\n");
    return JNI_FALSE;
  }

  return JNI_TRUE;

}


/*
 * Class:     StackResize
 * Method:    expectNoResize
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_StackResize_expectNoResize
  (JNIEnv *env, jclass cls, jint previousStackSize){
  jboolean returnBooleanValue;
  jmethodID methodID;

  /* check to see if the stack has been resized on subsequent
   * transition to native code
   */
  methodID = (*env) -> GetStaticMethodID(env, cls, "checkResizeOccurred", "(I)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method checkResizeNotOccurred\n");
    return JNI_FALSE;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethod(env, cls, methodID,
                                                         previousStackSize);
  if (returnBooleanValue == JNI_TRUE) {
    if (verbose)
      printf("> Unexpected stack resize on second native method\n");
    return JNI_FALSE;
  }

  return JNI_TRUE;   /* test pass */


}


