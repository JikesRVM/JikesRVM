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

/**
 * O/S support services required by the java class libraries.
 * See also: BootRecord.java
 */

// Aix and Linux version.  PowerPC and IA32.

#include "sys.h"

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//
#ifdef _AIX43
#include </usr/include/unistd.h>
EXTERNAL void profil(void *, uint, ulong, uint);
EXTERNAL int sched_yield(void);
#endif

#include <stdio.h>
#include <stdlib.h>      // getenv() and others
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>               // nanosleep() and other
#include <utime.h>
#include <setjmp.h>


#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_SOLARIS) 
#include <sys/stat.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#ifdef RVM_FOR_LINUX
#include <asm/ioctls.h>
#include <sys/syscall.h>
#endif

# include <sched.h>

/* OSX/Darwin */
#elif (defined __MACH__)
#include <sys/stat.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <mach-o/dyld.h>
#include <mach/host_priv.h>
#include <mach/mach_init.h>
#include <mach/mach_host.h>
#include <mach/vm_map.h>
#include <mach/processor_info.h>
#include <mach/processor.h>
#include <mach/thread_act.h>
#include <sys/types.h>
#include <sys/sysctl.h>
/* As of 10.4, dlopen comes with the OS */
#include <dlfcn.h>
#define MAP_ANONYMOUS MAP_ANON
#include <sched.h>

/* AIX/PowerPC */
#else
#include <sys/cache.h>
#include <sys/ioctl.h>
#endif

#include <sys/shm.h>        /* disclaim() */
#include <strings.h>        /* bzero() */
#include <sys/mman.h>       /* mmap & munmap() */
#include <sys/shm.h>
#include <errno.h>
#include <dlfcn.h>
#include <inttypes.h>           // uintptr_t

#ifdef _AIX
EXTERNAL timer_t gettimerid(int timer_type, int notify_type);
EXTERNAL int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#include <jni.h>

///////////////////////////JVM_Native interfaces///////////////////

