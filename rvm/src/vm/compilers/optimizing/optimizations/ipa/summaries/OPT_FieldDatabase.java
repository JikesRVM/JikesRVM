/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * database to hold field-level information
 * this is a mapping from VM_Field -> FieldDatabaseEntry
 *
 * @author Stephen Fink
 */
final class OPT_FieldDatabase extends java.util.HashMap {
  final static private boolean DEBUG = false;

  OPT_FieldDatabase() { }

  FieldDatabaseEntry findOrCreateEntry(VM_Field f) {
    FieldDatabaseEntry e = (FieldDatabaseEntry)get(f);
    if (e == null) {
      e = new FieldDatabaseEntry(f);
      put(f,e);
    }
    return e;
  }

  /** 
   * return the concrete type of a field, or null if none determined
   */
  public VM_TypeReference getConcreteType(VM_Field f) {
    FieldDatabaseEntry e = (FieldDatabaseEntry)get(f);
    if (e == null) return null;
    
    if (e.allMethodsAreAnalyzed()) {
      return e.getConcreteType();
    }
      
    return null;
  }

  // a data structure holding information about a field
  final class FieldDatabaseEntry {
    private java.util.HashMap summaries;        // VM_Method -> FieldWriterInfo
    boolean cachedAllAnalyzed;  // have we already determined all methods are analyzed?
    VM_TypeReference cachedConcreteType;        // cache a copy of the concrete type already determined for this field
    
    FieldWriterInfo findMethodInfo(VM_Method m) { 
      return (FieldWriterInfo)summaries.get(m);
    }

    // are all methods that may write this field analyzed already?
    boolean allMethodsAreAnalyzed() {
      
      if (cachedAllAnalyzed) return true;
      for (java.util.Iterator i = summaries.values().iterator(); i.hasNext(); ) {
        FieldWriterInfo info = (FieldWriterInfo)i.next();
        if (!info.isAnalyzed()) return false;
      }
      cachedAllAnalyzed = true;
      return true;
    }
    
    // return the concrete type of the field; null if no consistent
    // concrete type has yet been determined.
    VM_TypeReference getConcreteType() {
      if (cachedConcreteType != null) return cachedConcreteType;
      VM_TypeReference result = null;
      for (java.util.Iterator i = summaries.values().iterator(); i.hasNext(); ) {
        FieldWriterInfo info = (FieldWriterInfo)i.next();
        if (!info.isAnalyzed()) return null;
        if (info.isBottom()) return null;
        VM_TypeReference t = info.concreteType;
        if (result != null) {
          // make sure that all methods set the same concrete type.
          if (result != t) return null;
        } else {
          result = info.concreteType;
        }
      }
      // found a concrete type.  cache it and return it.
      cachedConcreteType = result;
      return result;
    }

    // create a new FieldDatabaseEntry, with a FieldWriterInfo
    // for each method that may write this field
    FieldDatabaseEntry(VM_Field f) {
      if (VM.VerifyAssertions) VM._assert(f.isPrivate());

      VM_Class klass = f.getDeclaringClass();
      summaries = new java.util.HashMap(1);
       
      // walk thru each method of the declaring class.  
      // If a method m may write to f, then create a FieldWriterInfo for m
      VM_Method[] declaredMethods = klass.getDeclaredMethods();
      for (int i=0; i<declaredMethods.length; i++) {
        VM_Method m = declaredMethods[i];
        if (m.mayWrite(f)) {
          FieldWriterInfo info = new FieldWriterInfo();
          if (DEBUG) debug("New summary METHOD " + m + " FIELD " + f + " INFO " + info);
          summaries.put(m, info);
        }
      }
    }
  } // class FieldDatabaseEntry

  // a data structure holding information about a particular <method,field>
  // combination, where the method may write the field
  final class FieldWriterInfo {
    final static int BOTTOM   = 0x1;
    final static int ANALYZED = 0x2;
    int status;
    VM_TypeReference concreteType;

    void setBottom()                    { status |= BOTTOM;   }
    void setAnalyzed()          { status |= ANALYZED; }
      
    boolean isBottom()          { return (status & BOTTOM)   != 0; }
    boolean isAnalyzed()                { return (status & ANALYZED) != 0; }
  }

  // print a debug message
  private static void debug(String s) {
    if (DEBUG) VM.sysWrite(s + " \n");
  }
}
