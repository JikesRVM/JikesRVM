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
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.fail;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.ADDRESSES;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.DETAILED;

import java.lang.reflect.Constructor;
import java.util.BitSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.vmmagic.unboxed.Address;

public class FieldValues {

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkObject the object containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   * @param rvmFieldAddress the address that the field is being written to
   */
  static boolean copyKnownInstanceField(Object jdkObject, String rvmFieldName, TypeReference rvmFieldType, Address rvmFieldAddress)
    throws IllegalAccessException {

    // Class library independent objects
    if (jdkObject instanceof java.lang.Class)   {
      Object value = null;
      String fieldName = null;
      boolean fieldIsFinal = false;
      if (rvmFieldName.equals("type")) {
        // Looks as though we're trying to write the type for Class,
        // lets go over the common ones
        if (jdkObject == java.lang.Boolean.TYPE) {
          value = RVMType.BooleanType;
        } else if (jdkObject == java.lang.Byte.TYPE) {
          value = RVMType.ByteType;
        } else if (jdkObject == java.lang.Character.TYPE) {
          value = RVMType.CharType;
        } else if (jdkObject == java.lang.Double.TYPE) {
          value = RVMType.DoubleType;
        } else if (jdkObject == java.lang.Float.TYPE) {
          value = RVMType.FloatType;
        } else if (jdkObject == java.lang.Integer.TYPE) {
          value = RVMType.IntType;
        } else if (jdkObject == java.lang.Long.TYPE) {
          value = RVMType.LongType;
        } else if (jdkObject == java.lang.Short.TYPE) {
          value = RVMType.ShortType;
        } else if (jdkObject == java.lang.Void.TYPE) {
          value = RVMType.VoidType;
        } else {
          value = TypeReference.findOrCreate((Class<?>)jdkObject).peekType();
          if (value == null) {
            fail("Failed to populate Class.type for " + jdkObject);
          }
        }
        fieldName = "type";
        fieldIsFinal = true;
      }
      if ((fieldName != null) && (value != null)) {
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.lang.Class",
                                            fieldName);
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
          BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, true, false);
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        } else {
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        }
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
        return true;
      } else {
        // Unknown Class field or value for type
        return false;
      }
    } else if ((jdkObject instanceof java.lang.String) &&
        (rvmFieldName.equals("count")) && (rvmFieldType.isIntType())) {
      // The fields "count" and "offset" are not guaranteed to be present in
      // the String implementation in the class library (case in point: IcedTea 7).
      // We don't need to do anything for "offset" (the default value of 0 is correct)
      // but we need to ensure that "count" has the correct value.
      BootImageWriter.bootImage().setFullWord(rvmFieldAddress, ((java.lang.String) jdkObject).length());
      return true;
    }
    // Class library dependent fields
    if (BootImageWriter.classLibrary() == "harmony") {
      if ((jdkObject instanceof java.lang.String) &&
          (rvmFieldName.equals("hashCode")) &&
          (rvmFieldType.isIntType())
          ) {
        // Populate String's hashCode value
        BootImageWriter.bootImage().setFullWord(rvmFieldAddress, jdkObject.hashCode());
        return true;
      } else if (jdkObject instanceof java.util.Locale) {
        String fieldName;
        Object value;
        if (rvmFieldName.equals("countryCode")) {
          value = ((java.util.Locale)jdkObject).getCountry();
          fieldName = "countryCode";
        } else if (rvmFieldName.equals("languageCode")) {
          value = ((java.util.Locale)jdkObject).getLanguage();
          fieldName = "languageCode";
        } else if (rvmFieldName.equals("variantCode")) {
          value = ((java.util.Locale)jdkObject).getVariant();
          fieldName = "languageCode";
        } else {
          return false;
        }
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.util.Locale",
                                            fieldName);
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
          throw new Error("Failed to populate " + fieldName + " in Locale");
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        } else {
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        }
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
        return true;
      } else if ((jdkObject instanceof java.util.WeakHashMap) &&
                 (rvmFieldName.equals("referenceQueue"))) {
        Object value = new java.lang.ref.ReferenceQueue();
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.util.WeakHashMap",
                                            "referenceQueue");
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
          throw new Error("Failed to populate referenceQueue in WeakHashMap");
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        } else {
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
        }
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
        return true;
      } else if (jdkObject instanceof java.lang.ref.ReferenceQueue) {
        if (rvmFieldName.equals("firstReference")) {
          return false;
        } else {
          throw new Error("Unknown field " + rvmFieldName + " in java.lang.ref.ReferenceQueue");
        }
      } else if (jdkObject instanceof java.lang.reflect.Constructor)   {
        Constructor<?> cons = (Constructor<?>)jdkObject;
        if (rvmFieldName.equals("vmConstructor")) {
          // fill in this RVMMethod field
          String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
          RVMType type = TypeReference.findOrCreate(typeName).peekType();
          if (type == null) {
            throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
          }
          final RVMClass klass = type.asClass();
          Class<?>[] consParams = cons.getParameterTypes();
          RVMMethod constructor = null;
          loop_over_all_constructors:
          for (RVMMethod vmCons : klass.getConstructorMethods()) {
            TypeReference[] vmConsParams = vmCons.getParameterTypes();
            if (vmConsParams.length == consParams.length) {
              for (int j = 0; j < vmConsParams.length; j++) {
                if (!consParams[j].equals(vmConsParams[j].resolve().getClassForType())) {
                  continue loop_over_all_constructors;
                }
              }
              constructor = vmCons;
              break;
            }
          }
          if (constructor == null) {
            throw new Error("Failed to populate Constructor.cons for " + cons);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push("vmConstructor",
                                              "java.lang.Constructor",
                                              "cons");
          Address imageAddress = BootImageMap.findOrCreateEntry(constructor).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(constructor, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else if (rvmFieldName.equals("isAccessible")) {
          // This field is inherited accesible flag is actually part of
          // AccessibleObject
          BootImageWriter.bootImage().setByte(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
          return true;
        } else if (rvmFieldName.equals("invoker")) {
          // Bytecode reflection field, can only be installed in running VM
          BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false, false);
          return true;
        } else {
          // Unknown Constructor field
          throw new Error("Unknown field " + rvmFieldName + " in java.lang.reflect.Constructor");
        }
      } else {
        // unknown field
        return false;
      }
    } else if (BootImageWriter.classLibrary() == "classpath") {
      if ((jdkObject instanceof java.lang.String) &&
          (rvmFieldName.equals("cachedHashCode")) &&
          (rvmFieldType.isIntType())
          ) {
        // Populate String's cachedHashCode value
        BootImageWriter.bootImage().setFullWord(rvmFieldAddress, jdkObject.hashCode());
        return true;
      } else if (jdkObject instanceof java.lang.reflect.Constructor)   {
        Constructor<?> cons = (Constructor<?>)jdkObject;
        if (rvmFieldName.equals("cons")) {
          // fill in this RVMMethod field
          String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
          RVMType type = TypeReference.findOrCreate(typeName).peekType();
          if (type == null) {
            throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
          }
          final RVMClass klass = type.asClass();
          Class<?>[] consParams = cons.getParameterTypes();
          RVMMethod constructor = null;
          loop_over_all_constructors:
          for (RVMMethod vmCons : klass.getConstructorMethods()) {
            TypeReference[] vmConsParams = vmCons.getParameterTypes();
            if (vmConsParams.length == consParams.length) {
              for (int j = 0; j < vmConsParams.length; j++) {
                if (!consParams[j].equals(vmConsParams[j].resolve().getClassForType())) {
                  continue loop_over_all_constructors;
                }
              }
              constructor = vmCons;
              break;
            }
          }
          if (constructor == null) {
            throw new Error("Failed to populate Constructor.cons for " + cons);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push("VMConstructor",
                                              "java.lang.Constructor",
                                              "cons");
          Object vmcons = java.lang.reflect.JikesRVMSupport.createVMConstructor(constructor);
          Address imageAddress = BootImageMap.findOrCreateEntry(vmcons).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(vmcons, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else if (rvmFieldName.equals("flag")) {
          // This field is inherited accesible flag is actually part of
          // AccessibleObject
          BootImageWriter.bootImage().setByte(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
          return true;
        } else {
          // Unknown Constructor field
          return false;
        }
      } else if (jdkObject instanceof java.lang.ref.ReferenceQueue) {
        if (rvmFieldName.equals("lock")) {
          VM.sysWriteln("writing the lock field.");
          Object value = new org.jikesrvm.scheduler.LightMonitor();
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.lang.ref.ReferenceQueue",
                                            "lock");
          Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            throw new Error("Failed to populate lock in ReferenceQueue");
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else if (rvmFieldName.equals("first")) {
          return false;
        } else {
          throw new Error("Unknown field " + rvmFieldName + " in java.lang.ref.ReferenceQueue");
        }
      } else if (jdkObject instanceof java.util.BitSet) {
        BitSet bs = (BitSet)jdkObject;
        if (rvmFieldName.equals("bits")) {
          int max = 0; // highest bit set in set
          for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            max = i;
          }
          long[] bits = new long[(max + 63) / 64];
          for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            bits[i / 64] |= 1L << (i & 63);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push("[J", "java.util.BitSet", "bits");
          Address imageAddress = BootImageMap.findOrCreateEntry(bits).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(bits, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else {
          // Unknown BitSet field
          return false;
        }
      } else {
        // Unknown field
        return false;
      }
    } else {
      throw new Error("Unknown class library: \"" + BootImageWriter.classLibrary() + "\"");
    }
  }

}
