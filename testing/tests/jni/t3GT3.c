/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$

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
