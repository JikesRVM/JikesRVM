package com.ibm.JikesRVM.GenerateInterfaceDeclarations;
import java.lang.reflect.Field;

class DoubleEmitter 
  extends Shared
{
  private String cPrefix;
  private Class cl;             // Class we'll pull up declarations for?

  DoubleEmitter(Class cl, String cPrefix) {
    this.cPrefix = cPrefix;
    this.cl = cl;
  }
  void emit(String fieldName) 
    throws NoSuchFieldException,IllegalAccessException 
  {

    final String f = fieldName;
    Field fld = cl.getField(f);
    
    p("static const double " + cPrefix + fieldName
      + "               = "
      + fld.get(null) + ";\n");
  }
}
