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
/* Test method invocation from native code
 * Implement native methods from MethodInvocation.java
 */

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>             /* malloc() */
#include <math.h>               /* fabs() */

#include "MethodInvocation.h"
#include <jni.h>

int verbose=1;

int invokeStaticMethodV_part2(JNIEnv *env, jclass cls, jobject objArg, ...);
int invokeVirtualMethodV_part2(JNIEnv *env, jclass cls, jobject objTarget, jobject objArg, ...);
int invokeNonVirtualMethodV_part2(JNIEnv *env, jclass cls, jobject objTarget, jobject objArg, ...);


/******************************************************************************
 * Class:     MethodInvocation
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_MethodInvocation_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}


/******************************************************************************
 * Class:     MethodInvocation, arguments passed as an array of jvalue unions
 * Method:    invokeStaticMethodA
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeStaticMethodA
  (JNIEnv *env, jclass cls, jobject objArg) {
  jmethodID methodID;
  jvalue *argA;
  jboolean matched;
  jclass checkClass;

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  int summary = 0;

  jlong longval = 0x01234567l;         /* some dummy long value */
  longval = longval << 16;

  /* create an array of jvalue unions and initialize arguments */
  argA = (jvalue *) malloc(sizeof(jvalue) * 9);
  (&argA[0])->b = 12;                   /* byte    */
  (&argA[1])->c = 'a';                  /* char    */
  (&argA[2])->s = 56;                   /* short   */
  (&argA[3])->i = 7890;                 /* int     */
  (&argA[4])->j = longval;              /* long    */
  (&argA[5])->f = (float)3.14;          /* float   */
  (&argA[6])->d = 2.18;                 /* double  */
  (&argA[7])->l = objArg;               /* object  */
  (&argA[8])->z = 1;                    /* boolean */



  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethodA(env, cls, methodID, argA);

  if (returnBooleanValue != 0) {
    if (verbose)
      printf("> FAIL CallStaticBooleanMethodA: return %s, expect false\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallStaticByteMethodA(env, cls, methodID, argA);
  expectByteValue = (&argA[0])->b + 3;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallStaticByteMethodA: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallStaticCharMethodA(env, cls, methodID, argA);

  if (returnCharValue != 'b') {
    if (verbose)
      printf("> FAIL CallStaticCharMethodA: return %c, expect b\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallStaticShortMethodA(env, cls, methodID, argA);
  expectShortValue = (&argA[2])->s + 15;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallStaticShortMethodA: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallStaticIntMethodA(env, cls, methodID, argA);
  expectIntValue = (&argA[3])->i + (&argA[2])->s;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallStaticIntMethodA: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallStaticLongMethodA(env, cls, methodID, argA);
  expectLongValue = (&argA[4])->j + (long) (&argA[3])->i;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallStaticLongMethodA: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallStaticFloatMethodA(env, cls, methodID, argA);
  /* expectFloatValue = (float) (&argA[5])->f + (float) 100.0; Does NOT match 103.14 ?? */
  expectFloatValue = (float) 103.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallStaticFloatMethodA: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallStaticDoubleMethodA(env, cls, methodID, argA);
  expectDoubleValue = (&argA[6])->d + 100.0;

  if (fabs(returnDoubleValue-expectDoubleValue) > 1.0E-12) {
    if (verbose)
      printf("> FAIL CallStaticDoubleMethodA: return %3.3f, expect %3.3f\n",
             returnDoubleValue, expectDoubleValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallStaticObjectMethodA(env, cls, methodID, argA);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallStaticObjectMethodA\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallStaticObjectMethodA: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnVoid\n");
    return 1;
  }

  (*env) -> CallStaticVoidMethodA(env, cls, methodID, argA);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 789) {
    if (verbose)
      printf("> FAIL CallStaticVoidMethodA: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }

  return summary;

}




/******************************************************************************
 * Class:     MethodInvocation, arguments passed as a pointer to a var args list
 *            The first part gets the C compiler to package the arguments as a va_list
 * Method:    invokeStaticMethodV
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeStaticMethodV
  (JNIEnv *env, jclass cls, jobject objArg) {

  int result;

  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'a';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 1;              /* boolean */

  result = invokeStaticMethodV_part2(env, cls, objArg, varByte, varChar, varShort, varInt,
                            varLong, varFloat, varDouble, varObject, varBoolean);

  return result;
}



int invokeStaticMethodV_part2(JNIEnv *env, jclass cls, jobject objArg, ...) {

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  jmethodID methodID;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;

  va_list ap;
  va_start(ap, objArg);

  /*
  for (i=0; i<12; i++) {
    printf("> invokeStaticMethodV: %d = 0x%08X \n", i, (int) (* ((int *) ((int)ap + (i*4))) )   );
  }
  */


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethodV(env, cls, methodID, ap);

  if (returnBooleanValue != 0) {
    if (verbose)
      printf("> FAIL CallStaticBooleanMethodV: return %s, expect false\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallStaticByteMethodV(env, cls, methodID, ap);
  expectByteValue = 15;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallStaticByteMethodV: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallStaticCharMethodV(env, cls, methodID, ap);

  if (returnCharValue != 'b') {
    if (verbose)
      printf("> FAIL CallStaticCharMethodV: return %c, expect b\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallStaticShortMethodV(env, cls, methodID, ap);
  expectShortValue = 71;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallStaticShortMethodV: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallStaticIntMethodV(env, cls, methodID, ap);
  expectIntValue = 7946;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallStaticIntMethodV: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallStaticLongMethodV(env, cls, methodID, ap);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + 7890;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallStaticLongMethodV: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallStaticFloatMethodV(env, cls, methodID, ap);
  expectFloatValue = (float) 103.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallStaticFloatMethodV: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallStaticDoubleMethodV(env, cls, methodID, ap);
  expectDoubleValue = 102.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallStaticDoubleMethodV: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallStaticObjectMethodV(env, cls, methodID, ap);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallStaticObjectMethodV\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallStaticObjectMethodV: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnVoid\n");
    return 1;
  }

  (*env) -> CallStaticVoidMethodV(env, cls, methodID, ap);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 789) {
    if (verbose)
      printf("> FAIL CallStaticVoidMethodV: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }

  va_end(ap);

  return summary;

}




/*
 * Class:     MethodInvocation, arguments passed as variable argument list in registers
 * Method:    invokeStaticMethod
 * Signature: (Ljava/lang/Object;)I
 */
/* returnValue = (*env) -> CallStaticIntMethod(env, cls, methodID, 2, 3); */

JNIEXPORT jint JNICALL Java_MethodInvocation_invokeStaticMethod
  (JNIEnv *env, jclass cls, jobject objArg) {

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;

  jmethodID methodID;
  jfieldID fid;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;
  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'a';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 1;              /* boolean */


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallStaticBooleanMethod(env, cls, methodID,
                                                         varByte, varChar, varShort, varInt,
                                                         varLong, varFloat, varDouble, varObject,
                                                         varBoolean);

  if (returnBooleanValue != 0) {
    if (verbose)
      printf("> FAIL CallStaticBooleanMethod: return %s, expect false\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallStaticByteMethod(env, cls, methodID,
                                                    varByte, varChar, varShort, varInt,
                                                    varLong, varFloat, varDouble, varObject,
                                                    varBoolean);
  expectByteValue = 15;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallStaticByteMethod: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallStaticCharMethod(env, cls, methodID,
                                                    varByte, varChar, varShort, varInt,
                                                    varLong, varFloat, varDouble, varObject,
                                                    varBoolean);

  if (returnCharValue != 'b') {
    if (verbose)
      printf("> FAIL CallStaticCharMethod: return %c, expect b\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallStaticShortMethod(env, cls, methodID,
                                                     varByte, varChar, varShort, varInt,
                                                     varLong, varFloat, varDouble, varObject,
                                                     varBoolean);
  expectShortValue = 71;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallStaticShortMethod: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }



  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallStaticIntMethod(env, cls, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);
  expectIntValue = 7946;


  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallStaticIntMethod: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallStaticLongMethod(env, cls, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + 7890;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallStaticLongMethod: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallStaticFloatMethod(env, cls, methodID,
                                                     varByte, varChar, varShort, varInt,
                                                     varLong, varFloat, varDouble, varObject,
                                                     varBoolean);
  expectFloatValue = (float) 103.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallStaticFloatMethod: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallStaticDoubleMethod(env, cls, methodID,
                                                       varByte, varChar, varShort, varInt,
                                                       varLong, varFloat, varDouble, varObject,
                                                       varBoolean);
  expectDoubleValue = 102.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallStaticDoubleMethod: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallStaticObjectMethod(env, cls, methodID,
                                                       varByte, varChar, varShort, varInt,
                                                       varLong, varFloat, varDouble, varObject,
                                                       varBoolean);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallStaticObjectMethod\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallStaticObjectMethod: return %p\n", returnObjectValue);
      summary = 1;
    }
  }

  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetStaticMethodID(env, cls, "staticReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method staticReturnVoid\n");
    return 1;
  }

  (*env) -> CallStaticVoidMethod(env, cls, methodID,
                                 varByte, varChar, varShort, varInt,
                                 varLong, varFloat, varDouble, varObject,
                                 varBoolean);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 789) {
    if (verbose)
      printf("> FAIL CallStaticVoidMethod: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }


  return summary;

}



/******************************************************************************
 * Class:     MethodInvocation, arguments passed as an array of jvalue unions
 * Method:    invokeVirtualMethodA
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeVirtualMethodA
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {

  jmethodID methodID;
  jvalue *argA;

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  int summary;
  jboolean matched;
  jclass checkClass;

  jlong longval = 0x01234567l;         /* some dummy long value */
  longval = longval << 16;

  /* create an array of jvalue unions and initialize arguments */
  argA = (jvalue *) malloc(sizeof(jvalue) * 9);
  (&argA[0])->b = 12;                   /* byte    */
  (&argA[1])->c = 'x';                  /* char    */
  (&argA[2])->s = 56;                   /* short   */
  (&argA[3])->i = 7890;                 /* int     */
  (&argA[4])->j = longval;              /* long    */
  (&argA[5])->f = (float)3.14;          /* float   */
  (&argA[6])->d = 2.18;                 /* double  */
  (&argA[7])->l = objArg;               /* object  */
  (&argA[8])->z = 0;                    /* boolean */

  summary = 0;


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallBooleanMethodA(env, objTarget, methodID, argA);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallBooleanMethodA: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallByteMethodA(env, objTarget, methodID, argA);
  expectByteValue = (&argA[0])->b + 7;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallByteMethodA: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallCharMethodA(env, objTarget, methodID, argA);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallCharMethodA: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallShortMethodA(env, objTarget, methodID, argA);
  expectShortValue = (&argA[2])->s + 23;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallShortMethodA: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }

  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallIntMethodA(env, objTarget, methodID, argA);
  expectIntValue = (&argA[3])->i + (&argA[0])->b;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallIntMethodA: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }

  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallLongMethodA(env, objTarget, methodID, argA);
  expectLongValue = (&argA[4])->j + (long) (&argA[2])->s;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallLongMethodA: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallFloatMethodA(env, objTarget, methodID, argA);
  /* expectFloatValue = (float) (&argA[5])->f + (float) 32.0; DoesNOT match 35.14 */
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallFloatMethodA: return %E, expect %E, diff = %E\n", returnFloatValue, expectFloatValue, (returnFloatValue - expectFloatValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallDoubleMethodA(env, objTarget, methodID, argA);
  expectDoubleValue = (&argA[6])->d + 1000.0;

  if (fabs(returnDoubleValue-expectDoubleValue) > 1.0E-12) {
    if (verbose)
      printf("> FAIL CallDoubleMethodA: return %3.3f, expect %3.3f\n",
             returnDoubleValue, expectDoubleValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnObject",
                                   "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GeMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallObjectMethodA(env, objTarget, methodID, argA);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallObjectMethodA\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallObjectMethodA: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallVoidMethodA(env, objTarget, methodID, argA);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallVoidMethodA: test flag set to %d , expect 456\n", voidFlag);
    summary = 1;                     /* fail this test */
  }


  return summary;
}




/******************************************************************************
 * Class:     MethodInvocation
 * Method:    invokeVirtualMethodV
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeVirtualMethodV
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {
  int result;

  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'x';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 0;              /* boolean */

  result = invokeVirtualMethodV_part2(env, cls, objTarget, objArg, varByte, varChar, varShort,
                                     varInt, varLong, varFloat, varDouble, varObject, varBoolean);

  return result;
}



int invokeVirtualMethodV_part2(JNIEnv *env, jclass cls, jobject objTarget, jobject objArg, ...) {

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  jmethodID methodID;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;

  va_list ap;
  va_start(ap, objArg);

  /* check out the var arg list
  for (i=0; i<12; i++) {
    printf("> invokeVirtualMethodV: %d = 0x%08X \n", i, (int) (* ((int *) ((int)ap + (i*4))) )   );
  }
  */


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallBooleanMethodV(env, objTarget, methodID, ap);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallBooleanMethodV: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallByteMethodV(env, objTarget, methodID, ap);
  expectByteValue = 19;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallByteMethodV: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallCharMethodV(env, objTarget, methodID, ap);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallCharMethodV: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallShortMethodV(env, objTarget, methodID, ap);
  expectShortValue = 79;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallShortMethodV: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallIntMethodV(env, objTarget, methodID, ap);
  expectIntValue = 7902;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallIntMethodV: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallLongMethodV(env, objTarget, methodID, ap);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + 56;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallLongMethodV: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallFloatMethodV(env, objTarget, methodID, ap);
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallFloatMethodV: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallDoubleMethodV(env, objTarget, methodID, ap);
  expectDoubleValue = 1002.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallDoubleMethodV: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallObjectMethodV(env, objTarget, methodID, ap);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallObjectMethodV\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallObjectMethodV: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallVoidMethodV(env, objTarget, methodID, ap);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallVoidMethodV: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }

  va_end(ap);

  return summary;


}




/******************************************************************************
 * Class:     MethodInvocation
 * Method:    invokeVirtualMethod
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeVirtualMethod
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;

  jmethodID methodID;
  jfieldID fid;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;
  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'x';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 0;              /* boolean */


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallBooleanMethod(env, objTarget, methodID,
                                                   varByte, varChar, varShort, varInt,
                                                   varLong, varFloat, varDouble, varObject,
                                                   varBoolean);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallBooleanMethod: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallByteMethod(env, objTarget, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);
  expectByteValue = varByte + 7;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallByteMethod: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallCharMethod(env, objTarget, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallCharMethod: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallShortMethod(env, objTarget, methodID,
                                               varByte, varChar, varShort, varInt,
                                               varLong, varFloat, varDouble, varObject,
                                               varBoolean);
  expectShortValue = varShort + 23;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallShortMethod: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }



  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallIntMethod(env, objTarget, methodID,
                                           varByte, varChar, varShort, varInt,
                                           varLong, varFloat, varDouble, varObject,
                                           varBoolean);
  expectIntValue = varInt + varByte;


  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallIntMethod: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallLongMethod(env, objTarget, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + varShort;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallLongMethod: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallFloatMethod(env, objTarget, methodID,
                                               varByte, varChar, varShort, varInt,
                                               varLong, varFloat, varDouble, varObject,
                                               varBoolean);
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallFloatMethod: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallDoubleMethod(env, objTarget, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);
  expectDoubleValue = 1002.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallDoubleMethod: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallObjectMethod(env, objTarget, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallObjectMethod\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallObjectMethod: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, cls, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallVoidMethod(env, objTarget, methodID,
                           varByte, varChar, varShort, varInt,
                           varLong, varFloat, varDouble, varObject,
                           varBoolean);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallVoidMethod: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }


  return summary;


}


/******************************************************************************
 * Class:     MethodInvocation, arguments passed as an array of jvalue unions
 * Method:    invokeNonVirtualMethodA
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeNonVirtualMethodA
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {

  jclass targetClass;

  jmethodID methodID;
  jvalue *argA;

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  int summary;
  jboolean matched;
  jclass checkClass;

  jlong longval = 0x01234567l;         /* some dummy long value */
  longval = longval << 16;

  /* create an array of jvalue unions and initialize arguments */
  argA = (jvalue *) malloc(sizeof(jvalue) * 9);
  (&argA[0])->b = 12;                   /* byte    */
  (&argA[1])->c = 'x';                  /* char    */
  (&argA[2])->s = 56;                   /* short   */
  (&argA[3])->i = 7890;                 /* int     */
  (&argA[4])->j = longval;              /* long    */
  (&argA[5])->f = (float)3.14;          /* float   */
  (&argA[6])->d = 2.18;                 /* double  */
  (&argA[7])->l = objArg;               /* object  */
  (&argA[8])->z = 0;                    /* boolean */

  summary = 0;

  /* get the superclass of objTarget */
  targetClass = (*env) -> FindClass(env, "MethodInvocation");
  if (targetClass==NULL) {
    printf("> invokeNonvirtualMethodA:  cannot find superclass MethodInvocation to check CallNonVirtual<type>MethodA\n");
    return 1;
  }


  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallNonvirtualBooleanMethodA(env, objTarget, targetClass, methodID, argA);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallNonvirtualBooleanMethodA: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallNonvirtualByteMethodA(env, objTarget, targetClass, methodID, argA);
  expectByteValue = (&argA[0])->b + 7;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualByteMethodA: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallNonvirtualCharMethodA(env, objTarget, targetClass, methodID, argA);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallNonvirtualCharMethodA: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallNonvirtualShortMethodA(env, objTarget, targetClass, methodID, argA);
  expectShortValue = (&argA[2])->s + 23;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualShortMethodA: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }

  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallNonvirtualIntMethodA(env, objTarget, targetClass, methodID, argA);
  expectIntValue = (&argA[3])->i + (&argA[0])->b;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualIntMethodA: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }

  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallNonvirtualLongMethodA(env, objTarget, targetClass, methodID, argA);
  expectLongValue = (&argA[4])->j + (long) (&argA[2])->s;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallNonvirtualLongMethodA: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallNonvirtualFloatMethodA(env, objTarget, targetClass, methodID, argA);
  /*expectFloatValue = (float) (&argA[5])->f + (float) 32.0; Does NOT match 35.14 */
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualFloatMethodA: return %E, expect %E, diff = %E\n", returnFloatValue, expectFloatValue, (returnFloatValue - expectFloatValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallNonvirtualDoubleMethodA(env, objTarget, targetClass, methodID, argA);
  expectDoubleValue = (&argA[6])->d + 1000.0;

  if (fabs(returnDoubleValue-expectDoubleValue) > 1.0E-12) {
    if (verbose)
      printf("> FAIL CallNonvirtualDoubleMethodA: return %3.3f, expect %3.3f\n",
             returnDoubleValue, expectDoubleValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnObject",
                                   "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GeMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallNonvirtualObjectMethodA(env, objTarget, targetClass, methodID, argA);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallNonvirtualObjectMethodA\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallNonvirtualObjectMethodA: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallNonvirtualVoidMethodA(env, objTarget, targetClass, methodID, argA);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallNonvirtualVoidMethodA: test flag set to %d , expect 456\n", voidFlag);
    summary = 1;                     /* fail this test */
  }


  return summary;
}




