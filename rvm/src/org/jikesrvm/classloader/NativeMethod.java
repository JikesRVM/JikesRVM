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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.BootImageCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.runtime.DynamicLibrary;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.pragma.Pure;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A native method of a java class.
 */
public final class NativeMethod extends RVMMethod {

  /**
   * the IP of the native procedure
   */
  private Address nativeIP;

  /**
   * the TOC of the native procedure.
   * Only used if VM.BuildForPowerOpenABI.
   * TODO: Consider making a PowerOpen subclass of NativeMethod
   *       and pushing this field down to it.  For now, just bloat up
   *       all native methods by 1 slot.
   */
  private Address nativeTOC;

  /**
   * Construct native method information
   *
   * @param declaringClass the RVMClass object of the class that
   *                       declared this method.
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  NativeMethod(TypeReference declaringClass, MemberReference memRef, short modifiers,
                  TypeReference[] exceptionTypes, Atom signature, RVMAnnotation[] annotations,
                  RVMAnnotation[][] parameterAnnotations, Object annotationDefault) {
    super(declaringClass,
          memRef,
          modifiers,
          exceptionTypes,
          signature,
          annotations,
          parameterAnnotations,
          annotationDefault);
  }

  @Override
  protected synchronized CompiledMethod genCode() {
    if (isSysCall()) {
      // SysCalls are just place holder methods, the compiler
      // generates a call to the first argument - the address of a C
      // function
      Entrypoints.sysCallMethod.compile();
      return Entrypoints.sysCallMethod.getCurrentCompiledMethod();
    } else if (!resolveNativeMethod()) {
      // if fail to resolve native, get code to throw unsatifiedLinkError
      Entrypoints.unimplementedNativeMethodMethod.compile();
      return Entrypoints.unimplementedNativeMethodMethod.getCurrentCompiledMethod();
    } else {
      if (VM.writingBootImage) {
        return BootImageCompiler.compile(this);
      } else {
        return RuntimeCompiler.compile(this);
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
  @Pure
  private String replaceCharWithString(String originalString, char targetChar, String replaceString) {
    int first = originalString.indexOf(targetChar);
    int next = originalString.indexOf(targetChar, first + 1);
    if (first != -1) {
      StringBuilder returnString  = new StringBuilder(originalString.substring(0, first));
      returnString.append(replaceString);
      while (next != -1) {
        returnString.append(originalString.substring(first + 1, next));
        returnString.append(replaceString);
        first = next;
        next = originalString.indexOf(targetChar, next + 1);
      }
      returnString.append(originalString.substring(first + 1));
      return returnString.toString();
    } else {
      return originalString;
    }
  }

  /**
   * Compute the mangled name of the native routine: Java_Class_Method_Sig
   */
  @Pure
  private String getMangledName(boolean sig) {
    String mangledClassName, mangledMethodName;
    String className = getDeclaringClass().toString();
    String methodName = getName().toString();

    // Mangled Class name
    // Special case: underscore in class name
    mangledClassName = replaceCharWithString(className, '_', "_1");
    // Special case: dollar in class name
    mangledClassName = replaceCharWithString(mangledClassName, '$', "_00024");

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

    final String nativeProcedureName = getMangledName(false);
    final String nativeProcedureNameWithSignature = getMangledName(true);

    Address symbolAddress = DynamicLibrary.resolveSymbol(nativeProcedureNameWithSignature);
    if (symbolAddress.isZero()) {
      symbolAddress = DynamicLibrary.resolveSymbol(nativeProcedureName);
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
