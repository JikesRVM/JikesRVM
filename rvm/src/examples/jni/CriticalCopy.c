/*
 * (C) Copyright IBM Corp. 2001
 *$Id$
 */
/* Test JNI Functions that access Java internal data directly
 * Implement native methods from CriticalCopy.java 
 * 
 * @author Ton Ngo, Steve Smith 
 * @date   6/19/00
 */

#include <CriticalCopy.h>

int verbose=1;

/*
 * Class:     CriticalCopy
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_CriticalCopy_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}

/*
 * Class:     CriticalCopy
 * Method:    primitiveIntegerArray
 * Signature: ([I)I
 */
JNIEXPORT jint JNICALL Java_CriticalCopy_primitiveIntegerArray
  (JNIEnv *env, jclass cls, jintArray sourceArray) {

  int i;
  jint length;
  jint *nativeArray;
  jboolean copyFlag = JNI_TRUE;

  length = (*env) -> GetArrayLength(env, sourceArray);
  nativeArray = (*env) -> GetPrimitiveArrayCritical(env, sourceArray, &copyFlag);

  /* check the flag to make sure we get direct access instead of a copy */
  if (copyFlag==JNI_TRUE) {
    printf(" > GetPrimitiveArrayCritical: expect to get a direct pointer, got a copy instead\n");
    return -1;
  }

  if (verbose) {
    printf("Array length is %d\n", length);
    printf("Array address is %p\n", nativeArray);
    printf("Current contents: \n");
    for (i=0; i<length; i++) {
      printf("    %d = %d\n", i, nativeArray[i]);
    }
  }


  /* fill up the array with new values */
  for (i=0; i<length; i++) {
    nativeArray[i] = i;
  }

  (*env) -> ReleasePrimitiveArrayCritical(env, sourceArray, nativeArray, 0);
  
  return 0;

}

/*
 * Class:     CriticalCopy
 * Method:    primitiveByteArray
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_CriticalCopy_primitiveByteArray
  (JNIEnv *env, jclass cls, jbyteArray sourceArray) {



  return 0;

}

