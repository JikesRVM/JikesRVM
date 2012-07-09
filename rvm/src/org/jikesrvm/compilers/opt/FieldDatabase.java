/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 * database to hold field-level information
 * this is a mapping from RVMField -> FieldDatabaseEntry
 */
final class FieldDatabase {
  private static final boolean DEBUG = false;

  private final ImmutableEntryHashMapRVM<RVMField, FieldDatabase.FieldDatabaseEntry> db =
    new ImmutableEntryHashMapRVM<RVMField, FieldDatabase.FieldDatabaseEntry>();

  FieldDatabaseEntry findOrCreateEntry(RVMField f) {
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
  public TypeReference getConcreteType(RVMField f) {
    FieldDatabaseEntry e = db.get(f);
    if (e == null) return null;

    if (e.allMethodsAreAnalyzed()) {
      return e.getConcreteType();
    }

    return null;
  }

  /**
   * A data structure holding information about a field.
   */
  static final class FieldDatabaseEntry {
    private final ImmutableEntryHashMapRVM<RVMMethod, FieldWriterInfo> summaries;
    /** have we already determined all methods are analyzed? */
    boolean cachedAllAnalyzed;
    /** cache a copy of the concrete type already determined for this field */
    TypeReference cachedConcreteType;

    FieldWriterInfo findMethodInfo(RVMMethod m) {
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
    TypeReference getConcreteType() {
      if (cachedConcreteType != null) return cachedConcreteType;
      TypeReference result = null;
      for (FieldWriterInfo info : summaries.values()) {
        if (!info.isAnalyzed()) return null;
        if (info.isBottom()) return null;
        TypeReference t = info.concreteType;
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
    FieldDatabaseEntry(RVMField f) {
      if (VM.VerifyAssertions) VM._assert(f.isPrivate());

      RVMClass klass = f.getDeclaringClass();
      summaries = new ImmutableEntryHashMapRVM<RVMMethod, FieldWriterInfo>(1);

      // walk thru each method of the declaring class.
      // If a method m may write to f, then create a FieldWriterInfo for m
      for (RVMMethod m : klass.getDeclaredMethods()) {
        if (m.mayWrite(f)) {
          FieldWriterInfo info = new FieldWriterInfo();
          if (DEBUG) debug("New summary METHOD " + m + " FIELD " + f + " INFO " + info);
          summaries.put(m, info);
        }
      }
    }
  } // class FieldDatabaseEntry

  /**
   * A data structure holding information about a particular
   * {@code <method,field>}  combination, where the method
   * may write the field.
   */
  static final class FieldWriterInfo {
    static final int BOTTOM = 0x1;
    static final int ANALYZED = 0x2;
    int status;
    TypeReference concreteType;

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
