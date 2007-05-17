/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.VM_BootImageCompiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_RuntimeCompiler;
import org.jikesrvm.runtime.VM_DynamicLibrary;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A native method of a java class.
 */
public final class VM_NativeMethod extends VM_Method {

  /**
   * the name of the native procedure in the native library
   */
  private String nativeProcedureName;

  /**
   * the IP of the native p rocedure
   */
  private Address nativeIP;

  /**
   * the TOC of the native procedure.
   * Only used if VM.BuildForPowerOpenABI.
   * TODO: Consider making a PowerOpen subclass of VM_NativeMethod
   *       and pushing this field down to it.  For now, just bloat up
   *       all native methods by 1 slot.
   */
  private Address nativeTOC;

  /**
   * Construct native method information
   *
   * @param declaringClass the VM_Class object of the class that
   *                       declared this method.
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  VM_NativeMethod(VM_TypeReference declaringClass, VM_MemberReference memRef, short modifiers,
                  VM_TypeReference[] exceptionTypes, VM_Atom signature, VM_Annotation[] annotations,
                  VM_Annotation[] parameterAnnotations, Object annotationDefault) {
    super(declaringClass,
          memRef,
          modifiers,
          exceptionTypes,
          signature,
          annotations,
          parameterAnnotations,
          annotationDefault);
  }

  /**
   * Generate the code for this method
   */
  protected synchronized VM_CompiledMethod genCode() {
    if (isSysCall()) {
      // SysCalls are just place holder methods, the compiler
      // generates a call to the first argument - the address of a C
      // function
      VM_Entrypoints.sysCallMethod.compile();
      return VM_Entrypoints.sysCallMethod.getCurrentCompiledMethod();
    } else if (!resolveNativeMethod()) {
      // if fail to resolve native, get code to throw unsatifiedLinkError
      VM_Entrypoints.unimplementedNativeMethodMethod.compile();
      return VM_Entrypoints.unimplementedNativeMethodMethod.getCurrentCompiledMethod();
    } else {
      if (VM.writingBootImage) {
        return VM_BootImageCompiler.compile(this);
      } else {
        return VM_RuntimeCompiler.compile(this);
      }
    }
  }

  /**
   * Get the native IP for this method
   */
  public Address getNativeIP() {
    return nativeIP;
  }

  /**
   * get the native TOC for this method
   */
  public Address getNativeTOC() {
    if (VM.BuildForPowerOpenABI) {
      return nativeTOC;
    } else {
      return Address.zero();
    }
  }

  /**
   * replace a character in a string with a string
   */
  private String replaceCharWithString(String originalString, char targetChar, String replaceString) {
    String returnString;
    int first = originalString.indexOf(targetChar);
    int next = originalString.indexOf(targetChar, first + 1);
    if (first != -1) {
      returnString = originalString.substring(0, first) + replaceString;
      while (next != -1) {
        returnString += originalString.substring(first + 1, next) + replaceString;
        first = next;
        next = originalString.indexOf(targetChar, next + 1);
      }
      returnString += originalString.substring(first + 1);
    } else {
      returnString = originalString;
    }
    return returnString;
  }

  /**
   * Compute the mangled name of the native routine: Java_Class_Method_Sig
   */
  private String getMangledName(boolean sig) {
    String mangledClassName, mangledMethodName;
    String className = getDeclaringClass().toString();
    String methodName = getName().toString();

    // Mangled Class name
    // Special case: underscore in class name
    mangledClassName = replaceCharWithString(className, '_', "_1");

    // Mangled Method name
    // Special case: underscore in method name
    //   class._underscore  -> class__1underscore
    //   class.with_underscore  -> class_with_1underscore
    mangledMethodName = replaceCharWithString(methodName, '_', "_1");

    if (sig) {
      String sigName = getDescriptor().toString();
      sigName = sigName.substring(sigName.indexOf('(') + 1, sigName.indexOf(')'));
      sigName = replaceCharWithString(sigName, '[', "_3");
      sigName = replaceCharWithString(sigName, ';', "_2");
      sigName = sigName.replace('/', '_');
      mangledMethodName += "__" + sigName;
    }

    String mangledName = "Java_" + mangledClassName + "_" + mangledMethodName;
    mangledName = mangledName.replace('.', '_');
    // VM.sysWrite("getMangledName:  " + mangledName + " \n");

    return mangledName;
  }

  private boolean resolveNativeMethod() {
    if (!nativeIP.isZero()) {
      // method has already been resolved via registerNative.
      return true;
    }

    nativeProcedureName = getMangledName(false);
    String nativeProcedureNameWithSignature = getMangledName(true);

    Address symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureNameWithSignature);
    if (symbolAddress.isZero()) {
      symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureName);
    }

    if (symbolAddress.isZero()) {
      // native procedure not found in library
      return false;
    } else {
      if (VM.BuildForPowerOpenABI) {
        nativeIP = symbolAddress.loadAddress();
        nativeTOC = symbolAddress.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS));
      } else {
        nativeIP = symbolAddress;
      }
      return true;
    }
  }

  /**
   * Registers a native method
   * @param symbolAddress address of native function that implements the method
   */
  public synchronized void registerNativeSymbol(Address symbolAddress) {
    if (VM.BuildForPowerOpenABI) {
      nativeIP = symbolAddress.loadAddress();
      nativeTOC = symbolAddress.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS));
    } else {
      nativeIP = symbolAddress;
    }
    replaceCompiledMethod(null);
  }

  /**
   * Unregisters a native method
   */
  public synchronized void unregisterNativeSymbol() {
    if (VM.BuildForPowerOpenABI) {
      nativeIP = Address.zero();
      nativeTOC = Address.zero();
    } else {
      nativeIP = Address.zero();
    }
    replaceCompiledMethod(null);
  }
}
