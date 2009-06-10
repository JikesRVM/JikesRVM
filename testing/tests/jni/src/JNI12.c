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
/* Test method invocation from native code
 * Implement native methods from JNI12.java
 */

#include <stdio.h>
#include <stdlib.h>             /* malloc() */

#include <jni.h>
#include "JNI12.h"

#define TRACE 0
int verbose=1;

/*
 * Class:     Allocation
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_JNI12_setVerboseOff (JNIEnv *env, jclass cls)
{
    verbose=0;
}


jweak heldWeak = NULL;
jobject heldGlobal = NULL;

/*
 * Class:     JNI12
 * Method:    testReflectedMethods
 * Signature: (Ljava/lang/Class;Ljava/lang/reflect/Method;)Ljava/lang/reflect/Method;
 */
jobject JNICALL
Java_JNI12_testReflectedMethods(JNIEnv *env, jclass mine, jclass cls, jobject oldMethObj)
{
    jmethodID methID;
    jobject newMethObj;
    if (TRACE)
        fprintf(stderr, "Entering testReflectedMethods\n");
    methID = (*env)->FromReflectedMethod(env, oldMethObj);
    if (TRACE || verbose)
        printf("derived methID = %p\n", methID);
    if ( ! methID ) {
        fprintf(stderr, "FromReflectedMethod failed!\n");
        return NULL;
    }
    newMethObj =
        (*env)->ToReflectedMethod(env, cls, methID, JNI_FALSE);

    if (TRACE)
        fprintf(stderr, "Exiting testReflectedMethods\n");
    return newMethObj;
}


jobject JNICALL
Java_JNI12_testGlobalCreationAndReturn(JNIEnv *env, jclass mine, jobject methObj)
{
    if (TRACE)
        fprintf(stderr, "Trying NewGlobalRef\n");
    heldGlobal = (*env)->NewGlobalRef(env, methObj);
    if (TRACE)
        fprintf(stderr, "methObj = %p ==> heldGlobal = %p \n", methObj, heldGlobal);
    return heldGlobal;
}


jobject JNICALL
Java_JNI12_testWeakCreationAndReturn(JNIEnv *env, jclass mine, jobject methObj)
{
    if (TRACE)
        fprintf(stderr, "Trying NewWeakGlobalRef\n");
    heldWeak = (*env)->NewWeakGlobalRef(env, methObj);
    if (TRACE)
        fprintf(stderr, "methObj = %p ==> heldWeak = %p \n", methObj, heldWeak);
    return heldWeak;
}


/** 0 on success, nonzero on failure. */
jint JNICALL
Java_JNI12_testGlobalPersistenceAndDestruction(JNIEnv *env, jclass mine, jobject passedNewMethObj)
{
    if (TRACE)
        fprintf(stderr, "methObj = %p ==> heldGlobal = %p \n", passedNewMethObj, heldGlobal);
    if (! (*env)->IsSameObject(env, heldGlobal, passedNewMethObj)) {
        fprintf(stderr, "IsSameObject failed on retained global ref!\n");
        return -1;
    }

    (*env)->DeleteGlobalRef(env, heldGlobal);
    heldGlobal = NULL;
    return 0;                   /* OK */
}

/** 0 on success, nonzero on failure. */
jint JNICALL
Java_JNI12_testWeakPersistenceAndDestruction(JNIEnv *env, jclass mine, jobject passedNewMethObj)
{
    if (TRACE)
        fprintf(stderr, "methObj = %p ==> heldWeak = %p \n", passedNewMethObj, heldWeak);
    if (! (*env)->IsSameObject(env, heldWeak, passedNewMethObj)) {
        fprintf(stderr, "IsSameObject failed on retained weak ref!\n");
        return -1;
    }

    (*env)->DeleteWeakGlobalRef(env, heldWeak);
    heldWeak = NULL;
    return 0;                   /* OK */
}



/*
 * Class:     JNI12
 * Method:    testReflectedFields
 * Signature: (Ljava/lang/Class;Ljava/lang/reflect/Field;)Ljava/lang/reflect/Field;
 *  Also tests NewLocalRef.
 */
jobject JNICALL
Java_JNI12_testReflectedFields(JNIEnv *env, jclass myClass,
                               jclass cls, jobject oldFldObj)
{
    jfieldID fldID;
    jobject newFldObj;
    if (TRACE) {
        fprintf(stderr, "Entering testReflectedFields\n");
        fprintf(stderr, "oldFldObj = %p \n", oldFldObj);
    }
    fldID = (*env)->FromReflectedField(env, oldFldObj);
    if (TRACE)
        fprintf(stderr, "ran FromReflectedField\n");
    if (TRACE || verbose)
        printf("derived fldID = %p\n", fldID);
    if ( ! fldID ) {
        fprintf(stderr, "FromReflectedField failed!\n");
        return NULL;
    }
    if (TRACE)
        fprintf(stderr, "calling ToReflectedField\n");
    newFldObj = (*env)->ToReflectedField(env, cls, fldID, JNI_FALSE);
    if (TRACE) {
        fprintf(stderr, "ran ToReflectedField\n");
        fprintf(stderr, "Exiting testReflectedFields\n");
    }
    /** Test NewLocalRef while we're at it. */
    return (*env)->NewLocalRef(env, newFldObj);
}

/* Local Variables: */
/* c-font-lock-extra-types: ("JNIEnv" "jclass" "jweak" "jfieldID" "jobject" "jmethodID") */
/* End: */
