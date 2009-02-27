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
 * Implementation of Harmony VMI Invocation API for Jikes RVM.
 */

#define LINUX
#define TRACE 0
#include "bootImageRunner.h"
#include "vmi.h"
#include "zipsup.h"

struct VMInterfaceFunctions_ vmi_impl = {
   &CheckVersion,
   &GetJavaVM,
   &GetPortLibrary,
   &GetVMLSFunctions,
   #ifndef HY_ZIP_API
   &GetZipCachePool,
   #else /* HY_ZIP_API */
   &GetZipFunctions,
   #endif /* HY_ZIP_API */
   &GetInitArgs,
   &GetSystemProperty,
   &SetSystemProperty,
   &CountSystemProperties,
   &IterateSystemProperties
};

VMInterface vmi = &vmi_impl;
HyPortLibrary hyPortLibrary;
HyPortLibraryVersion hyPortLibraryVersion;
#ifndef HY_ZIP_API
HyZipCachePool *hyZipCachePool;
#endif

extern UDATA JNICALL HyVMLSAllocKeys (JNIEnv * env, UDATA * pInitCount, ...);
extern void JNICALL HyVMLSFreeKeys (JNIEnv * env, UDATA * pInitCount, ...);
extern void * JNICALL HyVMLSGet (JNIEnv * env, void *key);
extern void * JNICALL HyVMLSSet (JNIEnv * env, void **pKey, void *value);

HyVMLSFunctionTable vmls_impl = {
    &HyVMLSAllocKeys,
    &HyVMLSFreeKeys,
    &HyVMLSGet,
    &HyVMLSSet
};

HyZipCachePool* zipCachePool = NULL;

vmiError JNICALL CheckVersion (VMInterface * vmi, vmiVersion * version)
{
    return VMI_ERROR_UNIMPLEMENTED;
}

JavaVM * JNICALL GetJavaVM (VMInterface * vmi)
{
    return &sysJavaVM;
}

HyPortLibrary portLib;

extern HyPortLibrary hyPortLibrary;
HyPortLibrary * JNICALL GetPortLibrary (VMInterface * vmi)
{
    return &hyPortLibrary;
}

HyVMLSFunctionTable * JNICALL GetVMLSFunctions (VMInterface * vmi)
{
    if (TRACE) fprintf(stderr, "VMI call GetVMLSFunctions\n");
    return &vmls_impl;
}

#ifndef HY_ZIP_API
extern HyZipCachePool *hyZipCachePool;
HyZipCachePool * JNICALL GetZipCachePool (VMInterface * vmi)
{
    return hyZipCachePool;

}
#else /* HY_ZIP_API */
struct VMIZipFunctionTable * JNICALL GetZipFunctions (VMInterface * vmi)
{
    fprintf(stderr, "UNIMPLEMENTED VMI call GetZipFunctions\n");
    return NULL;
}
#endif /* HY_ZIP_API */

JavaVMInitArgs * JNICALL GetInitArgs (VMInterface * vmi)
{
    return JavaArgs;
}

vmiError JNICALL GetSystemProperty (VMInterface * vmi, char *key, char **valuePtr)
{
    if (TRACE) fprintf(stderr, "UNIMPLEMENTED VMI call GetSystemProperty\n");
    return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL SetSystemProperty (VMInterface * vmi, char *key, char *value)
{
    if (TRACE) fprintf(stderr, "UNIMPLEMENTED VMI call SetSystemProperty\n");
    return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL CountSystemProperties (VMInterface * vmi, int *countPtr)
{
    if (TRACE) fprintf(stderr, "UNIMPLEMENTED VMI call CountSystemProperties\n");
    return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL IterateSystemProperties (VMInterface * vmi, vmiSystemPropertyIterator iterator, void *userData)
{
    if (TRACE) fprintf(stderr, "UNIMPLEMENTED VMI call IterateSystemProperties\n");
    return VMI_ERROR_UNIMPLEMENTED;
}

/**
 * Extract the VM Interface from a JNI JavaVM
 *
 * @param[in] vm  The JavaVM to query
 *
 * @return a VMInterface pointer
 */
VMInterface* JNICALL 
VMI_GetVMIFromJavaVM(JavaVM* vm)
{
    return &vmi;
}

/**
 * Extract the VM Interface from a JNIEnv
 *
 * @param[in] env  The JNIEnv to query
 *
 * @return a VMInterface pointer
 */
VMInterface* JNICALL 
VMI_GetVMIFromJNIEnv(JNIEnv* env)
{
    return &vmi;
}

extern void initializeVMLocalStorage(JavaVM * vm);

void JNICALL
VMI_Initialize()
{
    HYPORT_SET_VERSION (&hyPortLibraryVersion, HYPORT_CAPABILITY_MASK);
    if (0 != hyport_init_library (&hyPortLibrary, &hyPortLibraryVersion, sizeof (HyPortLibrary))) {
        fprintf(stderr, "Harmony port library init failed\n");
        abort();
    }
#ifndef HY_ZIP_API
    hyZipCachePool = zipCachePool_new(&hyPortLibrary);
    if (hyZipCachePool == NULL)
    {
	fprintf(stderr, "Error accessing zip functions");
        abort();
    }
#endif
    initializeVMLocalStorage(&sysJavaVM);
}