/******************************************************************************
 * Class:     MethodInvocation
 * Method:    invokeNonVirtualMethodV
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeNonVirtualMethodV
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {
  int result;

  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'x';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 0;              /* boolean */

  result = invokeNonVirtualMethodV_part2(env, cls, objTarget, objArg, varByte, varChar, varShort,
                                     varInt, varLong, varFloat, varDouble, varObject, varBoolean);

  return result;
}



int invokeNonVirtualMethodV_part2(JNIEnv *env, jclass cls, jobject objTarget, jobject objArg, ...) {

  jclass targetClass;

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;
  jfieldID fid;

  jmethodID methodID;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;

  va_list ap;
  va_start(ap, objArg);

  /* check out the var arg list
  for (i=0; i<12; i++) {
    printf("> invokeNonVirtualMethodV: %d = 0x%08X \n", i, (int) (* ((int *) ((int)ap + (i*4))) )   );
  }
  */

  /* get the superclass of objTarget */
  targetClass = (*env) -> FindClass(env, "MethodInvocation");
  if (targetClass==NULL) {
    printf("> invokeNonvirtualMethodV:  cannot find superclass MethodInvocation to check CallNonVirtual<type>MethodA\n");
    return 1;
  }

  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallNonvirtualBooleanMethodV(env, objTarget, targetClass, methodID, ap);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallNonvirtualBooleanMethodV: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallNonvirtualByteMethodV(env, objTarget, targetClass, methodID, ap);
  expectByteValue = 19;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualByteMethodV: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallNonvirtualCharMethodV(env, objTarget, targetClass, methodID, ap);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallNonvirtualCharMethodV: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallNonvirtualShortMethodV(env, objTarget, targetClass, methodID, ap);
  expectShortValue = 79;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualShortMethodV: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallNonvirtualIntMethodV(env, objTarget, targetClass, methodID, ap);
  expectIntValue = 7902;

  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualIntMethodV: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallNonvirtualLongMethodV(env, objTarget, targetClass, methodID, ap);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + 56;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallNonvirtualLongMethodV: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallNonvirtualFloatMethodV(env, objTarget, targetClass, methodID, ap);
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualFloatMethodV: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallNonvirtualDoubleMethodV(env, objTarget, targetClass, methodID, ap);
  expectDoubleValue = 1002.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualDoubleMethodV: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallNonvirtualObjectMethodV(env, objTarget, targetClass, methodID, ap);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallNonvirtualObjectMethodV\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallNonvirtualObjectMethodV: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallNonvirtualVoidMethodV(env, objTarget, targetClass, methodID, ap);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallNonvirtualVoidMethodV: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }

  va_end(ap);

  return summary;


}




