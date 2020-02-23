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
#include <sys/types.h> // lseek, socket (on some unixes)
#include <sys/stat.h> // fstat
#include <unistd.h> // lseek, read
#include <errno.h> // errno
#include <string.h> // strerror
#include <sys/ioctl.h> // ioctl
#include <sys/socket.h> // socket
#include <math.h> // isnan

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

JNIEXPORT void JNICALL JVM_GetClassName(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassName");
}

JNIEXPORT void JNICALL JVM_GetClassInterfaces(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassInterfaces");
}

JNIEXPORT void JNICALL JVM_GetClassLoader(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassLoader");
}

JNIEXPORT void JNICALL JVM_IsInterface(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_IsInterface");
}

JNIEXPORT void JNICALL JVM_GetClassSigners(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassSigners");
}

JNIEXPORT void JNICALL JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers) {
  UNREACHABLE("JVM_SetClassSigners");
}

JNIEXPORT void JNICALL JVM_IsArrayClass(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_IsArrayClass");
}

JNIEXPORT void JNICALL JVM_IsPrimitiveClass(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_IsPrimitiveClass");
}

JNIEXPORT void JNICALL JVM_GetComponentType(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetComponentType");
}

JNIEXPORT void JNICALL JVM_GetClassModifiers(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassModifiers");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  UNREACHABLE("JVM_GetClassDeclaredFields");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  UNREACHABLE("JVM_GetClassDeclaredMethods");
}

JNIEXPORT void JNICALL JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  UNREACHABLE("JVM_GetClassDeclaredConstructors");
}

JNIEXPORT void JNICALL JVM_GetProtectionDomain(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassDeclaredConstructors");
}

JNIEXPORT void JNICALL JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain) {
  UNREACHABLE("JVM_SetProtectionDomain");
}

JNIEXPORT void JNICALL JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass) {
  UNREACHABLE("JVM_GetDeclaredClasses");
}

JNIEXPORT void JNICALL JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass) {
  UNREACHABLE("JVM_GetDeclaringClass");
}

JNIEXPORT void JNICALL JVM_GetClassSignature(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassSignature");
}

JNIEXPORT void JNICALL JVM_GetClassAnnotations(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassAnnotations");
}

JNIEXPORT void JNICALL JVM_GetClassConstantPool(JNIEnv *env, jclass cls) {
  UNREACHABLE("JVM_GetClassConstantPool");
}

JNIEXPORT void JNICALL JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls) {
  UNREACHABLE("JVM_DesiredAssertionStatus");
}

JNIEXPORT void JNICALL JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass) {
  UNREACHABLE("JVM_GetEnclosingMethodInfo");
}

JNIEXPORT void JNICALL JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused) {
  UNREACHABLE("JVM_AssertionStatusDirectives");
}

// java.lang.SecurityManager and java.util.ResourceBundle

JNIEXPORT jobjectArray JNICALL JVM_GetClassContext(JNIEnv *env) {
  UNREACHABLE("JVM_GetClassContext");
}

// java.lang.Compiler support

JNIEXPORT void JNICALL JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls) {
  UNREACHABLE("JVM_CompileClass");
}

JNIEXPORT void JNICALL JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname) {
  UNREACHABLE("JVM_CompileClasses");
}

JNIEXPORT void JNICALL JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg) {
  UNREACHABLE("JVM_CompilerCommand");
}

JNIEXPORT void JNICALL JVM_EnableCompiler(JNIEnv *env, jclass compCls) {
  UNREACHABLE("JVM_EnableCompiler");
}

JNIEXPORT void JNICALL JVM_DisableCompiler(JNIEnv *env, jclass compCls) {
  UNREACHABLE("JVM_DisableCompiler");
}

// java.lang.System support?

JNIEXPORT void JNICALL JVM_IHashCode(JNIEnv *env, jobject obj) {
  UNREACHABLE("JVM_IHashCode");
}

// java.lang.Object support

JNIEXPORT void JNICALL JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms) {
  UNREACHABLE("JVM_MonitorWait");
}

JNIEXPORT void JNICALL JVM_MonitorNotify(JNIEnv *env, jobject obj) {
  UNREACHABLE("JVM_MonitorNotify");
}

JNIEXPORT void JNICALL JVM_MonitorNotifyAll(JNIEnv *env, jobject obj) {
  UNREACHABLE("JVM_MonitorNotifyAll");
}

