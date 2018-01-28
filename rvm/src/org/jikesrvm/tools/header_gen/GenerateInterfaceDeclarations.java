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
package org.jikesrvm.tools.header_gen;

import static org.jikesrvm.objectmodel.ThinLockConstants.TL_THREAD_ID_SHIFT;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_BAD_WORKING_DIR;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_COULD_NOT_EXECUTE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_EXECUTABLE_NOT_FOUND;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_JNI_TROUBLE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_MISC_TROUBLE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_SYSCALL_TROUBLE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_TIMER_TROUBLE;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_UNEXPECTED_CALL_TO_SYS;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP;
import static org.jikesrvm.util.Services.unboxedValueString;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import org.jikesrvm.HeapLayoutConstants;
import org.jikesrvm.VM;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.Services;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Emit a header file containing declarations required to access VM
 * data structures from C.
 */
public class GenerateInterfaceDeclarations {

  static PrintStream out;
  static final GenArch arch;

  static {
    GenArch tmp = null;
    try {
      tmp =
          (GenArch) Class.forName(VM.BuildForIA32 ? "org.jikesrvm.tools.header_gen.GenArch_ia32" : "org.jikesrvm.tools.header_gen.GenArch_ppc").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(EXIT_STATUS_MISC_TROUBLE);     // we must *not* go on if the above has failed
    }
    arch = tmp;
  }

  static void p(String s) {
    out.print(s);
  }

  static void p(String s, Offset off) {
    if (VM.BuildFor64Addr) {
      out.print(s + off.toLong());
    } else {
      out.print(s + Services.addressAsHexString(off.toWord().toAddress()));
    }
  }

  static void pln(String s) {
    out.println(s);
  }

  static void pln(String s, int i) {
    out.println("#define " + s + " 0x" + Integer.toHexString(i));
  }

  static void pln(String s, Address addr) {
    out.println("#define " + s + " ((Address)" + Services.addressAsHexString(addr) + ")");
  }

  static void pln(String s, Offset off) {
    out.println("#define " + s + " ((Offset)" + Services.addressAsHexString(off.toWord().toAddress()) + ")");
  }

  static void pln() {
    out.println();
  }

  GenerateInterfaceDeclarations() {
  }

  protected static final long bootImageDataAddress = HeapLayoutConstants.BOOT_IMAGE_DATA_START.toLong();
  protected static final long bootImageCodeAddress = HeapLayoutConstants.BOOT_IMAGE_CODE_START.toLong();
  protected static final long bootImageRMapAddress = HeapLayoutConstants.BOOT_IMAGE_RMAP_START.toLong();
  static String outFileName;

