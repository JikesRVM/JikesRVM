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
/* Test miscellaneous query JNI Functions
 * Implement native methods from ClassQuery.java
 */

#include <stdio.h>
#include "ClassQuery.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     ClassQuery
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_ClassQuery_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}

/*
 * Class:     ClassQuery
 * Method:    testSuperClass
 * Signature: (Ljava/lang/Class;)Ljava/lang/Class;
 */
JNIEXPORT jclass JNICALL Java_ClassQuery_testSuperClass
  (JNIEnv *env, jclass cls, jclass subcls) {

  jclass supercls = (*env) -> GetSuperclass(env, subcls);
  return supercls;

}

/*
 * Class:     ClassQuery
 * Method:    testAssignable
 * Signature: (Ljava/lang/Class;Ljava/lang/Class;)Z
 */
JNIEXPORT jboolean JNICALL Java_ClassQuery_testAssignable
  (JNIEnv *env, jclass cls, jclass subcls, jclass supercls) {

  return (*env) -> IsAssignableFrom(env, subcls, supercls);

}

/*
 * Class:     ClassQuery
 * Method:    testSameObject
 * Signature: (Ljava/lang/Object;Ljava/lang/Object;)Z
 */
JNIEXPORT jboolean JNICALL Java_ClassQuery_testSameObject
  (JNIEnv *env, jclass cls, jobject obj1, jobject obj2) {

  return (*env) -> IsSameObject(env, obj1, obj2);

}



/*
 * Class:     ClassQuery
 * Method:    testAllocObject
 * Signature: (Ljava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_ClassQuery_testAllocObject
  (JNIEnv *env, jclass cls, jclass classToCreate) {

  return (*env) -> AllocObject(env, classToCreate);

}


/*
 * Class:     ClassQuery
 * Method:    testGetObjectClass
 * Signature: (Ljava/lang/Object;)Ljava/lang/Class;
 */
JNIEXPORT jclass JNICALL Java_ClassQuery_testGetObjectClass
  (JNIEnv *env, jclass cls, jobject obj) {

  return (*env) -> GetObjectClass(env, obj);

}
