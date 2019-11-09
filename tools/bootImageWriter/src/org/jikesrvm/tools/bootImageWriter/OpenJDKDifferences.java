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
package org.jikesrvm.tools.bootImageWriter;

import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_ALLOCATED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_PRESENT;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.ADDRESSES;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.DETAILED;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.SUMMARY;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.vmmagic.unboxed.Address;

/**
 * Provides functionality to handle differences between JDK 6 and JDK 8 during boot image writing.
 * <p>
 * The main pain point right now is {@link HashMap}. The internal structure changed between OpenJDK 6 and OpenJDk 8
 * but both implementations use a field named table. This results in the field getting nulled out because the types
 * don't match. Additionally, the hash code used by the map also changed. This implies that the table array needs to
 * rebuilt for OpenJDK 6.
 */
public class OpenJDKDifferences {

  static boolean classOughtToBeSkippedFromCopying(Class<?> openJdkClass) {
    return openJdkClass.getName().startsWith("sun.nio.fs.UnixNativeDispatcher$1");
  }

  private static class TableEntryWithNewIndex {

    private Object tableEntry;
    private int newIndex;

    TableEntryWithNewIndex(Object tableEntry, int newIndex) {
      this.tableEntry = tableEntry;
      this.newIndex = newIndex;
    }

  }

  static void rebuildHashMapForOpenJDK6(Object jdkObject,
      Class<?> jdkType, Address rvmFieldAddress, String rvmFieldName)
      throws IllegalAccessException, Error {
    try {
      Field tableField = getField(jdkType, "table");
      Object[] table = (Object[]) tableField.get(jdkObject);
      if (table != null) {
        OpenJDKDifferences.rearrangeTableArray(table);
      } else {
        // JDK 6 HashMap always has a table with at least 1 entry so create one if needed
        Class<?> hashMapNode = Class.forName("java.util.HashMap$Node");
        Object[] repl = (Object[]) Array.newInstance(hashMapNode, 1);
        tableField.set(jdkObject, repl);
        Address imageAddress = BootImageMap.findOrCreateEntry(repl).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
          throw new Error("Failed to populate " + rvmFieldName + " in HashMap");
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = BootImageWriter.copyToBootImage(repl, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        } else {
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        }
      }
    } catch (SecurityException e) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("Failure for object " + jdkObject + " from class " + jdkObject.getClass());
      throw new OpenJDKMappingError(e);
    } catch (NoSuchFieldException e) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("Failure for object " + jdkObject + " from class " + jdkObject.getClass());
      throw new OpenJDKMappingError(e);
    } catch (ClassNotFoundException e) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("Failure for object " + jdkObject + " from class " + jdkObject.getClass());
      throw new OpenJDKMappingError(e);
    }
  }

  private static Field getField(Class<?> jdkType, String name) {
    Field soughtField = null;
    Class<?> superClass = jdkType;
    while (superClass != null) {
      soughtField = searchAllFields(name, superClass);
      if (soughtField == null) {
        superClass = superClass.getSuperclass();
      } else {
        superClass = null;
      }
    }
    if (VM.VerifyAssertions) VM._assert(soughtField != null);
    soughtField.setAccessible(true);
    return soughtField;
  }

  private static Field searchAllFields(String name, Class<?> superClass) {
    Field[] declaredFields = superClass.getDeclaredFields();
    for (Field f : declaredFields) {
      if (name.equals(f.getName())) {
        return f;
      }
    }
    return null;
  }

  private static void rearrangeTableArray(Object[] o)
      throws ClassNotFoundException, NoSuchFieldException,
      IllegalAccessException {
    Class<?> hashMapNodeClass = Class.forName("java.util.HashMap$Node");
    Field hashField = getField(hashMapNodeClass, "hash");
    hashField.setAccessible(true);
    Field nextField = getField(hashMapNodeClass, "next");
    nextField.setAccessible(true);
    Field valueField = getField(hashMapNodeClass, "value");
    valueField.setAccessible(true);
    Field keyField = getField(hashMapNodeClass, "key");
    keyField.setAccessible(true);
    ArrayList<OpenJDKDifferences.TableEntryWithNewIndex> indexChange = new ArrayList<OpenJDKDifferences.TableEntryWithNewIndex>(o.length);
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("old array");
    for (int i = 0; i < o.length; i++) {
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: " + (o[i] == null ? "null" : o[i].toString()));
    }
    for (int i = 0; i < o.length; i++) {
      Object entryInTable = o[i];
      if (entryInTable == null) {
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: null");
      } else {
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: " + o);
        int hash = (Integer) hashField.get(entryInTable);
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: old hash" + hash);
        Object key = keyField.get(entryInTable);
        int newHash = OpenJDKDifferences.hash(key.hashCode());
        hashField.set(entryInTable, newHash);
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: new hash" + newHash);
        int newIndex = OpenJDKDifferences.jdk6IndexForHashCode(newHash, o.length);
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: new index: " + newIndex);
        OpenJDKDifferences.TableEntryWithNewIndex ic = new OpenJDKDifferences.TableEntryWithNewIndex(entryInTable, newIndex);
        indexChange.add(ic);
      }
      o[i] = null;
    }
    for (TableEntryWithNewIndex ic : indexChange) {
      Object place = o[ic.newIndex];
      if (place != null) {
        nextField.set(place, ic.tableEntry);
      } else {
        o[ic.newIndex] = ic.tableEntry;
      }
    }
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("new array");
    for (int i = 0; i < o.length; i++) {
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("o[" + i + "]: " + (o[i] == null ? "null" : o[i].toString()));
    }
  }

  // Code from OpenJDK HashMap

  private static int jdk6IndexForHashCode(int h, int l) {
    int index = h & (l - 1);
    return index;
  }

  private static int hash(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
  }

}
