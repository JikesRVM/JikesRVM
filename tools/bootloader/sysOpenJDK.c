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

#include "sys.h"

#include <fcntl.h> // open
#include <sys/types.h> // lseek
#include <unistd.h> // lseek, read
#include <errno.h> // errno
#include <string.h> // strerror

#include "jni.h"

#define OPENJDK_DEBUG_PRINTF(...) if (OPENJDK_INTERFACE_DEBUG) fprintf(SysTraceFile, __VA_ARGS__)

static int OPENJDK_INTERFACE_DEBUG = 0;

// Implementations of native functions required by the JDK.
// Some functions can never be called because their Java callers
// have been replaced during the build process. Those functions
// are unimplemented.

// TODO: Need a Java class that takes classes from here. Should be similar
// to JNIFunctions nad have methods annotated with @NativeBridge.
// Perhaps something like OpenJDK bridge?

// These are stubs that serve to satisfy linking constraints for
// the other native libraries of OpenJDK. They are not expected to
// be called.


#define OPENJDK_INTERFACE_VERSION 4

void UNREACHABLE(const char* functionName) {
  ERROR_PRINTF("%s called: should be unreachable because it's implemented in Java!\n", functionName);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
}

void TODO(const char* functionName) {
  ERROR_PRINTF("%s called: this function is not yet implemented!\n", functionName);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
}

// java.lang.Class support ?

JNIEXPORT void JNICALL JVM_GetClassName() {
  UNREACHABLE("JVM_GetClassName");
}

JNIEXPORT void JNICALL JVM_GetClassInterfaces() {
  UNREACHABLE("JVM_GetClassInterfaces");
}

JNIEXPORT void JNICALL JVM_GetClassLoader() {
  UNREACHABLE("JVM_GetClassLoader");
}

JNIEXPORT void JNICALL JVM_IsInterface() {
  UNREACHABLE("JVM_IsInterface");
}

JNIEXPORT void JNICALL JVM_GetClassSigners() {
  UNREACHABLE("JVM_GetClassSigners");
}

JNIEXPORT void JNICALL JVM_SetClassSigners() {
  UNREACHABLE("JVM_SetClassSigners");
}

JNIEXPORT void JNICALL JVM_IsArrayClass() {
  UNREACHABLE("JVM_IsArrayClass");
}

JNIEXPORT void JNICALL JVM_IsPrimitiveClass() {
  UNREACHABLE("JVM_IsPrimitiveClass");
}

JNIEXPORT void JNICALL JVM_GetComponentType() {
  UNREACHABLE("JVM_GetComponentType");
}

