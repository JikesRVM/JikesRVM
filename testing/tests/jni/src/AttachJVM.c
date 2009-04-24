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
/****************************************************************
 * This program tests the JNI functions provided in the library:
 *    CreateJavaVM
 *    DestroyJavaVM
 *    GetCreatedJavaVMs
 *    AttachCurrentThread
 *    DetachCurrentThread
 *    GetEnv
 * This is adapted from the example in the JNI book by Sheng Liang
 * (page 90-91)
 */

#include <pthread.h>
#include <jni.h>
JavaVM *jvm; /* The virtual machine instance */

#define PATH_SEPARATOR ';'
#define BUFLEN 10

/* try version 1.1 for now */
#undef JNI_VERSION_1_2

#define NUM_THREAD 2
int verbose_mode;
int threads_result[NUM_THREAD], main_pass, thread_pass;

/* where myMain.class is */
/* #define USER_CLASSPATH "."  */
#define USER_CLASSPATH ".:/usr/jdk_base/classes:/usr/jdk_base/lib/classes.jar:/usr/jdk_base/lib/rt.jar:/usr/jdk_base/lib/i18n.jar:/usr/jdk_base/lib/classes.zip"

void dummyBreakpoint () {
  return;
}

/****************************************************
 *  Procedure to be executed by the pthreads
 */
void *thread_fun(void *arg)
{
  int rc;
  jint res;
  jclass cls;
  jmethodID mid;
  jstring jstr;
  jclass stringClass;
  jobjectArray args;
  JNIEnv *env, *env2;
  char buf[100];
  int threadNum = (int)arg;
  int thread_pass = 1;

  /****************************************************
   * Test AttachCurrentThread
   * Pass NULL as the third argument
   */
  if (verbose_mode)
    printf("C thread %d: attaching to JVM, pid = %d\n", threadNum, pthread_self());
  res = (*jvm)->AttachCurrentThread(jvm, &env, NULL);
  if (res < 0) {
    if (verbose_mode)
      fprintf(stderr, "C thread %d: attach failed\n", threadNum);
    threads_result[threadNum] = 0;
    return;
  } else {
    if (verbose_mode)
      fprintf(stderr, "C thread %d: attach succeeds, invoking program\n ", threadNum);
  }


  /****************************************************
   *  Now with a JNIEnv handle, try a few JNI functions
   */
  cls = (*env)->FindClass(env, "myMain");

  if (cls == 0) {
    if (verbose_mode)
      printf("C thread %d: ERROR, cannot find class myMain\n", threadNum);
    thread_pass = 0;
    goto detach;
  } else {
    if (verbose_mode)
      printf("C thread %d: FindClass returns %d \n", threadNum, cls);
  }

  dummyBreakpoint();

  mid = (*env)->GetStaticMethodID(env, cls, "compute",
                                  "([Ljava/lang/String;)I");
  if (mid == 0) {
    if (verbose_mode)
      printf("C thread %d: ERROR, GetStaticMethodID fails\n", threadNum);
    thread_pass = 0;
    goto detach;
  } else {
    if (verbose_mode)
      printf("C thread %d: GetStaticMethodID returns %d\n", threadNum, mid);
  }

  sprintf(buf, "C thread %d: Hello from C", threadNum);
  jstr = (*env)->NewStringUTF(env, buf);
  if (jstr == 0) {
    if (verbose_mode)
      printf("C thread %d: ERROR, NewStringUTF fails\n", threadNum);
    thread_pass = 0;
    goto detach;
  } else {
    if (verbose_mode)
      printf("C thread %d: NewStringUTF returns %d\n", threadNum, jstr);
  }

  stringClass = (*env)->FindClass(env, "java/lang/String");
  args = (*env)->NewObjectArray(env, 1, stringClass, jstr);
  if (args == 0) {
    if (verbose_mode)
      printf("C thread %d: ERROR, NewObjectArray fails\n", threadNum);
    thread_pass = 0;
    goto detach;
  } else {
    if (verbose_mode)
      printf("C thread %d: NewObjectArray returns %d\n", threadNum, args);
  }

  res = (*env)->CallStaticIntMethod(env, cls, mid, args);

  if ((*env)->ExceptionOccurred(env)) {
    if (verbose_mode)
      (*env)->ExceptionDescribe(env);
    thread_pass = 0;
  } else if (res==123) {
    if (verbose_mode)
      printf("C thread %d: Java call succeeds\n", threadNum);
  } else if (res==456) {
    if (verbose_mode)
      printf("C thread %d: ERROR, parameter fails to be passed to Java\n");
    thread_pass = 0;
  } else {
    if (verbose_mode)
      printf("C thread %d: ERROR, parameter fails to be returned from Java \n");
    thread_pass = 0;
  }


  /****************************************************
   * test GetEnv
   */
  res = (*jvm)->GetEnv(jvm, ((void *) &env2), JNI_VERSION_1_1);
  if (res < 0) {
    if (verbose_mode)
      fprintf(stderr, "C thread %d: GetEnv fails\n", threadNum);
    thread_pass = 0;
  } else {
    if (env2==env) {
      if (verbose_mode)
        fprintf(stderr, "C thread %d: GetEnv succeeds\n", threadNum);
    } else {
      if (verbose_mode)
        fprintf(stderr, "C thread %d: GetEnv fails\n", threadNum);
      thread_pass = 0;
    }
  }


  /****************************************************
   * Test DetachCurrentThread
   */
 detach:
  rc = (*jvm)->DetachCurrentThread(jvm);

  if (rc!=0) {
    if (verbose_mode)
      printf("C thread %d: Error detaching thread\n", threadNum);
    thread_pass = 0;
  } else {
    if (verbose_mode)
      printf("C thread %d: DetachCurrentThread succeeds\n", threadNum);
  }

  threads_result[threadNum] = thread_pass;

  pthread_exit(NULL);


}