JNIEXPORT void JNICALL JVM_Clone(JNIEnv *env, jobject obj) {
  UNREACHABLE("JVM_Clone");
}

// java.lang.Runtime support ?

JNIEXPORT void JNICALL JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored) {
  UNREACHABLE("JVM_CurrentTimeMillis");
}

JNIEXPORT void JNICALL JVM_NanoTime(JNIEnv *env, jclass ignored) {
  UNREACHABLE("JVM_NanoTime");
}

// java.lang.System support?
JNIEXPORT void JNICALL JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length) {
  UNREACHABLE("JVM_ArrayCopy");
}


// java.lang.Thread support?

JNIEXPORT void JNICALL JVM_StartThread(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_StartThread");
}

JNIEXPORT void JNICALL JVM_StopThread(JNIEnv *env, jobject thread, jobject exception) {
  UNREACHABLE("JVM_StartThread");
}

JNIEXPORT void JNICALL JVM_IsThreadAlive(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_IsThreadAlive");
}

JNIEXPORT void JNICALL JVM_SuspendThread(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_SuspendThread");
}

JNIEXPORT void JNICALL JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio) {
  UNREACHABLE("JVM_SetThreadPriority");
}

JNIEXPORT void JNICALL JVM_ResumeThread(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_ResumeThread");
}

JNIEXPORT void JNICALL JVM_Yield(JNIEnv *env, jclass threadClass) {
  UNREACHABLE("JVM_Yield");
}

JNIEXPORT void JNICALL JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis) {
  UNREACHABLE("JVM_Sleep");
}

JNIEXPORT void JNICALL JVM_CurrentThread(JNIEnv *env, jclass threadClass) {
  UNREACHABLE("JVM_CurrentThread");
}

JNIEXPORT void JNICALL JVM_CountStackFrames(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_CountStackFrames");
}

JNIEXPORT void JNICALL JVM_Interrupt(JNIEnv *env, jobject thread) {
  UNREACHABLE("JVM_Interrupt");
}

JNIEXPORT void JNICALL JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted) {
  UNREACHABLE("JVM_IsInterrupted");
}

JNIEXPORT void JNICALL JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj) {
  UNREACHABLE("JVM_HoldsLock");
}

// JMX support ?

JNIEXPORT void JNICALL JVM_GetAllThreads(JNIEnv *env, jclass dummy) {
  UNREACHABLE("JVM_HoldsLock");
}

JNIEXPORT void JNICALL JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads) {
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

JNIEXPORT void JNICALL JVM_RawMonitorDestroy(void *monitor) {
  OPENJDK_DEBUG_PRINTF("JVM_RawMonitorDestroy: %p\n", monitor);
  sysMonitorDestroy((Word)monitor);
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
    OPENJDK_DEBUG_PRINTF("JVM_Open: error opening file %s: %s\n", fileName, strerror(savedErrorNumber));
    return (jint) openReturnVal;
  }
  OPENJDK_DEBUG_PRINTF("JVM_Open: returning %d\n", openReturnVal);
  return (jint) openReturnVal;
}

JNIEXPORT jlong JNICALL JVM_Lseek(jint fileDescriptor, jlong offset, jint whence) {
  // TODO Check implementation for correctness
  // TODO 64-bit large file support with lseek64
  jlong newPosition;
  OPENJDK_DEBUG_PRINTF("JVM_Lseek: %d %ld %d\n", fileDescriptor, (long) offset, (int) whence);
  newPosition = (jlong) lseek64((int) fileDescriptor, (off64_t) offset, (int) whence);
  if (newPosition < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Lseek: error re-positioning offset in open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    // FIXME ought to return the unchanged offset here
  }
  return (jlong) newPosition;
}

JNIEXPORT jint JNICALL JVM_Read(jint fileDescriptor, char * buffer, jint byteCount) {
  jint bytesRead;
  OPENJDK_DEBUG_PRINTF("JVM_Read: %d %p %d\n", (int) fileDescriptor, (void *) buffer, (int) byteCount);
  bytesRead = (jint) read(fileDescriptor, buffer, (size_t) byteCount);
  if (bytesRead < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Read: error reading bytes from open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    return -1;
  }
  return bytesRead;
}

