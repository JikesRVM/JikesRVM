/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */
// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import java.lang.reflect.*;

/**
 * Retrieve static fields from Java classes using reflection.  
 *
 * @author Steven Augart
 * @date 11 March 2003
 */

class FieldReader extends Shared {
  private Class cl;

  FieldReader(Class cl) {
    this.cl = cl;
  };

  Object read(String fieldName) {
    Field fld = null;
    try {
      fld = cl.getField(fieldName);
    } catch (NoSuchFieldException e) {
      reportTrouble("Unable to find the field " 
                    + cl.getName() + "." + fieldName, e);
    }
    try {
      return fld.get(null);
    } catch (IllegalAccessException e) {
      reportTrouble("Permission denied to get the field " 
                    + cl.getName() + "." + fieldName, e);
    }
    return null;                // Unreachable
  }

  String asString(String fieldName) {
    return read(fieldName).toString();
  }

  String[] asStringArray(String fieldName) {
    return (String[]) read(fieldName);
  }

  int asInt(String fieldName) {
    Number n = (Number) read(fieldName);
    return n.intValue();
  }

  double asDouble(String fieldName) {
    Number n = (Number) read(fieldName);
    return n.doubleValue();
  }

}
