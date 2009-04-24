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
/* Test field access from native code
 * Implement native methods from FieldAccess.java
 */

#include <stdio.h>
#include "FieldAccess.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     FieldAccess
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_FieldAccess_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}

/*******************************************************************
 * Access static fields
 *
 *******************************************************************/

/*
 * Class:     FieldAccess
 * Method:    accessStaticIntField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticIntField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jint si;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticInt", "I");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticField: fail to get field ID for staticInt\n");
    return 1;
  }


  si = (*env) -> GetStaticIntField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticIntField: field ID = %p \n", fid);
    printf(">           read field staticInt = %d \n", si);
  }

  if (si==123)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticBooleanField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticBooleanField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jboolean val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticBoolean", "Z");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticBooleanField: fail to get field ID for staticBoolean\n");
    return 1;
  }

  val = (*env) -> GetStaticBooleanField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticBooleanField: field ID = %p \n", fid);
    printf(">           read field staticBoolean = %d \n", val);
  }

  if (val)    /* expect true */
    return 0;
  else
    return 1;
}


/*
 * Class:     FieldAccess
 * Method:    accessStaticByteField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticByteField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jbyte val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticByte", "B");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticByteField: fail to get field ID for staticByte\n");
    return 1;
  }

  val = (*env) -> GetStaticByteField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticByteField: field ID = %p \n", fid);
    printf(">           read field staticByte = %d \n", val);
  }

  if (val==12)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticCharField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticCharField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jchar val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticChar", "C");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticCharField: fail to get field ID for staticChar\n");
    return 1;
  }

  val = (*env) -> GetStaticCharField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticCharField: field ID = %p \n", fid);
    printf(">           read field staticChar = %c \n", val);
  }

  if (val=='a')
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticShortField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticShortField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jshort val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticShort", "S");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticShortField: fail to get field ID for staticShort\n");
    return 1;
  }

  val = (*env) -> GetStaticShortField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticShortField: field ID = %p \n", fid);
    printf(">           read field staticShort = %d \n", val);
  }

  if (val==67)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticLongField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticLongField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jlong val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticLong", "J");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticLongField: fail to get field ID for staticLong\n");
    return 1;
  }

  val = (*env) -> GetStaticLongField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticLongField: field ID = %p \n", fid);
    printf(">           read field staticLong = 0x%X \n", (int) val);
  }

  if (val==246l)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticFloatField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticFloatField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jfloat val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticFloat", "F");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticFloatField: fail to get field ID for staticFloat\n");
    return 1;
  }

  val = (*env) -> GetStaticFloatField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticFloatField: field ID = %p \n", fid);
    printf(">           read field staticFloat = %2.3f \n", val);
  }

  /* Need to convert to float because C constants are double by default */
  if (val==(float).123)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticDoubleField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticDoubleField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jdouble val;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticDouble", "D");
  if (fid == NULL) {
    if (verbose)
      printf("accessStaticDoubleField: fail to get field ID for staticDouble\n");
    return 1;
  }

  val = (*env) -> GetStaticDoubleField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticDoubleField: field ID = %p \n", fid);
    printf(">           read field staticDouble = %3.3f \n", val);
  }

  if (val==567.123)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessStaticObjectField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessStaticObjectField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;
  jobject obj;
  jclass objClass;
  jboolean matchClass;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticObject", "LFieldAccess;");
  if (fid == NULL) {
    if (verbose)
      printf("> accessStaticObjectField: fail to get field ID for staticObject\n");
    return 1;
  }

  obj = (*env) -> GetStaticObjectField(env, cls, fid);
  if (verbose) {
    printf("> accessStaticObjectField: field ID = %p \n", fid);
    printf(">           read field staticObject = %p \n", obj);
  }

  /* For object, check if it's an instance of the class FieldAccess */
  objClass = (*env) -> FindClass(env, "FieldAccess");
  if (verbose) {
    printf("> accessStaticObjectField: jnienv = %p, jclass = %p \n", env, cls);
    printf("> accessStaticObjectField: class ref = %p \n", objClass);
  }
  matchClass = (*env) -> IsInstanceOf(env, obj, objClass);
  if (matchClass)
    return 0;
  else
    return 1;
}



