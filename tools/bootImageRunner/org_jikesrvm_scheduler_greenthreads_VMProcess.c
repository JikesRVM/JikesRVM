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
 
/*****************************************************************
 * JNI interface to manage processes.
 */

// Unix includes
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <assert.h>
#if (defined __MACH__ )
#include <crt_externs.h>
#define environ (*_NSGetEnviron())
#else
extern char **environ;
#endif

// Java includes
#include <jni.h>

#define NEED_EXIT_STATUS_CODES
#include "InterfaceDeclarations.h"

// generated class header
#include "org_jikesrvm_scheduler_greenthreads_VMProcess.h"

// local stuff

// Enable debug print statements?
//#define DEBUG

//////////////////////////////////////////////////////////////
// Private functions and data
//////////////////////////////////////////////////////////////

// A class to represent arrays of malloc'ed C strings
// as used for argv[] and env[] arrays.  By making this
// a class whose instances are allocated on the stack,
// we can be sure that the memory is cleaned
// up properly regardness of how the code using
// the array returns or handles errors.  (In C++ terminology,
// this is an "auto pointer").
class StringArray {
private:
  int numStrings;
  char **array;

public:
  // Constructor
  StringArray(int numStrings_) {
    // Allocate the array to hold pointers to the strings.
    // Note that both argv[] and env[] must be terminated
    // by a null pointer.  Hence, we need to allocate space
    // for an additional pointer (in addition to the pointers
    // for the actual strings).
    numStrings = numStrings_;
    int arrayLength = numStrings_ + 1;
    array = (char**) malloc(sizeof(char*) * (arrayLength));

    // Set all string pointers to null.
    for (int i = 0; i < arrayLength; ++i)
      array[i] = 0;
  }

  // Destructor
  ~StringArray() {
    // Free array and strings, if allocated
    if (array != 0) {
      for (int i = 0; i < numStrings; ++i) {
        if (array[i] != 0)
          free(array[i]);
      }
      free(array);
    }
  }

  // Return the array.
  char **get() {
    return array;
  }

  // Set a string, which is assumed to have been
  // allocated using malloc().
  void setAndAdoptString(int num, char *str) {
    array[num] = str;
  }

  // Release the array and strings.
  // Called to indicate that they don't need to be
  // deallocated.
  void release() {
    array = 0;
  }
};

// Simple auto pointer class for a single malloc'ed string.
class StringPtr {
private:
  char *str;

public:
  StringPtr(char *stringToAdopt) { str = stringToAdopt; }

  ~StringPtr() {
    if (str != 0)
      free(str);
  }

  char *get() { return str; }

  void release() { str = 0; }
};

// Convert a Java string to a C string.
//
// Taken:
// env - the JNIEnv
// jstr - the Java String to be converted
//
// Returned: pointer to a newly malloc'ed C string
static char *convertString(JNIEnv *env, jstring jstr)
{
  jsize len = env->GetStringLength(jstr);
  const jchar *javaChars = env->GetStringChars(jstr, 0);
  assert(javaChars);

  char *str = (char*) malloc(len + 1);

  // FIXME: we really should do a more intelligent conversion here
  for (int i = 0; i < len; ++i)
    str[i] = (char) javaChars[i];
  str[len] = '\0'; // ensure string is nul-terminated

  env->ReleaseStringChars(jstr, javaChars);

  return str;
}

// Constants for pipe creation and management
const int INPUT = 0, OUTPUT = 1;

/* Create a pipe, and set appropriate file descriptor
   field in the VMProcess object.
   Returns -1 on error, 0 on OK. */
static int
createPipe(int descriptors[2], JNIEnv* env,
           jclass processClassID, jobject self, const char* fieldName, int end)
{
    if (pipe(descriptors))
        return -1;
    jfieldID fieldID = env->GetFieldID(processClassID, fieldName, "I");
    assert(fieldID);
    env->SetIntField(self, fieldID, descriptors[end]);
#ifdef DEBUG
    fprintf(stderr, "using %d as %s\n", descriptors[end], fieldName);
#endif
    return 0;
}

