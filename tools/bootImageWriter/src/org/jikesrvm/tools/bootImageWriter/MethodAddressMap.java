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

import static org.jikesrvm.HeapLayoutConstants.BOOT_IMAGE_CODE_START;
import static org.jikesrvm.HeapLayoutConstants.BOOT_IMAGE_DATA_START;
import static org.jikesrvm.HeapLayoutConstants.BOOT_IMAGE_RMAP_START;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.SUMMARY;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.objectmodel.RuntimeTable;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.util.Services;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Implements generation of a method address map.
 */
public abstract class MethodAddressMap {

  /**
   * Junk released from Statics when instantiation finishes and may
   * be needed to generate the boot image report.
   */
  private static Object staticsJunk;

  static void setStaticsJunk(Object staticsJunk) {
    MethodAddressMap.staticsJunk = staticsJunk;
  }

  /**
   * Write method address map for use with dbx debugger.
   *
   * @param fileName name of file to write the map to
   */
  static void writeAddressMap(String mapFileName) throws IOException {
    if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("writing ", mapFileName);

    // Restore previously unnecessary Statics data structures
    Statics.bootImageReportGeneration(staticsJunk);


    FileOutputStream fos = new FileOutputStream(mapFileName);
    BufferedOutputStream bos = new BufferedOutputStream(fos, 128);
    PrintStream out = new PrintStream(bos, false);

    out.println("#! /bin/bash");
    out.println("# This is a method address map, for use with the ``dbx'' debugger.");
    out.println("# To sort by \"code\" address, type \"bash <name-of-this-file>\".");
    out.println("# Bootimage data: " + Services.addressAsHexString(BOOT_IMAGE_DATA_START) +
                "..." + Services.addressAsHexString(BOOT_IMAGE_DATA_START.plus(BootImageWriter.bootImageDataSize())));
    out.println("# Bootimage code: " + Services.addressAsHexString(BOOT_IMAGE_CODE_START) +
                "..." + Services.addressAsHexString(BOOT_IMAGE_CODE_START.plus(BootImageWriter.bootImageCodeSize())));
    out.println("# Bootimage refs: " + Services.addressAsHexString(BOOT_IMAGE_RMAP_START) +
                "..." + Services.addressAsHexString(BOOT_IMAGE_RMAP_START.plus(BootImageWriter.bootImageRMapSize())));

    out.println();
    out.println("(/bin/grep 'code     0x' | /bin/sort -k 4.3,4) << EOF-EOF-EOF");
    out.println();
    out.println("JTOC Map");
    out.println("--------");
    out.println("slot  offset     category contents            details");
    out.println("----  ------     -------- --------            -------");

    String pad = "        ";

    // Numeric JTOC fields
    for (int jtocSlot = Statics.getLowestInUseSlot();
         jtocSlot < Statics.middleOfTable;
         jtocSlot++) {
      Offset jtocOff = Statics.slotAsOffset(jtocSlot);
      String category;
      String contents;
      String details;
      RVMField field = BootImageWriter.getRvmStaticField(jtocOff);
      RVMField field2 = BootImageWriter.getRvmStaticField(jtocOff.plus(4));
      boolean couldBeLongLiteral = Statics.isLongSizeLiteral(jtocSlot);
      boolean couldBeIntLiteral = Statics.isIntSizeLiteral(jtocSlot);
      if (couldBeLongLiteral && ((field == null) || (field2 == null))) {
        if ((field == null) && (field2 == null)) {
          category = "literal      ";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = Services.intAsHexString((int) (lval >> 32)) +
            Services.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L";
        } else if ((field == null) && (field2 != null)) {
          category = "literal/field";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = Services.intAsHexString((int) (lval >> 32)) +
            Services.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L / " + field2.toString();
        } else if ((field != null) && (field2 == null)) {
          category = "literal/field";
          long lval = Statics.getSlotContentsAsLong(jtocOff);
          contents = Services.intAsHexString((int) (lval >> 32)) +
            Services.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
          details  = lval + "L / " + field.toString();
        } else {
          throw new Error("Unreachable");
        }
        jtocSlot++;
      } else if (couldBeIntLiteral) {
        if (field != null) {
          category = "literal/field";
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          contents = Services.intAsHexString(ival) + pad;
          details  = Integer.toString(ival) + " / " + field.toString();
        } else {
          category = "literal      ";
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          contents = Services.intAsHexString(ival) + pad;
          details  = Integer.toString(ival);
        }
      } else {
        if (field != null) {
          category = "field        ";
          details  = field.toString();
          TypeReference type = field.getType();
          if (type.isIntLikeType()) {
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            contents = Services.intAsHexString(ival) + pad;
          } else if (type.isLongType()) {
            long lval = Statics.getSlotContentsAsLong(jtocOff);
            contents = Services.intAsHexString((int) (lval >> 32)) +
              Services.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
            jtocSlot++;
          } else if (type.isFloatType()) {
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            contents = Float.toString(Float.intBitsToFloat(ival)) + pad;
          } else if (type.isDoubleType()) {
            long lval = Statics.getSlotContentsAsLong(jtocOff);
            contents = Double.toString(Double.longBitsToDouble(lval)) + pad;
            jtocSlot++;
          } else if (type.isWordLikeType()) {
            if (VM.BuildFor32Addr) {
              int ival = Statics.getSlotContentsAsInt(jtocOff);
              contents = Services.intAsHexString(ival) + pad;
            } else {
              long lval = Statics.getSlotContentsAsLong(jtocOff);
              contents = Services.intAsHexString((int) (lval >> 32)) +
                Services.intAsHexString((int) (lval & 0xffffffffL)).substring(2);
              jtocSlot++;
            }
          } else {
            // Unknown?
            int ival = Statics.getSlotContentsAsInt(jtocOff);
            category = "<? - field>  ";
            details  = "<? - " + field.toString() + ">";
            contents = Services.intAsHexString(ival) + pad;
          }
        } else {
          // Unknown?
          int ival = Statics.getSlotContentsAsInt(jtocOff);
          category = "<?>        ";
          details  = "<?>";
          contents = Services.intAsHexString(ival) + pad;
        }
      }
      out.println((jtocSlot + "        ").substring(0,8) +
                  Services.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
    }

    // Reference JTOC fields
    for (int jtocSlot = Statics.middleOfTable,
           n = Statics.getHighestInUseSlot();
         jtocSlot <= n;
         jtocSlot += Statics.getReferenceSlotSize()) {
      Offset jtocOff = Statics.slotAsOffset(jtocSlot);
      Object obj     = BootImageMap.getObject(MethodAddressMap.getIVal(jtocOff));
      String category;
      String details;
      String contents = Services.addressAsHexString(MethodAddressMap.getReferenceAddr(jtocOff, false)) + pad;
      RVMField field = BootImageWriter.getRvmStaticField(jtocOff);
      if (Statics.isReferenceLiteral(jtocSlot)) {
        if (field != null) {
          category = "literal/field";
        } else {
          category = "literal      ";
        }
        if (obj == null) {
          details = "(null)";
        } else if (obj instanceof String) {
          details = "\"" + obj + "\"";
        } else if (obj instanceof Class) {
          details = obj.toString();;
        } else if (obj instanceof TIB) {
          category = "literal tib  ";
          RVMType type = ((TIB)obj).getType();
          details = (type == null) ? "?" : type.toString();
        } else {
          details = "object " + obj.getClass();
        }
        if (field != null) {
          details += " / " + field.toString();
        }
      } else if (field != null) {
        category = "field        ";
        details  = field.toString();
      } else if (obj instanceof TIB) {
        // TIBs confuse the statics as their backing is written into the boot image
        category = "tib          ";
        RVMType type = ((TIB)obj).getType();
        details = (type == null) ? "?" : type.toString();
      } else {
        category = "unknown      ";
        if (obj instanceof String) {
          details = "\"" + obj + "\"";
        } else if (obj instanceof Class) {
          details = obj.toString();
        } else {
          CompiledMethod m = MethodAddressMap.findMethodOfCode(obj);
          if (m != null) {
            category = "code         ";
            details = m.getMethod().toString();
          } else if (obj != null) {
            details  = "<?> - unrecognized field or literal of type " + obj.getClass();
          } else {
            details  = "<?>";
          }
        }
      }
      out.println((jtocSlot + "        ").substring(0,8) +
                  Services.addressAsHexString(jtocOff.toWord().toAddress()) + " " +
                  category + "  " + contents + "  " + details);
    }

    out.println();
    out.println("Method Map");
    out.println("----------");
    out.println("                          address             size             method");
    out.println("                          -------             ------           ------");
    out.println();
    for (int i = 0; i < CompiledMethods.numCompiledMethods(); ++i) {
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethodUnchecked(i);
      if (compiledMethod != null) {
        RVMMethod m = compiledMethod.getMethod();
        if (m != null && compiledMethod.isCompiled()) {
          CodeArray instructions = compiledMethod.getEntryCodeArray();
          Address code = BootImageMap.getImageAddress(instructions.getBacking(), true);
          out.println(".     .          code     " + Services.addressAsHexString(code) +
                      "          " + "0x" + Integer.toHexString(compiledMethod.size()) + "          "  + compiledMethod.getMethod());
        }
      }
    }

    // Extra information on the layout of objects in the boot image
    if (false) {
      out.println();
      out.println("Object Map");
      out.println("----------");
      out.println("                          address             type");
      out.println("                          -------             ------");
      out.println();

      SortedSet<BootImageMap.Entry> set = new TreeSet<BootImageMap.Entry>(new Comparator<BootImageMap.Entry>() {
        @Override
        public int compare(BootImageMap.Entry a, BootImageMap.Entry b) {
          return Integer.valueOf(a.imageAddress.toInt()).compareTo(b.imageAddress.toInt());
        }
      });
      for (Enumeration<BootImageMap.Entry> e = BootImageMap.elements(); e.hasMoreElements();) {
        BootImageMap.Entry entry = e.nextElement();
        set.add(entry);
      }
      for (Iterator<BootImageMap.Entry> i = set.iterator(); i.hasNext();) {
        BootImageMap.Entry entry = i.next();
        Address data = entry.imageAddress;
        out.println(".     .          data     " + Services.addressAsHexString(data) +
                    "          " + entry.jdkObject.getClass());
      }
    }

    out.println();
    out.println("EOF-EOF-EOF");
    out.flush();
    out.close();
  }

  /**
   * Read a reference from the JTOC
   * @param jtocOff offset in JTOC
   * @param fatalIfNotFound whether to terminate on failure
   * @return address of object or zero if not found
   */
  private static Address getReferenceAddr(Offset jtocOff, boolean fatalIfNotFound) {
    int ival = MethodAddressMap.getIVal(jtocOff);
    if (ival != 0) {
      Object jdkObject = BootImageMap.getObject(ival);
      if (jdkObject instanceof RuntimeTable) {
        jdkObject = ((RuntimeTable<?>)jdkObject).getBacking();
      }
      return BootImageMap.getImageAddress(jdkObject, fatalIfNotFound);
    } else {
      return Address.zero();
    }
  }

  /**
   * Read an integer value from the JTOC
   * @param jtocOff offset in JTOC
   * @return integer at offset
   */
  private static int getIVal(Offset jtocOff) {
    int ival;
    if (VM.BuildFor32Addr) {
      ival = Statics.getSlotContentsAsInt(jtocOff);
    } else {
      ival = (int)Statics.getSlotContentsAsLong(jtocOff); // just a cookie
    }
    return ival;
  }

  private static CompiledMethod findMethodOfCode(Object code) {
    for (int i = 0; i < CompiledMethods.numCompiledMethods(); ++i) {
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethodUnchecked(i);
      if (compiledMethod != null &&
          compiledMethod.isCompiled() &&
          compiledMethod.getEntryCodeArray() == code)
        return compiledMethod;
    }
    return null;
  }

}
