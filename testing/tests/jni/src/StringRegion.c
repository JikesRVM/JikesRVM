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
 * Test JNI 1.2 Functions related to Strings
 * Implement native methods from StringRegion.java
 */

#include <jni.h>
#include <stddef.h>             /* size_t */
#include <stdio.h>              /* printf() */
#include <string.h>             /* strcmp() */
/* Native methods for Class StringRegion */

#include "StringRegion.h"

static int verbose = 1;

/*
 * Class:     StringRegion
 * Method:    setVerboseOff
 * Signature: ()V
 */
void JNICALL
Java_StringRegion_setVerboseOff(JNIEnv * env, jclass cls)
{
    verbose = 0;
}


static const char sample[] = "Live Free or Die";
static const size_t nchars = sizeof sample - 1;


/*
 * Class:     StringRegion
 * Method:    testStringRegion
 * Signature: (Ljava/lang/String;)I
 */
jint JNICALL
Java_StringRegion_testStringRegion(JNIEnv *env, jclass cls, jstring str)
{
    int trouble = 0;

    jchar buf1[nchars];
    jchar *bufp = buf1;
    jsize offset = 3;
    jsize len = nchars - offset;
    size_t i;
    char buf2[nchars + 1];      /* trailing null? */

    (*env)->GetStringRegion(env, str, offset, len, bufp);
    if ((*env) -> ExceptionCheck(env)) {
        fprintf(stderr, "> Unexpected exception from GetStringRegion\n");
    }

    for (i = 0; i < nchars - offset; ++i) {
        int sampIdx = offset + i;
        if (buf1[i] != sample[sampIdx]) {
            fprintf(stderr, "> buf1[%d] = '%c', sample[%d] = '%c'\n",
                    i, buf1[i], sampIdx, sample[sampIdx]);
            ++trouble;
        }
    }

    // Test for the exception (error reporting) too; sigh.
    (*env)->GetStringRegion(env, str, 1, nchars, buf1);
    if (!(*env)->ExceptionCheck(env)) {
        fprintf(stderr, "> Should have triggered a StringIndexOutOfBoundsException; did not!\n");
        ++trouble;
    }
    (*env)->ExceptionClear(env);

    // Now GetStringUTFRegion.  We are using only low-bit chars, so we should
    // get a string-for-string match with bytes.
    (*env)->GetStringUTFRegion(env, str, 0, nchars, buf2);
    if ((*env) -> ExceptionCheck(env)) {
        fprintf(stderr, "> Unexpected exception from GetStringUTFRegion\n");
        ++trouble;
    }
    buf2[nchars] = '\0';

    if ( strcmp(sample, buf2) != 0 ) {
        fprintf (stderr, "> GetStringUTFRegion: Expected \"%s\", got \"%s\"\n",
                 sample, buf2);
        ++trouble;
    }

    return trouble;
}


/*
 * Class:     StringRegion
 * Method:    testStringCritical
 * Signature: (Ljava/lang/String;)I
 *
 * It's supposed to get the string critically and then mutate the string.
 */
jint JNICALL
Java_StringRegion_testStringCritical(JNIEnv * env, jclass cls, jstring str)
{
    int trouble = 0;
    jboolean isCopy;
    int offset = 0;
    size_t i;

    jchar *js = (*env)->GetStringCritical(env, str, &isCopy);
    if (! js) {
        fprintf(stderr, "> GetStringCritical returned NULL!\n");
        return 1;
    }
    if ((*env) -> ExceptionCheck(env)) {
        fprintf(stderr, "> Unexpected exception from GetStringCritical\n");
        ++trouble;
    }

//    if ( isCopy ) {
//        fprintf(stderr, "> GetStringCritical returned a copy; should not happen!!\n");
//    }

    for (i = 0; i < nchars; ++i) {
        int sampIdx = i;
        if (js[i] != sample[sampIdx]) {
            fprintf(stderr, "> js[%d] = '%c', sample[%d] = '%c'\n",
                    i, js[i], sampIdx, sample[sampIdx]);
            ++trouble;
        }
    }
    for (i = 0; i < 9; ++i) {
        js[i] = "Free Java"[i];
    }


    (*env)->ReleaseStringCritical(env, str, js);
    if ((*env) -> ExceptionCheck(env)) {
        fprintf(stderr, "> Unexpected exception from ReleaseStringCritical\n");
        ++trouble;
    }

    return trouble;
}