/*******************************************************************
 * Access instance fields
 *
 *******************************************************************/

/*
 * Class:     FieldAccess
 * Method:    accessIntField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessIntField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jint fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceInt", "I");
  if (fid == NULL) {
    if (verbose)
      printf("> accessIntField: fail to get field ID for instanceInt\n");
    return 1;
  }

  fval = (*env) -> GetIntField(env, obj, fid);
  if (verbose) {
    printf("> accessIntField: field ID = %p \n", fid);
    printf(">   read field instanceInt = %d \n", fval);
  }

  if (fval==456)
    return 0;
  else
    return 1;
}

/*
 * Class:     FieldAccess
 * Method:    accessBooleanField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessBooleanField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jboolean fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceBoolean", "Z");
  if (fid == NULL) {
    if (verbose)
      printf("> accessBooleanField: fail to get field ID for instanceBoolean\n");
    return 1;
  }

  fval = (*env) -> GetBooleanField(env, obj, fid);
  if (verbose) {
    printf("> accessBooleanField: field ID = %p \n", fid);
    printf(">   read field instanceBoolean = %d \n", fval);
  }

  if (!fval)    /* expect false */
    return 0;
  else
    return 1;
}


/*
 * Class:     FieldAccess
 * Method:    accessByteField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessByteField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jbyte fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceByte", "B");
  if (fid == NULL) {
    if (verbose)
      printf("> accessByteField: fail to get field ID for instanceByte\n");
    return 1;
  }

  fval = (*env) -> GetByteField(env, obj, fid);
  if (verbose) {
    printf("> accessByteField: field ID = %p \n", fid);
    printf(">   read field instanceByte = %d \n", fval);
  }

  if (fval==34)
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessCharField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessCharField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jchar fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceChar", "C");
  if (fid == NULL) {
    if (verbose)
      printf("> accessCharField: fail to get field ID for instanceChar\n");
    return 1;
  }

  fval = (*env) -> GetCharField(env, obj, fid);
  if (verbose) {
    printf("> accessCharField: field ID = %p \n", fid);
    printf(">   read field instanceChar = %c \n", fval);
  }

  if (fval=='t')
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessShortField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessShortField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jshort fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceShort", "S");
  if (fid == NULL) {
    if (verbose)
      printf("> accessShortField: fail to get field ID for instanceShort\n");
    return 1;
  }

  fval = (*env) -> GetShortField(env, obj, fid);
  if (verbose) {
    printf("> accessShortField: field ID = %p \n", fid);
    printf(">   read field instanceShort = %d \n", fval);
  }

  if (fval==45)
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessLongField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessLongField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jlong fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceLong", "J");
  if (fid == NULL) {
    if (verbose)
      printf("> accessLongField: fail to get field ID for instanceLong\n");
    return 1;
  }

  fval = (*env) -> GetLongField(env, obj, fid);
  if (verbose) {
    printf("> accessLongField: field ID = %p \n", fid);
    printf(">   read field instanceLong = 0x%lld \n", (long long) fval);
  }

  if (fval==135l)
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessFloatField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessFloatField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jfloat fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceFloat", "F");
  if (fid == NULL) {
    if (verbose)
      printf("> accessIntField: fail to get field ID for instanceFloat\n");
    return 1;
  }

  fval = (*env) -> GetFloatField(env, obj, fid);
  if (verbose) {
    printf("> accessFloatField: field ID = %p \n", fid);
    printf(">   read field instanceFloat = %2.3f \n", fval);
  }

  if (fval==(float).456)
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessDoubleField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessDoubleField
  (JNIEnv *env, jclass cls, jobject obj) {
  jfieldID fid;
  jdouble fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceDouble", "D");
  if (fid == NULL) {
    if (verbose)
      printf("> accessDoubleField: fail to get field ID for instanceDouble\n");
    return 1;
  }

  fval = (*env) -> GetDoubleField(env, obj, fid);
  if (verbose) {
    printf("> accessDoubleField: field ID = %p \n", fid);
    printf(">   read field instanceDouble = %4.3f \n", fval);
  }

  if (fval==1234.567)
    return 0;
  else
    return 1;

}


/*
 * Class:     FieldAccess
 * Method:    accessObjectField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_accessObjectField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;
  jobject fieldObj;
  jclass objClass;
  jboolean matchClass;

  fid = (*env) -> GetFieldID(env, cls, "instanceObject", "LFieldAccess;");
  if (fid == NULL) {
    if (verbose)
      printf("> accessObjectField: fail to get field ID for instanceObject\n");
    return 1;
  }

  fieldObj = (*env) -> GetObjectField(env, obj, fid);
  if (verbose) {
    printf("> accessObjectField: field ID = %p \n", fid);
    printf(">           read field instanceObject = %p \n", fieldObj);
  }

  /* For object, check if it's an instance of the class FieldAccess */
  objClass = (*env) -> FindClass(env, "FieldAccess");
  /*  printf("> accessObjectField: jnienv = %d, jclass = %d \n", env, cls); */
  if (verbose) {
    printf("> accessObjectField: class ref = %p \n", objClass);
  }
  matchClass = (*env) -> IsInstanceOf(env, fieldObj, objClass);
  if (matchClass)
    return 0;
  else
    return 1;

}

