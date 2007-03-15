/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */

/*
 * @author Dick Anttanasio
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
