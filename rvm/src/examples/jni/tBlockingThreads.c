/*
 * (C) Copyright IBM Corp. 2001
 *$Id$
 */
/*
 * @author unascribed
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



