/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

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

  /**
   * the TOC of the native procedure
   */
  private VM_Address nativeTOC;                              

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
    return nativeTOC;
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
    String nativeProcedureNameWithSigniture = getMangledName(true);

    // get the library in VM_ClassLoader
    // resolve the native routine in the libraries
    VM_DynamicLibrary libs[] = VM_ClassLoader.getDynamicLibraries();
    VM_Address symbolAddress = VM_Address.zero();
    if (libs!=null) {
      for (int i=1; i<libs.length && symbolAddress.isZero(); i++) {
        VM_DynamicLibrary lib = libs[i];

        if (lib!=null && symbolAddress==VM_Address.zero()) {
          symbolAddress = lib.getSymbol(nativeProcedureNameWithSigniture);
          if (symbolAddress != VM_Address.zero()) {
              nativeProcedureName = nativeProcedureNameWithSigniture;
              break;
          }
        }

        if (lib != null && symbolAddress==VM_Address.zero()) {
          symbolAddress = lib.getSymbol(nativeProcedureName);
          if (symbolAddress != VM_Address.zero()) {
              nativeProcedureName = nativeProcedureName;
              break;
          }
        }
      }
    }

    if (symbolAddress.isZero()) {
      // native procedure not found in library
      return false;
    } else {
      //-#if RVM_FOR_LINUX || RVM_FOR_OSX
      // both intel and linux use direct address
      nativeIP = symbolAddress;         // Intel use direct branch address
      nativeTOC = VM_Address.zero();                    // not used
      //-#else
      nativeIP  = VM_Magic.getMemoryAddress(symbolAddress);     // AIX use a triplet linkage
      nativeTOC = VM_Magic.getMemoryAddress(symbolAddress.add(BYTES_IN_ADDRESS));
      //-#endif
      // VM.sysWrite("resolveNativeMethod: " + nativeProcedureName + ", IP = " + VM.intAsHexString(nativeIP) + ", TOC = " + VM.intAsHexString(nativeTOC) + "\n");
      return true;
    }
  }
}
