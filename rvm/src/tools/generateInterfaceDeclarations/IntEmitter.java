/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */

// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import java.lang.reflect.Field;
import com.ibm.JikesRVM.VM_Address;

/**
 * Print the specified static fields in a Java class as C "const int"
 * declarations. 
 *
 * @author Steven Augart
 * @date 11 March 2003
 */


class IntEmitter 
  extends Shared
{
  private String cPrefix;
  private FieldReader reader;   // Pull up declarations for the class.

  public IntEmitter(Class cl, String cPrefix) {
    this.cPrefix = cPrefix;
    reader = new FieldReader(cl);
  }

  public void emit(String[] fieldNames) 
  {
    for (int i = 0; i < fieldNames.length; ++i) {
      emit(fieldNames[i]);
    }
  }
  
  public void emit(String fieldName) {
    p("static const int " + cPrefix + fieldName
      + "               = "
      + reader.asString(fieldName) + ";\n");
  }

  public void emitAddress(String fieldName) {
    VM_Address addr = (VM_Address) reader.read(fieldName);
    p("static const int " + cPrefix + fieldName
      + "               = "
      + addr.toInt() + ";\n");
  }
}
