/*
 * (C) Copyright IBM Corp. 2001
 */
/* Test miscellaneous query JNI Functions 
 * Implement native methods from ClassQuery.java 
 * 
 * Ton Ngo, Steve Smith 3/21/00
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
