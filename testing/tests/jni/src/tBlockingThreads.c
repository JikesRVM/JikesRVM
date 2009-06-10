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
#include <stdio.h>
#include "tBlockingThreads.h"
#include <jni.h>

/*
 * Class:     tBlockingThreads
 * Method:    nativeBlocking
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_tBlockingThreads_nativeBlocking
(JNIEnv * env, jclass cls, jint time) {

  printf("nativeBlocking: sleeping for time =%d \n", time);

  sleep(time);

  printf("nativeBlocking: returning\n");
}



