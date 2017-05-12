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
 * Implementation of JNI Invocation API for Jikes RVM.
 */

#define NEED_BOOT_RECORD_INITIALIZATION 1
#include "sys.h"
#include <stdarg.h>
#include <unistd.h> // getpagesize
#include <string.h> // strerror

#include <errno.h> // errno
#include <sys/mman.h>  // PROT_*

#ifdef RVM_FOR_HARMONY
#ifdef RVM_FOR_LINUX
#define LINUX 1
#endif
#include "hythread.h"
#endif

TLS_KEY_TYPE VmThreadKey;

/** String used for name of RVM */
char *Me;

/** C access to shared C/Java boot record data structure */
struct BootRecord *bootRecord;

/** Number of Java args */
int JavaArgc;

/** Java args */
char **JavaArgs;

/** Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile;

/** Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile;

/** Verbose command line option */
int verbose = 0;

/** Verbose signal handling command line option */
int verboseSignalHandling = 0;

/** Verbose boot up set */
int verboseBoot = 0;

/** File name for part of boot image containing code */
char *bootCodeFilename;

/** File name for part of boot image containing data */
char *bootDataFilename;

/** File name for part of boot image containing the root map */
char *bootRMapFilename;

Extent initialHeapSize;
Extent maximumHeapSize;
Extent pageSize;

/* Prototypes */
static jint JNICALL DestroyJavaVM(JavaVM UNUSED * vm);
static jint JNICALL AttachCurrentThread(JavaVM UNUSED * vm, /* JNIEnv */ void ** penv, /* JavaVMAttachArgs */ void *args);
static jint JNICALL DetachCurrentThread(JavaVM UNUSED *vm);
static jint JNICALL GetEnv(JavaVM UNUSED *vm, void **penv, jint version);
static jint JNICALL AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, /* JNIEnv */ void UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args);

static void  sysSetJNILinkage();

static jobject JNICALL NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...);

static jobject JNICALL CallObjectMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jboolean JNICALL CallBooleanMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jbyte JNICALL CallByteMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jchar JNICALL CallCharMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jshort JNICALL CallShortMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jint JNICALL CallIntMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jlong JNICALL CallLongMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jfloat JNICALL CallFloatMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jdouble JNICALL CallDoubleMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static void JNICALL CallVoidMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);

static jobject JNICALL CallNonvirtualObjectMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jboolean JNICALL CallNonvirtualBooleanMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jbyte JNICALL CallNonvirtualByteMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jchar JNICALL CallNonvirtualCharMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jshort JNICALL CallNonvirtualShortMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jint JNICALL CallNonvirtualIntMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jlong JNICALL CallNonvirtualLongMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jfloat JNICALL CallNonvirtualFloatMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jdouble JNICALL CallNonvirtualDoubleMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static void JNICALL CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);

static jobject JNICALL CallStaticObjectMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jboolean JNICALL CallStaticBooleanMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jbyte JNICALL CallStaticByteMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jchar JNICALL CallStaticCharMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jshort JNICALL CallStaticShortMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jint JNICALL CallStaticIntMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jlong JNICALL CallStaticLongMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jfloat JNICALL CallStaticFloatMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jdouble JNICALL CallStaticDoubleMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static void JNICALL CallStaticVoidMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);

/** JNI invoke interface implementation */
static const struct JNIInvokeInterface_ externalJNIFunctions = {
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  DestroyJavaVM,
  AttachCurrentThread,
  DetachCurrentThread,
  GetEnv,         // JNI 1.2
  AttachCurrentThreadAsDaemon   // JNI 1.4
};

/** JavaVM interface implementation */
const struct JavaVM_ sysJavaVM = {
  &externalJNIFunctions, // functions
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  NULL, // threadIDTable
  NULL, // jniEnvTable
};

/** JNI standard JVM initialization arguments */
JavaVMInitArgs *sysInitArgs;

