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
#include <unistd.h>
#include "t3GT3.h"
#include <jni.h>

/*
 * Class:     t3GT3
 * Method:    nativeBlocking
 * Signature: (I)I
 */
JNIEXPORT void JNICALL Java_t3GT3_nativeBlocking
(JNIEnv * env, jclass cls, jint time) {

//  printf("nativeBlocking: sleeping for time =%d \n", time);

  sleep(time);

// printf("nativeBlocking: returning\n");
}
