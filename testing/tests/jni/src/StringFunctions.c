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
/* Test JNI Functions related to Strings
 * Implement native methods from StringFunctions.java
 */

#include <stdio.h>
#include "StringFunctions.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     StringFunctions
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_StringFunctions_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}


/*
 * Class:     StringFunctions
 * Method:    accessNewString
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_StringFunctions_accessNewString
  (JNIEnv *env, jclass cls, jstring s) {
  int i;

  /* unicode chars for "hiTon" */
  const unsigned char unicode_chars[10] = { 'h', 'i', 'T', 'o', 'n'};
  unsigned short unicode_short[5];
  jstring returnString;

  for (i=0; i<5; i++)
    unicode_short[i] = (short) unicode_chars[i];

  returnString = (*env) -> NewString(env, unicode_short, 5);
  if (verbose) {
    printf("> accessNewString: returnString = 0x%p\n", returnString);
  }
  return returnString;
}


/*
 * Class:     StringFunctions
 * Method:    accessGetStringLength
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_StringFunctions_accessGetStringLength
  (JNIEnv *env, jclass cls, jstring s) {

  jsize returnValue;

  returnValue = (*env) -> GetStringLength(env, s);
  if (verbose) {
    printf("> accessGetStringLength: returnValue = %d\n", returnValue);
  }
  return returnValue;
}


/*
 * Class:     StringFunctions
 * Method:    accessNewStringUTF
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_StringFunctions_accessNewStringUTF
  (JNIEnv *env, jclass cls, jstring s) {

  const char * ascii_chars = "hiSteve";
  jstring returnString;

  returnString = (*env) -> NewStringUTF(env, ascii_chars);
  if (verbose) {
    printf("> accessNewStringUTF: returnString = 0x%p\n", returnString);
  }

  return returnString;
}


/*
 * Class:     StringFunctions
 * Method:    accessGetStringUTFLength
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_StringFunctions_accessGetStringUTFLength
  (JNIEnv *env, jclass cls, jstring s) {

  jsize returnValue;

  returnValue = (*env) -> GetStringUTFLength(env, s);
  if (verbose) {
    printf("> accessGetStringUTFLength: returnValue = %d\n", returnValue);
  }
  return returnValue;
}

/*
 * Class:     StringFunctions
 * Method:    testGetReleaseStringChars
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_StringFunctions_testGetReleaseStringChars
  (JNIEnv *env, jclass cls, jstring s) {

    jboolean  isCopy;
    jint stringLen;
    const jchar * stringChars;
    jstring returnString;

    stringLen = (*env) -> GetStringLength(env, s);
    stringChars = (*env) -> GetStringChars(env, s, &isCopy);

    returnString = (*env) -> NewString(env, stringChars, stringLen);

    (*env) -> ReleaseStringChars(env, s, stringChars);

    if (verbose) {
        printf("> testGetReleaseStringChars: isCopy = %d\n", (int)isCopy);
        printf("> testGetReleaseStringChars: stringLen = %d\n", stringLen);
        printf("> testGetReleaseStringChars: returnString = 0x%p\n", returnString);
    }

    return returnString;
}

/*
 * Class:     StringFunctions
 * Method:    testGetReleaseStringUTFChars
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_StringFunctions_testGetReleaseStringUTFChars
  (JNIEnv *env, jclass cls, jstring s) {

    jboolean  isCopy;
    jint stringLenUTF;
    const char * stringBytesUTF;
    jstring returnString;

    stringLenUTF = (*env) -> GetStringUTFLength(env, s);
    stringBytesUTF = (*env) -> GetStringUTFChars(env, s, &isCopy);

    returnString = (*env) -> NewStringUTF(env, stringBytesUTF);

    (*env) -> ReleaseStringUTFChars(env, s, stringBytesUTF);

    if (verbose) {
        printf("> testGetReleaseStringUTFChars: isCopy = %d\n", (int)isCopy);
        printf("> testGetReleaseStringUTFChars: stringLenUTF = %d\n", stringLenUTF);
        printf("> testGetReleaseStringUTFChars: returnString = 0x%p\n", returnString);
    }

    return returnString;
}