JNIEXPORT void JNICALL JVM_GetClassModifiers() {
  UNREACHABLE("JVM_GetClassModifiers");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredFields() {
  UNREACHABLE("JVM_GetClassDeclaredFields");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredMethods() {
  UNREACHABLE("JVM_GetClassDeclaredMethods");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredConstructors() {
  UNREACHABLE("JVM_GetClassDeclaredConstructors");
}

JNIEXPORT void JNICALL JVM_GetProtectionDomain() {
  UNREACHABLE("JVM_GetClassDeclaredConstructors");
}

JNIEXPORT void JNICALL JVM_SetProtectionDomain() {
  UNREACHABLE("JVM_SetProtectionDomain");
}

JNIEXPORT void JNICALL JVM_GetDeclaredClasses() {
  UNREACHABLE("JVM_GetDeclaredClasses");
}

JNIEXPORT void JNICALL JVM_GetDeclaringClass() {
  UNREACHABLE("JVM_GetDeclaringClass");
}

JNIEXPORT void JNICALL JVM_GetClassSignature() {
  UNREACHABLE("JVM_GetClassSignature");
}

JNIEXPORT void JNICALL JVM_GetClassAnnotations() {
  UNREACHABLE("JVM_GetClassAnnotations");
}

JNIEXPORT void JNICALL JVM_GetClassConstantPool() {
  UNREACHABLE("JVM_GetClassConstantPool");
}

JNIEXPORT void JNICALL JVM_DesiredAssertionStatus() {
  UNREACHABLE("JVM_DesiredAssertionStatus");
}

JNIEXPORT void JNICALL JVM_GetEnclosingMethodInfo() {
  UNREACHABLE("JVM_GetEnclosingMethodInfo");
}

JNIEXPORT void JNICALL JVM_AssertionStatusDirectives() {
  UNREACHABLE("JVM_AssertionStatusDirectives");
}

// java.lang.compiler support ?

JNIEXPORT void JNICALL JVM_CompileClass() {
  UNREACHABLE("JVM_CompileClass");
}

JNIEXPORT void JNICALL JVM_CompileClasses() {
  UNREACHABLE("JVM_CompileClasses");
}

JNIEXPORT void JNICALL JVM_CompilerCommand() {
  UNREACHABLE("JVM_CompilerCommand");
}

JNIEXPORT void JNICALL JVM_EnableCompiler() {
  UNREACHABLE("JVM_EnableCompiler");
}

JNIEXPORT void JNICALL JVM_DisableCompiler() {
  UNREACHABLE("JVM_DisableCompiler");
}

// java.lang.System support?

JNIEXPORT void JNICALL JVM_IHashCode() {
  UNREACHABLE("JVM_IHashCode");
}

// java.lang.Object support ?

JNIEXPORT void JNICALL JVM_MonitorWait() {
  UNREACHABLE("JVM_MonitorWait");
}

JNIEXPORT void JNICALL JVM_MonitorNotify() {
  UNREACHABLE("JVM_MonitorNotify");
}

JNIEXPORT void JNICALL JVM_MonitorNotifyAll() {
  UNREACHABLE("JVM_MonitorNotifyAll");
}

JNIEXPORT void JNICALL JVM_Clone() {
  UNREACHABLE("JVM_Clone");
}

// java.lang.Runtime support ?

JNIEXPORT void JNICALL JVM_CurrentTimeMillis() {
  UNREACHABLE("JVM_CurrentTimeMillis");
}

JNIEXPORT void JNICALL JVM_NanoTime() {
  UNREACHABLE("JVM_NanoTime");
}

// java.lang.System support?
JNIEXPORT void JNICALL JVM_ArrayCopy() {
  UNREACHABLE("JVM_ArrayCopy");
}


// java.lang.Thread support?

JNIEXPORT void JNICALL JVM_StartThread() {
  UNREACHABLE("JVM_StartThread");
}

JNIEXPORT void JNICALL JVM_StopThread() {
  UNREACHABLE("JVM_StartThread");
}

JNIEXPORT void JNICALL JVM_IsThreadAlive() {
  UNREACHABLE("JVM_IsThreadAlive");
}

JNIEXPORT void JNICALL JVM_SuspendThread() {
  UNREACHABLE("JVM_SuspendThread");
}

JNIEXPORT void JNICALL JVM_SetThreadPriority() {
  UNREACHABLE("JVM_SetThreadPriority");
}

JNIEXPORT void JNICALL JVM_ResumeThread() {
  UNREACHABLE("JVM_ResumeThread");
}

JNIEXPORT void JNICALL JVM_Yield() {
  UNREACHABLE("JVM_Yield");
}

JNIEXPORT void JNICALL JVM_Sleep() {
  UNREACHABLE("JVM_Sleep");
}

JNIEXPORT void JNICALL JVM_CurrentThread() {
  UNREACHABLE("JVM_CurrentThread");
}

JNIEXPORT void JNICALL JVM_CountStackFrames() {
  UNREACHABLE("JVM_CountStackFrames");
}

JNIEXPORT void JNICALL JVM_Interrupt() {
  UNREACHABLE("JVM_Interrupt");
}

JNIEXPORT void JNICALL JVM_IsInterrupted() {
  UNREACHABLE("JVM_IsInterrupted");
}

JNIEXPORT void JNICALL JVM_HoldsLock() {
  UNREACHABLE("JVM_HoldsLock");
}

// JMX support ?

JNIEXPORT void JNICALL JVM_GetAllThreads() {
  UNREACHABLE("JVM_HoldsLock");
}

JNIEXPORT void JNICALL JVM_DumpThreads() {
  UNREACHABLE("JVM_DumpThreads");
}

// Meta stuff ?

JNIEXPORT jint JNICALL JVM_GetInterfaceVersion() {
  return OPENJDK_INTERFACE_VERSION;
}

// don't know

JNIEXPORT void * JNICALL JVM_RawMonitorCreate() {
  Word monitor = sysMonitorCreate();
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorCreate: created monitor at %p\n", (void *)monitor);
  return (void *)monitor;
}

JNIEXPORT char * JNICALL JVM_NativePath (char *path) {
  OPENJDK_DEBUG_PRINTF("JVM_NativePath: %s\n", path);
  // TODO Evaluate whether it's actually necessary to do anything.
  // According to upstream documentation, this function is
  // supposed to do clean up on the path string, e.g. by removing
  // unnecessary separators. The cleanup is supposed to happen
  // in-place.
  return path;
}

JNIEXPORT jint JNICALL JVM_RawMonitorEnter (void *monitor) {
  jint returnVal;
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorEnter: %p\n", monitor);
  returnVal = (jint) sysMonitorEnter((Word)monitor);
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorEnter: returning %d\n", returnVal);
  return returnVal;
}

JNIEXPORT jint JNICALL JVM_RawMonitorExit (void *monitor) {
  jint returnVal;
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorExit: %p\n", monitor);
  returnVal = (jint) sysMonitorExit((Word)monitor);
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorExit: returning %d\n", returnVal);
  return returnVal;
}

// File stuff

JNIEXPORT jint JNICALL JVM_Open(const char *fileName, jint flags, jint mode) {
  // TODO Check implementation for correctness
  int openReturnVal;
  OPENJDK_DEBUG_PRINTF("JVM_Open: %s %d %d\n", fileName, flags, mode);
  openReturnVal = open(fileName, (int) flags, (mode_t) mode);
  if (openReturnVal < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Open: error opening file %s: %s\n", fileName, strerror(savedErrorNumber));
    return (jint) openReturnVal;
  }
  OPENJDK_DEBUG_PRINTF("JVM_Open: returning %d\n", openReturnVal);
  return (jint) openReturnVal;
}

JNIEXPORT jlong JNICALL JVM_Lseek(jint fileDescriptor, jlong offset, jint whence) {
  // TODO Check implementation for correctness
  // TODO 64-bit large file support with lseek64
  long newPosition;
  OPENJDK_DEBUG_PRINTF("JVM_Lseek: %d %ld %d\n", fileDescriptor, (long) offset, (int) whence);
  newPosition = lseek((int) fileDescriptor, (off_t) offset, (int) whence);
  if (newPosition < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Lseek: error re-positioning offset in open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    // FIXME ought to return the unchanged offset here
  }
  return (jlong) newPosition;
}

JNIEXPORT jint JNICALL JVM_Read(jint fileDescriptor, char * buffer, jint byteCount) {
  int bytesRead;
  OPENJDK_DEBUG_PRINTF("JVM_Read: %d %p %d\n", (int) fileDescriptor, (void *) buffer, (int) byteCount);
  bytesRead = (jint) read(fileDescriptor, buffer, (size_t) byteCount);
  if (bytesRead < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Read: error reading bytes from open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    return -1;
  }
  return 0;
}

JNIEXPORT jint JNICALL JVM_Write(jint fileDescriptor, char * buffer, jint byteCount) {
  int bytesWritten;
  OPENJDK_DEBUG_PRINTF("JVM_Write: %d %p %d\n", (int) fileDescriptor, (void *) buffer, (int) byteCount);
  bytesWritten = write((int) fileDescriptor, buffer, (size_t) byteCount);
  if (bytesWritten < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Write: error writing bytes to open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    return -1;
  }
  return 0;
}

JNIEXPORT jint JNICALL JVM_Close(jint fileDescriptor) {
  int returnVal;
  OPENJDK_DEBUG_PRINTF("JVM_Close: %d\n", fileDescriptor);
  returnVal = (jint) close(fileDescriptor);
  if (returnVal < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Close: error closing file with descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    return -1;
  }
  OPENJDK_DEBUG_PRINTF("JVM_Close: returning %d\n", returnVal);
  return (jint) returnVal;
}

// Atomic Long

JNIEXPORT jboolean JNICALL JVM_SupportsCX8() {
  return (jboolean) JNI_TRUE;
}


// Error handling

JNIEXPORT jint JNICALL JVM_GetLastErrorString(char *buffer, int bufferLength) {
  // TODO Does this refer to native errors? If so, we need to save the last error
  // number into thread-local storage and read it from there. This will require
  // some changes to other sys* files.
  const char * errorString = strerror(errno);
  int strLen = strlen(errorString);
  int copySize = strLen;
  if (copySize >= bufferLength) {
    copySize = bufferLength - 1;
    buffer[bufferLength] = '\0';
  }
  strncpy(buffer, errorString, copySize);
  return (jint) copySize;
}
