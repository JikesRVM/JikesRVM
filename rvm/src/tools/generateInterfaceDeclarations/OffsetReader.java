/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */
// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import java.lang.reflect.*;

/**
 * Call the getOffset() method in VM_NormalMethod and VM_Class fields, using
 * reflection. 
 * 
 *
 * @author Steven Augart
 * @date 11 March 2003
 */

class OffsetReader extends Shared {

  /**  Class we'll use to pull up declarations.  We keep this around so that
       we can print better error messages. */
  final private String className;

  final private FieldReader reader;
  
  public OffsetReader(Class cl) {
    reader = new FieldReader(cl);
    className = cl.getName();
  }

  /** The class VM_NormalMethod, obtained by reflection 
      via the Alternate Reality Class Loader. */
  private static Class normMethClass;
  
  /** The (instance) Method VM_NormalMethod.getOffset() */
  private static Method normMethGetOffset = null;

  /** The class VM_Field, obtained by reflection 
      via the Alternate Reality Class Loader. */
  private static Class fieldClass;

  /**  The (instance) Method VM_Field.getOffset() */
  private static Method fieldGetOffset = null;

  /**  The (instance) Method java.lang.Object.getClass() */
  private static Method objectGetClass = null;

  private static boolean initialized = false;
  
  private static synchronized void initialize() 
    throws NoSuchMethodException
  {
    if (initialized) 
      return;

    normMethClass 
      = getClassNamed("com.ibm.JikesRVM.classloader.VM_NormalMethod");
    normMethGetOffset = normMethClass.getMethod("getOffset", new Class[0]);
    
    fieldClass = getClassNamed("com.ibm.JikesRVM.classloader.VM_Field");
    fieldGetOffset = fieldClass.getMethod("getOffset", new Class[0]);
    
    /* The class java.lang.Object, obtained by reflection via the Alternate
     * Reality class loader */
    Class objectClass = getClassNamed("java.lang.Object");
    try {
      objectGetClass = objectClass.getMethod("getClass", new Class[0]);
    } catch (NoSuchMethodException e) {
      reportTrouble("The Alternate Reality java.lang.Object doesn't have"
                    + " a getClass() method; this should never happen", e);
      // Unreached
    }
    initialized = true;
  }
    


  /** Run the method cl.(field).getOffset().  
      "field"'s type is VM_NormalMethod or VM_Field.
   * */ 
  public int get(final String fieldName) 
  {
    /* Don't do the following.  What if the defn. of VM_NormalMethod were
       to change?  We wouldn't want to get the old one.

       VM_NormalMethod fldValue = (VM_NormalMethod) fld.get(null); // XXX
    */
    
    try {
      if (! initialized)
        initialize();
    } catch (NoSuchMethodException e) {
      reportTrouble("Missing a necessary getOffset() method; whoops!", e);
    }

    Integer offset = null;

    Object fldValue = reader.read(fieldName);

    Class fldValueClass = null;
    
    try {
      fldValueClass = (Class) objectGetClass.invoke(fldValue, new Object[0]);
    } catch (IllegalAccessException e) {
      reportTrouble("Alternate Reality's Object.getClass() should be a public method, but isn't", 
                    e);
      // Unreached
    } catch (InvocationTargetException e) {
      reportTrouble("Alternate Reality's Object.getClass() threw an exception", e);
      // Unreached
    }

    try {
      if (normMethClass.isAssignableFrom(fldValueClass)) {
        offset = (Integer) normMethGetOffset.invoke(fldValue, new Object[0]);
      } else if (fieldClass.isAssignableFrom(fldValueClass)) {
        offset = (Integer) fieldGetOffset.invoke(fldValue, new Object[0]);
      } else {
        reportTrouble("The object \"" + className + "." + fieldName
                      + " is neither a VM_NormalMethod nor a VM_Field");
      }
    } catch (InvocationTargetException e) {
      reportTrouble("Invoking \"" + className + "." + fieldName 
                    + "." + "getOffset()\" via reflection threw an exception",
                    e.getCause());
    } catch (IllegalAccessException e) {
      reportTrouble("Invoking \"" + className + "." + fieldName 
                    + "." + "getOffset()\" via reflection was not allowed",
                    e.getCause());
    }
    return offset.intValue();
  }
}
