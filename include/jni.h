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
#ifndef JNI_H
#define JNI_H

#include <inttypes.h>
#include <stdio.h>  /* For 1.1 version of args (i.e., vfprintf) */
#include <stdarg.h> /* For va_list */

#define JNIEXPORT
#define JNICALL

#ifdef __cplusplus
extern "C" {
#endif


#if !defined PJNICALL
#define PJNICALL JNICALL *
#endif

/* Boolean values */

#define JNI_FALSE                 0
#define JNI_TRUE                  1

/* Codes for ReleaseXArrayElements */

#define JNI_COMMIT                1
#define JNI_ABORT                 2

/* JNI version numbers */

#define JNI_VERSION_1_1 0x00010001
#define JNI_VERSION_1_2 0x00010002
#define JNI_VERSION_1_4 0x00010004

/* Error codes */

#define JNI_OK                    0
#define JNI_ERR                 (-1)
#define JNI_EDETACHED           (-2)
#define JNI_EVERSION		(-3)
#define JNI_ENOMEM		(-4)
#define JNI_EEXIST		(-5)
#define JNI_EINVAL		(-6)

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;
typedef jint jsize;

#ifdef __cplusplus
class _jobject {};
class _jweak : public _jobject {};
class _jclass : public _jobject {};
class _jstring : public _jobject {};
class _jthrowable : public _jobject {};
class _jarray : public _jobject {};
class _jobjectArray : public _jarray {};
class _jbooleanArray : public _jarray {};
class _jbyteArray : public _jarray {};
class _jcharArray : public _jarray {};
class _jshortArray : public _jarray {};
class _jintArray : public _jarray {};
class _jlongArray : public _jarray {};
class _jfloatArray : public _jarray {};
class _jdoubleArray : public _jarray {};
typedef _jobject * jobject;
typedef _jweak * jweak;
typedef _jclass * jclass;
typedef _jstring * jstring;
typedef _jthrowable * jthrowable;
typedef _jarray * jarray;
typedef _jobjectArray * jobjectArray;
typedef _jbooleanArray * jbooleanArray;
typedef _jbyteArray * jbyteArray;
typedef _jcharArray * jcharArray;
typedef _jshortArray * jshortArray;
typedef _jintArray * jintArray;
typedef _jlongArray * jlongArray;
typedef _jfloatArray * jfloatArray;
typedef _jdoubleArray * jdoubleArray;
#else
struct _jobject;
typedef struct _jobject * jobject;
typedef jobject jweak;
typedef jobject jclass;
typedef jobject jstring;
typedef jobject jthrowable;
typedef jobject jarray;
typedef jarray jobjectArray;
typedef jarray jbooleanArray;
typedef jarray jbyteArray;
typedef jarray jcharArray;
typedef jarray jshortArray;
typedef jarray jintArray;
typedef jarray jlongArray;
typedef jarray jfloatArray;
typedef jarray jdoubleArray;
#endif

struct _jfieldID;
typedef struct _jfieldID *jfieldID;
struct _jmethodID;
typedef struct _jmethodID *jmethodID;

/* Used for CallXXXMethodA API */
typedef union jvalue {
    jboolean z;
    jbyte    b;
    jchar    c;
    jshort   s;
    jint     i;
    jlong    j;
    jfloat   f;
    jdouble  d;
    jobject  l;
} jvalue;

typedef struct {
    char *name;
    char *signature;
    void *fnPtr;
} JNINativeMethod;

struct JNINativeInterface_;
struct JNIEnv_;
struct JNIInvokeInterface_;
struct JavaVM_;
#ifdef __cplusplus
typedef  JNIEnv_ JNIEnv;
typedef JavaVM_ JavaVM;
#else
typedef const struct JNINativeInterface_ *JNIEnv;
typedef const struct JNIInvokeInterface_ *JavaVM;
#endif

struct JNINativeInterface_ {
    void *reserved0;
    void *reserved1;
    void *reserved2;
    void *reserved3;
    jint (PJNICALL GetVersion)(JNIEnv *env);
    jclass (PJNICALL DefineClass)(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len);
    jclass (PJNICALL FindClass)(JNIEnv *env, const char *name);
    jmethodID (PJNICALL FromReflectedMethod)(JNIEnv *env, jobject method);
    jfieldID (PJNICALL FromReflectedField)(JNIEnv *env, jobject field);
    jobject (PJNICALL ToReflectedMethod)(JNIEnv *env, jclass cls, jmethodID methodID, jboolean isStatic);
    jclass (PJNICALL GetSuperclass)(JNIEnv *env, jclass sub);
    jboolean (PJNICALL IsAssignableFrom)(JNIEnv *env, jclass sub, jclass sup);
    jobject (PJNICALL ToReflectedField)(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
    jint (PJNICALL Throw)(JNIEnv *env, jthrowable obj);
    jint (PJNICALL ThrowNew)(JNIEnv *env, jclass clazz, const char *msg);
    jthrowable (PJNICALL ExceptionOccurred)(JNIEnv *env);
    void (PJNICALL ExceptionDescribe)(JNIEnv *env);
    void (PJNICALL ExceptionClear)(JNIEnv *env);
    void (PJNICALL FatalError)(JNIEnv *env, const char *msg);
    jint (PJNICALL PushLocalFrame)(JNIEnv *env, jint capacity);
    jobject (PJNICALL PopLocalFrame)(JNIEnv *env, jobject result);
    jobject (PJNICALL NewGlobalRef)(JNIEnv *env, jobject lobj);
    void (PJNICALL DeleteGlobalRef)(JNIEnv *env, jobject gref);
    void (PJNICALL DeleteLocalRef)(JNIEnv *env, jobject obj);
    jboolean (PJNICALL IsSameObject)(JNIEnv *env, jobject obj1, jobject obj2);
    jobject (PJNICALL NewLocalRef)(JNIEnv *env, jobject ref);
    jint (PJNICALL EnsureLocalCapacity)(JNIEnv *env, jint capacity);
    jobject (PJNICALL AllocObject)(JNIEnv *env, jclass clazz);
    jobject (PJNICALL NewObject)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jobject (PJNICALL NewObjectV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jobject (PJNICALL NewObjectA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jclass (PJNICALL GetObjectClass)(JNIEnv *env, jobject obj);
    jboolean (PJNICALL IsInstanceOf)(JNIEnv *env, jobject obj, jclass clazz);
    jmethodID (PJNICALL GetMethodID)(JNIEnv *env, jclass clazz, const char *name, const char *sig);
    jobject (PJNICALL CallObjectMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jobject (PJNICALL CallObjectMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jobject (PJNICALL CallObjectMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue * args);
    jboolean (PJNICALL CallBooleanMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jboolean (PJNICALL CallBooleanMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jboolean (PJNICALL CallBooleanMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue * args);
    jbyte (PJNICALL CallByteMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jbyte (PJNICALL CallByteMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jbyte (PJNICALL CallByteMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jchar (PJNICALL CallCharMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jchar (PJNICALL CallCharMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jchar (PJNICALL CallCharMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jshort (PJNICALL CallShortMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jshort (PJNICALL CallShortMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jshort (PJNICALL CallShortMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jint (PJNICALL CallIntMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jint (PJNICALL CallIntMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jint (PJNICALL CallIntMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jlong (PJNICALL CallLongMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jlong (PJNICALL CallLongMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jlong (PJNICALL CallLongMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jfloat (PJNICALL CallFloatMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jfloat (PJNICALL CallFloatMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jfloat (PJNICALL CallFloatMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    jdouble (PJNICALL CallDoubleMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    jdouble (PJNICALL CallDoubleMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    jdouble (PJNICALL CallDoubleMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue *args);
    void (PJNICALL CallVoidMethod)(JNIEnv *env, jobject obj, jmethodID methodID, ...);
    void (PJNICALL CallVoidMethodV)(JNIEnv *env, jobject obj, jmethodID methodID, va_list args);
    void (PJNICALL CallVoidMethodA)(JNIEnv *env, jobject obj, jmethodID methodID, jvalue * args);
    jobject (PJNICALL CallNonvirtualObjectMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jobject (PJNICALL CallNonvirtualObjectMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jobject (PJNICALL CallNonvirtualObjectMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue * args);
    jboolean (PJNICALL CallNonvirtualBooleanMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jboolean (PJNICALL CallNonvirtualBooleanMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jboolean (PJNICALL CallNonvirtualBooleanMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue * args);
    jbyte (PJNICALL CallNonvirtualByteMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jbyte (PJNICALL CallNonvirtualByteMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jbyte (PJNICALL CallNonvirtualByteMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jchar (PJNICALL CallNonvirtualCharMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jchar (PJNICALL CallNonvirtualCharMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jchar (PJNICALL CallNonvirtualCharMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jshort (PJNICALL CallNonvirtualShortMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jshort (PJNICALL CallNonvirtualShortMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jshort (PJNICALL CallNonvirtualShortMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jint (PJNICALL CallNonvirtualIntMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jint (PJNICALL CallNonvirtualIntMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jint (PJNICALL CallNonvirtualIntMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jlong (PJNICALL CallNonvirtualLongMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jlong (PJNICALL CallNonvirtualLongMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jlong (PJNICALL CallNonvirtualLongMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jfloat (PJNICALL CallNonvirtualFloatMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jfloat (PJNICALL CallNonvirtualFloatMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jfloat (PJNICALL CallNonvirtualFloatMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    jdouble (PJNICALL CallNonvirtualDoubleMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    jdouble (PJNICALL CallNonvirtualDoubleMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    jdouble (PJNICALL CallNonvirtualDoubleMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue *args);
    void (PJNICALL CallNonvirtualVoidMethod)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
    void (PJNICALL CallNonvirtualVoidMethodV)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args);
    void (PJNICALL CallNonvirtualVoidMethodA)(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, jvalue * args);
    jfieldID (PJNICALL GetFieldID)(JNIEnv *env, jclass clazz, const char *name, const char *sig);
    jobject (PJNICALL GetObjectField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jboolean (PJNICALL GetBooleanField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jbyte (PJNICALL GetByteField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jchar (PJNICALL GetCharField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jshort (PJNICALL GetShortField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jint (PJNICALL GetIntField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jlong (PJNICALL GetLongField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jfloat (PJNICALL GetFloatField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    jdouble (PJNICALL GetDoubleField)(JNIEnv *env, jobject obj, jfieldID fieldID);
    void (PJNICALL SetObjectField)(JNIEnv *env, jobject obj, jfieldID fieldID, jobject val);
    void (PJNICALL SetBooleanField)(JNIEnv *env, jobject obj, jfieldID fieldID, jboolean val);
    void (PJNICALL SetByteField)(JNIEnv *env, jobject obj, jfieldID fieldID, jbyte val);
    void (PJNICALL SetCharField)(JNIEnv *env, jobject obj, jfieldID fieldID, jchar val);
    void (PJNICALL SetShortField)(JNIEnv *env, jobject obj, jfieldID fieldID, jshort val);
    void (PJNICALL SetIntField)(JNIEnv *env, jobject obj, jfieldID fieldID, jint val);
    void (PJNICALL SetLongField)(JNIEnv *env, jobject obj, jfieldID fieldID, jlong val);
    void (PJNICALL SetFloatField)(JNIEnv *env, jobject obj, jfieldID fieldID, jfloat val);
    void (PJNICALL SetDoubleField)(JNIEnv *env, jobject obj, jfieldID fieldID, jdouble val);
    jmethodID (PJNICALL GetStaticMethodID)(JNIEnv *env, jclass clazz, const char *name, const char *sig);
    jobject (PJNICALL CallStaticObjectMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jobject (PJNICALL CallStaticObjectMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jobject (PJNICALL CallStaticObjectMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jboolean (PJNICALL CallStaticBooleanMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jboolean (PJNICALL CallStaticBooleanMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jboolean (PJNICALL CallStaticBooleanMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jbyte (PJNICALL CallStaticByteMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jbyte (PJNICALL CallStaticByteMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jbyte (PJNICALL CallStaticByteMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jchar (PJNICALL CallStaticCharMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jchar (PJNICALL CallStaticCharMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jchar (PJNICALL CallStaticCharMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jshort (PJNICALL CallStaticShortMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jshort (PJNICALL CallStaticShortMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jshort (PJNICALL CallStaticShortMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jint (PJNICALL CallStaticIntMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jint (PJNICALL CallStaticIntMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jint (PJNICALL CallStaticIntMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jlong (PJNICALL CallStaticLongMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jlong (PJNICALL CallStaticLongMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jlong (PJNICALL CallStaticLongMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jfloat (PJNICALL CallStaticFloatMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jfloat (PJNICALL CallStaticFloatMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jfloat (PJNICALL CallStaticFloatMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    jdouble (PJNICALL CallStaticDoubleMethod)(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
    jdouble (PJNICALL CallStaticDoubleMethodV)(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args);
    jdouble (PJNICALL CallStaticDoubleMethodA)(JNIEnv *env, jclass clazz, jmethodID methodID, jvalue *args);
    void (PJNICALL CallStaticVoidMethod)(JNIEnv *env, jclass cls, jmethodID methodID, ...);
    void (PJNICALL CallStaticVoidMethodV)(JNIEnv *env, jclass cls, jmethodID methodID, va_list args);
    void (PJNICALL CallStaticVoidMethodA)(JNIEnv *env, jclass cls, jmethodID methodID, jvalue * args);
    jfieldID (PJNICALL GetStaticFieldID)(JNIEnv *env, jclass clazz, const char *name, const char *sig);
    jobject (PJNICALL GetStaticObjectField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jboolean (PJNICALL GetStaticBooleanField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jbyte (PJNICALL GetStaticByteField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jchar (PJNICALL GetStaticCharField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jshort (PJNICALL GetStaticShortField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jint (PJNICALL GetStaticIntField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jlong (PJNICALL GetStaticLongField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jfloat (PJNICALL GetStaticFloatField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    jdouble (PJNICALL GetStaticDoubleField)(JNIEnv *env, jclass clazz, jfieldID fieldID);
    void (PJNICALL SetStaticObjectField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jobject value);
    void (PJNICALL SetStaticBooleanField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jboolean value);
    void (PJNICALL SetStaticByteField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jbyte value);
    void (PJNICALL SetStaticCharField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jchar value);
    void (PJNICALL SetStaticShortField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jshort value);
    void (PJNICALL SetStaticIntField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jint value);
    void (PJNICALL SetStaticLongField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jlong value);
    void (PJNICALL SetStaticFloatField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jfloat value);
    void (PJNICALL SetStaticDoubleField)(JNIEnv *env, jclass clazz, jfieldID fieldID, jdouble value);
    jstring (PJNICALL NewString)(JNIEnv *env, const jchar *unicode, jsize len);
    jsize (PJNICALL GetStringLength)(JNIEnv *env, jstring str);
    const jchar *(PJNICALL GetStringChars)(JNIEnv *env, jstring str, jboolean *isCopy);
    void (PJNICALL ReleaseStringChars)(JNIEnv *env, jstring str, const jchar *chars);
    jstring (PJNICALL NewStringUTF)(JNIEnv *env, const char *utf);
    jsize (PJNICALL GetStringUTFLength)(JNIEnv *env, jstring str);
    const char* (PJNICALL GetStringUTFChars)(JNIEnv *env, jstring str, jboolean *isCopy);
    void (PJNICALL ReleaseStringUTFChars)(JNIEnv *env, jstring str, const char* chars);
    jsize (PJNICALL GetArrayLength)(JNIEnv *env, jarray array);
    jobjectArray (PJNICALL NewObjectArray)(JNIEnv *env, jsize len, jclass clazz, jobject init);
    jobject (PJNICALL GetObjectArrayElement)(JNIEnv *env, jobjectArray array, jsize index_);
    void (PJNICALL SetObjectArrayElement)(JNIEnv *env, jobjectArray array, jsize index_, jobject val);
    jbooleanArray (PJNICALL NewBooleanArray)(JNIEnv *env, jsize len);
    jbyteArray (PJNICALL NewByteArray)(JNIEnv *env, jsize len);
    jcharArray (PJNICALL NewCharArray)(JNIEnv *env, jsize len);
    jshortArray (PJNICALL NewShortArray)(JNIEnv *env, jsize len);
    jintArray (PJNICALL NewIntArray)(JNIEnv *env, jsize len);
    jlongArray (PJNICALL NewLongArray)(JNIEnv *env, jsize len);
    jfloatArray (PJNICALL NewFloatArray)(JNIEnv *env, jsize len);
    jdoubleArray (PJNICALL NewDoubleArray)(JNIEnv *env, jsize len);
    jboolean * (PJNICALL GetBooleanArrayElements)(JNIEnv *env, jbooleanArray array, jboolean *isCopy);
    jbyte * (PJNICALL GetByteArrayElements)(JNIEnv *env, jbyteArray array, jboolean *isCopy);
    jchar * (PJNICALL GetCharArrayElements)(JNIEnv *env, jcharArray array, jboolean *isCopy);
    jshort * (PJNICALL GetShortArrayElements)(JNIEnv *env, jshortArray array, jboolean *isCopy);
    jint * (PJNICALL GetIntArrayElements)(JNIEnv *env, jintArray array, jboolean *isCopy);
    jlong * (PJNICALL GetLongArrayElements)(JNIEnv *env, jlongArray array, jboolean *isCopy);
    jfloat * (PJNICALL GetFloatArrayElements)(JNIEnv *env, jfloatArray array, jboolean *isCopy);
    jdouble * (PJNICALL GetDoubleArrayElements)(JNIEnv *env, jdoubleArray array, jboolean *isCopy);
    void (PJNICALL ReleaseBooleanArrayElements)(JNIEnv *env, jbooleanArray array, jboolean *elems, jint mode);
    void (PJNICALL ReleaseByteArrayElements)(JNIEnv *env, jbyteArray array, jbyte *elems, jint mode);
    void (PJNICALL ReleaseCharArrayElements)(JNIEnv *env, jcharArray array, jchar *elems, jint mode);
    void (PJNICALL ReleaseShortArrayElements)(JNIEnv *env, jshortArray array, jshort *elems, jint mode);
    void (PJNICALL ReleaseIntArrayElements)(JNIEnv *env, jintArray array, jint *elems, jint mode);
    void (PJNICALL ReleaseLongArrayElements)(JNIEnv *env, jlongArray array, jlong *elems, jint mode);
    void (PJNICALL ReleaseFloatArrayElements)(JNIEnv *env, jfloatArray array, jfloat *elems, jint mode);
    void (PJNICALL ReleaseDoubleArrayElements)(JNIEnv *env, jdoubleArray array, jdouble *elems, jint mode);
    void (PJNICALL GetBooleanArrayRegion)(JNIEnv *env, jbooleanArray array, jsize start, jsize l, jboolean *buf);
    void (PJNICALL GetByteArrayRegion)(JNIEnv *env, jbyteArray array, jsize start, jsize len, jbyte *buf);
    void (PJNICALL GetCharArrayRegion)(JNIEnv *env, jcharArray array, jsize start, jsize len, jchar *buf);
    void (PJNICALL GetShortArrayRegion)(JNIEnv *env, jshortArray array, jsize start, jsize len, jshort *buf);
    void (PJNICALL GetIntArrayRegion)(JNIEnv *env, jintArray array, jsize start, jsize len, jint *buf);
    void (PJNICALL GetLongArrayRegion)(JNIEnv *env, jlongArray array, jsize start, jsize len, jlong *buf);
    void (PJNICALL GetFloatArrayRegion)(JNIEnv *env, jfloatArray array, jsize start, jsize len, jfloat *buf);
    void (PJNICALL GetDoubleArrayRegion)(JNIEnv *env, jdoubleArray array, jsize start, jsize len, jdouble *buf);
    void (PJNICALL SetBooleanArrayRegion)(JNIEnv *env, jbooleanArray array, jsize start, jsize l, jboolean *buf);
    void (PJNICALL SetByteArrayRegion)(JNIEnv *env, jbyteArray array, jsize start, jsize len, jbyte *buf);
    void (PJNICALL SetCharArrayRegion)(JNIEnv *env, jcharArray array, jsize start, jsize len, jchar *buf);
    void (PJNICALL SetShortArrayRegion)(JNIEnv *env, jshortArray array, jsize start, jsize len, jshort *buf);
    void (PJNICALL SetIntArrayRegion)(JNIEnv *env, jintArray array, jsize start, jsize len, jint *buf);
    void (PJNICALL SetLongArrayRegion)(JNIEnv *env, jlongArray array, jsize start, jsize len, jlong *buf);
    void (PJNICALL SetFloatArrayRegion)(JNIEnv *env, jfloatArray array, jsize start, jsize len, jfloat *buf);
    void (PJNICALL SetDoubleArrayRegion)(JNIEnv *env, jdoubleArray array, jsize start, jsize len, jdouble *buf);
    jint (PJNICALL RegisterNatives)(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint nMethods);
    jint (PJNICALL UnregisterNatives)(JNIEnv *env, jclass clazz);
    jint (PJNICALL MonitorEnter)(JNIEnv *env, jobject obj);
    jint (PJNICALL MonitorExit)(JNIEnv *env, jobject obj);
    jint (PJNICALL GetJavaVM)(JNIEnv *env, JavaVM **vm);
    void (PJNICALL GetStringRegion)(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);
    void (PJNICALL GetStringUTFRegion)(JNIEnv *env, jstring str, jsize start, jsize len, char *buf);
    void * (PJNICALL GetPrimitiveArrayCritical)(JNIEnv *env, jarray array, jboolean *isCopy);
    void (PJNICALL ReleasePrimitiveArrayCritical)(JNIEnv *env, jarray array, void *carray, jint mode);
    jchar * (PJNICALL GetStringCritical)(JNIEnv *env, jstring string, jboolean *isCopy);
    void (PJNICALL ReleaseStringCritical)(JNIEnv *env, jstring string, jchar *carray);
    jweak (PJNICALL NewWeakGlobalRef)(JNIEnv *env, jobject obj);
    void (PJNICALL DeleteWeakGlobalRef)(JNIEnv *env, jweak obj);
    jboolean (PJNICALL ExceptionCheck)(JNIEnv *env);
    jobject (PJNICALL NewDirectByteBuffer)(JNIEnv *env, void *address, jlong capacity);
    void * (PJNICALL GetDirectBufferAddress)(JNIEnv *env, jobject buf);
    jlong (PJNICALL GetDirectBufferCapacity)(JNIEnv *env, jobject buf);
};
struct JNIEnv_ {
    const struct JNINativeInterface_ *functions;
    void *threadSynch;
    void *reserved1[6];

#ifdef __cplusplus
    jint GetVersion() { return functions->GetVersion(this); }
    jclass DefineClass(const char *name, jobject loader, const jbyte *buf, jsize len) { return functions->DefineClass(this, name, loader, buf, len); }
    jclass FindClass(const char *name) { return functions->FindClass(this, name); }
    jmethodID FromReflectedMethod(jobject method) { return functions->FromReflectedMethod(this, method); }
    jfieldID FromReflectedField(jobject field) { return functions->FromReflectedField(this, field); }
    jobject ToReflectedMethod(jclass cls, jmethodID methodID, jboolean isStatic) { return functions->ToReflectedMethod(this, cls, methodID, isStatic); }
    jclass GetSuperclass(jclass sub) { return functions->GetSuperclass(this, sub); }
    jboolean IsAssignableFrom(jclass sub, jclass sup) { return functions->IsAssignableFrom(this, sub, sup); }
    jobject ToReflectedField(jclass cls, jfieldID fieldID, jboolean isStatic) { return functions->ToReflectedField(this, cls, fieldID, isStatic); }
    jint Throw(jthrowable obj) { return functions->Throw(this, obj); }
    jint ThrowNew(jclass clazz, const char *msg) { return functions->ThrowNew(this, clazz, msg); }
    jthrowable ExceptionOccurred() { return functions->ExceptionOccurred(this); }
    void ExceptionDescribe() { functions->ExceptionDescribe(this); }
    void ExceptionClear() { functions->ExceptionClear(this); }
    void FatalError(const char *msg) { functions->FatalError(this, msg); }
    jint PushLocalFrame(jint capacity) { return functions->PushLocalFrame(this, capacity); }
    jobject PopLocalFrame(jobject result) { return functions->PopLocalFrame(this, result); }
    jobject NewGlobalRef(jobject lobj) { return functions->NewGlobalRef(this, lobj); }
    void DeleteGlobalRef(jobject gref) { functions->DeleteGlobalRef(this, gref); }
    void DeleteLocalRef(jobject obj) { functions->DeleteLocalRef(this, obj); }
    jboolean IsSameObject(jobject obj1, jobject obj2) { return functions->IsSameObject(this, obj1, obj2); }
    jobject NewLocalRef(jobject ref) { return functions->NewLocalRef(this, ref); }
    jint EnsureLocalCapacity(jint capacity) { return functions->EnsureLocalCapacity(this, capacity); }
    jobject AllocObject(jclass clazz) { return functions->AllocObject(this, clazz); }
    jobject NewObject(jclass clazz, jmethodID methodID, ...) { jobject retval; va_list parms; va_start(parms, methodID); retval = functions->NewObjectV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jobject NewObjectV(jclass clazz, jmethodID methodID, va_list args) { return functions->NewObjectV(this, clazz, methodID, args); }
    jobject NewObjectA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->NewObjectA(this, clazz, methodID, args); }
    jclass GetObjectClass(jobject obj) { return functions->GetObjectClass(this, obj); }
    jboolean IsInstanceOf(jobject obj, jclass clazz) { return functions->IsInstanceOf(this, obj, clazz); }
    jmethodID GetMethodID(jclass clazz, const char *name, const char *sig) { return functions->GetMethodID(this, clazz, name, sig); }
    jobject CallObjectMethod(jobject obj, jmethodID methodID, ...) { jobject retval; va_list parms; va_start(parms, methodID); retval = functions->CallObjectMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jobject CallObjectMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallObjectMethodV(this, obj, methodID, args); }
    jobject CallObjectMethodA(jobject obj, jmethodID methodID, jvalue * args) { return functions->CallObjectMethodA(this, obj, methodID,  args); }
    jboolean CallBooleanMethod(jobject obj, jmethodID methodID, ...) { jboolean retval; va_list parms; va_start(parms, methodID); retval = functions->CallBooleanMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jboolean CallBooleanMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallBooleanMethodV(this, obj, methodID, args); }
    jboolean CallBooleanMethodA(jobject obj, jmethodID methodID, jvalue * args) { return functions->CallBooleanMethodA(this, obj, methodID,  args); }
    jbyte CallByteMethod(jobject obj, jmethodID methodID, ...) { jbyte retval; va_list parms; va_start(parms, methodID); retval = functions->CallByteMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jbyte CallByteMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallByteMethodV(this, obj, methodID, args); }
    jbyte CallByteMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallByteMethodA(this, obj, methodID, args); }
    jchar CallCharMethod(jobject obj, jmethodID methodID, ...) { jchar retval; va_list parms; va_start(parms, methodID); retval = functions->CallCharMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jchar CallCharMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallCharMethodV(this, obj, methodID, args); }
    jchar CallCharMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallCharMethodA(this, obj, methodID, args); }
    jshort CallShortMethod(jobject obj, jmethodID methodID, ...) { jshort retval; va_list parms; va_start(parms, methodID); retval = functions->CallShortMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jshort CallShortMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallShortMethodV(this, obj, methodID, args); }
    jshort CallShortMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallShortMethodA(this, obj, methodID, args); }
    jint CallIntMethod(jobject obj, jmethodID methodID, ...) { jint retval; va_list parms; va_start(parms, methodID); retval = functions->CallIntMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jint CallIntMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallIntMethodV(this, obj, methodID, args); }
    jint CallIntMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallIntMethodA(this, obj, methodID, args); }
    jlong CallLongMethod(jobject obj, jmethodID methodID, ...) { jlong retval; va_list parms; va_start(parms, methodID); retval = functions->CallLongMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jlong CallLongMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallLongMethodV(this, obj, methodID, args); }
    jlong CallLongMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallLongMethodA(this, obj, methodID, args); }
    jfloat CallFloatMethod(jobject obj, jmethodID methodID, ...) { jfloat retval; va_list parms; va_start(parms, methodID); retval = functions->CallFloatMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jfloat CallFloatMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallFloatMethodV(this, obj, methodID, args); }
    jfloat CallFloatMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallFloatMethodA(this, obj, methodID, args); }
    jdouble CallDoubleMethod(jobject obj, jmethodID methodID, ...) { jdouble retval; va_list parms; va_start(parms, methodID); retval = functions->CallDoubleMethodV(this, obj, methodID, parms); va_end(parms); return retval; }
    jdouble CallDoubleMethodV(jobject obj, jmethodID methodID, va_list args) { return functions->CallDoubleMethodV(this, obj, methodID, args); }
    jdouble CallDoubleMethodA(jobject obj, jmethodID methodID, jvalue *args) { return functions->CallDoubleMethodA(this, obj, methodID, args); }
    void CallVoidMethod(jobject obj, jmethodID methodID, ...) { va_list parms; va_start(parms, methodID); functions->CallVoidMethodV(this, obj, methodID, parms); va_end(parms); }
    void CallVoidMethodV(jobject obj, jmethodID methodID, va_list args) { functions->CallVoidMethodV(this, obj, methodID, args); }
    void CallVoidMethodA(jobject obj, jmethodID methodID, jvalue * args) { functions->CallVoidMethodA(this, obj, methodID,  args); }
    jobject CallNonvirtualObjectMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jobject retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualObjectMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jobject CallNonvirtualObjectMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualObjectMethodV(this, obj, clazz, methodID, args); }
    jobject CallNonvirtualObjectMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue * args) { return functions->CallNonvirtualObjectMethodA(this, obj, clazz, methodID,  args); }
    jboolean CallNonvirtualBooleanMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jboolean retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualBooleanMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jboolean CallNonvirtualBooleanMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualBooleanMethodV(this, obj, clazz, methodID, args); }
    jboolean CallNonvirtualBooleanMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue * args) { return functions->CallNonvirtualBooleanMethodA(this, obj, clazz, methodID,  args); }
    jbyte CallNonvirtualByteMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jbyte retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualByteMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jbyte CallNonvirtualByteMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualByteMethodV(this, obj, clazz, methodID, args); }
    jbyte CallNonvirtualByteMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualByteMethodA(this, obj, clazz, methodID, args); }
    jchar CallNonvirtualCharMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jchar retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualCharMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jchar CallNonvirtualCharMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualCharMethodV(this, obj, clazz, methodID, args); }
    jchar CallNonvirtualCharMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualCharMethodA(this, obj, clazz, methodID, args); }
    jshort CallNonvirtualShortMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jshort retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualShortMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jshort CallNonvirtualShortMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualShortMethodV(this, obj, clazz, methodID, args); }
    jshort CallNonvirtualShortMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualShortMethodA(this, obj, clazz, methodID, args); }
    jint CallNonvirtualIntMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jint retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualIntMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jint CallNonvirtualIntMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualIntMethodV(this, obj, clazz, methodID, args); }
    jint CallNonvirtualIntMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualIntMethodA(this, obj, clazz, methodID, args); }
    jlong CallNonvirtualLongMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jlong retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualLongMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jlong CallNonvirtualLongMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualLongMethodV(this, obj, clazz, methodID, args); }
    jlong CallNonvirtualLongMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualLongMethodA(this, obj, clazz, methodID, args); }
    jfloat CallNonvirtualFloatMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jfloat retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualFloatMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jfloat CallNonvirtualFloatMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualFloatMethodV(this, obj, clazz, methodID, args); }
    jfloat CallNonvirtualFloatMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualFloatMethodA(this, obj, clazz, methodID, args); }
    jdouble CallNonvirtualDoubleMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { jdouble retval; va_list parms; va_start(parms, methodID); retval = functions->CallNonvirtualDoubleMethodV(this, obj, clazz, methodID, parms); va_end(parms); return retval; }
    jdouble CallNonvirtualDoubleMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { return functions->CallNonvirtualDoubleMethodV(this, obj, clazz, methodID, args); }
    jdouble CallNonvirtualDoubleMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallNonvirtualDoubleMethodA(this, obj, clazz, methodID, args); }
    void CallNonvirtualVoidMethod(jobject obj, jclass clazz, jmethodID methodID, ...) { va_list parms; va_start(parms, methodID); functions->CallNonvirtualVoidMethodV(this, obj, clazz, methodID, parms); va_end(parms); }
    void CallNonvirtualVoidMethodV(jobject obj, jclass clazz, jmethodID methodID, va_list args) { functions->CallNonvirtualVoidMethodV(this, obj, clazz, methodID, args); }
    void CallNonvirtualVoidMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue * args) { functions->CallNonvirtualVoidMethodA(this, obj, clazz, methodID,  args); }
    jfieldID GetFieldID(jclass clazz, const char *name, const char *sig) { return functions->GetFieldID(this, clazz, name, sig); }
    jobject GetObjectField(jobject obj, jfieldID fieldID) { return functions->GetObjectField(this, obj, fieldID); }
    jboolean GetBooleanField(jobject obj, jfieldID fieldID) { return functions->GetBooleanField(this, obj, fieldID); }
    jbyte GetByteField(jobject obj, jfieldID fieldID) { return functions->GetByteField(this, obj, fieldID); }
    jchar GetCharField(jobject obj, jfieldID fieldID) { return functions->GetCharField(this, obj, fieldID); }
    jshort GetShortField(jobject obj, jfieldID fieldID) { return functions->GetShortField(this, obj, fieldID); }
    jint GetIntField(jobject obj, jfieldID fieldID) { return functions->GetIntField(this, obj, fieldID); }
    jlong GetLongField(jobject obj, jfieldID fieldID) { return functions->GetLongField(this, obj, fieldID); }
    jfloat GetFloatField(jobject obj, jfieldID fieldID) { return functions->GetFloatField(this, obj, fieldID); }
    jdouble GetDoubleField(jobject obj, jfieldID fieldID) { return functions->GetDoubleField(this, obj, fieldID); }
    void SetObjectField(jobject obj, jfieldID fieldID, jobject val) { functions->SetObjectField(this, obj, fieldID, val); }
    void SetBooleanField(jobject obj, jfieldID fieldID, jboolean val) { functions->SetBooleanField(this, obj, fieldID, val); }
    void SetByteField(jobject obj, jfieldID fieldID, jbyte val) { functions->SetByteField(this, obj, fieldID, val); }
    void SetCharField(jobject obj, jfieldID fieldID, jchar val) { functions->SetCharField(this, obj, fieldID, val); }
    void SetShortField(jobject obj, jfieldID fieldID, jshort val) { functions->SetShortField(this, obj, fieldID, val); }
    void SetIntField(jobject obj, jfieldID fieldID, jint val) { functions->SetIntField(this, obj, fieldID, val); }
    void SetLongField(jobject obj, jfieldID fieldID, jlong val) { functions->SetLongField(this, obj, fieldID, val); }
    void SetFloatField(jobject obj, jfieldID fieldID, jfloat val) { functions->SetFloatField(this, obj, fieldID, val); }
    void SetDoubleField(jobject obj, jfieldID fieldID, jdouble val) { functions->SetDoubleField(this, obj, fieldID, val); }
    jmethodID GetStaticMethodID(jclass clazz, const char *name, const char *sig) { return functions->GetStaticMethodID(this, clazz, name, sig); }
    jobject CallStaticObjectMethod(jclass clazz, jmethodID methodID, ...) { jobject retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticObjectMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jobject CallStaticObjectMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticObjectMethodV(this, clazz, methodID, args); }
    jobject CallStaticObjectMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticObjectMethodA(this, clazz, methodID, args); }
    jboolean CallStaticBooleanMethod(jclass clazz, jmethodID methodID, ...) { jboolean retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticBooleanMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jboolean CallStaticBooleanMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticBooleanMethodV(this, clazz, methodID, args); }
    jboolean CallStaticBooleanMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticBooleanMethodA(this, clazz, methodID, args); }
    jbyte CallStaticByteMethod(jclass clazz, jmethodID methodID, ...) { jbyte retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticByteMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jbyte CallStaticByteMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticByteMethodV(this, clazz, methodID, args); }
    jbyte CallStaticByteMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticByteMethodA(this, clazz, methodID, args); }
    jchar CallStaticCharMethod(jclass clazz, jmethodID methodID, ...) { jchar retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticCharMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jchar CallStaticCharMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticCharMethodV(this, clazz, methodID, args); }
    jchar CallStaticCharMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticCharMethodA(this, clazz, methodID, args); }
    jshort CallStaticShortMethod(jclass clazz, jmethodID methodID, ...) { jshort retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticShortMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jshort CallStaticShortMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticShortMethodV(this, clazz, methodID, args); }
    jshort CallStaticShortMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticShortMethodA(this, clazz, methodID, args); }
    jint CallStaticIntMethod(jclass clazz, jmethodID methodID, ...) { jint retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticIntMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jint CallStaticIntMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticIntMethodV(this, clazz, methodID, args); }
    jint CallStaticIntMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticIntMethodA(this, clazz, methodID, args); }
    jlong CallStaticLongMethod(jclass clazz, jmethodID methodID, ...) { jlong retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticLongMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jlong CallStaticLongMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticLongMethodV(this, clazz, methodID, args); }
    jlong CallStaticLongMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticLongMethodA(this, clazz, methodID, args); }
    jfloat CallStaticFloatMethod(jclass clazz, jmethodID methodID, ...) { jfloat retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticFloatMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jfloat CallStaticFloatMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticFloatMethodV(this, clazz, methodID, args); }
    jfloat CallStaticFloatMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticFloatMethodA(this, clazz, methodID, args); }
    jdouble CallStaticDoubleMethod(jclass clazz, jmethodID methodID, ...) { jdouble retval; va_list parms; va_start(parms, methodID); retval = functions->CallStaticDoubleMethodV(this, clazz, methodID, parms); va_end(parms); return retval; }
    jdouble CallStaticDoubleMethodV(jclass clazz, jmethodID methodID, va_list args) { return functions->CallStaticDoubleMethodV(this, clazz, methodID, args); }
    jdouble CallStaticDoubleMethodA(jclass clazz, jmethodID methodID, jvalue *args) { return functions->CallStaticDoubleMethodA(this, clazz, methodID, args); }
    void CallStaticVoidMethod(jclass cls, jmethodID methodID, ...) { va_list parms; va_start(parms, methodID); functions->CallStaticVoidMethodV(this, cls, methodID, parms); va_end(parms); }
    void CallStaticVoidMethodV(jclass cls, jmethodID methodID, va_list args) { functions->CallStaticVoidMethodV(this, cls, methodID, args); }
    void CallStaticVoidMethodA(jclass cls, jmethodID methodID, jvalue * args) { functions->CallStaticVoidMethodA(this, cls, methodID,  args); }
    jfieldID GetStaticFieldID(jclass clazz, const char *name, const char *sig) { return functions->GetStaticFieldID(this, clazz, name, sig); }
    jobject GetStaticObjectField(jclass clazz, jfieldID fieldID) { return functions->GetStaticObjectField(this, clazz, fieldID); }
    jboolean GetStaticBooleanField(jclass clazz, jfieldID fieldID) { return functions->GetStaticBooleanField(this, clazz, fieldID); }
    jbyte GetStaticByteField(jclass clazz, jfieldID fieldID) { return functions->GetStaticByteField(this, clazz, fieldID); }
    jchar GetStaticCharField(jclass clazz, jfieldID fieldID) { return functions->GetStaticCharField(this, clazz, fieldID); }
    jshort GetStaticShortField(jclass clazz, jfieldID fieldID) { return functions->GetStaticShortField(this, clazz, fieldID); }
    jint GetStaticIntField(jclass clazz, jfieldID fieldID) { return functions->GetStaticIntField(this, clazz, fieldID); }
    jlong GetStaticLongField(jclass clazz, jfieldID fieldID) { return functions->GetStaticLongField(this, clazz, fieldID); }
    jfloat GetStaticFloatField(jclass clazz, jfieldID fieldID) { return functions->GetStaticFloatField(this, clazz, fieldID); }
    jdouble GetStaticDoubleField(jclass clazz, jfieldID fieldID) { return functions->GetStaticDoubleField(this, clazz, fieldID); }
    void SetStaticObjectField(jclass clazz, jfieldID fieldID, jobject value) { functions->SetStaticObjectField(this, clazz, fieldID, value); }
    void SetStaticBooleanField(jclass clazz, jfieldID fieldID, jboolean value) { functions->SetStaticBooleanField(this, clazz, fieldID, value); }
    void SetStaticByteField(jclass clazz, jfieldID fieldID, jbyte value) { functions->SetStaticByteField(this, clazz, fieldID, value); }
    void SetStaticCharField(jclass clazz, jfieldID fieldID, jchar value) { functions->SetStaticCharField(this, clazz, fieldID, value); }
    void SetStaticShortField(jclass clazz, jfieldID fieldID, jshort value) { functions->SetStaticShortField(this, clazz, fieldID, value); }
    void SetStaticIntField(jclass clazz, jfieldID fieldID, jint value) { functions->SetStaticIntField(this, clazz, fieldID, value); }
    void SetStaticLongField(jclass clazz, jfieldID fieldID, jlong value) { functions->SetStaticLongField(this, clazz, fieldID, value); }
    void SetStaticFloatField(jclass clazz, jfieldID fieldID, jfloat value) { functions->SetStaticFloatField(this, clazz, fieldID, value); }
    void SetStaticDoubleField(jclass clazz, jfieldID fieldID, jdouble value) { functions->SetStaticDoubleField(this, clazz, fieldID, value); }
    jstring NewString(const jchar *unicode, jsize len) { return functions->NewString(this, unicode, len); }
    jsize GetStringLength(jstring str) { return functions->GetStringLength(this, str); }
    const jchar* GetStringChars(jstring str, jboolean *isCopy) { return functions->GetStringChars(this, str, isCopy); }
    void ReleaseStringChars(jstring str, const jchar *chars) { functions->ReleaseStringChars(this, str, chars); }
    jstring NewStringUTF(const char *utf) { return functions->NewStringUTF(this, utf); }
    jsize GetStringUTFLength(jstring str) { return functions->GetStringUTFLength(this, str); }
    const char* GetStringUTFChars(jstring str, jboolean *isCopy) { return functions->GetStringUTFChars(this, str, isCopy); }
    void ReleaseStringUTFChars(jstring str, const char* chars) { functions->ReleaseStringUTFChars(this, str, chars); }
    jsize GetArrayLength(jarray array) { return functions->GetArrayLength(this, array); }
    jobjectArray NewObjectArray(jsize len, jclass clazz, jobject init) { return functions->NewObjectArray(this, len, clazz, init); }
    jobject GetObjectArrayElement(jobjectArray array, jsize index_) { return functions->GetObjectArrayElement(this, array, index_); }
    void SetObjectArrayElement(jobjectArray array, jsize index_, jobject val) { functions->SetObjectArrayElement(this, array, index_, val); }
    jbooleanArray NewBooleanArray(jsize len) { return functions->NewBooleanArray(this, len); }
    jbyteArray NewByteArray(jsize len) { return functions->NewByteArray(this, len); }
    jcharArray NewCharArray(jsize len) { return functions->NewCharArray(this, len); }
    jshortArray NewShortArray(jsize len) { return functions->NewShortArray(this, len); }
    jintArray NewIntArray(jsize len) { return functions->NewIntArray(this, len); }
    jlongArray NewLongArray(jsize len) { return functions->NewLongArray(this, len); }
    jfloatArray NewFloatArray(jsize len) { return functions->NewFloatArray(this, len); }
    jdoubleArray NewDoubleArray(jsize len) { return functions->NewDoubleArray(this, len); }
    jboolean* GetBooleanArrayElements(jbooleanArray array, jboolean *isCopy) { return functions->GetBooleanArrayElements(this, array, isCopy); }
    jbyte* GetByteArrayElements(jbyteArray array, jboolean *isCopy) { return functions->GetByteArrayElements(this, array, isCopy); }
    jchar*  GetCharArrayElements(jcharArray array, jboolean *isCopy) { return functions->GetCharArrayElements(this, array, isCopy); }
    jshort* GetShortArrayElements(jshortArray array, jboolean *isCopy) { return functions->GetShortArrayElements(this, array, isCopy); }
    jint* GetIntArrayElements(jintArray array, jboolean *isCopy) { return functions->GetIntArrayElements(this, array, isCopy); }
    jlong* GetLongArrayElements(jlongArray array, jboolean *isCopy) { return functions->GetLongArrayElements(this, array, isCopy); }
    jfloat* GetFloatArrayElements(jfloatArray array, jboolean *isCopy) { return functions->GetFloatArrayElements(this, array, isCopy); }
    jdouble* GetDoubleArrayElements(jdoubleArray array, jboolean *isCopy) { return functions->GetDoubleArrayElements(this, array, isCopy); }
    void ReleaseBooleanArrayElements(jbooleanArray array, jboolean *elems, jint mode) { functions->ReleaseBooleanArrayElements(this, array, elems, mode); }
    void ReleaseByteArrayElements(jbyteArray array, jbyte *elems, jint mode) { functions->ReleaseByteArrayElements(this, array, elems, mode); }
    void ReleaseCharArrayElements(jcharArray array, jchar *elems, jint mode) { functions->ReleaseCharArrayElements(this, array, elems, mode); }
    void ReleaseShortArrayElements(jshortArray array, jshort *elems, jint mode) { functions->ReleaseShortArrayElements(this, array, elems, mode); }
    void ReleaseIntArrayElements(jintArray array, jint *elems, jint mode) { functions->ReleaseIntArrayElements(this, array, elems, mode); }
    void ReleaseLongArrayElements(jlongArray array, jlong *elems, jint mode) { functions->ReleaseLongArrayElements(this, array, elems, mode); }
    void ReleaseFloatArrayElements(jfloatArray array, jfloat *elems, jint mode) { functions->ReleaseFloatArrayElements(this, array, elems, mode); }
    void ReleaseDoubleArrayElements(jdoubleArray array, jdouble *elems, jint mode) { functions->ReleaseDoubleArrayElements(this, array, elems, mode); }
    void GetBooleanArrayRegion(jbooleanArray array, jsize start, jsize l, jboolean *buf) { functions->GetBooleanArrayRegion(this, array, start, l, buf); }
    void GetByteArrayRegion(jbyteArray array, jsize start, jsize len, jbyte *buf) { functions->GetByteArrayRegion(this, array, start, len, buf); }
    void GetCharArrayRegion(jcharArray array, jsize start, jsize len, jchar *buf) { functions->GetCharArrayRegion(this, array, start, len, buf); }
    void GetShortArrayRegion(jshortArray array, jsize start, jsize len, jshort *buf) { functions->GetShortArrayRegion(this, array, start, len, buf); }
    void GetIntArrayRegion(jintArray array, jsize start, jsize len, jint *buf) { functions->GetIntArrayRegion(this, array, start, len, buf); }
    void GetLongArrayRegion(jlongArray array, jsize start, jsize len, jlong *buf) { functions->GetLongArrayRegion(this, array, start, len, buf); }
    void GetFloatArrayRegion(jfloatArray array, jsize start, jsize len, jfloat *buf) { functions->GetFloatArrayRegion(this, array, start, len, buf); }
    void GetDoubleArrayRegion(jdoubleArray array, jsize start, jsize len, jdouble *buf) { functions->GetDoubleArrayRegion(this, array, start, len, buf); }
    void SetBooleanArrayRegion(jbooleanArray array, jsize start, jsize l, jboolean *buf) { functions->SetBooleanArrayRegion(this, array, start, l, buf); }
    void SetByteArrayRegion(jbyteArray array, jsize start, jsize len, jbyte *buf) { functions->SetByteArrayRegion(this, array, start, len, buf); }
    void SetCharArrayRegion(jcharArray array, jsize start, jsize len, jchar *buf) { functions->SetCharArrayRegion(this, array, start, len, buf); }
    void SetShortArrayRegion(jshortArray array, jsize start, jsize len, jshort *buf) { functions->SetShortArrayRegion(this, array, start, len, buf); }
    void SetIntArrayRegion(jintArray array, jsize start, jsize len, jint *buf) {
        functions->SetIntArrayRegion(this, array, start, len, buf); }
    void SetLongArrayRegion(jlongArray array, jsize start, jsize len,
                            jlong *buf) {
        functions->SetLongArrayRegion(this, array, start, len, buf); }
    void SetFloatArrayRegion(jfloatArray array, jsize start, jsize len,
                             jfloat *buf) {
        functions->SetFloatArrayRegion(this, array, start, len, buf); }
    void SetDoubleArrayRegion(jdoubleArray array, jsize start, jsize len,
                              jdouble *buf) {
        functions->SetDoubleArrayRegion(this, array, start, len, buf); }
    jint RegisterNatives(jclass clazz, const JNINativeMethod *methods,
                         jint nMethods) {
        return functions->RegisterNatives(this, clazz, methods, nMethods); }
    jint UnregisterNatives(jclass clazz) {
        return functions->UnregisterNatives(this, clazz); }
    jint MonitorEnter(jobject obj) {
        return functions->MonitorEnter(this, obj); }
    jint MonitorExit(jobject obj) { return functions->MonitorExit(this, obj); }
    jint GetJavaVM(JavaVM **vm) { return functions->GetJavaVM(this, vm); }
    void GetStringRegion(jstring str, jsize start, jsize len, jchar *buf) {
        functions->GetStringRegion(this, str, start, len, buf); }
    void GetStringUTFRegion(jstring str, jsize start, jsize len, char *buf) {
        functions->GetStringUTFRegion(this, str, start, len, buf); }
    void* GetPrimitiveArrayCritical(jarray array, jboolean *isCopy) {
        return functions->GetPrimitiveArrayCritical(this, array, isCopy); }
    void ReleasePrimitiveArrayCritical(jarray array, void *carray, jint mode) {
        functions->ReleasePrimitiveArrayCritical(this, array, carray, mode); }
    jchar* GetStringCritical(jstring string, jboolean *isCopy) {
        return functions->GetStringCritical(this, string, isCopy);
    }
    void ReleaseStringCritical(jstring string, jchar *carray) {
        functions->ReleaseStringCritical(this, string, carray); }
    jweak NewWeakGlobalRef(jobject obj) {
        return functions->NewWeakGlobalRef(this, obj); }
    void DeleteWeakGlobalRef(jweak obj) {
        functions->DeleteWeakGlobalRef(this, obj);
    }
    jboolean ExceptionCheck() { return functions->ExceptionCheck(this); }
    jobject NewDirectByteBuffer(JNIEnv *env, void *address, jlong capacity) {
        return functions->NewDirectByteBuffer(env, address, capacity);
    }
    void * GetDirectBufferAddress(JNIEnv *env, jobject buf) {
        return functions->GetDirectBufferAddress(env, buf);
    }

    jlong GetDirectBufferCapacity(JNIEnv *env, jobject buf) {
        return functions->GetDirectBufferCapacity(env, buf);
    }
#endif
};

/******
 * Arguments for RVM  (Java 1.1 version)
 * Default values set by calling JNI_GetDefaultJavaVMInitArgs(JDK1_1InitArgs *)
 */
typedef struct JDK1_1InitArgs {
    jint version;
    char **properties;
    jint checkSource;
    jint nativeStackSize;
    jint javaStackSize;
    jint minHeapSize;
    jint maxHeapSize;
    jint verifyMode;
    char *classpath;
    jint (PJNICALL vfprintf)(FILE *fp, const char *format, va_list args);
    void (PJNICALL exit)(jint code);
    void (PJNICALL abort)(void);
    jint enableClassGC;
    jint enableVerboseGC;
    jint disableAsyncGC;

    /* The following are RVM specific */
    jint verbose;
    unsigned smallHeapSize;     /* specify with option "-h"          */
    unsigned largeHeapSize;     /* specify with option "-lh"         */
    unsigned nurserySize;       /* specify with option "-nh"         */
    unsigned permanentHeapSize; /* specify with option "-ph"         */
    char*    sysLogFile;        /* specify with option "-sysLogFile" */
    char*    bootFilename;      /* boot image, specify with option "-i" */
    char**   JavaArgs;          /* command line arguments to pass to the VM */

} JDK1_1InitArgs;


typedef struct JDK1_1AttachArgs {
    void * __padding; /* C compilers don't allow empty structures. */
} JDK1_1AttachArgs;


/* 1.2 args */
typedef struct JavaVMOption {
    char *optionString;
    void *extraInfo;
} JavaVMOption;
typedef struct JavaVMInitArgs {
    jint version;

    jint nOptions;
    JavaVMOption *options;
    jboolean ignoreUnrecognized;
} JavaVMInitArgs;
typedef struct {
    jint version;
    char *name;
    jobject group;
} JavaVMAttachArgs;

struct JNIInvokeInterface_ {
    void *reserved0;
    void *reserved1;
    void *reserved2;
    jint (PJNICALL DestroyJavaVM)(JavaVM *vm);
    jint (PJNICALL AttachCurrentThread)(JavaVM *vm, void **penv, void *args);
    jint (PJNICALL DetachCurrentThread)(JavaVM *vm);
    jint (PJNICALL GetEnv)(JavaVM *vm, void **penv, jint version);
    jint (PJNICALL AttachCurrentThreadAsDaemon)(JavaVM *vm, /* JNIEnv */ void  **penv, void *args);
};
struct JavaVM_ {
    const struct JNIInvokeInterface_ *functions;
    void *reserved0;
    void *reserved1;
    void *reserved2;
    int *pthreadIDTable;
    JNIEnv **jniEnvTable;

#ifdef __cplusplus
    jint DestroyJavaVM() { return functions->DestroyJavaVM(this); }
    jint AttachCurrentThread(/* JNIEnv */ void ** p_env, void * args) { return functions->AttachCurrentThread(this, p_env, args); }
    jint DetachCurrentThread() { return functions->DetachCurrentThread(this); }
    jint GetEnv(void ** p_env, jint version) { return functions->GetEnv(this, p_env, version); }
    jint AttachCurrentThreadAsDaemon(/* JNIEnv */ void ** p_env, void * args) { return functions->AttachCurrentThreadAsDaemon(this, p_env, args); }
#endif
};

/* Jikes RVM needs to implement these: */
jint JNICALL JNI_GetDefaultJavaVMInitArgs(void *);
jint JNICALL JNI_CreateJavaVM(JavaVM **, JNIEnv **, void *);
jint JNICALL JNI_GetCreatedJavaVMs(JavaVM **, jsize, jsize *);

#ifdef __cplusplus
}
#endif

#endif /* JNI_H */
