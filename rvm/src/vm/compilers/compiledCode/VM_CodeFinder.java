/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.util.Enumeration;

/**
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_CodeFinder {

  private static boolean containsMethod(VM_CompiledMethod c, VM_Method m) {
    switch (c.getCompilerType() ) {

    case VM_CompiledMethod.BASELINE:
      return (c.getMethod() == m);

    //-#if RVM_WITH_OPT_COMPILER
    case VM_CompiledMethod.OPT: 
      if (c.getMethod() == m) 
        return true;
      else {
        VM_OptCompiledMethod x = (VM_OptCompiledMethod)c;
        VM_OptMachineCodeMap map = x.getMCMap();
        int[] enc = map.inlineEncoding;
        int i = 2; 
        boolean skip = false;
        while (i < enc.length) {
          if (enc[i] < 0) { skip = true; i++; }
          else if (enc[i] >= 0 && skip) { skip = false; i++; }
          else if (enc[i] >= 0 && !skip) {
            VM_CompiledMethod z =
              VM_CompiledMethods.getCompiledMethod(enc[i]);
            if ( z.getMethod() == m ) 
              return true;
            else {
              skip = true; i++;
            }
          }
        }
      }
    //-#endif
    }
    
    return false;
  }

  public static Enumeration getCompiledCode(final VM_Method sourceMethod) {
    final VM_CompiledMethod[] allCompiledCode =
      VM_CompiledMethods.getCompiledMethods();

    int i;
    for(i = 0; i < allCompiledCode.length; i++) 
      if (containsMethod(allCompiledCode[i], sourceMethod))
        break;

    final int j = i;
    return new Enumeration() {
        private int index = j;
        
        public boolean hasMoreElements() {
          return index < allCompiledCode.length;
        }

        public Object nextElement() {
          VM_CompiledMethod x = allCompiledCode[index];
          
          for (; index < allCompiledCode.length; index++) 
            if (containsMethod(allCompiledCode[index], sourceMethod))
              break;

          return x;
        }
      };
  }
}