/* Close file descriptors returned from pipe().

   Return -1 for error, 0 for OK.*/
static int
closePipe(int descriptors[])
{
  errno = 0;
  while (close(descriptors[INPUT]) < 0
         && errno == EINTR)
    ;
  while (close(descriptors[OUTPUT]) < 0
         && errno == EINTR)
    ;
  return errno ? -1 : 0;
}

//////////////////////////////////////////////////////////////
// Implementation of native methods
//////////////////////////////////////////////////////////////

/*
 * Class:     VMProcess
 * Method:    exec4
 * Signature: (Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_org_jikesrvm_scheduler_greenthreads_VMProcess_exec4
  (JNIEnv *env,
   jobject self,
   jstring programName,
   jobjectArray argvArguments,
   jobjectArray environment,
   jstring dirPathStr)
{

  // Get the program name
  StringPtr programString(convertString(env, programName));
#ifdef DEBUG
  fprintf(stderr, "program name is %s\n", programString.get());
#endif

  // Build argv array
  jsize argvLen = env->GetArrayLength((jarray) argvArguments);
  StringArray argv(argvLen);
  for (int i = 0; i < argvLen; ++i) {
    jstring arg = (jstring) env->GetObjectArrayElement(argvArguments, i);
    assert(arg);
    char *str = convertString(env, arg);
#ifdef DEBUG
    fprintf(stderr, "arg %d is %s\n", i, str);
#endif
    argv.setAndAdoptString(i, str);
  }

  // Build environment array (if any)
  jsize envpLen = (environment != 0)
    ? env->GetArrayLength((jarray) environment)
    : 0;
  StringArray envp(envpLen);
  for (int i = 0; i < envpLen; ++i) {
    jstring arg = (jstring) env->GetObjectArrayElement(environment, i);
    assert(arg);
    char *str = convertString(env, arg);
#ifdef DEBUG
    fprintf(stderr, "env %d is %s\n", i, str);
#endif
    envp.setAndAdoptString(i, str);
  }

  // Get the directory path (if any)
  StringPtr dirPath(
    (dirPathStr != 0)
      ? convertString(env, dirPathStr)
      : 0
  );
#ifdef DEBUG
  fprintf(stderr, "working directory is %s\n",
    (dirPath.get() != 0) ? dirPath.get() : "unspecified, will use current");
#endif

  // Create pipes to communicate with child process.

  jclass ProcessClassID = env->FindClass( "org/jikesrvm/scheduler/greenthreads/VMProcess" );
  assert(ProcessClassID);
  int inputPipe[2], outputPipe[2], errorPipe[2];
  pid_t fid = -1;
  int ret = createPipe(inputPipe, env, ProcessClassID, self,
                       "inputDescriptor", OUTPUT);
  if (ret)
    goto fail;
  ret = createPipe(outputPipe, env, ProcessClassID, self,
                   "outputDescriptor", INPUT);
  if (ret)
    goto close_inputPipe_and_fail;
  ret = createPipe(errorPipe, env, ProcessClassID, self,
                   "errorDescriptor", INPUT);
  if (ret)
    goto close_outputPipe_and_fail;

  // do the exec
  fid = fork();
  if (fid == 0) {
    // child

    // If a working directory was specified, try to
    // make it the current directory.
    if (dirPath.get() != 0) {
      if (chdir(dirPath.get()) != 0) {
#ifdef DEBUG
        fprintf(stderr, "chdir() failed: %s\n", strerror(errno));
#endif
        // FIXME:
        // Presumably we should throw some sort of I/O error
        // (from Runtime.exec()) if we can't change into the
        // working directory the caller specified.
        // Instead, we just return this value as the exit code.
        exit(EXIT_STATUS_BAD_WORKING_DIR);
      }
    }

#define SHOULD_NEVER_FAIL(cmd) do                       \
{                                                       \
  if ((cmd) < 0) {                                      \
    perror(#cmd " failed, but should never; aborting"); \
    abort();                                            \
  }                                                     \
} while(0)

    /* Attach pipes to stdin, stdout, stderr
       These absolutely should never fail. */
    SHOULD_NEVER_FAIL(dup2(inputPipe[INPUT], 0));
    SHOULD_NEVER_FAIL(dup2(outputPipe[OUTPUT], 1));
    SHOULD_NEVER_FAIL(dup2(errorPipe[OUTPUT], 2));

    /* Close the original file descriptors returned by pipe().  Since they're
       already open, they should never fail either. */
    SHOULD_NEVER_FAIL(closePipe(inputPipe));
    SHOULD_NEVER_FAIL(closePipe(outputPipe));
    SHOULD_NEVER_FAIL(closePipe(errorPipe));

    // Set environment for child process.
    if (environment != 0) {
      environ = envp.get();
    }
