package com.ibm.JikesRVM.GenerateInterfaceDeclarations;
import java.lang.reflect.*;
import com.ibm.JikesRVM.VM_Address;

class IntEmitter 
  extends Shared
{
  private String cPrefix;
  private Class cl;             // Class we'll pull up declarations for?

  public IntEmitter(Class cl, String cPrefix) {
    this.cPrefix = cPrefix;
    this.cl = cl;
  }

  public void emit(String[] fieldNames) 
    throws NoSuchFieldException
  {
    final String[] fn = fieldNames;
    for (int i = 0; i < fn.length; ++i) {
      emit(fn);
    }
  }
  
  public void emit(String fieldName) 
    throws NoSuchFieldException, IllegalAccessException
  {
    final String f = fieldName;
    Field fld = cl.getField(f);
    
    p("static const int " + cPrefix + fieldName
      + "               = "
      + fld.get(null) + ";\n");
  }

  public void emitAddress(String fieldName) 
    throws NoSuchFieldException, IllegalAccessException
  {
    final String f = fieldName;
    Field fld = cl.getField(f);
    
    VM_Address addr = (VM_Address) fld.get(null);
    p("static const int " + cPrefix + fieldName
      + "               = "
      + addr.toInt() + ";\n");
  }
}
