/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.*;
import org.jikesrvm.util.*;
import org.jikesrvm.classloader.*;

/**
 * database to hold field-level information
 * this is a mapping from VM_Field -> FieldDatabaseEntry
 *
 * @author Stephen Fink
 */
final class OPT_FieldDatabase extends VM_HashMap<VM_Field,OPT_FieldDatabase.FieldDatabaseEntry> {
  private static final boolean DEBUG = false;

  FieldDatabaseEntry findOrCreateEntry(VM_Field f) {
    FieldDatabaseEntry e = get(f);
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
    FieldDatabaseEntry e = get(f);
    if (e == null) return null;
    
    if (e.allMethodsAreAnalyzed()) {
      return e.getConcreteType();
    }
      
    return null;
  }

  // a data structure holding information about a field
  final class FieldDatabaseEntry {
    private VM_HashMap<VM_Method,FieldWriterInfo> summaries;
    boolean cachedAllAnalyzed;  // have we already determined all methods are analyzed?
    VM_TypeReference cachedConcreteType;        // cache a copy of the concrete type already determined for this field
    
    FieldWriterInfo findMethodInfo(VM_Method m) { 
      return summaries.get(m);
    }

    // are all methods that may write this field analyzed already?
    boolean allMethodsAreAnalyzed() {
      
      if (cachedAllAnalyzed) return true;
      for (FieldWriterInfo info : summaries.values()) {
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
      for (FieldWriterInfo info : summaries.values()) {
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
      summaries = new VM_HashMap<VM_Method,FieldWriterInfo>(1);
       
      // walk thru each method of the declaring class.  
      // If a method m may write to f, then create a FieldWriterInfo for m
      for (VM_Method m : klass.getDeclaredMethods()) {
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
    static final int BOTTOM   = 0x1;
    static final int ANALYZED = 0x2;
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