/***************************************************************/

main(int argc, char **argv) {
  JNIEnv *env;
  int i, rc, numJavaThreads;
  jsize numJVM;
  JavaVM **jvmBuf;
  jint res;
  jclass cls;
  jmethodID mid;
  jstring jstr;
  jclass stringClass;
  jobjectArray args;
  jfieldID fid;

  pthread_t first_th;
  pthread_t second_th;


  /***************************************************
   * Test JNI_CreateJavaVM
   */

#ifdef JNI_VERSION_1_2
  JavaVMInitArgs vm_args;
  JavaVMOption options[1];
  options[0].optionString =
    "-Djava.class.path=" USER_CLASSPATH;
  vm_args.version = 0x00010002;
  vm_args.options = options;
  vm_args.nOptions = 1;
  vm_args.ignoreUnrecognized = TRUE;
  /* Create the Java VM */
  res = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args);

#else
  JDK1_1InitArgs vm_args;
  char classpath[1024];
  vm_args.version = 0x00010001;
  JNI_GetDefaultJavaVMInitArgs(&vm_args);
  /* Append USER_CLASSPATH to the default system class path */
  /* sprintf(classpath, "%s%c%s",
     vm_args.classpath, PATH_SEPARATOR, USER_CLASSPATH); */
  sprintf(classpath,"%s", USER_CLASSPATH);       /* just hardcode the path for now */
  vm_args.classpath = classpath;
  /* Create the Java VM */
  res = JNI_CreateJavaVM(&jvm, &env, &vm_args);
