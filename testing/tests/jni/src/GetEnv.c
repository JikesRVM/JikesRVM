/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */

/* Test the JavaVM and GetEnv JNI functionality 
 * Implement native methods from GetEnv.java 
 * 
 */

#include "GetEnv.h"

void JNICALL Java_GetEnv_nativeCall(JNIEnv *env, jclass cls) {
  jint code;
  JavaVM *vm;
  JNIEnv *nenv;

  fprintf(stderr, "&vm is %x\n", &vm);

  // 1. get the VM using the GetJavaVM interface
  code = (*env)->GetJavaVM(env, &vm);

  fprintf(stderr, "vm is %x\n", vm);

  // 2. hopefully, that worked
  if (code != JNI_OK)
    fprintf(stderr, "GetJavaVM failed\n");

  // 3. get environment using GetEnv interface
  (*vm)->GetEnv(vm, (void **)&nenv, JNI_VERSION_1_1);

  // 4. it should be the same as we were given
  if (env != nenv)
    fprintf(stderr, "GetEnv return bad environment\n");
}

  