  public static void main(String[] args) throws Exception {

    // Process command line directives.
    //
    for (int i = 0, n = args.length; i < n; ++i) {
      if (args[i].equals("-out")) {              // output file
        if (++i == args.length) {
          System.err.println("Error: The -out flag requires an argument");
          System.exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        outFileName = args[i];
        continue;
      }
      System.err.println("Error: unrecognized command line argument: " + args[i]);
      System.exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }

    if (outFileName == null) {
      out = System.out;
    } else {
      try {
        // We'll let an unhandled exception throw an I/O error for us.
        out = new PrintStream(new FileOutputStream(outFileName));
      } catch (IOException e) {
        reportTrouble("Caught an exception while opening" + outFileName + " for writing: " + e.toString());
      }
    }

    VM.initForTool();

    emitStuff();
    if (out.checkError()) {
      reportTrouble("an output error happened");
    }
    //    try {
    out.close();              // exception thrown up.
    //    } catch (IOException e) {
    //      reportTrouble("An output error when closing the output: " + e.toString());
    //    }
    System.exit(0);
  }

  private static void reportTrouble(String msg) {
    System.err.println(
        "org.jikesrvm.tools.header_gen.GenerateInterfaceDeclarations: While we were creating InterfaceDeclarations.h, there was a problem.");
    System.err.println(msg);
    System.err.print("The build system will delete the output file");
    if (outFileName != null) {
      System.err.print(' ');
      System.err.print(outFileName);
    }
    System.err.println();

    System.exit(1);
  }

  private static void emitStuff() {
    p("/*------ MACHINE GENERATED by ");
    p("org.jikesrvm.tools.header_gen.GenerateInterfaceDeclarations.java: DO NOT EDIT");
    pln("------*/");
    pln();

    if (VM.PortableNativeSync) {
      pln("#define PORTABLE_NATIVE_SYNC 1");
      pln();
    }

    pln("#ifdef NEED_BOOT_RECORD_DECLARATIONS");
    emitBootRecordDeclarations();
    pln("#endif /* NEED_BOOT_RECORD_DECLARATIONS */");
    pln();

    pln("#ifdef NEED_BOOT_RECORD_INITIALIZATION");
    emitBootRecordInitialization();
    pln("#endif /* NEED_BOOT_RECORD_INITIALIZATION */");
    pln();

    pln("#ifdef NEED_VIRTUAL_MACHINE_DECLARATIONS");
    emitVirtualMachineDeclarations(bootImageDataAddress, bootImageCodeAddress, bootImageRMapAddress);
    pln("#endif /* NEED_VIRTUAL_MACHINE_DECLARATIONS */");
    pln();

    pln("#ifdef NEED_EXIT_STATUS_CODES");
    emitExitStatusCodes();
    pln("#endif /* NEED_EXIT_STATUS_CODES */");
    pln();

    pln("#ifdef NEED_ASSEMBLER_DECLARATIONS");
    emitAssemblerDeclarations();
    pln("#endif /* NEED_ASSEMBLER_DECLARATIONS */");

    pln("#ifdef NEED_MEMORY_MANAGER_DECLARATIONS");
    pln("#define MAXHEAPS " + org.jikesrvm.mm.mminterface.MemoryManager.getMaxHeaps());
    pln("#endif /* NEED_MEMORY_MANAGER_DECLARATIONS */");
    pln();

  }

  static void emitCDeclarationsForJavaType(String Cname, RVMClass cls) {

    // How many instance fields are there?
    //
    RVMField[] allFields = cls.getDeclaredFields();
    int fieldCount = 0;
    for (RVMField field : allFields) {
      if (!field.isStatic()) {
        fieldCount++;
      }
    }

    RVMField[] fields = new RVMField[fieldCount];
    for (int i = 0, j = 0; i < allFields.length; i++) {
      if (!allFields[i].isStatic()) {
        fields[j++] = allFields[i];
      }
    }
    Arrays.sort(fields, new AscendingOffsetComparator());

    // Emit field declarations
    //
    pln("struct " + Cname + " {");

    // Set up cursor - scalars will waste 4 bytes on 64-bit arch
    //
    boolean needsAlign = VM.BuildFor64Addr;
    int addrSize = VM.BuildFor32Addr ? 4 : 8;

    // Header Space for objects
    int startOffset = ObjectModel.objectStartOffset(cls);
    Offset current = Offset.fromIntSignExtend(startOffset);
    for (int i = 0; current.sLT(fields[0].getOffset()); i++) {
      pln("  uint32_t    headerPadding" + i + ";");
      current = current.plus(4);
    }

    for (int i = 0; i < fields.length; i++) {
      RVMField field = fields[i];
      TypeReference t = field.getType();
      Offset offset = field.getOffset();
      String name = field.getName().toString();
      // Align by blowing 4 bytes if needed
      if (needsAlign && current.plus(4).EQ(offset)) {
        pln("  uint32_t    padding" + i + ";");
        current = current.plus(4);
      }
      if (!current.EQ(offset)) {
        System.err.printf("current (%s) and offset (%s) are neither identical nor differ by 4",
                          unboxedValueString(current),
                          unboxedValueString(offset));
        System.exit(1);
      }
      if (t.isIntType()) {
        current = current.plus(4);
        pln("   uint32_t " + name + ";");
      } else if (t.isLongType()) {
        current = current.plus(8);
        pln("   uint64_t " + name + ";");
      } else if (t.isWordLikeType()) {
        pln("   Address " + name + ";");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isWordLikeType()) {
        pln("   Address * " + name + ";");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isIntType()) {
        pln("   unsigned int * " + name + ";");
        current = current.plus(addrSize);
      } else if (t.isReferenceType()) {
        pln("   Address " + name + ";");
        current = current.plus(addrSize);
      } else {
        System.err.println("Unexpected field " + name + " with type " + t);
        throw new RuntimeException("unexpected field type");
      }
    }

    pln("};");
  }

  static void emitBootRecordDeclarations() {
    RVMClass bootRecord = TypeReference.findOrCreate(org.jikesrvm.runtime.BootRecord.class).resolve().asClass();
    emitCDeclarationsForJavaType("BootRecord", bootRecord);
  }

  // Emit declarations for BootRecord object.
  //
  static void emitBootRecordInitialization() {
    RVMClass bootRecord = TypeReference.findOrCreate(org.jikesrvm.runtime.BootRecord.class).resolve().asClass();
    RVMField[] fields = bootRecord.getDeclaredFields();

    // emit field initializers
    //
    pln("static void setLinkage(struct BootRecord* br){");
    for (int i = fields.length; --i >= 0;) {
      RVMField field = fields[i];
      if (field.isStatic()) {
        continue;
      }

      String fieldName = field.getName().toString();
      if (fieldName.indexOf("gcspy") > -1 && !VM.BuildWithGCSpy) {
        continue;  // ugh.  NOTE: ugly hack to side-step unconditional inclusion of GCSpy stuff
      }
      int suffixIndex = fieldName.indexOf("IP");
      if (suffixIndex > 0) {
        // java field "xxxIP" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        // e. g.,
        //sysFOOIP = (int) sysFOO;
        pln("  br->" + fieldName + " = (Address)" + functionName + ";");
      } else if (fieldName.equals("sysJavaVM")) {
        pln("  br->" + fieldName + " = (Address)&" + fieldName + ";");
      }
    }

    pln("}");
  }

  // Emit virtual machine class interface information.
  //
  static void emitVirtualMachineDeclarations(long bootImageDataAddress, long bootImageCodeAddress,
                                             long bootImageRMapAddress) {

    // load address for the boot image
    //
    pln("bootImageDataAddress", Address.fromLong(bootImageDataAddress));
    pln("bootImageCodeAddress", Address.fromLong(bootImageCodeAddress));
    pln("bootImageRMapAddress", Address.fromLong(bootImageRMapAddress));

    // values in Constants, from Configuration
    //
    pln("Constants_STACK_SIZE_GUARD", StackFrameLayout.getStackSizeGuard());
    pln("Constants_INVISIBLE_METHOD_ID", StackFrameLayout.getInvisibleMethodID());
    pln("Constants_STACKFRAME_HEADER_SIZE", StackFrameLayout.getStackFrameHeaderSize());
    pln("Constants_STACKFRAME_METHOD_ID_OFFSET", StackFrameLayout.getStackFrameMethodIDOffset());
    pln("Constants_STACKFRAME_FRAME_POINTER_OFFSET", StackFrameLayout.getStackFramePointerOffset());
    pln("Constants_STACKFRAME_SENTINEL_FP", StackFrameLayout.getStackFrameSentinelFP());

    pln("ThinLockConstants_TL_THREAD_ID_SHIFT", TL_THREAD_ID_SHIFT);

    // values in RuntimeEntrypoints
    //
    pln("Runtime_TRAP_UNKNOWN", RuntimeEntrypoints.TRAP_UNKNOWN);
    pln("Runtime_TRAP_NULL_POINTER", RuntimeEntrypoints.TRAP_NULL_POINTER);
    pln("Runtime_TRAP_ARRAY_BOUNDS", RuntimeEntrypoints.TRAP_ARRAY_BOUNDS);
    pln("Runtime_TRAP_DIVIDE_BY_ZERO", RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO);
    pln("Runtime_TRAP_STACK_OVERFLOW", RuntimeEntrypoints.TRAP_STACK_OVERFLOW);
    pln("Runtime_TRAP_CHECKCAST", RuntimeEntrypoints.TRAP_CHECKCAST);
    pln("Runtime_TRAP_REGENERATE", RuntimeEntrypoints.TRAP_REGENERATE);
    pln("Runtime_TRAP_JNI_STACK", RuntimeEntrypoints.TRAP_JNI_STACK);
    pln("Runtime_TRAP_MUST_IMPLEMENT", RuntimeEntrypoints.TRAP_MUST_IMPLEMENT);
    pln("Runtime_TRAP_STORE_CHECK", RuntimeEntrypoints.TRAP_STORE_CHECK);
    pln("Runtime_TRAP_UNREACHABLE_BYTECODE", RuntimeEntrypoints.TRAP_UNREACHABLE_BYTECODE);
    pln();

    // fields in RVMThread
    //
    Offset offset = Entrypoints.threadStackField.getOffset();
    pln("RVMThread_stack_offset", offset);
    offset = Entrypoints.stackLimitField.getOffset();
    pln("RVMThread_stackLimit_offset", offset);
    offset = Entrypoints.threadExceptionRegistersField.getOffset();
    pln("RVMThread_exceptionRegisters_offset", offset);
    offset = Entrypoints.jniEnvField.getOffset();
    pln("RVMThread_jniEnv_offset", offset);
    offset = Entrypoints.execStatusField.getOffset();
    pln("RVMThread_execStatus_offset", offset);
    // constants in RVMThread
    pln("RVMThread_TERMINATED",  RVMThread.TERMINATED);
    // fields in Registers
    //
    offset = ArchEntrypoints.registersGPRsField.getOffset();
    pln("Registers_gprs_offset", offset);
    offset = ArchEntrypoints.registersFPRsField.getOffset();
    pln("Registers_fprs_offset", offset);
    offset = ArchEntrypoints.registersIPField.getOffset();
    pln("Registers_ip_offset", offset);

    offset = ArchEntrypoints.registersInUseField.getOffset();
    pln("Registers_inuse_offset", offset);

    // fields in JNIEnvironment
    offset = Entrypoints.JNIExternalFunctionsField.getOffset();
    pln("JNIEnvironment_JNIExternalFunctions_offset", offset);

    arch.emitArchVirtualMachineDeclarations();
  }

  // Codes for exit(3).
  static void emitExitStatusCodes() {
    pln("/* Automatically generated from the exitStatus declarations in ExitStatus.java */");
    pln("EXIT_STATUS_EXECUTABLE_NOT_FOUND", EXIT_STATUS_EXECUTABLE_NOT_FOUND);
    pln("EXIT_STATUS_COULD_NOT_EXECUTE", EXIT_STATUS_COULD_NOT_EXECUTE);
    pln("EXIT_STATUS_MISC_TROUBLE", EXIT_STATUS_MISC_TROUBLE);
    pln("EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR", EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    pln("EXIT_STATUS_SYSCALL_TROUBLE", EXIT_STATUS_SYSCALL_TROUBLE);
    pln("EXIT_STATUS_TIMER_TROUBLE", EXIT_STATUS_TIMER_TROUBLE);
    pln("EXIT_STATUS_UNSUPPORTED_INTERNAL_OP", EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    pln("EXIT_STATUS_UNEXPECTED_CALL_TO_SYS", EXIT_STATUS_UNEXPECTED_CALL_TO_SYS);
    pln("EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION", EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
    pln("EXIT_STATUS_BOGUS_COMMAND_LINE_ARG", EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    pln("EXIT_STATUS_JNI_TROUBLE", EXIT_STATUS_JNI_TROUBLE);
    pln("EXIT_STATUS_BAD_WORKING_DIR", EXIT_STATUS_BAD_WORKING_DIR);
  }

  // Emit assembler constants.
  //
  static void emitAssemblerDeclarations() {
    arch.emitArchAssemblerDeclarations();
  }
}



