/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 * database to hold field-level information
 * this is a mapping from VM_Field -> FieldDatabaseEntry
 */
final class FieldDatabase {
  private static final boolean DEBUG = false;

  private final ImmutableEntryHashMapRVM<VM_Field, FieldDatabase.FieldDatabaseEntry> db =
    new ImmutableEntryHashMapRVM<VM_Field, FieldDatabase.FieldDatabaseEntry>();

  FieldDatabaseEntry findOrCreateEntry(VM_Field f) {
    FieldDatabaseEntry e = db.get(f);
    if (e == null) {
      e = new FieldDatabaseEntry(f);
      db.put(f, e);
    }
    return e;
  }

  /**
   * return the concrete type of a field, or null if none determined
   */
  public VM_TypeReference getConcreteType(VM_Field f) {
    FieldDatabaseEntry e = db.get(f);
    if (e == null) return null;

    if (e.allMethodsAreAnalyzed()) {
      return e.getConcreteType();
    }

    return null;
  }

  // a data structure holding information about a field
  static final class FieldDatabaseEntry {
    private final ImmutableEntryHashMapRVM<VM_Method, FieldWriterInfo> summaries;
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
      summaries = new ImmutableEntryHashMapRVM<VM_Method, FieldWriterInfo>(1);

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
  static final class FieldWriterInfo {
    static final int BOTTOM = 0x1;
    static final int ANALYZED = 0x2;
    int status;
    VM_TypeReference concreteType;

    void setBottom() { status |= BOTTOM; }

    void setAnalyzed() { status |= ANALYZED; }

    boolean isBottom() { return (status & BOTTOM) != 0; }

    boolean isAnalyzed() { return (status & ANALYZED) != 0; }
  }

  // print a debug message
  private static void debug(String s) {
    if (DEBUG) VM.sysWrite(s + " \n");
  }
}