/**
 * Fish out an address stored in an instance field of an object.
 */
static void * getFieldAsAddress(void *objPtr, int fieldOffset)
{
  char *fieldAddress = ((char*) objPtr) + fieldOffset;
  return *((void**) fieldAddress);
}

/**
 * Get the JNI environment object from the VM thread.
 */
static JNIEnv * getJniEnvFromVmThread(void *vmThreadPtr)
{
  void *jniEnvironment;
  void *jniEnv;
  if (vmThreadPtr == 0)
    return 0; // oops

  // Follow chain of pointers:
  // RVMThread -> JNIEnvironment -> thread's native JNIEnv
  jniEnvironment = getFieldAsAddress(vmThreadPtr, RVMThread_jniEnv_offset);
  // Convert JNIEnvironment to JNIEnv* expected by native code
  // by creating the appropriate interior pointer.
  jniEnv = ((char*)jniEnvironment + JNIEnvironment_JNIExternalFunctions_offset);

  return (JNIEnv*) jniEnv;
}


//////////////////////////////////////////////////////////////
// JNI Invocation API functions
//////////////////////////////////////////////////////////////

/**
 * Destroying the Java VM only makes sense if programs can create a VM
 * on-the-fly. Further, as of Sun's Java 1.2, it still didn't support
 * unloading virtual machine instances. It is supposed to block until all
 * other user threads are gone, and then return an error code.
 *
 * TODO: Implement.
 */
static jint JNICALL DestroyJavaVM(JavaVM UNUSED * vm)
{
  ERROR_PRINTF("JikesRVM: Unimplemented JNI call DestroyJavaVM\n");
  return JNI_ERR;
}

/**
 * "Trying to attach a thread that is already attached is a no-op".  We
 * implement that common case.  (In other words, it works like GetEnv()).
 * However, we do not implement the more difficult case of actually attempting
 * to attach a native thread that is not currently attached to the VM.
 *
 * TODO: Implement for actually attaching unattached threads.
 */
static jint JNICALL AttachCurrentThread(JavaVM * vm, /* JNIEnv */ void ** penv, /* JavaVMAttachArgs */ void *args)
{
  JavaVMAttachArgs *aargs = (JavaVMAttachArgs *) args;
  jint version;
  jint retval;
  if (aargs == NULL) {
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
  retval = GetEnv(vm, penv, version);
  if (retval == JNI_OK)
    return retval;
  else if (retval == JNI_EDETACHED) {
    ERROR_PRINTF("JikesRVM: JNI call AttachCurrentThread Unimplemented for threads not already attached to the VM\n");
  } else {
    ERROR_PRINTF("JikesRVM: JNI call AttachCurrentThread failed; returning UNEXPECTED error code %d\n", (int) retval);
  }

  // Upon failure:
  *penv = NULL;               // Make sure we don't yield a bogus one to use.
  return retval;
}

/* TODO: Implement */
static jint JNICALL DetachCurrentThread(JavaVM UNUSED *vm)
{
  ERROR_PRINTF("UNIMPLEMENTED JNI call DetachCurrentThread\n");
  return JNI_ERR;
}

jint JNICALL GetEnv(JavaVM UNUSED *vm, void **penv, jint version)
{
  void *vmThread;
  JNIEnv *env;
  if (version > JNI_VERSION_1_4)
    return JNI_EVERSION;

  // Return NULL if we are not on a VM thread
  vmThread = GET_THREAD_LOCAL(VmThreadKey);
  if (vmThread == NULL) {
    *penv = NULL;
    return JNI_EDETACHED;
  }

  // Get the JNIEnv from the RVMThread object
  env = getJniEnvFromVmThread(vmThread);

  *((JNIEnv**)penv) = env;

  return JNI_OK;
}

/** JNI 1.4 */
/* TODO: Implement */
static jint JNICALL AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, /* JNIEnv */ void UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args)
{
  ERROR_PRINTF("Unimplemented JNI call AttachCurrentThreadAsDaemon\n");
  return JNI_ERR;
}