/*
 * Class:     FieldAccess
 * Method:    setStaticIntField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticIntField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticInt", "I");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticIntField: fail to get field ID for staticInt\n");
    return 1;
  }


  (*env) -> SetStaticIntField(env, cls, fid, 456);
  if (verbose) {
    printf("> setStaticIntField: field ID = %p \n", fid);
  }

  return 0;
}


/*
 * Class:     FieldAccess
 * Method:    setStaticBooleanField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticBooleanField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticBoolean", "Z");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticBooleanField: fail to get field ID for staticBoolean\n");
    return 1;
  }

  (*env) -> SetStaticBooleanField(env, cls, fid, JNI_FALSE);
  if (verbose) {
    printf("> setStaticBooleanField: field ID = %p \n", fid);
  }

  return 0;

  }


/*
 * Class:     FieldAccess
 * Method:    setStaticByteField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticByteField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticByte", "B");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticByteField: fail to get field ID for staticByte\n");
    return 1;
  }

  (*env) -> SetStaticByteField(env, cls, fid, 24);
  if (verbose) {
    printf("> setStaticByteField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticCharField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticCharField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticChar", "C");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticCharField: fail to get field ID for staticChar\n");
    return 1;
  }

  (*env) -> SetStaticCharField(env, cls, fid, 'b');
  if (verbose) {
    printf("> setStaticCharField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticShortField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticShortField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticShort", "S");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticShortField: fail to get field ID for staticShort\n");
    return 1;
  }

  (*env) -> SetStaticShortField(env, cls, fid, 76);
  if (verbose) {
    printf("> setStaticShortField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticLongField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticLongField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticLong", "J");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticLongField: fail to get field ID for staticLong\n");
    return 1;
  }

  (*env) -> SetStaticLongField(env, cls, fid, 357l);
  if (verbose) {
    printf("> setStaticLongField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticFloatField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticFloatField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticFloat", "F");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticFloatField: fail to get field ID for staticFloat\n");
    return 1;
  }

  /* Need to convert to float because C constants are double by default */
  (*env) -> SetStaticFloatField(env, cls, fid, (float).234);
  if (verbose) {
    printf("> setStaticFloatField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticDoubleField
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticDoubleField
  (JNIEnv *env, jclass cls) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticDouble", "D");
  if (fid == NULL) {
    if (verbose)
      printf("setStaticDoubleField: fail to get field ID for staticDouble\n");
    return 1;
  }

  (*env) -> SetStaticDoubleField(env, cls, fid, 123.456);
  if (verbose) {
    printf("> setStaticDoubleField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setStaticObjectField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setStaticObjectField
  (JNIEnv *env, jclass cls, jobject extraObj) {

  jfieldID fid;

  fid = (*env) -> GetStaticFieldID(env, cls, "staticObject", "LFieldAccess;");
  if (fid == NULL) {
    if (verbose)
      printf("> setStaticObjectField: fail to get field ID for staticObject\n");
    return 1;
  }

  (*env) -> SetStaticObjectField(env, cls, fid, extraObj);
  if (verbose) {
    printf("> setStaticObjectField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setIntField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setIntField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;
  jint fval;

  fid = (*env) -> GetFieldID(env, cls, "instanceInt", "I");
  if (fid == NULL) {
    if (verbose)
      printf("> setIntField: fail to get field ID for instanceInt\n");
    return 1;
  }

  (*env) -> SetIntField(env, obj, fid, 789);
  if (verbose) {
    printf("> setIntField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setBooleanField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setBooleanField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceBoolean", "Z");
  if (fid == NULL) {
    if (verbose)
      printf("> setBooleanField: fail to get field ID for instanceBoolean\n");
    return 1;
  }

  (*env) -> SetBooleanField(env, obj, fid, JNI_TRUE);
  if (verbose) {
    printf("> setBooleanField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setByteField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setByteField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceByte", "B");
  if (fid == NULL) {
    if (verbose)
      printf("> setByteField: fail to get field ID for instanceByte\n");
    return 1;
  }

  (*env) -> SetByteField(env, obj, fid, 77);
  if (verbose) {
    printf("> setByteField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setCharField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setCharField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceChar", "C");
  if (fid == NULL) {
    if (verbose)
      printf("> setCharField: fail to get field ID for instanceChar\n");
    return 1;
  }

  (*env) -> SetCharField(env, obj, fid, 'q');
  if (verbose) {
    printf("> setCharField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setShortField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setShortField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceShort", "S");
  if (fid == NULL) {
    if (verbose)
      printf("> setShortField: fail to get field ID for instanceShort\n");
    return 1;
  }

  (*env) -> SetShortField(env, obj, fid, 25);
  if (verbose) {
    printf("> setShortField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setLongField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setLongField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceLong", "J");
  if (fid == NULL) {
    if (verbose)
      printf("> setLongField: fail to get field ID for instanceLong\n");
    return 1;
  }

  (*env) -> SetLongField(env, obj, fid, 345l);
  if (verbose) {
    printf("> setLongField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setFloatField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setFloatField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceFloat", "F");
  if (fid == NULL) {
    if (verbose)
      printf("> setIntField: fail to get field ID for instanceFloat\n");
    return 1;
  }

  (*env) -> SetFloatField(env, obj, fid, (float).789);
  if (verbose) {
    printf("> setFloatField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setDoubleField
 * Signature: (Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setDoubleField
  (JNIEnv *env, jclass cls, jobject obj) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceDouble", "D");
  if (fid == NULL) {
    if (verbose)
      printf("> setDoubleField: fail to get field ID for instanceDouble\n");
    return 1;
  }

  (*env) -> SetDoubleField(env, obj, fid, 234.456);
  if (verbose) {
    printf("> setDoubleField: field ID = %p \n", fid);
  }

  return 0;

}


/*
 * Class:     FieldAccess
 * Method:    setObjectField
 * Signature: (Ljava/lang/Object;Ljava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_FieldAccess_setObjectField
  (JNIEnv *env, jclass cls, jobject targetObj, jobject extraObject) {

  jfieldID fid;

  fid = (*env) -> GetFieldID(env, cls, "instanceObject", "LFieldAccess;");
  if (fid == NULL) {
    if (verbose)
      printf("> setObjectField: fail to get field ID for instanceObject\n");
    return 1;
  }

  (*env) -> SetObjectField(env, targetObj, fid, extraObject);
  if (verbose) {
    printf("> setObjectField: field ID = %p \n", fid);
  }

  return 0;

}



