/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
/*****************************************************************
 * JNI functions to support OnLoad
 *
 * @author Ian Rogers
 * @author Dave Grove
 */

// Java includes
#include <jni.h>

// generated class header
#include "org_jikesrvm_runtime_VM_0005fDynamicLibrary.h"

extern struct JavaVM_ sysJavaVM;

typedef jint (*JNI_OnLoad)(JavaVM_ *vm, void *reserved);

/*
 * Class:     comibm.jikesrvm.VM_DynamicLibrary
 * Method:    runJNI_OnLoad
 * Signature: (Lorg/vmmagic/unboxed/Address;)I
 */
extern "C" JNIEXPORT jint JNICALL Java_org_jikesrvm_runtime_VM_1DynamicLibrary_runJNI_1OnLoad (JNIEnv *env,
                                                                                           jclass clazz,
                                                                                           jobject JNI_OnLoadAddress) {
  return ((JNI_OnLoad)JNI_OnLoadAddress)(&sysJavaVM, NULL);
}
