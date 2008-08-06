/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * @file
 * @ingroup Reference
 * @brief Reference implementation of VM-local storage.  
 *
 * @detail VM-local storage (VMLS) is similar to thread-local storage (TLS) in that it provides 
 * key-value storage slots that can hold arbitrary data.  While TLS provides per-thread
 * slots associated with a fixed key, VMLS provides per-JavaVM space associated with a fixed key.
 * 
 * VMLS is used as replacement for static variables which would otherwise prevent multiple
 * JavaVM instances from co-existing in the same process.  Instead of storing values (such as
 * JNI method/field ID's) in static variables -- allocate a VMLS key and use the VMI functions
 * to get/set it's value on a per-vm basis.
 */

#define USING_VMI

#include "jni.h"			/* for definitions of JavaVM */
#include "hycomp.h"			/* for portable types (UDATA,etc...) */
#include "hythread.h"		/* for synchronization */
#include "hyport.h"			/* for port library */
#include "vmi.h"			/* for VMI functions */
#include <string.h>			/* for memset */

/* A constant that bounds the number of keys available. */
#define VMLS_MAX_KEYS 256

/**
 * A structure to hold per-vm data.
 */
typedef struct VMLSContainer {
	JavaVM* javaVM;
	void** vmLocalStorage[VMLS_MAX_KEYS];
	struct VMLSContainer* linkNext;
} VMLSContainer;

/**
  * A structure used to hold process-level VMLS information.
  */
typedef struct HyVMLSTable {
 	UDATA initialized;
 	UDATA head;
 	UDATA freeKeys;
 	UDATA keys[VMLS_MAX_KEYS];
	VMLSContainer* containerHead;
} HyVMLSTable;

/* An instance of the VMLS data shared by all JavaVMs */
static HyVMLSTable VMLSTable;


/**
 * Destroy one or more slots of VM local storage. 
 *
 * @code void  JNICALL HyVMLSFreeKeys(JNIEnv * env, UDATA * pInitCount, ...); @endcode
 *
 * @param[in] env  A JNIEnv pointer
 * @param[in] pInitCount  Pointer to the reference count for these slots
 * @param[out] ...  Pointers to the allocated keys
 */
void JNICALL HyVMLSFreeKeys(JNIEnv * env, UDATA * pInitCount, ...)
{
#if defined(HY_NO_THR)
	THREAD_ACCESS_FROM_ENV(env);
#endif /* HY_NO_THR */
	va_list args;
	HyVMLSTable* vmls = GLOBAL_DATA(VMLSTable);

	/* Obtain a process-level lock */
	hythread_monitor_t globalMonitor = hythread_global_monitor();
	hythread_monitor_enter(globalMonitor);

	if (--(*pInitCount) == 0) {
		void ** pKey;

		va_start(args, pInitCount);
		while ((pKey = va_arg(args, void *)) != NULL) {
			UDATA	key;

			key = (UDATA) *pKey;
			*pKey = NULL;
			if (vmls->keys[key] == (UDATA) -1) {
				vmls->keys[key] = vmls->head;
				vmls->head = key;
				++vmls->freeKeys;
			}
		}
		va_end(args);
	}

	/* Release the lock */
	hythread_monitor_exit(globalMonitor);
}


/**
 * Allocate one or more slots of VM local storage. 
 *
 * @code UDATA  JNICALL HyVMLSAllocKeys(JNIEnv * env, UDATA * pInitCount, ...); @endcode
 *
 * @param[in] env  A JNIEnv pointer
 * @param[in/out] pInitCount  Pointer to the reference count for these slots
 * @param[out] ...  Locations to store the allocated keys
 *
 * @return 0 on success, 1 on failure.
 *
 * @note Newly allocated VMLS slots contain NULL in all VMs.
 */
UDATA JNICALL HyVMLSAllocKeys(JNIEnv * env, UDATA * pInitCount, ...)
{
#if defined(HY_NO_THR)
	THREAD_ACCESS_FROM_ENV(env);
#endif /* HY_NO_THR */
	va_list args;
	HyVMLSTable *vmls = GLOBAL_DATA(VMLSTable);

	/* Obtain a process-level lock */
	hythread_monitor_t globalMonitor = hythread_global_monitor();
	hythread_monitor_enter(globalMonitor);

	if (++(*pInitCount) == 1) {
		JavaVM *javaVM = NULL;
		void **pKey;
		UDATA count = 0;

		/* Count the number of keys being initialized */
		va_start(args, pInitCount);
		while ((pKey = va_arg(args, void *)) != NULL) {
			++count;
		}
		va_end(args);

		/* Fail if there are not enough free keys left */
		if (count > vmls->freeKeys) {
			/* Free the lock */
			hythread_monitor_exit(globalMonitor);
			return 1;
		}

		/* Allocate the keys */
		va_start(args, pInitCount);
		while ((pKey = va_arg(args, void *)) != NULL) {
			UDATA key;
			VMLSContainer* cursor;

			key = vmls->head;
			vmls->head = vmls->keys[key];
			vmls->keys[key] = (UDATA) - 1;
			*pKey = (void *) key;

			/* Initialize the value of the key to NULL this VM and all other known VMs.
			   VMs which are being created (i.e. not in the list yet) will have all VMLS
			   values set to NULL already.  The current VM may or may not be in the list,
			   which means that the list may be empty.  The global monitor protects the
			   VM list as well as the key list. */

			/* Iterate through all vms */
			cursor = vmls->containerHead;
			while (cursor) {
				cursor->vmLocalStorage[key - 1] = NULL;
				cursor = cursor->linkNext;
			}
		}
		va_end(args);
		vmls->freeKeys -= count;
	}

	/* Release the lock */
	hythread_monitor_exit(globalMonitor);
	return 0;
}


/**
 * Retrieve the value in a VM local storage slot. 
 *
 * @code void*  JNICALL HyVMLSGet(JNIEnv * env, void * key); @endcode
 *
 * @param[in] env  JNIEnv pointer
 * @param[in] key  The VMLS key
 *
 * @return The contents of the VM local storage slot in the VM that contains the specified env
 */
void * JNICALL HyVMLSGet(JNIEnv * env, void * key)
{
	HyVMLSTable* vmls = GLOBAL_DATA(VMLSTable);
	JavaVM *javaVM = NULL;
	VMLSContainer* cursor = vmls->containerHead;

	/* Fetch the javaVM */
	(*env)->GetJavaVM(env,&javaVM);

	/* Iterate all containers */
	while (cursor) {
		/* Locate the correct container identified by javaVM */
		if (cursor->javaVM == javaVM) {
			return cursor->vmLocalStorage[((UDATA) key) - 1];
		}
		cursor = cursor->linkNext;
	}

	return NULL;
}


/**
 * Store a value into a VM local storage slot.  Note the extra level of indirection on
 * the key parameter.  This indirection enables the use of these functions even when
 * multi-vm is not supported.  In these cases pKey is a pointer to the raw memory where
 * the value will be stored.
 *
 * @code void*  JNICALL HyVMLSSet(JNIEnv * env, void ** pKey, void * value); @endcode
 *
 * @param[in] env  JNIEnv pointer
 * @param[in] pKey  Pointer to the VM local storage key (NOTE: the extra level of indirection).
 * @param[in] value  Value to store
 *
 * @return The value stored
 */
void * JNICALL HyVMLSSet(JNIEnv * env, void ** pKey, void * value)
{
	HyVMLSTable* vmls = GLOBAL_DATA(VMLSTable);
	JavaVM *javaVM = NULL;
	VMLSContainer* cursor = vmls->containerHead;

	/* Fetch the javaVM */
	(*env)->GetJavaVM(env,&javaVM);

	/* Iterate all containers */
	while (cursor) {
		/* Locate the correct container identified by javaVM */
		if (cursor->javaVM == javaVM) {
			cursor->vmLocalStorage[((UDATA) *pKey) - 1] = value;
			return value;
		}
	}

	return value;
}


/** 
 * This method must be called each time a JavaVM is created.  On the
 * first invocation this method will allocate the global structures
 * needed for book-keeping.
 *
 * @param[in] vm The JavaVM.
 */	
void initializeVMLocalStorage(JavaVM * vm)
{
#if defined(HY_NO_THR)
	THREAD_ACCESS_FROM_JAVAVM(vm);
#endif /* HY_NO_THR */
	HyVMLSTable* vmls = GLOBAL_DATA(VMLSTable);
	VMLSContainer* container = NULL;

	/* Reach for the VM interface */
	PORT_ACCESS_FROM_JAVAVM(vm);

	if (!vmls->initialized) {
		UDATA i;

		/* Obtain the global lock */
		hythread_monitor_t globalMonitor = hythread_global_monitor();
		hythread_monitor_enter(globalMonitor);

		/* Test again now that we own the lock */
		if (!vmls->initialized) {

			/* Initialize keys */
			for (i = 1; i < VMLS_MAX_KEYS-1; ++i) {
				vmls->keys[i] = i + 1;
			}

			vmls->keys[0] = 0;
			vmls->keys[VMLS_MAX_KEYS-1] = 0;
			vmls->head = 1;
			vmls->freeKeys = VMLS_MAX_KEYS-1;
			vmls->initialized = TRUE;
			vmls->head = 1;
			vmls->containerHead = NULL;
		}

		/* Allocate a new container */
		container = hymem_allocate_memory(sizeof(VMLSContainer));
		if (container) {
			memset(container,0,sizeof(VMLSContainer));
			container->javaVM = vm;
		}

		/* Insert it into the list */
		container->linkNext = vmls->containerHead;
		vmls->containerHead = container;
		
		/* Release the global lock */
		hythread_monitor_exit(globalMonitor);
	}
}


/** 
 * This method must be called each time a JavaVM is destroyed.
 *
 * @param[in] vm The JavaVM.
 */	
void freeVMLocalStorage(JavaVM * vm)
{
	HyVMLSTable* vmls = GLOBAL_DATA(VMLSTable);
	VMLSContainer* cursor = vmls->containerHead;
	VMLSContainer* previous = NULL;

	/* Reach for the VM interface */
#if defined(HY_NO_THR)
	THREAD_ACCESS_FROM_JAVAVM(vm);
#endif /* HY_NO_THR */
	PORT_ACCESS_FROM_JAVAVM(vm);

	/* Obtain the global lock */
	hythread_monitor_t globalMonitor = hythread_global_monitor();
	hythread_monitor_enter(globalMonitor);

	/* Iterate all containers */
	while (cursor) {

		/* Locate the correct container identified by javaVM */
		if (cursor->javaVM == vm) {
			
			/* Unlink the container from the list */
			if (previous) {
				previous->linkNext = cursor->linkNext;
			} else {
				vmls->containerHead = cursor->linkNext;
			}

			hymem_free_memory(cursor);
			break;
		}

		/* Move to the next element */
		previous = cursor;
		cursor = cursor->linkNext;
	}
		
	/* Release the global lock */
	hythread_monitor_exit(globalMonitor);
}
