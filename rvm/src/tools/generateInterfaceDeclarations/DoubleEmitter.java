/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */

// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import java.lang.reflect.Field;

/**
 * Print the specified static fields of a Java class as C "const double"
 * declarations. 
 *
 * @author Steven Augart
 * @date 11 March 2003
 */


class DoubleEmitter 
  extends Shared
{
  private String cPrefix;
  private FieldReader reader;   // Pull up declarations for the class.

  DoubleEmitter(Class cl, String cPrefix) {
    this.cPrefix = cPrefix;
    reader = new FieldReader(cl);
  }

  public void emit(String fieldName) {
    p("static const double " + cPrefix + fieldName
      + "               = "
      + reader.asDouble(fieldName) + ";\n");
  }
}