EXTERNAL {

#define JVM_INTERFACE_VERSION (4)

JNIEXPORT jint JNICALL
JVM_GetInterfaceVersion(void)
{
	return JVM_INTERFACE_VERSION;
}

JNIEXPORT jint JNICALL
JVM_IHashCode(JNIEnv *env, jobject obj)
{
	printf("JVM_IHashCode(JNIEnv *env, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms)
{
	printf("JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms)");
}

JNIEXPORT void JNICALL
JVM_MonitorNotify(JNIEnv *env, jobject obj)
{
	printf("JVM_MonitorNotify(JNIEnv *env, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_MonitorNotifyAll(JNIEnv *env, jobject obj)
{
	printf("JVM_MonitorNotifyAll(JNIEnv *env, jobject obj)");
}

JNIEXPORT jobject JNICALL
JVM_Clone(JNIEnv *env, jobject obj)
{
	printf("JVM_Clone(JNIEnv *env, jobject obj)");
}

JNIEXPORT jstring JNICALL
JVM_InternString(JNIEnv *env, jstring str)
{
	printf("JVM_InternString(JNIEnv *env, jstring str)");

}

JNIEXPORT jlong JNICALL
JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored)
{
	printf("JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored)");
}

JNIEXPORT jlong JNICALL
JVM_NanoTime(JNIEnv *env, jclass ignored)
{
	printf("JVM_NanoTime(JNIEnv *env, jclass ignored)");
}

JNIEXPORT void JNICALL
JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length)
{
	printf("JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_InitProperties(JNIEnv *env, jobject p)
{
	printf("JVM_InitProperties(JNIEnv *env, jobject p)");
}

JNIEXPORT void JNICALL
JVM_OnExit(void (*func)(void))
{
	printf("JVM_OnExit(void (*func)(void))");
}

JNIEXPORT void JNICALL
JVM_Exit(jint code)
{
	printf("JVM_Exit(jint code)");
}

JNIEXPORT void JNICALL
JVM_Halt(jint code)
{
	printf("JVM_Halt(jint code)");
}

JNIEXPORT void JNICALL
JVM_GC(void)
{
	printf("JVM_GC(void)");
}

JNIEXPORT jlong JNICALL
JVM_MaxObjectInspectionAge(void)
{
	printf("JVM_MaxObjectInspectionAge(void)");
}

JNIEXPORT void JNICALL
JVM_TraceInstructions(jboolean on)
{
	printf("JVM_TraceInstructions(jboolean on)");
}

JNIEXPORT void JNICALL
JVM_TraceMethodCalls(jboolean on)
{
	printf("JVM_TraceMethodCalls(jboolean on)");
}

JNIEXPORT jlong JNICALL
JVM_TotalMemory(void)
{
	printf("JVM_TotalMemory(void)");
}

JNIEXPORT jlong JNICALL
JVM_FreeMemory(void)
{
	printf("JVM_FreeMemory(void)");
}

JNIEXPORT jlong JNICALL
JVM_MaxMemory(void)
{
	printf("JVM_MaxMemory(void)");
}

JNIEXPORT jint JNICALL
JVM_ActiveProcessorCount(void)
{
	printf("JVM_ActiveProcessorCount(void)");
}

JNIEXPORT void * JNICALL
JVM_LoadLibrary(const char *name)
{
	printf("JVM_LoadLibrary(const char *name)");
}

JNIEXPORT void JNICALL
JVM_UnloadLibrary(void * handle)
{
	printf("JVM_UnloadLibrary(void * handle)");
}

JNIEXPORT void * JNICALL
JVM_FindLibraryEntry(void *handle, const char *name)
{
	printf("JVM_FindLibraryEntry(void *handle, const char *name)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSupportedJNIVersion(jint version)
{
	printf("JVM_IsSupportedJNIVersion(jint version)");
}

JNIEXPORT jboolean JNICALL
JVM_IsNaN(jdouble d)
{
	printf("JVM_IsNaN(jdouble d)");
}

JNIEXPORT void JNICALL
JVM_FillInStackTrace(JNIEnv *env, jobject throwable)
{
	printf("JVM_FillInStackTrace(JNIEnv *env, jobject throwable)");
}

JNIEXPORT void JNICALL
JVM_PrintStackTrace(JNIEnv *env, jobject throwable, jobject printable)
{
	printf("JVM_PrintStackTrace(JNIEnv *env, jobject throwable, jobject printable)");
}

JNIEXPORT jint JNICALL
JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable)
{
	printf("JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable)");
}

JNIEXPORT jobject JNICALL
JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index)
{
	printf("JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index)");
}

JNIEXPORT void JNICALL
JVM_InitializeCompiler (JNIEnv *env, jclass compCls)
{
	printf("JVM_InitializeCompiler (JNIEnv *env, jclass compCls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSilentCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_IsSilentCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT jboolean JNICALL
JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls)
{
	printf("JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname)
{
	printf("JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname)");
}

JNIEXPORT jobject JNICALL
JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg)
{
	printf("JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg)");
}

JNIEXPORT void JNICALL
JVM_EnableCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_EnableCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT void JNICALL
JVM_DisableCompiler(JNIEnv *env, jclass compCls)
{
	printf("JVM_DisableCompiler(JNIEnv *env, jclass compCls)");
}

JNIEXPORT void JNICALL
JVM_StartThread(JNIEnv *env, jobject thread)
{
	printf("JVM_StartThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_StopThread(JNIEnv *env, jobject thread, jobject exception)
{
	printf("JVM_StopThread(JNIEnv *env, jobject thread, jobject exception)");
}

JNIEXPORT jboolean JNICALL
JVM_IsThreadAlive(JNIEnv *env, jobject thread)
{
	printf("JVM_IsThreadAlive(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_SuspendThread(JNIEnv *env, jobject thread)
{
	printf("JVM_SuspendThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_ResumeThread(JNIEnv *env, jobject thread)
{
	printf("JVM_ResumeThread(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio)
{
	printf("JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio)");
}

JNIEXPORT void JNICALL
JVM_Yield(JNIEnv *env, jclass threadClass)
{
	printf("JVM_Yield(JNIEnv *env, jclass threadClass)");
}

JNIEXPORT void JNICALL
JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis)
{
	printf("JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis)");
}

JNIEXPORT jobject JNICALL
JVM_CurrentThread(JNIEnv *env, jclass threadClass)
{
	printf("JVM_CurrentThread(JNIEnv *env, jclass threadClass)");
}

JNIEXPORT jint JNICALL
JVM_CountStackFrames(JNIEnv *env, jobject thread)
{
	printf("JVM_CountStackFrames(JNIEnv *env, jobject thread)");
}

JNIEXPORT void JNICALL
JVM_Interrupt(JNIEnv *env, jobject thread)
{
	printf("JVM_Interrupt(JNIEnv *env, jobject thread)");
}

JNIEXPORT jboolean JNICALL
JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted)
{
	printf("JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted)");
}

JNIEXPORT jboolean JNICALL
JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj)
{
	printf("JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj)");
}

JNIEXPORT void JNICALL
JVM_DumpAllStacks(JNIEnv *env, jclass unused)
{
	printf("JVM_DumpAllStacks(JNIEnv *env, jclass unused)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetAllThreads(JNIEnv *env, jclass dummy)
{
	printf("JVM_GetAllThreads(JNIEnv *env, jclass dummy)");
}

JNIEXPORT jobjectArray JNICALL
JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads)
{
	printf("JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads)");
}

JNIEXPORT jclass JNICALL
JVM_CurrentLoadedClass(JNIEnv *env)
{
	printf("JVM_CurrentLoadedClass(JNIEnv *env)");
}

JNIEXPORT jobject JNICALL
JVM_CurrentClassLoader(JNIEnv *env)
{
	printf("JVM_CurrentClassLoader(JNIEnv *env)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassContext(JNIEnv *env)
{
	printf("JVM_GetClassContext(JNIEnv *env)");
}

JNIEXPORT jint JNICALL
JVM_ClassDepth(JNIEnv *env, jstring name)
{
	printf("JVM_ClassDepth(JNIEnv *env, jstring name)");
}

JNIEXPORT jint JNICALL
JVM_ClassLoaderDepth(JNIEnv *env)
{
	printf("JVM_ClassLoaderDepth(JNIEnv *env)");
}

JNIEXPORT jstring JNICALL
JVM_GetSystemPackage(JNIEnv *env, jstring name)
{
	printf("JVM_GetSystemPackage(JNIEnv *env, jstring name)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetSystemPackages(JNIEnv *env)
{
	printf("JVM_GetSystemPackages(JNIEnv *env)");
}

JNIEXPORT jobject JNICALL
JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass, jclass initClass)
{
	printf("JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass, jclass initClass)");
}

JNIEXPORT jobject JNICALL
JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length)
{
	printf("JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_LatestUserDefinedLoader(JNIEnv *env)
{
	printf("JVM_LatestUserDefinedLoader(JNIEnv *env)");
}

JNIEXPORT jclass JNICALL
JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass, jstring currClassName)
{
	printf("JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass, jstring currClassName)");
}

JNIEXPORT jint JNICALL
JVM_GetArrayLength(JNIEnv *env, jobject arr)
{
	printf("JVM_GetArrayLength(JNIEnv *env, jobject arr)");
}

JNIEXPORT jobject JNICALL
JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index)
{
	printf("JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index)");
}

JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode)
{
	printf("JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode)");
}

JNIEXPORT void JNICALL
JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val)
{
	printf("JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val)");
}

JNIEXPORT void JNICALL
JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode)
{
	printf("JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode)");
}

JNIEXPORT jobject JNICALL
JVM_NewArray(JNIEnv *env, jclass eltClass, jint length)
{
	printf("JVM_NewArray(JNIEnv *env, jclass eltClass, jint length)");
}

JNIEXPORT jobject JNICALL
JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim)
{
	printf("JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim)");
}

JNIEXPORT jclass JNICALL
JVM_GetCallerClass(JNIEnv *env, int n)
{
	printf("JVM_GetCallerClass(JNIEnv *env, int n)");
}

JNIEXPORT jclass JNICALL
JVM_FindPrimitiveClass(JNIEnv *env, const char *utf)
{
	printf("JVM_FindPrimitiveClass(JNIEnv *env, const char *utf)");
}

JNIEXPORT void JNICALL
JVM_ResolveClass(JNIEnv *env, jclass cls)
{
	printf("JVM_ResolveClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init, jobject loader, jboolean throwError)
{
	printf("JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init, jobject loader, jboolean throwError)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromBootLoader(JNIEnv *env, const char *name)
{
	printf("JVM_FindClassFromBootLoader(JNIEnv *env, const char *name)");
}

JNIEXPORT jclass JNICALL
JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init, jclass from)
{
	printf("JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init, jclass from)");
}

JNIEXPORT jclass JNICALL
JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name)
{
	printf("JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd)
{
	printf("JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source)
{
	printf("JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jboolean verify)
{
	printf("JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jboolean verify)");
}

JNIEXPORT jclass JNICALL
JVM_DefineClassWithCP(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jobjectArray constants)
{
	printf("JVM_DefineClassWithCP(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source, jobjectArray constants)");
}

JNIEXPORT jstring JNICALL
JVM_GetClassName(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassName(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassInterfaces(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassInterfaces(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassLoader(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassLoader(JNIEnv *env, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsInterface(JNIEnv *env, jclass cls)
{
	printf("JVM_IsInterface(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassSigners(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassSigners(JNIEnv *env, jclass cls)");
}

JNIEXPORT void JNICALL
JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers)
{
	printf("JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers)");
}

JNIEXPORT jobject JNICALL
JVM_GetProtectionDomain(JNIEnv *env, jclass cls)
{
	printf("JVM_GetProtectionDomain(JNIEnv *env, jclass cls)");
}

JNIEXPORT void JNICALL
JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain)
{
	printf("JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain)");
}

JNIEXPORT jboolean JNICALL
JVM_IsArrayClass(JNIEnv *env, jclass cls)
{
	printf("JVM_IsArrayClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jboolean JNICALL
JVM_IsPrimitiveClass(JNIEnv *env, jclass cls)
{
	printf("JVM_IsPrimitiveClass(JNIEnv *env, jclass cls)");
}

JNIEXPORT jclass JNICALL
JVM_GetComponentType(JNIEnv *env, jclass cls)
{
	printf("JVM_GetComponentType(JNIEnv *env, jclass cls)");
}

JNIEXPORT jint JNICALL
JVM_GetClassModifiers(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassModifiers(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass)
{
	printf("JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass)");
}

JNIEXPORT jclass JNICALL
JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass)
{
	printf("JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass)");
}

JNIEXPORT jstring JNICALL
JVM_GetClassSignature(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassSignature(JNIEnv *env, jclass cls)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetClassAnnotations(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassAnnotations(JNIEnv *env, jclass cls)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetFieldAnnotations(JNIEnv *env, jobject field)
{
	printf("JVM_GetFieldAnnotations(JNIEnv *env, jobject field)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodAnnotations(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodAnnotations(JNIEnv *env, jobject method)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method)");
}

JNIEXPORT jbyteArray JNICALL
JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method)
{
	printf("JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly)
{
	printf("JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly)");
}

JNIEXPORT jint JNICALL
JVM_GetClassAccessFlags(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassAccessFlags(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassConstantPool(JNIEnv *env, jclass cls)
{
	printf("JVM_GetClassConstantPool(JNIEnv *env, jclass cls)");
}

JNIEXPORT jint JNICALL 
JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool)
{
	printf("JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool)");
}

JNIEXPORT jclass JNICALL 
JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jclass JNICALL 
JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL 
JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobjectArray JNICALL 
JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jint JNICALL
JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jlong JNICALL 
JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jfloat JNICALL 
JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jdouble JNICALL 
JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jstring JNICALL 
JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jstring JNICALL 
JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index)
{
	printf("JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index)");
}

JNIEXPORT jobject JNICALL
JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException)
{
  jobject result;
  printf("##Trap to RVM:JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException)");
  result = env->functions->RVM_DoPrivileged(env,cls,action,context,&wrapException);
  printf("##Retrn from JVM_DoPrivileged, result is %p\n",result);

}

JNIEXPORT jobject JNICALL
JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls)
{
	printf("JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls)
{
	printf("JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls)");
}

JNIEXPORT void * JNICALL
JVM_RegisterSignal(jint sig, void *handler)
{
	printf("JVM_RegisterSignal(jint sig, void *handler)");
}

JNIEXPORT jboolean JNICALL
JVM_RaiseSignal(jint sig)
{
	printf("JVM_RaiseSignal(jint sig)");
}

JNIEXPORT jint JNICALL
JVM_FindSignal(const char *name)
{
	printf("JVM_FindSignal(const char *name)");
}

JNIEXPORT jboolean JNICALL
JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls)
{
	printf("JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused)
{
	printf("JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused)");
}

JNIEXPORT jboolean JNICALL
JVM_SupportsCX8(void)
{
	printf("JVM_SupportsCX8(void)");
	return true;
}

JNIEXPORT jboolean JNICALL
JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fldID, jlong oldVal, jlong newVal)
{
	printf("JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fldID, jlong oldVal, jlong newVal)");
}



/*
 * Structure to pass one probe description to JVM.
 *
 * The VM will overwrite the definition of the referenced method with
 * code that will fire the probe.
 */
typedef struct {
    jmethodID method;
    jstring   function;
    jstring   name;
    void*     reserved[4];     // for future use
} JVM_DTraceProbe;

/**
 * Encapsulates the stability ratings for a DTrace provider field
 */
typedef struct {
    jint nameStability;
    jint dataStability;
    jint dependencyClass;
} JVM_DTraceInterfaceAttributes;

/*
 * Structure to pass one provider description to JVM
 */
typedef struct {
    jstring                       name;
    JVM_DTraceProbe*              probes;
    jint                          probe_count;
    JVM_DTraceInterfaceAttributes providerAttributes;
    JVM_DTraceInterfaceAttributes moduleAttributes;
    JVM_DTraceInterfaceAttributes functionAttributes;
    JVM_DTraceInterfaceAttributes nameAttributes;
    JVM_DTraceInterfaceAttributes argsAttributes;
    void*                         reserved[4]; // for future use
} JVM_DTraceProvider;

JNIEXPORT jint JNICALL
JVM_DTraceGetVersion(JNIEnv* env)
{
	printf("JVM_DTraceGetVersion(JNIEnv* env)");
}

JNIEXPORT jlong JNICALL
JVM_DTraceActivate(JNIEnv* env, jint version, jstring module_name, jint providers_count, JVM_DTraceProvider* providers)
{
	printf("JVM_DTraceActivate(JNIEnv* env, jint version, jstring module_name, jint providers_count, JVM_DTraceProvider* providers)");
}

JNIEXPORT jboolean JNICALL
JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method)
{
	printf("JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method)");
}

JNIEXPORT void JNICALL
JVM_DTraceDispose(JNIEnv* env, jlong handle)
{
	printf("JVM_DTraceDispose(JNIEnv* env, jlong handle)");
}

JNIEXPORT jboolean JNICALL
JVM_DTraceIsSupported(JNIEnv* env)
{
	printf("JVM_DTraceIsSupported(JNIEnv* env)");
}

JNIEXPORT const char * JNICALL
JVM_GetClassNameUTF(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassNameUTF(JNIEnv *env, jclass cb)");
}

JNIEXPORT void JNICALL
JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types)
{
	printf("JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types)");
}

JNIEXPORT jint JNICALL
JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT jint JNICALL
JVM_GetClassFieldsCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassFieldsCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT jint JNICALL
JVM_GetClassMethodsCount(JNIEnv *env, jclass cb)
{
	printf("JVM_GetClassMethodsCount(JNIEnv *env, jclass cb)");
}

JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index, unsigned short *exceptions)
{
	printf("JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index, unsigned short *exceptions)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index)
{
	printf("JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index)");
}

JNIEXPORT void JNICALL
JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index, unsigned char *code)
{
	printf("JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index, unsigned char *code)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index)
{
	printf("JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index)");
}

/*
 * A structure used to a capture exception table entry in a Java method.
 */
typedef struct {
    jint start_pc;
    jint end_pc;
    jint handler_pc;
    jint catchType;
} JVM_ExceptionTableEntryType;


JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index, jint entry_index, JVM_ExceptionTableEntryType *entry)
{
	printf("JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index, jint entry_index, JVM_ExceptionTableEntryType *entry)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jint JNICALL
JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT jboolean JNICALL
JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index)
{
	printf("JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index)");
}

JNIEXPORT const char * JNICALL
JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT const char * JNICALL
JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index)
{
	printf("JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index)");
}

JNIEXPORT jint JNICALL
JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)
{
	printf("JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)");
}

JNIEXPORT jint JNICALL
JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)
{
	printf("JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass)");
}

JNIEXPORT void JNICALL
JVM_ReleaseUTF(const char *utf)
{
	printf("JVM_ReleaseUTF(const char *utf)");
}

JNIEXPORT jboolean JNICALL
JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2)
{
	printf("JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2)");
}

JNIEXPORT jint JNICALL
JVM_GetLastErrorString(char *buf, int len)
{
	printf("JVM_GetLastErrorString(char *buf, int len)\n");
	if (errno == 0) {
	  return 0;
	} else {
	  const char *s = strerror(errno);
	  printf("LastErrorString is %s\n",s);
	  int n = strlen(s);
	  if (n >= len) n = len - 1;
	  strncpy(buf, s, n);
	  buf[n] = '\0';
	  return n;
	}
}

JNIEXPORT char * JNICALL
JVM_NativePath(char * path)
{
  //  printf("JVM_NativePath path:%s\n",path);
  return path;
}

JNIEXPORT jint JNICALL
JVM_Open(const char *fname, jint flags, jint mode)
{

	int result = open(fname,flags,mode);
	if (result < 0){
	  result = -1;
	  if (errno == EEXIST)
	    result = -100;//JVM_EXIST
	}
#ifdef DEBUG_OPENJDK	
	printf("JVM_Open(%s,%d,%d):%d\n",fname,flags,mode,result);
#endif
	return result;	   	
}

JNIEXPORT jint JNICALL
JVM_Close(jint fd)
{
#ifdef DEBUG_OPENJDK
	printf("JVM_Close(jint fd)\n");
#endif
	return close(fd);
}

JNIEXPORT jint JNICALL
JVM_Read(jint fd, char *buf, jint nbytes)
{
  if (openjdkVerbose)
    printf("JVM_Read(jint fd, char *buf, jint nbytes)\n");
  return read(fd,buf,nbytes);
}

JNIEXPORT jint JNICALL
JVM_Write(jint fd, char *buf, jint nbytes)
{
  if (openjdkVerbose)
    printf("JVM_Write(jint fd, char *buf, jint nbytes)");
  write(fd,buf,nbytes);
}

JNIEXPORT jint JNICALL
JVM_Available(jint fd, jlong *pbytes)
{
	jlong cur, end;
	int mode;
	struct stat bufStat;

	if (fstat(fd, &bufStat) >= 0) {
	  mode = bufStat.st_mode;
	  if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
            int n;
            if (ioctl(fd, FIONREAD, &n) >= 0) {
	      //	      printf("JVM_Available: fd %d, return %d\n",n);
	      *pbytes = n;
	      return 1;
            }
	  }
	}
	if ((cur = lseek(fd, 0, SEEK_CUR)) == -1) {
	  return 0;
	} else if ((end = lseek(fd, 0, SEEK_END)) == -1) {
	  return 0;
#ifndef __MACH__
	} else if (lseek64(fd, cur, SEEK_SET) == -1) {
	  return 0;
#endif
	}
	*pbytes = end - cur;
	//	printf("JVM_Available: fd %d, return %d\n",end - cur);
	return 1;	
}

JNIEXPORT jlong JNICALL
JVM_Lseek(jint fd, jlong offset, jint whence)
{
  if (openjdkVerbose)
    printf("JVM_Lseek(jint fd, jlong offset, jint whence)\n");
  return lseek(fd, offset, whence);
}

JNIEXPORT jint JNICALL
JVM_SetLength(jint fd, jlong length)
{
	printf("JVM_SetLength(jint fd, jlong length)\n");
	return ftruncate(fd, length);
}

JNIEXPORT jint JNICALL
JVM_Sync(jint fd)
{
	printf("JVM_Sync(jint fd)\n");
	return fsync(fd);
	
}

JNIEXPORT jint JNICALL
JVM_InitializeSocketLibrary(void)
{
	printf("JVM_InitializeSocketLibrary(void)");
}

JNIEXPORT jint JNICALL
JVM_Socket(jint domain, jint type, jint protocol)
{
	printf("JVM_Socket(jint domain, jint type, jint protocol)");
}

JNIEXPORT jint JNICALL
JVM_SocketClose(jint fd)
{
	printf("JVM_SocketClose(jint fd)");
}

JNIEXPORT jint JNICALL
JVM_SocketShutdown(jint fd, jint howto)
{
	printf("JVM_SocketShutdown(jint fd, jint howto)");
}

JNIEXPORT jint JNICALL
JVM_Recv(jint fd, char *buf, jint nBytes, jint flags)
{
	printf("JVM_Recv(jint fd, char *buf, jint nBytes, jint flags)");
}

JNIEXPORT jint JNICALL
JVM_Send(jint fd, char *buf, jint nBytes, jint flags)
{
	printf("JVM_Send(jint fd, char *buf, jint nBytes, jint flags)");
}

JNIEXPORT jint JNICALL
JVM_Timeout(int fd, long timeout)
{
	printf("JVM_Timeout(int fd, long timeout)");
}

JNIEXPORT jint JNICALL
JVM_Listen(jint fd, jint count)
{
	printf("JVM_Listen(jint fd, jint count)");
}

JNIEXPORT jint JNICALL
JVM_Connect(jint fd, struct sockaddr *him, jint len)
{
	printf("JVM_Connect(jint fd, struct sockaddr *him, jint len)");
}

JNIEXPORT jint JNICALL
JVM_Bind(jint fd, struct sockaddr *him, jint len)
{
	printf("JVM_Bind(jint fd, struct sockaddr *him, jint len)");
}

JNIEXPORT jint JNICALL
JVM_Accept(jint fd, struct sockaddr *him, jint *len)
{
	printf("JVM_Accept(jint fd, struct sockaddr *him, jint *len)");
}

JNIEXPORT jint JNICALL
JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen)
{
	printf("JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen)");
}

JNIEXPORT jint JNICALL
JVM_SendTo(jint fd, char *buf, int len,int flags, struct sockaddr *to, int tolen)
{
	printf("JVM_SendTo(jint fd, char *buf, int len,int flags, struct sockaddr *to, int tolen)");
}

JNIEXPORT jint JNICALL
JVM_SocketAvailable(jint fd, jint *result)
{
	printf("JVM_SocketAvailable(jint fd, jint *result)");
}

JNIEXPORT jint JNICALL
JVM_GetSockName(jint fd, struct sockaddr *him, int *len)
{
	printf("JVM_GetSockName(jint fd, struct sockaddr *him, int *len)");
}

JNIEXPORT jint JNICALL
JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen)
{
	printf("JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen)");
}

JNIEXPORT jint JNICALL
JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen)
{
	printf("JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen)");
}

JNIEXPORT struct protoent * JNICALL
JVM_GetProtoByName(char* name)
{
	printf("JVM_GetProtoByName(char* name)");
}

JNIEXPORT struct hostent* JNICALL
JVM_GetHostByAddr(const char* name, int len, int type)
{
	printf("JVM_GetHostByAddr(const char* name, int len, int type)");
}

JNIEXPORT struct hostent* JNICALL
JVM_GetHostByName(char* name)
{
	printf("JVM_GetHostByName(char* name)");
}

JNIEXPORT int JNICALL
JVM_GetHostName(char* name, int namelen)
{
	printf("JVM_GetHostName(char* name, int namelen)");
}

JNIEXPORT void * JNICALL
JVM_RawMonitorCreate(void)
{
  //	printf("JVM_RawMonitorCreate(void)\n");
	return (void *)sysMonitorCreate();	
}

JNIEXPORT void JNICALL
JVM_RawMonitorDestroy(void *mon)
{
  //	printf("JVM_RawMonitorDestroy(void *mon)\n");
	sysMonitorDestroy((Word)mon);
}

JNIEXPORT jint JNICALL
JVM_RawMonitorEnter(void *mon)
{
  //	printf("JVM_RawMonitorEnter(void *mon)\n");
	sysMonitorEnter((Word)mon);
	return 0;
}

JNIEXPORT void JNICALL
JVM_RawMonitorExit(void *mon)
{
  //	printf("JVM_RawMonitorExit(void *mon)\n");
	sysMonitorExit((Word)mon);
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassFields(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassFields(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassMethods(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassMethods(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetClassConstructors(JNIEnv *env, jclass cls, jint which)
{
	printf("JVM_GetClassConstructors(JNIEnv *env, jclass cls, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassField(JNIEnv *env, jclass cls, jstring name, jint which)
{
	printf("JVM_GetClassField(JNIEnv *env, jclass cls, jstring name, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassMethod(JNIEnv *env, jclass cls, jstring name, jobjectArray types, jint which)
{
	printf("JVM_GetClassMethod(JNIEnv *env, jclass cls, jstring name, jobjectArray types, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_GetClassConstructor(JNIEnv *env, jclass cls, jobjectArray types, jint which)
{
	printf("JVM_GetClassConstructor(JNIEnv *env, jclass cls, jobjectArray types, jint which)");
}

JNIEXPORT jobject JNICALL
JVM_NewInstance(JNIEnv *env, jclass cls)
{
	printf("JVM_NewInstance(JNIEnv *env, jclass cls)");
}

JNIEXPORT jobject JNICALL
JVM_GetField(JNIEnv *env, jobject field, jobject obj)
{
	printf("JVM_GetField(JNIEnv *env, jobject field, jobject obj)");
}

JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveField(JNIEnv *env, jobject field, jobject obj, unsigned char wCode)
{
	printf("JVM_GetPrimitiveField(JNIEnv *env, jobject field, jobject obj, unsigned char wCode)");
}

JNIEXPORT void JNICALL
JVM_SetField(JNIEnv *env, jobject field, jobject obj, jobject val)
{
	printf("JVM_SetField(JNIEnv *env, jobject field, jobject obj, jobject val)");
}

JNIEXPORT void JNICALL
JVM_SetPrimitiveField(JNIEnv *env, jobject field, jobject obj, jvalue v, unsigned char vCode)
{
	printf("JVM_SetPrimitiveField(JNIEnv *env, jobject field, jobject obj, jvalue v, unsigned char vCode)");
}

JNIEXPORT jobject JNICALL
JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0)
{
	printf("JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0)");
}

JNIEXPORT jobject JNICALL
JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0)
{
	printf("JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0)");
}

JNIEXPORT void* JNICALL
JVM_GetManagement(jint version)
{
	printf("JVM_GetManagement(jint version)");
}

JNIEXPORT jobject JNICALL
JVM_InitAgentProperties(JNIEnv *env, jobject agent_props)
{
	printf("JVM_InitAgentProperties(JNIEnv *env, jobject agent_props)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass)
{
	printf("JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass)");
}

JNIEXPORT jintArray JNICALL
JVM_GetThreadStateValues(JNIEnv* env, jint javaThreadState)
{
	printf("JVM_GetThreadStateValues(JNIEnv* env, jint javaThreadState)");
}

JNIEXPORT jobjectArray JNICALL
JVM_GetThreadStateNames(JNIEnv* env, jint javaThreadState, jintArray values)
{
	printf("JVM_GetThreadStateNames(JNIEnv* env, jint javaThreadState, jintArray values)");
}


/* =========================================================================
 * The following defines a private JVM interface that the JDK can query
 * for the JVM version and capabilities.  sun.misc.Version defines
 * the methods for getting the VM version and its capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JVM_GetVersionInfo and Abstract_VM_Version class
 *    SDK:  Version class
 *
 * Similary, a private JDK interface JDK_GetVersionInfo0 is defined for
 * JVM to query for the JDK version and capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JDK_Version class
 *    SDK:  JDK_GetVersionInfo0
 *
 * ==========================================================================
 */
typedef struct {
    /* HotSpot Express VM version string:
     * <major>.<minor>-bxx[-<identifier>][-<debug_flavor>]
     */
    unsigned int jvm_version; /* Consists of major.minor.0.build */
    unsigned int update_version : 8;         /* 0 in HotSpot Express VM */
    unsigned int special_update_version : 8; /* 0 in HotSpot Express VM */
    unsigned int reserved1 : 16;
    unsigned int reserved2;

    /* The following bits represents JVM supports that JDK has dependency on.
     * JDK can use these bits to determine which JVM version
     * and support it has to maintain runtime compatibility.
     *
     * When a new bit is added in a minor or update release, make sure
     * the new bit is also added in the main/baseline.
     */
    unsigned int is_attachable : 1;
    unsigned int is_kernel_jvm : 1;
    unsigned int : 30;
    unsigned int : 32;
    unsigned int : 32;
} jvm_version_info;

JNIEXPORT void JNICALL
JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size)
{
	printf("JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size)");
}
}