#endif /* JNI_VERSION_1_2 */


  main_pass = 1;
  thread_pass = 1;
  verbose_mode = 0;
  if (argc > 1) {
    /* argv++; */
    if (!strcmp(argv[1], "-verbose")) {
      verbose_mode = 1;
    }
  }

  if (res < 0) {
    fprintf(stderr, "FAIL: AttachJVM, cannot create Java VM from C\n");
    exit(1);
  } else {
    if (verbose_mode)
      printf("C: JVM created, now spawning threads ...\n");
  }

  /***************************************************
   * Now with a JNIEnv, try a few JNI functions
   */

  cls = (*env)->FindClass(env, "myMain");

  if (cls == 0) {
    if (verbose_mode)
      printf("ERROR: cannot find class myMain\n");
    main_pass = 0;
    goto destroy;
  } else {
    if (verbose_mode)
      printf("FindClass returns %d \n", cls);
  }

  mid = (*env)->GetStaticMethodID(env, cls, "main",
                                  "([Ljava/lang/String;)V");
  if (mid == 0) {
    if (verbose_mode)
      printf("ERROR: GetStaticMethodID fails\n");
    main_pass = 0;
    goto destroy;
  } else {
    if (verbose_mode)
      printf("GetStaticMethodID returns %d\n", mid);
  }

  jstr = (*env)->NewStringUTF(env, " main thread from C!");
  if (jstr == 0) {
    if (verbose_mode)
      printf("ERROR: NewStringUTF fails\n");
    main_pass = 0;
    goto destroy;
  } else {
    if (verbose_mode)
      printf("NewStringUTF returns %d\n", jstr);
  }

  stringClass = (*env)->FindClass(env, "java/lang/String");
  args = (*env)->NewObjectArray(env, 1, stringClass, jstr);
  if (args == 0) {
    if (verbose_mode)
      printf("ERROR: NewObjectArray fails\n");
    main_pass = 0;
    goto destroy;
  } else {
    if (verbose_mode)
      printf("NewObjectArray returns %d\n", args);
  }

  (*env)->CallStaticVoidMethod(env, cls, mid, args);

  if ((*env)->ExceptionOccurred(env)) {
    if (verbose_mode) {
      printf("ERROR: CallStaticVoidMethod fails\n");
      (*env)->ExceptionDescribe(env);
    }
    main_pass = 0;
  } else {
    if (verbose_mode)
      printf("CallStaticVoidMethod succeeds.\n");
  }



  /***************************************************
   * Now create the threads and let them do some more Java work
   * pass the thread number to every thread
   */

  pthread_create(&first_th,  NULL, thread_fun, (void *) 0);
  pthread_create(&second_th, NULL, thread_fun, (void *) 1);

  /* wait for the pthreads to finish */
  pthread_join(first_th, NULL);
  pthread_join(second_th, NULL);

  /* sleep(60);  */


  if (verbose_mode)
    printf("C: threads are done, taking down JVM.\n");



  /***************************************************
   *  Test GetCreatedJVMs
   */
  numJVM = 0;
  jvmBuf = (JavaVM **) malloc(sizeof(JavaVM *) * BUFLEN);
  rc = JNI_GetCreatedJavaVMs(jvmBuf, BUFLEN, &numJVM);
  if (rc!=0) {
    if (verbose_mode)
      printf("AttachJVM: ERROR calling GetCreatedJavaVMs\n");
    main_pass = 0;
  } else {
    if (numJVM>1) {
      if (verbose_mode)
        printf("AttachJVM: ERROR GetCreatedJavaVMs returns more than one JVM instance\n");
      main_pass = 0;
    } else if (numJVM==0) {
      if (verbose_mode)
        printf("AttachJVM: ERROR GetCreatedJavaVMs returns none \n");
      main_pass = 0;
    } else if (jvmBuf[0]==jvm) {
      if (verbose_mode)
        printf("AttachJVM: GetCreatedJavaVMs succeeds\n");
    }
  }


  /***************************************************
   *  Test DestroyJavaVM
   */
 destroy:
  rc = (*jvm)->DestroyJavaVM(jvm);

  if (rc!=0) {
    if (verbose_mode)
      printf("C: DestroyJavaVM returns with -1 as expected \n");
  } else {
    if (verbose_mode)
      printf("C: Unexpected return value from DestroyJavaVM\n");
    main_pass = 0;
  }

  /***************************************************
   * Check tests
   */
  for (i=0; i<NUM_THREAD; i++) {
    if (threads_result[i]==0)
      thread_pass = 0;
  }


  if (main_pass && thread_pass)
    fprintf(stdout, "PASS: AttachJVM\n");
  else
    fprintf(stdout, "FAIL: AttachJVM\n");

}