JNIEXPORT jint JNICALL JVM_Write(jint fileDescriptor, char * buffer, jint byteCount) {
  jint bytesWritten;
  OPENJDK_DEBUG_PRINTF("JVM_Write: %d %p %d\n", (int) fileDescriptor, (void *) buffer, (int) byteCount);
  bytesWritten = write((int) fileDescriptor, buffer, (size_t) byteCount);
  if (bytesWritten < 0) {
    int savedErrorNumber = errno;
    ERROR_PRINTF("JVM_Write: error writing bytes to open file with file descriptor %d: %s\n", fileDescriptor, strerror(savedErrorNumber));
    return -1;
  }
  return bytesWritten;
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

JNIEXPORT jint JNICALL JVM_Available(jint fd, jlong *pbytes) {
  OPENJDK_DEBUG_PRINTF("JVM_Available: %d %p\n", fd, (void *) pbytes);
  int ret = 1;
  struct stat buf;
  int fStatRet = fstat(fd, &buf);
  if (fStatRet < 0) {
    return 0;
  }
  mode_t mode = buf.st_mode;
  // directories are an error case
  if (S_ISDIR(mode)) {
    return 0;
  }

  if (S_ISFIFO(mode) || S_ISSOCK(mode)) {
    int ioctlRet = ioctl((int) fd, FIONREAD, (int *) pbytes);
    // anything non-negative is success according to API docs
    if (ioctlRet >= 0) {
        return ret;
    }
    return 0;
  }
  // TODO what to do about S_ISREG, S_ISCHR, S_ISBLK, S_ISLNK?
  // For now, use lseek64
  off64_t offsetZero = 0;
  off64_t currentOffset = lseek64(fd, offsetZero, SEEK_CUR);
  if (currentOffset < 0) {
    ERROR_PRINTF("JVM_Available: error determining current position of file with fd %d: %s\n", fd, strerror(errno));
    return 0;
  }
  off64_t endOffset = lseek64(fd, offsetZero, SEEK_END);
  if (endOffset < 0) {
    ERROR_PRINTF("JVM_Available: error determining end position of file with fd %d: %s\n", fd, strerror(errno));
    return 0;
  }
  off64_t oldOffset = lseek64(fd, currentOffset, SEEK_SET);
  if (oldOffset < 0 || oldOffset != currentOffset) {
    ERROR_PRINTF("JVM_Available: error resetting file position of file with fd %d: %s\n", fd, strerror(errno));
    return 0;
  }
  *pbytes = (jlong) (endOffset - currentOffset);
  return (jint) ret;
}

JNIEXPORT jint JNICALL JVM_Sync(jint fd) {
    int syncOk = fsync(fd);
    if (syncOk < 0) {
      ERROR_PRINTF("JVM_Sync: error calling JVM_Sync on file with fd %d: %s\n", fd, strerror(errno));
    }
    return (jint) syncOk;
}

// Atomic Long

JNIEXPORT jboolean JNICALL JVM_SupportsCX8() {
  return (jboolean) JNI_TRUE;
}


// Error handling

JNIEXPORT jint JNICALL JVM_GetLastErrorString(char *buffer, int bufferLength) {
  // no error
  if (errno == 0) {
    return (jint) 0;
  }

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

// getAvailableProcessors() from java.lang.Runtime and sun.management.VMManagementImpl

JNIEXPORT jint JVM_ActiveProcessorCount() {
  return sysNumProcessors();
}

// socket library

JNIEXPORT jint JNICALL JVM_InitializeSocketLibrary(void) {
  return (jint) 0;
}

JNIEXPORT jint JNICALL JVM_Socket(jint domain, jint type, jint protocol) {
  return socket((int) domain, (int) type, (int) protocol);
}

// dynamic libraries

// called from check for IPv6 support in OpenJDK 6 in net_util_md.c
JNIEXPORT void * JNICALL JVM_FindLibraryEntry(void *handle, const char *name) {
  OPENJDK_DEBUG_PRINTF("JVM_FindLibraryEntry: %p %s\n", handle, name);
  return sysDlsym((Address) handle, (char *) name);
}

// serialization

JNIEXPORT jboolean JNICALL JVM_IsNaN(jdouble d) {
  OPENJDK_DEBUG_PRINTF("JVM_IsNaN: %f\n", (double) d);
  return (jboolean) isnan((double) d);
}