#if 0
    else {
      fprintf(stderr, "Current environment:\n");
      char **p = environ;
      while (*p != 0 ) {
        fprintf(stderr, "\t%s\n", *p);
        ++p;
      }
    }
#endif

    // Execute the program.
    // XXX See comment below on error handling.
    // int err = execvp(programString.get(), argv.get());
    (void) execvp(programString.get(), argv.get());
    // We get here only if an error occurred.

#ifdef DEBUG
    fprintf(stderr, "execvp() failed: %s\n", strerror(errno));
#endif

    programString.release();
    argv.release();
    envp.release();
    dirPath.release();

    // FIXME:
    // Unfortunately, it's difficult to convey an error code
    // back to the parent process to let it know that we couldn't
    // actually execute the program.  We could use shared memory
    // or a special pipe to send the error information.
    // For now, just exit with a non-zero status.
    /* However, traditionally the shell and xargs use status 127 to mean that
     * they were unable to find something to execute.
     * To quote the bash manpage, "If a command is found
     *  but is not executable, the return status is 126.?
     * We shall adopt those customs here. --Steve Augart*/
    if (errno == ENOENT || errno == ENOTDIR)
        exit(EXIT_STATUS_EXECUTABLE_NOT_FOUND);
    exit(EXIT_STATUS_COULD_NOT_EXECUTE); // couldn't be executed for some
                                         // other reason.
  } else if (fid > 0) {
    // parent

    // Store child's pid
    jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
    assert(pidFieldID);
    env->SetIntField(self, pidFieldID, fid);
#ifdef DEBUG
    fprintf(stderr, "child process id is %d\n", fid);
#endif

    // Close unused ends of pipes

    // input side of child's stdin:
    SHOULD_NEVER_FAIL(close(inputPipe[INPUT]));
    // output side of child's stdout:
    SHOULD_NEVER_FAIL(close(outputPipe[OUTPUT]));
    // output side of child's stderr
    SHOULD_NEVER_FAIL(close(errorPipe[OUTPUT]));

    // Note: memory for programName, argv, and envp will be cleaned
    // up automatically

#ifdef DEBUG
    fprintf(stderr, "done exec\n");
#endif

    return fid;
  }
  else {
    // An error occurred in fork()
#ifdef DEBUG
    fprintf(stderr, "fork() failed: %s\n", strerror(errno));
#endif

    // Close pipes
    closePipe(errorPipe);
  close_outputPipe_and_fail:
    closePipe(outputPipe);
  close_inputPipe_and_fail:
    closePipe(inputPipe);
  fail:
    return -1;
  }
}

/*
 * Class:     VMProcess
 * Method:    destroyInternal
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_jikesrvm_scheduler_greenthreads_VMProcess_destroyInternal
  (JNIEnv *env, jobject self)
{
  // extract pid field from VMProcess object
  jclass ProcessClassID = env->GetObjectClass( self );
  assert(ProcessClassID);
  jfieldID pidFieldID = env->GetFieldID(ProcessClassID, "pid", "I");
  assert(pidFieldID);
  int pid = env->GetIntField(self, pidFieldID);

  // send kill signal
  kill(pid, SIGTERM);
}
