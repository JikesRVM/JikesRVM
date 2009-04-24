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
/*
 * Test that NULL is assigned JNI id 0.
 * Implement native methods from NullIdentity.java
 */

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>             /* malloc() */
#include <math.h>               /* fabs() */

#include "MethodInvocation.h"
#include <jni.h>

int verbose=1;


JNIEXPORT void JNICALL Java_NullIdentity_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}


JNIEXPORT jint JNICALL Java_NullIdentity_nullFirst(JNIEnv *env,
                                                   jclass cls,
                                                   jobject o1) {

    int ans = 0;
    if (o1 != 0) {
        if (verbose) fprintf(stderr, "nullFirst: NULL not equal to id 0");
        return 1;
    }
    return 0;
}


JNIEXPORT jint JNICALL Java_NullIdentity_nullSecond(JNIEnv *env,
                                                    jclass cls,
                                                    jobject o1,
                                                    jobject o2){

    int ans = 0;
    if (o2 != 0) {
        if (verbose) fprintf(stderr, "nullSecond: NULL not equal to id 0");
        return 1;
    }
    return 0;
}


JNIEXPORT jint JNICALL Java_NullIdentity_nullForceSpill(JNIEnv *env,
                                                        jclass cls,
                                                        jobject o1,
                                                        jobject o2,
                                                        jobject o3,
                                                        jobject o4,
                                                        jobject o5,
                                                        jobject o6,
                                                        jobject o7,
                                                        jobject o8,
                                                        jobject o9,
                                                        jobject o10,
                                                        jobject o11,
                                                        jobject o12) {
    int ans = 0;
    if (o12 != 0) {
        if (verbose) fprintf(stderr, "nullForceSpill: NULL not equal to id 0");
        return 1;
    }
    return 0;
}

