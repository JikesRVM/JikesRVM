/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2003
 *
 * $Id$
 */

// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;
import java.lang.reflect.*;

/**
 * Print the specified static fields in a Java class as C "const int"
 * declarations. 
 *
 * @author Steven Augart
 * @date 11 March 2003
 */


class OffsetEmitter extends Shared
{
  final private String cPrefix;

  final private OffsetReader reader;

  public OffsetEmitter(Class cl, String cPrefix) {
    this.cPrefix = cPrefix;
    this.reader = new OffsetReader(cl);
  }

  public void emit(String[] fieldNamePrefixes) 
    //      throws NoSuchFieldException, IllegalAccessException
  {
    final String[] fn = fieldNamePrefixes;
    for (int i = 0; i < fn.length; ++i) {
      emit(fn[i]);
    }
  }
  
  public void emit(String fieldNamePrefix) 
    //    throws NoSuchFieldException, IllegalAccessException
  {
    emit(fieldNamePrefix, fieldNamePrefix);
  }

  public void emit(String fieldNamePrefix, String cMemberNamePrefix) 
    //    throws NoSuchFieldException, IllegalAccessException
  {
    final int offset = reader.get(fieldNamePrefix + "Field");

    p("static const int " + cPrefix + cMemberNamePrefix + "_offset"
      + "               = "
      + offset + ";\n");
  }
}
