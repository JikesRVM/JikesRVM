/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$

package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM_PragmaUninterruptible;

public class OSR_ClassLoaderInterface {
  public static VM_Method getMethodById(int id) throws VM_PragmaUninterruptible {
    return VM_MethodDictionary.getValue(id);
  }

  public static VM_Type getTypeById(int id) throws VM_PragmaUninterruptible {
	return VM_TypeDictionary.getValue(id);
  }

  public static int findOrCreateMethodId(VM_Atom classDescriptor, 
                                  VM_Atom methodName, 
                                  VM_Atom methodDescriptor,
                                  ClassLoader classloader) {
	return VM_ClassLoader.findOrCreateMethodId(classDescriptor,
											   methodName,
											   methodDescriptor,
											   classloader);
  }
}
