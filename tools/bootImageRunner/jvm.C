/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/**
 * Implementation of JNI Invocation API for Jikes RVM.
 */

#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_EXIT_STATUS_CODES
#include <stdlib.h>
#include "InterfaceDeclarations.h"
#include "bootImageRunner.h"    // In tools/bootImageRunner.

// Fish out an address stored in an instance field of an object.
static void *
getFieldAsAddress(void *objPtr, int fieldOffset)
{
    char *fieldAddress = ((char*) objPtr) + fieldOffset;
    return *((void**) fieldAddress);
}

// Get the JNI environment object from the Processor.
static JNIEnv *
getJniEnvFromVmThread(void *vmThreadPtr)
{
    if (vmThreadPtr == 0)
        return 0; // oops

    // Follow chain of pointers:
    // RVMThread -> JNIEnvironment -> thread's native JNIEnv
    void *jniEnvironment =
        getFieldAsAddress(vmThreadPtr, RVMThread_jniEnv_offset);
    // Convert JNIEnvironment to JNIEnv* expected by native code
    // by creating the appropriate interior pointer.
    void *jniEnv = ((char*)jniEnvironment + JNIEnvironment_JNIExternalFunctions_offset);

    return (JNIEnv*) jniEnv;
}


//////////////////////////////////////////////////////////////
// JNI Invocation API functions
//////////////////////////////////////////////////////////////

/** Destroying the Java VM only makes sense if programs can create a VM
 * on-the-fly.   Further, as of Sun's Java 1.2, it sitll didn't support
 * unloading virtual machine instances.  It is supposed to block until all
 * other user threads are gone, and then return an error code.
 *
 * TODO: Implement.
 */
static
jint
DestroyJavaVM(JavaVM UNUSED * vm)
{
    fprintf(stderr, "JikesRVM: Unimplemented JNI call DestroyJavaVM\n");
    return JNI_ERR;
}

/* "Trying to attach a thread that is already attached is a no-op".  We
 * implement that common case.  (In other words, it works like GetEnv()).
 * However, we do not implement the more difficult case of actually attempting
 * to attach a native thread that is not currently attached to the VM.
 *
 * TODO: Implement for actually attaching unattached threads.
 */
static
jint
AttachCurrentThread(JavaVM UNUSED * vm, /* JNIEnv */ void ** penv, /* JavaVMAttachArgs */ void *args)
{
    JavaVMAttachArgs *aargs = (JavaVMAttachArgs *) args;
    jint version;
    if (args == NULL) {
        version = JNI_VERSION_1_1;
    } else {
        version = aargs->version ;
        /* We'd like to handle aargs->name and aargs->group */
    }

    // Handled for us by GetEnv().  We do it here anyway so that we avoid
    // printing an error message further along in this function.
    if (version > JNI_VERSION_1_4)
        return JNI_EVERSION;

    /* If we're already attached, we're gold. */
    register jint retval = GetEnv(vm, penv, version);
    if (retval == JNI_OK)
        return retval;
    else if (retval == JNI_EDETACHED) {
        fprintf(stderr, "JikesRVM: JNI call AttachCurrentThread Unimplemented for threads not already attached to the VM\n");
    } else {
        fprintf(stderr, "JikesRVM: JNI call AttachCurrentThread failed; returning UNEXPECTED error code %d\n", (int) retval);
    }

    // Upon failure:
    *penv = NULL;               // Make sure we don't yield a bogus one to use.
    return retval;
}

/* TODO: Implement */
static
jint
DetachCurrentThread(JavaVM UNUSED *vm)
{
    fprintf(stderr, "UNIMPLEMENTED JNI call DetachCurrentThread\n");
    return JNI_ERR;
}

jint
GetEnv(JavaVM UNUSED *vm, void **penv, jint version)
{
    if (version > JNI_VERSION_1_4)
        return JNI_EVERSION;

    // Return NULL if we are not on a VM thread
    void *vmThread = getVmThread();
    if (vmThread) {
        *penv = NULL;
        return JNI_EDETACHED;
    }

    // Get the JNIEnv from the RVMThread object
    JNIEnv *env = getJniEnvFromVmThread(vmThread);

    *penv = env;

    return JNI_OK;
}

/** JNI 1.4 */
/* TODO: Implement */
static
jint
AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, /* JNIEnv */ void UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args)
{
    fprintf(stderr, "Unimplemented JNI call AttachCurrentThreadAsDaemon\n");
    return JNI_ERR;
}

const struct JNIInvokeInterface_ externalJNIFunctions = {
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  DestroyJavaVM,
  AttachCurrentThread,
  DetachCurrentThread,
  GetEnv,         // JNI 1.2
  AttachCurrentThreadAsDaemon   // JNI 1.4
};

struct JavaVM_ sysJavaVM = {
  &externalJNIFunctions, // functions
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  NULL, // threadIDTable
  NULL, // jniEnvTable
};

