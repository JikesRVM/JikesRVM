/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

/**
 * A native method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_NativeMethod extends VM_Method {

  /**
   * the name of the native procedure in the native library
   */
  private String nativeProcedureName;                 

  /**
   * the IP of the native p rocedure
   */
  private VM_Address nativeIP;                               

  //-#if RVM_WITH_LINKAGE_TRIPLETS
  /**
   * the TOC of the native procedure
   */
  private VM_Address nativeTOC;                              
  //-#endif
  
  /**
   * @param declaringClass the VM_Class object of the class that declared this method.
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   */
  VM_NativeMethod(VM_Class declaringClass, VM_MemberReference memRef,
                  int modifiers, VM_TypeReference[] exceptionTypes) {
    super(declaringClass, memRef, modifiers, exceptionTypes);
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() {
    if (!resolveNativeMethod()) {
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
  public final VM_Address getNativeIP() { 
    return nativeIP;
  }
  
  /**
   * get the native TOC for this method
   */
  public VM_Address getNativeTOC() { 
    //-#if RVM_WITH_LINKAGE_TRIPLETS
    return nativeTOC;
    //-#else
    return VM_Address.zero();
    //-#endif
  }

  /**
   * replace a character in a string with a string
   */
  private String replaceCharWithString(String originalString, 
                                       char targetChar, 
                                       String replaceString) {
    String returnString;
    int first = originalString.indexOf(targetChar);
    int next  = originalString.indexOf(targetChar, first+1);
    if (first!=-1) {
      returnString = originalString.substring(0,first) + replaceString;
      while (next!=-1) {
        returnString += originalString.substring(first+1, next) + replaceString;
        first = next;
        next = originalString.indexOf(targetChar, next+1);
      }
      returnString += originalString.substring(first+1);
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
    String className = declaringClass.toString();
    String methodName = getName().toString();
    int first, next;

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
      sigName = sigName.substring( sigName.indexOf('(')+1, sigName.indexOf(')') );
      sigName = replaceCharWithString(sigName, '[', "_3");
      sigName = replaceCharWithString(sigName, ';', "_2");
      sigName = sigName.replace( '/', '_');
      mangledMethodName += "__" + sigName;
    }


    String mangledName = "Java_" + mangledClassName + "_" + mangledMethodName;
    mangledName = mangledName.replace( '.', '_' );
    // VM.sysWrite("getMangledName:  " + mangledName + " \n");

    return mangledName;
  }

  private boolean resolveNativeMethod() {
    nativeProcedureName = getMangledName(false);
    String nativeProcedureNameWithSignature = getMangledName(true);

    VM_Address symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureNameWithSignature);
    if (symbolAddress.isZero()) {
      symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureName);
    }

    if (symbolAddress.isZero()) {
      // native procedure not found in library
      return false;
    } else {
      //-#if RVM_WITH_LINKAGE_TRIPLETS
      nativeIP  = VM_Magic.getMemoryAddress(symbolAddress);
      nativeTOC = VM_Magic.getMemoryAddress(symbolAddress.add(BYTES_IN_ADDRESS));
      //-#else
      nativeIP = symbolAddress;
      //-#endif
      return true;
    }
  }
}
