/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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