/**
 * Determines the page size.
 * Taken:     (no arguments)
 * Returned:  page size in bytes (Java int)
 */
Extent determinePageSize()
{
  TRACE_PRINTF("%s: determinePageSize\n", Me);
  Extent pageSize = -1;
#ifdef _SC_PAGESIZE
  pageSize = (Extent) sysconf(_SC_PAGESIZE);
#elif _SC_PAGE_SIZE
  pageSize = (Extent) sysconf(_SC_PAGE_SIZE);
#else
  pageSize = (Extent) getpagesize();
#endif
  return pageSize;
}

/**
 * Map the given file to memory
 *
 * Taken:     fileName         [in] name of file
 *            targetAddress    [in] address to load file to
 *            executable       [in] are we mapping code into memory
 *            writable         [in] do we need to write to this memory?
 *            roundedImageSize [out] size of mapped memory rounded up to a whole
 * Returned:  address of mapped region
 */
static void* mapImageFile(const char *fileName, const void *targetAddress,
                          jboolean executable, jboolean writable, Extent *roundedImageSize) {
  Extent actualImageSize;
  void *bootRegion = 0;
  TRACE_PRINTF("%s: mapImageFile \"%s\" to %p\n", Me, fileName, targetAddress);
  FILE *fin = fopen (fileName, "r");
  if (!fin) {
    ERROR_PRINTF("%s: can't find bootimage file\"%s\"\n", Me, fileName);
    return 0;
  }
  /* measure image size */
  fseek (fin, 0L, SEEK_END);
  actualImageSize = (uint64_t) ftell(fin);
  *roundedImageSize = pageRoundUp(actualImageSize, pageSize);
  fseek (fin, 0L, SEEK_SET);
  int prot = PROT_READ;
  if (writable)
    prot |= PROT_WRITE;
  if (executable)
    prot |= PROT_EXEC;
  bootRegion = mmap((void*)targetAddress, *roundedImageSize,
       prot,
       MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE,
       fileno(fin), 0);
  if (bootRegion == (void *) MAP_FAILED) {
    ERROR_PRINTF("%s: mmap failed (errno=%d): %s\n", Me, errno, strerror(errno));
    return 0;
  }
  /* Quoting from the Linux mmap(2) manual page:
     "closing the file descriptor does not unmap the region."
  */
  if (fclose (fin) != 0) {
    ERROR_PRINTF("%s: close failed (errno=%d)\n", Me, errno);
    return 0;
  }
  if (bootRegion != targetAddress) {
    ERROR_PRINTF("%s: Attempted to mapImageFile to the address %p; "
    " got %p instead.  This should never happen.",
    Me, targetAddress, bootRegion);
    (void) munmap(bootRegion, *roundedImageSize);
    return 0;
  }
  return bootRegion;
}

/**
 * Start the VM
 *
 * Taken:     vmInSeparateThread [in] create a thread for the VM to
 * execute in rather than this thread
 * Returned:  1 upon any errors.  Never returns except to report an
 * error.
 */