/******************************************************************************
 * Class:     MethodInvocation
 * Method:    invokeNonVirtualMethod
 * Signature: (LMethodInvocation;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_MethodInvocation_invokeNonVirtualMethod
  (JNIEnv *env, jclass cls, jobject objTarget, jobject objArg) {

  jclass targetClass;

  jboolean returnBooleanValue;
  jchar returnCharValue;
  jshort returnShortValue, expectShortValue;
  jbyte returnByteValue, expectByteValue;
  jint returnIntValue, expectIntValue;
  jlong returnLongValue, expectLongValue;
  jfloat returnFloatValue, expectFloatValue;
  jdouble returnDoubleValue, expectDoubleValue;
  jobject returnObjectValue;
  jint voidFlag;

  jmethodID methodID;
  jfieldID fid;
  int summary = 0;
  jboolean matched;
  jclass checkClass;
  int i;
  jlong longval = 0x01234567l;         /* some dummy long value */

  /* dummy arguments for calling function */
  jbyte    varByte    = 12;             /* byte    */
  jchar    varChar    = 'x';            /* char    */
  jshort   varShort   = 56;             /* short   */
  jint     varInt     = 7890;           /* int     */
  jlong    varLong    = longval << 16;  /* long    */
  jfloat   varFloat   = (float)3.14;    /* float   */
  jdouble  varDouble  = 2.18;           /* double  */
  jobject  varObject  = objArg;         /* object  */
  jboolean varBoolean = 0;              /* boolean */

  /* get the superclass of objTarget */
  targetClass = (*env) -> FindClass(env, "MethodInvocation");
  if (targetClass==NULL) {
    printf("> invokeNonvirtualMethod:  cannot find superclass MethodInvocation to check CallNonVirtual<type>MethodA\n");
    return 1;
  }

  /**********************************************
   * Test 1:  pass every type, return boolean   *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnBoolean", "(BCSIJFDLjava/lang/Object;Z)Z");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnBoolean\n");
    return 1;
  }

  returnBooleanValue = (*env) -> CallNonvirtualBooleanMethod(env, objTarget, targetClass, methodID,
                                                   varByte, varChar, varShort, varInt,
                                                   varLong, varFloat, varDouble, varObject,
                                                   varBoolean);

  if (returnBooleanValue == 0) {
    if (verbose)
      printf("> FAIL CallNonvirtualBooleanMethod: return %s, expect true\n",
             returnBooleanValue==0 ? "false" : "true" );
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 2:  pass every type, return byte      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnByte", "(BCSIJFDLjava/lang/Object;Z)B");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnByte\n");
    return 1;
  }

  returnByteValue = (*env) -> CallNonvirtualByteMethod(env, objTarget, targetClass, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);
  expectByteValue = varByte + 7;

  if (returnByteValue != expectByteValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualByteMethod: return %d, expect %d\n", returnByteValue, expectByteValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 3:  pass every type, return char      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnChar", "(BCSIJFDLjava/lang/Object;Z)C");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnChar\n");
    return 1;
  }

  returnCharValue = (*env) -> CallNonvirtualCharMethod(env, objTarget, targetClass, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);

  if (returnCharValue != 'y') {
    if (verbose)
      printf("> FAIL CallNonvirtualCharMethod: return %c, expect y\n", returnCharValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 4:  pass every type, return short     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnShort", "(BCSIJFDLjava/lang/Object;Z)S");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnShort\n");
    return 1;
  }

  returnShortValue = (*env) -> CallNonvirtualShortMethod(env, objTarget, targetClass, methodID,
                                               varByte, varChar, varShort, varInt,
                                               varLong, varFloat, varDouble, varObject,
                                               varBoolean);
  expectShortValue = varShort + 23;

  if (returnShortValue != expectShortValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualShortMethod: return %d, expect %d\n", returnShortValue, expectShortValue);
    summary = 1;                     /* fail this test */
  }



  /**********************************************
   * Test 5:  pass every type, return int       *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnInt", "(BCSIJFDLjava/lang/Object;Z)I");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnInt\n");
    return 1;
  }

  returnIntValue = (*env) -> CallNonvirtualIntMethod(env, objTarget, targetClass, methodID,
                                           varByte, varChar, varShort, varInt,
                                           varLong, varFloat, varDouble, varObject,
                                           varBoolean);
  expectIntValue = varInt + varByte;


  if (returnIntValue != expectIntValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualIntMethod: return %d, expect %d\n", returnIntValue, expectIntValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 6:  pass every type, return long      *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnLong", "(BCSIJFDLjava/lang/Object;Z)J");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnLong\n");
    return 1;
  }

  returnLongValue = (*env) -> CallNonvirtualLongMethod(env, objTarget, targetClass, methodID,
                                             varByte, varChar, varShort, varInt,
                                             varLong, varFloat, varDouble, varObject,
                                             varBoolean);
  expectLongValue = 0x01234567l;
  expectLongValue = (expectLongValue << 16)  + varShort;

  if (returnLongValue != expectLongValue) {
    if (verbose) {
      printf("> FAIL CallNonvirtualLongMethod: return 0x%08X 0x%08X, expect 0x%08X 0x%08X\n",
             (int) (returnLongValue>>32), (int) returnLongValue,
             (int) (expectLongValue>>32), (int) expectLongValue);
    }
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 7:  pass every type, return float     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnFloat", "(BCSIJFDLjava/lang/Object;Z)F");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnFloat\n");
    return 1;
  }

  returnFloatValue = (*env) -> CallNonvirtualFloatMethod(env, objTarget, targetClass, methodID,
                                               varByte, varChar, varShort, varInt,
                                               varLong, varFloat, varDouble, varObject,
                                               varBoolean);
  expectFloatValue = (float) 35.14;

  if (returnFloatValue != expectFloatValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualFloatMethod: return %E, expect %E \n", returnFloatValue, expectFloatValue);
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 8:  pass every type, return double    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnDouble", "(BCSIJFDLjava/lang/Object;Z)D");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnDouble\n");
    return 1;
  }

  returnDoubleValue = (*env) -> CallNonvirtualDoubleMethod(env, objTarget, targetClass, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);
  expectDoubleValue = 1002.18;

  if (returnDoubleValue != expectDoubleValue) {
    if (verbose)
      printf("> FAIL CallNonvirtualDoubleMethod: return %3.3f, expect %3.3f, diff = %E\n",
             returnDoubleValue, expectDoubleValue, (returnDoubleValue - expectDoubleValue));
    summary = 1;                     /* fail this test */
  }


  /**********************************************
   * Test 9:  pass every type, return object    *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnObject",
                                         "(BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnObject\n");
    return 1;
  }

  returnObjectValue = (*env) -> CallNonvirtualObjectMethod(env, objTarget, targetClass, methodID,
                                                 varByte, varChar, varShort, varInt,
                                                 varLong, varFloat, varDouble, varObject,
                                                 varBoolean);

  /* check to see if we got a String object */
  checkClass = (*env) -> FindClass(env, "java/lang/String");
  if (checkClass==NULL) {
    printf("> Cannot find class String to check CallNonvirtualObjectMethod\n");
    summary = 1;
  } else {
    matched = (*env) -> IsInstanceOf(env, returnObjectValue, checkClass);
    if (!matched) {
      if (verbose)
        printf("> FAIL CallNonvirtualObjectMethod: return %p\n", returnObjectValue);
      summary = 1;
    }
  }


  /**********************************************
   * Test 10:  pass every type, return void     *
   **********************************************/
  methodID = (*env) -> GetMethodID(env, targetClass, "virtualReturnVoid", "(BCSIJFDLjava/lang/Object;Z)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetMethodID: fail to get method ID for virtual method virtualReturnVoid\n");
    return 1;
  }

  (*env) -> CallNonvirtualVoidMethod(env, objTarget, targetClass, methodID,
                           varByte, varChar, varShort, varInt,
                           varLong, varFloat, varDouble, varObject,
                           varBoolean);

  /* check the flag in the static field testFlagForVoid */
  fid = (*env) -> GetStaticFieldID(env, cls, "testFlagForVoid", "I");
  voidFlag = (*env) -> GetStaticIntField(env, cls, fid);

  if (voidFlag != 456) {
    if (verbose)
      printf("> FAIL CallNonvirtualVoidMethod: test flag set to %d , expect 789\n", voidFlag);
    summary = 1;                     /* fail this test */
  }


  return summary;


}


