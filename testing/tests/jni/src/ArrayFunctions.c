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
/**
 * Test JNI Functions related to Array
 * Implement native methods from ArrayFunctions.java
 */

#include <stdio.h>
#include "ArrayFunctions.h"
#include <jni.h>
#include <stdlib.h>             /* malloc() prototype */

int verbose=1;
static char *savedArrayPointer;

/*
 * Class:     ArrayFunctions
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ArrayFunctions_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}


/*
 * Class:     ArrayFunctions
 * Method:    testArrayLength
 * Signature: ([I)I
 */
JNIEXPORT jint JNICALL Java_ArrayFunctions_testArrayLength
  (JNIEnv *env, jclass cls, jintArray sourceArray) {

  jint arrayLength = (*env) -> GetArrayLength(env, sourceArray);
  return arrayLength;

}



/*
 * Class:     ArrayFunctions
 * Method:    accessNewIntArray
 * Signature: (I)[I
 */
JNIEXPORT jintArray JNICALL Java_ArrayFunctions_accessNewIntArray
  (JNIEnv *env, jclass cls, jint length) {

  jintArray newArray = (*env) -> NewIntArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewBooleanArray
 * Signature: (I)[Z
 */
JNIEXPORT jbooleanArray JNICALL Java_ArrayFunctions_accessNewBooleanArray
  (JNIEnv *env, jclass cls, jint length) {

  jbooleanArray newArray = (*env) -> NewBooleanArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewShortArray
 * Signature: (I)[S
 */
JNIEXPORT jshortArray JNICALL Java_ArrayFunctions_accessNewShortArray
  (JNIEnv *env, jclass cls, jint length) {

  jshortArray newArray = (*env) -> NewShortArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewByteArray
 * Signature: (I)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ArrayFunctions_accessNewByteArray
  (JNIEnv *env, jclass cls, jint length) {

  jbyteArray newArray = (*env) -> NewByteArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewCharArray
 * Signature: (I)[C
 */
JNIEXPORT jcharArray JNICALL Java_ArrayFunctions_accessNewCharArray
  (JNIEnv *env, jclass cls, jint length) {

  jcharArray newArray = (*env) -> NewCharArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewLongArray
 * Signature: (I)[J
 */
JNIEXPORT jlongArray JNICALL Java_ArrayFunctions_accessNewLongArray
  (JNIEnv *env, jclass cls, jint length) {

  jlongArray newArray = (*env) -> NewLongArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewDoubleArray
 * Signature: (I)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_ArrayFunctions_accessNewDoubleArray
  (JNIEnv *env, jclass cls, jint length) {

  jdoubleArray newArray = (*env) -> NewDoubleArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewFloatArray
 * Signature: (I)[F
 */
JNIEXPORT jfloatArray JNICALL Java_ArrayFunctions_accessNewFloatArray
  (JNIEnv *env, jclass cls, jint length) {

  jfloatArray newArray = (*env) -> NewFloatArray(env, length);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    accessNewObjectArray
 * Signature: (ILjava/lang/Class;Ljava/lang/Object;)[Ljava/lang/Object;
 */
JNIEXPORT jobjectArray JNICALL Java_ArrayFunctions_accessNewObjectArray
  (JNIEnv *env, jclass cls, jint length, jclass elementClass, jobject initElement) {

  jobjectArray newArray = (*env) -> NewObjectArray(env, length, elementClass, initElement);
  return newArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testIntArrayRegion
 * Signature: ([I)[I
 */
JNIEXPORT jintArray JNICALL Java_ArrayFunctions_testIntArrayRegion
  (JNIEnv *env, jclass cls, jintArray intArray) {

  int i;
  int size = 10;
  jint *buf = (jint *) malloc(sizeof(int) * size);
  (*env) -> GetIntArrayRegion(env, intArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] = buf[i] + 1;
  }

  (*env) -> SetIntArrayRegion(env, intArray, 0, 10, buf);

  return intArray;

}



/*
 * Class:     ArrayFunctions
 * Method:    testBooleanArrayRegion
 * Signature: ([Z)[Z
 */
JNIEXPORT jbooleanArray JNICALL Java_ArrayFunctions_testBooleanArrayRegion
  (JNIEnv *env, jclass cls, jbooleanArray sourceArray) {

  int i;
  int size = 10;
  jboolean *buf = (jboolean *) malloc(sizeof(jboolean) * size);
  (*env) -> GetBooleanArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    if (buf[i])
      buf[i] = 0;
    else
      buf[i] = 1;
  }

  (*env) -> SetBooleanArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testShortArrayRegion
 * Signature: ([S)[S
 */
JNIEXPORT jshortArray JNICALL Java_ArrayFunctions_testShortArrayRegion
  (JNIEnv *env, jclass cls, jshortArray sourceArray) {

  int i;
  int size = 10;
  jshort *buf = (jshort *) malloc(sizeof(jshort) * size);
  (*env) -> GetShortArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] += 1;
  }

  (*env) -> SetShortArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testByteArrayRegion
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ArrayFunctions_testByteArrayRegion
  (JNIEnv *env, jclass cls, jbyteArray sourceArray) {

  int i;
  int size = 10;
  jbyte *buf = (jbyte *) malloc(sizeof(jbyte) * size);
  (*env) -> GetByteArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] += 1;
  }

  (*env) -> SetByteArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testCharArrayRegion
 * Signature: ([C)[C
 */
JNIEXPORT jcharArray JNICALL Java_ArrayFunctions_testCharArrayRegion
  (JNIEnv *env, jclass cls, jcharArray sourceArray) {

  int i;
  int size = 10;
  jchar *buf = (jchar *) malloc(sizeof(jchar) * size);
  (*env) -> GetCharArrayRegion(env, sourceArray, 0, 10, buf);

  buf[0] = 'j';
  buf[1] = 'a';
  buf[2] = 'l';
  buf[3] = 'a';
  buf[4] = 'p';
  buf[5] = 'e';
  buf[6] = 'n';
  buf[7] = 'o';
  buf[8] = 'v';
  buf[9] = 'm';

  (*env) -> SetCharArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testLongArrayRegion
 * Signature: ([J)[J
 */
JNIEXPORT jlongArray JNICALL Java_ArrayFunctions_testLongArrayRegion
  (JNIEnv *env, jclass cls, jlongArray sourceArray) {

  int i;
  int size = 10;
  jlong *buf = (jlong *) malloc(sizeof(jlong) * size);
  (*env) -> GetLongArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] += (long) i;
  }

  (*env) -> SetLongArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testDoubleArrayRegion
 * Signature: ([D)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_ArrayFunctions_testDoubleArrayRegion
  (JNIEnv *env, jclass cls, jdoubleArray sourceArray) {

  int i;
  int size = 10;
  jdouble *buf = (jdouble *) malloc(sizeof(jdouble) * size);
  (*env) -> GetDoubleArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] += (double) i;
  }

  (*env) -> SetDoubleArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testFloatArrayRegion
 * Signature: ([F)[F
 */
JNIEXPORT jfloatArray JNICALL Java_ArrayFunctions_testFloatArrayRegion
  (JNIEnv *env, jclass cls, jfloatArray sourceArray) {

  int i;
  int size = 10;
  jfloat *buf = (jfloat *) malloc(sizeof(jfloat) * size);
  (*env) -> GetFloatArrayRegion(env, sourceArray, 0, 10, buf);

  for (i = 0; i<size; i++) {
    buf[i] += (float) i;
  }

  (*env) -> SetFloatArrayRegion(env, sourceArray, 0, 10, buf);

  return sourceArray;

}

/*
 * Flag to say whether last Get<Type>ArrayElements returned a copy
 * or a direct pointer
 */
static jboolean lastGetArrayElementsWasCopy = JNI_FALSE;

/*
 * Class:     ArrayFunctions
 * Method:    lastGetArrayElementsWasCopy
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_ArrayFunctions_lastGetArrayElementsWasCopy
  (JNIEnv *env, jclass cls) {
  return lastGetArrayElementsWasCopy;
}

/*
 * Class:     ArrayFunctions
 * Method:    testIntArrayElements
 * Signature: ([II)[I
 */
JNIEXPORT jintArray JNICALL Java_ArrayFunctions_testIntArrayElements
  (JNIEnv *env, jclass cls, jintArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jint *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetIntArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 1;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseIntArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jint *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 2;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseIntArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetIntArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 3;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseIntArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testBooleanArrayElements
 * Signature: ([ZI)[Z
 */
JNIEXPORT jbooleanArray JNICALL Java_ArrayFunctions_testBooleanArrayElements
  (JNIEnv *env, jclass cls, jbooleanArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jboolean *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetBooleanArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    buf[0] = JNI_TRUE; buf[1] = JNI_FALSE; buf[2] = JNI_TRUE; buf[3] = JNI_FALSE;
    buf[4] = JNI_TRUE; buf[5] = JNI_FALSE; buf[6] = JNI_TRUE; buf[7] = JNI_FALSE;
    buf[8] = JNI_TRUE; buf[9] = JNI_FALSE;
    /* copy back but don't free the buffer */
    (*env) -> ReleaseBooleanArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jboolean *) savedArrayPointer;
    buf[0] = JNI_FALSE; buf[1] = JNI_TRUE; buf[2] = JNI_FALSE; buf[3] = JNI_TRUE;
    buf[4] = JNI_FALSE; buf[5] = JNI_TRUE; buf[6] = JNI_FALSE; buf[7] = JNI_TRUE;
    buf[8] = JNI_FALSE; buf[9] = JNI_TRUE;
    /* copy back and free the buffer */
    (*env) -> ReleaseBooleanArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetBooleanArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    buf[0] = JNI_FALSE; buf[1] = JNI_TRUE; buf[2] = JNI_FALSE; buf[3] = JNI_TRUE;
    buf[4] = JNI_FALSE; buf[5] = JNI_TRUE; buf[6] = JNI_FALSE; buf[7] = JNI_TRUE;
    buf[8] = JNI_FALSE; buf[9] = JNI_TRUE;
    /* free the buffer without copying back */
    (*env) -> ReleaseBooleanArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testByteArrayElements
 * Signature: ([BI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ArrayFunctions_testByteArrayElements
  (JNIEnv *env, jclass cls, jbyteArray sourceArray, jint testState) {


  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jbyte *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetByteArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 4;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseByteArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jbyte *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 5;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseByteArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetByteArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 6;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseByteArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testShortArrayElements
 * Signature: ([SI)[S
 */
JNIEXPORT jshortArray JNICALL Java_ArrayFunctions_testShortArrayElements
  (JNIEnv *env, jclass cls, jshortArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jshort *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetShortArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 7;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseShortArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jshort *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 8;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseShortArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetShortArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 9;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseShortArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testCharArrayElements
 * Signature: ([CI)[C
 */
JNIEXPORT jcharArray JNICALL Java_ArrayFunctions_testCharArrayElements
  (JNIEnv *env, jclass cls, jcharArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jchar *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetCharArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    buf[0] = 'a'; buf[1] = 'b'; buf[2] = 'c'; buf[3] = 'd';
    buf[4] = 'e'; buf[5] = 'f'; buf[6] = 'g'; buf[7] = 'h';
    buf[8] = 'i'; buf[9] = 'j';
    /* copy back but don't free the buffer */
    (*env) -> ReleaseCharArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jchar *) savedArrayPointer;
    buf[0] = 'j'; buf[1] = 'a'; buf[2] = 'l'; buf[3] = 'e';
    buf[4] = 'p'; buf[5] = 'e'; buf[6] = 'n'; buf[7] = 'o';
    buf[8] = 'v'; buf[9] = 'm';
    /* copy back and free the buffer */
    (*env) -> ReleaseCharArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetCharArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = 'x';
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseCharArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testLongArrayElements
 * Signature: ([JI)[J
 */
JNIEXPORT jlongArray JNICALL Java_ArrayFunctions_testLongArrayElements
  (JNIEnv *env, jclass cls, jlongArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jlong *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetLongArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 10;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseLongArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jlong *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 11;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseLongArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetLongArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 12;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseLongArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testDoubleArrayElements
 * Signature: ([DI)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_ArrayFunctions_testDoubleArrayElements
  (JNIEnv *env, jclass cls, jdoubleArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jdouble *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetDoubleArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 13.0;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseDoubleArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jdouble *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 14.0;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseDoubleArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetDoubleArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + 15.0;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseDoubleArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}

/*
 * Class:     ArrayFunctions
 * Method:    testFloatArrayElements
 * Signature: ([FI)[F
 */
JNIEXPORT jfloatArray JNICALL Java_ArrayFunctions_testFloatArrayElements
  (JNIEnv *env, jclass cls, jfloatArray sourceArray, jint testState) {

  int i;
  int size = 10;
  jboolean isCopy = JNI_FALSE;
  jfloat *buf;

  switch (testState) {

  case 0:
    /* first time, get the array copy, update and save the pointer */
    buf = (*env) -> GetFloatArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    savedArrayPointer = (char *) buf;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + (float) 16.0;
    }
    /* copy back but don't free the buffer */
    (*env) -> ReleaseFloatArrayElements(env, sourceArray, buf, JNI_COMMIT);
    break;

  case 1:
    /* second time, reuse the array copy via the saved pointer */
    buf = (jfloat *) savedArrayPointer;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + (float) 17.0;
    }
    /* copy back and free the buffer */
    (*env) -> ReleaseFloatArrayElements(env, sourceArray, buf, 0);
    savedArrayPointer = 0;
    buf = 0;
    break;

  default:
    /* third time, get the array copy again, modify but don't update */
    buf = (*env) -> GetFloatArrayElements(env, sourceArray, &isCopy);
    lastGetArrayElementsWasCopy = isCopy;
    for (i = 0; i<size; i++) {
      buf[i] = buf[i] + (float) 18.0;
    }
    /* free the buffer without copying back */
    (*env) -> ReleaseFloatArrayElements(env, sourceArray, buf, JNI_ABORT);
    break;
  }

  return sourceArray;

}


/*
 * Class:     ArrayFunctions
 * Method:    testObjectArrayElement
 * Signature: ([Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_ArrayFunctions_testObjectArrayElement
  (JNIEnv *env, jclass cls, jobjectArray sourceArray, jobject toAssign,
   jint index_) {

  /* get the current element */
  jobject previous = (*env) -> GetObjectArrayElement(env, sourceArray, index_);

  /* change the element at this index */
  (*env) -> SetObjectArrayElement(env, sourceArray, index_, toAssign);

  return previous;

}