int createVM(int vmInSeparateThread)
{
  Extent roundedDataRegionSize;
  // Note that the data segment must be mapped as executable
  // because code for lazy compilation trampolines is placed
  // in the TIBs and TIBs are placed in the data segment.
  // See RVM-678.
  void *bootDataRegion = mapImageFile(bootDataFilename,
             (void *)bootImageDataAddress,
             JNI_TRUE,
                                      JNI_TRUE,
             &roundedDataRegionSize);
  if (bootDataRegion != (void *)bootImageDataAddress)
    return 1;

  Extent roundedCodeRegionSize;
  // Note that the code segment must be mapped as writable because the
  // optimizing compiler may try to patch methods in the boot image. If the
  // code from the boot image were write-protected, this would cause a
  // segmentation fault, which would manifest as a NullPointerException
  // with the current implementation (May 2015). If we wanted to have
  // read-only code for the boot image, we would need to make sure that
  // it is never necessary to patch code from the boot image.
  void *bootCodeRegion = mapImageFile(bootCodeFilename,
             (void *)bootImageCodeAddress,
             JNI_TRUE,
                                      JNI_TRUE,
             &roundedCodeRegionSize);
  if (bootCodeRegion != (void *)bootImageCodeAddress)
    return 1;

  Extent roundedRMapRegionSize;
  void *bootRMapRegion = mapImageFile(bootRMapFilename,
             (void *)bootImageRMapAddress,
             JNI_FALSE,
                                      JNI_FALSE,
             &roundedRMapRegionSize);
  if (bootRMapRegion != (void *)bootImageRMapAddress)
    return 1;


  /* validate contents of boot record */
  bootRecord = (struct BootRecord *) bootDataRegion;

  if (bootRecord->bootImageDataStart != (Address) bootDataRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
     Me, (void *)bootRecord->bootImageDataStart, bootDataRegion);
    return 1;
  }

  if (bootRecord->bootImageCodeStart != (Address) bootCodeRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
     Me, (void *)bootRecord->bootImageCodeStart, bootCodeRegion);
    return 1;
  }

  if (bootRecord->bootImageRMapStart != (Address) bootRMapRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
     Me, (void *)bootRecord->bootImageRMapStart, bootRMapRegion);
    return 1;
  }

  if ((bootRecord->spRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: sp (%p) is not word aligned\n",
     Me, (void *)bootRecord->spRegister);
    return 1;
  }

  if ((bootRecord->ipRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: ip (%p) is not word aligned\n",
     Me, (void *)bootRecord->ipRegister);
    return 1;
  }

  if (((uint32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
    ERROR_PRINTF("%s: image format error: missing stack sanity check marker (0x%x)\n",
    Me, ((int *) bootRecord->spRegister)[-1]);
    return 1;
  }

  /* write freespace information into boot record */
  bootRecord->initialHeapSize  = initialHeapSize;
  bootRecord->maximumHeapSize  = maximumHeapSize;
  bootRecord->bootImageDataStart   = (Address) bootDataRegion;
  bootRecord->bootImageDataEnd     = (Address) bootDataRegion + roundedDataRegionSize;
  bootRecord->bootImageCodeStart   = (Address) bootCodeRegion;
  bootRecord->bootImageCodeEnd     = (Address) bootCodeRegion + roundedCodeRegionSize;
  bootRecord->bootImageRMapStart   = (Address) bootRMapRegion;
  bootRecord->bootImageRMapEnd     = (Address) bootRMapRegion + roundedRMapRegionSize;
  bootRecord->verboseBoot      = verboseBoot;
  bootRecord->verboseSignalHandling = verboseSignalHandling;
  bootRecord->bytesInPage = pageSize;

  /* write syscall linkage information into boot record */
  setLinkage(bootRecord);

#ifndef RVM_FOR_POWERPC
  /*
   * Add C defined JNI functions into the JNI function table,
   * overwriting existing Java-based definitions. That's not
   * very clean, but it makes sure that IA32 can use var args
   * and PPC can use the Java-based definitions.
   */
  sysSetJNILinkage();
#endif

  /* Initialize system call routines and side data structures */
  sysInitialize();

  if (verbose) {
    TRACE_PRINTF("%s: boot record contents:\n", Me);
    TRACE_PRINTF("   bootImageDataStart:   %p\n", (void *)bootRecord->bootImageDataStart);
    TRACE_PRINTF("   bootImageDataEnd:     %p\n", (void *)bootRecord->bootImageDataEnd);
    TRACE_PRINTF("   bootImageCodeStart:   %p\n", (void *)bootRecord->bootImageCodeStart);
    TRACE_PRINTF("   bootImageCodeEnd:     %p\n", (void *)bootRecord->bootImageCodeEnd);
    TRACE_PRINTF("   bootImageRMapStart:   %p\n", (void *)bootRecord->bootImageRMapStart);
    TRACE_PRINTF("   bootImageRMapEnd:     %p\n", (void *)bootRecord->bootImageRMapEnd);
    TRACE_PRINTF("   initialHeapSize:      %zu\n", bootRecord->initialHeapSize);
    TRACE_PRINTF("   maximumHeapSize:      %zu\n", bootRecord->maximumHeapSize);
    TRACE_PRINTF("   spRegister:           %p\n", (void *)bootRecord->spRegister);
    TRACE_PRINTF("   ipRegister:           %p\n", (void *)bootRecord->ipRegister);
    TRACE_PRINTF("   tocRegister:          %p\n", (void *)bootRecord->tocRegister);
    TRACE_PRINTF("   sysConsoleWriteCharIP:%p\n", (void *)bootRecord->sysConsoleWriteCharIP);
    TRACE_PRINTF("   ...etc...                   \n");
  }

  /* force any machine code within image that's still in dcache to be
   * written out to main memory so that it will be seen by icache when
   * instructions are fetched back
   */
  sysSyncCache(bootCodeRegion, roundedCodeRegionSize);

  sysStartMainThread(vmInSeparateThread, bootRecord->ipRegister, bootRecord->spRegister,
                     *(Address *) (bootRecord->tocRegister + bootRecord->bootThreadOffset),
                     bootRecord->tocRegister, &bootRecord->bootCompleted);
  // should be unreachable but return is necessary to make compiler happy
  return 1;
}

JNIEXPORT jint JNICALL JNI_CreateJavaVM(JavaVM **mainJavaVM, JNIEnv **mainJNIEnv, void *initArgs)
{
  TRACE_PRINTF("%s: JNI call CreateJavaVM\n", Me);
  *mainJavaVM = (JavaVM*)&sysJavaVM;
  *mainJNIEnv = NULL;
  sysInitArgs = initArgs;
  return createVM(0);
}

JNIEXPORT jint JNICALL JNI_GetDefaultJavaVMInitArgs(void *initArgs UNUSED)
{
  ERROR_PRINTF("UNIMPLEMENTED JNI call JNI_GetDefaultJavaVMInitArgs\n");
  return JNI_ERR;
}

JNIEXPORT jint JNICALL JNI_GetCreatedJavaVMs(JavaVM **vmBuf UNUSED, jsize buflen UNUSED, jsize *nVMs UNUSED)
{
  ERROR_PRINTF("UNIMPLEMENTED JNI call JNI_GetCreatedJavaVMs\n");
  return JNI_ERR;
}

/**
 * Insert missing ... JNI methods into JNI function table
 */
static void  sysSetJNILinkage()
{
  struct JNINativeInterface_ *jniFunctions = (struct JNINativeInterface_ *)(bootRecord->JNIFunctions);

  jniFunctions->NewObject = NewObject;
  jniFunctions->CallObjectMethod  = CallObjectMethod;
  jniFunctions->CallBooleanMethod = CallBooleanMethod;
  jniFunctions->CallByteMethod    = CallByteMethod;
  jniFunctions->CallCharMethod    = CallCharMethod;
  jniFunctions->CallShortMethod   = CallShortMethod;
  jniFunctions->CallIntMethod     = CallIntMethod;
  jniFunctions->CallLongMethod    = CallLongMethod;
  jniFunctions->CallFloatMethod   = CallFloatMethod;
  jniFunctions->CallDoubleMethod  = CallDoubleMethod;
  jniFunctions->CallVoidMethod  = CallVoidMethod;
  jniFunctions->CallNonvirtualObjectMethod  = CallNonvirtualObjectMethod;
  jniFunctions->CallNonvirtualBooleanMethod = CallNonvirtualBooleanMethod;
  jniFunctions->CallNonvirtualByteMethod    = CallNonvirtualByteMethod;
  jniFunctions->CallNonvirtualCharMethod    = CallNonvirtualCharMethod;
  jniFunctions->CallNonvirtualShortMethod   = CallNonvirtualShortMethod;
  jniFunctions->CallNonvirtualIntMethod     = CallNonvirtualIntMethod;
  jniFunctions->CallNonvirtualLongMethod    = CallNonvirtualLongMethod;
  jniFunctions->CallNonvirtualFloatMethod   = CallNonvirtualFloatMethod;
  jniFunctions->CallNonvirtualDoubleMethod  = CallNonvirtualDoubleMethod;
  jniFunctions->CallNonvirtualVoidMethod    = CallNonvirtualVoidMethod;
  jniFunctions->CallStaticObjectMethod  = CallStaticObjectMethod;
  jniFunctions->CallStaticBooleanMethod = CallStaticBooleanMethod;
  jniFunctions->CallStaticByteMethod    = CallStaticByteMethod;
  jniFunctions->CallStaticCharMethod    = CallStaticCharMethod;
  jniFunctions->CallStaticShortMethod   = CallStaticShortMethod;
  jniFunctions->CallStaticIntMethod     = CallStaticIntMethod;
  jniFunctions->CallStaticLongMethod    = CallStaticLongMethod;
  jniFunctions->CallStaticFloatMethod   = CallStaticFloatMethod;
  jniFunctions->CallStaticDoubleMethod  = CallStaticDoubleMethod;
  jniFunctions->CallStaticVoidMethod  = CallStaticVoidMethod;
}

/* Methods to box ... into a va_list */

static jobject JNICALL NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: NewObject %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->NewObjectV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jobject JNICALL CallObjectMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallObjectMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallObjectMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallBooleanMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallBooleanMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallBooleanMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallByteMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallByteMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallByteMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallCharMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallCharMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallCharMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallShortMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallShortMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallShortMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallIntMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallIntMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallIntMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallLongMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallLongMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallLongMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallFloatMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallFloatMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallFloatMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallDoubleMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallDoubleMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  result = (*env)->CallDoubleMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallVoidMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallVoidMethod %p %p\n", Me, (void *)obj, (void *)methodID);
  (*env)->CallVoidMethodV(env, obj, methodID, ap);
  va_end(ap);
}

static jobject JNICALL CallNonvirtualObjectMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualObjectMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualObjectMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallNonvirtualBooleanMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualBooleanMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualBooleanMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallNonvirtualByteMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualByteMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualByteMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallNonvirtualCharMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualCharMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualCharMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallNonvirtualShortMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualShortMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualShortMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallNonvirtualIntMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualIntMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualIntMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallNonvirtualLongMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualLongMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualLongMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallNonvirtualFloatMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualFloatMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualFloatMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallNonvirtualDoubleMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualDoubleMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  result = (*env)->CallNonvirtualDoubleMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualVoidMethod %p %p %p\n", Me, (void *)obj, (void *)clazz, (void *)methodID);
  (*env)->CallNonvirtualVoidMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
}

static jobject JNICALL CallStaticObjectMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticObjectMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticObjectMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallStaticBooleanMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticBooleanMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticBooleanMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallStaticByteMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticByteMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticByteMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallStaticCharMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticCharMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticCharMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallStaticShortMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticShortMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticShortMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallStaticIntMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticIntMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticIntMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallStaticLongMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticLongMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticLongMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallStaticFloatMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticFloatMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticFloatMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallStaticDoubleMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticDoubleMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  result = (*env)->CallStaticDoubleMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallStaticVoidMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticVoidMethod %p %p\n", Me, (void *)clazz, (void *)methodID);
  (*env)->CallStaticVoidMethodV(env, clazz, methodID, ap);
  va_end(ap);
}
