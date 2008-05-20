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
package org.jikesrvm.tools.header_gen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.objectmodel.VM_ThinLockConstants;
import org.jikesrvm.runtime.VM_ArchEntrypoints;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.greenthreads.VM_FileSystem;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.VM_ThreadEventConstants;
import org.jikesrvm.scheduler.greenthreads.VM_ThreadIOConstants;
import org.jikesrvm.scheduler.greenthreads.VM_ThreadIOQueue;
import org.jikesrvm.scheduler.greenthreads.VM_ThreadProcessWaitQueue;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Emit a header file containing declarations required to access VM
 * data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
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
      System.exit(-1);     // we must *not* go on if the above has failed
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
      out.print(s + VM.addressAsHexString(off.toWord().toAddress()));
    }
  }

  static void pln(String s) {
    out.println(s);
  }

  static void pln(String s, Address addr) {
    out.print("const VM_Address " + s + VM.addressAsHexString(addr) + ";\n");
  }

  static void pln(String s, Offset off) {
    out.print("const VM_Offset " + s + VM.addressAsHexString(off.toWord().toAddress()) + ";\n");
  }

  static void pln() {
    out.println();
  }

  GenerateInterfaceDeclarations() {
  }

  static int bootImageDataAddress = 0;
  static int bootImageCodeAddress = 0;
  static int bootImageRMapAddress = 0;
  static String outFileName;

  public static void main(String[] args) throws Exception {

    // Process command line directives.
    //
    for (int i = 0, n = args.length; i < n; ++i) {
      if (args[i].equals("-da")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -da flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageDataAddress = Integer.decode(args[i]);
        continue;
      }
      if (args[i].equals("-ca")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -ca flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageCodeAddress = Integer.decode(args[i]);
        continue;
      }
      if (args[i].equals("-ra")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -ra flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageRMapAddress = Integer.decode(args[i]);
        continue;
      }
      if (args[i].equals("-out")) {              // output file
        if (++i == args.length) {
          System.err.println("Error: The -out flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        outFileName = args[i];
        continue;
      }
      System.err.println("Error: unrecognized command line argument: " + args[i]);
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }

    if (bootImageDataAddress == 0) {
      System.err.println("Error: Must specify boot image data load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (bootImageCodeAddress == 0) {
      System.err.println("Error: Must specify boot image code load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (bootImageRMapAddress == 0) {
      System.err.println("Error: Must specify boot image ref map load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
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
      System.err.print(" ");
      System.err.print(outFileName);
    }
    System.err.println();

    System.exit(1);
  }

  private static void emitStuff() {
    p("/*------ MACHINE GENERATED by ");
    p("org.jikesrvm.tools.header_gen.GenerateInterfaceDeclarations.java: DO NOT EDIT");
    p("------*/\n\n");

    pln("#if defined NEED_BOOT_RECORD_DECLARATIONS || defined NEED_VIRTUAL_MACHINE_DECLARATIONS");
    pln("#include <inttypes.h>");
    if (VM.BuildFor32Addr) {
      pln("#define VM_Address uint32_t");
      pln("#define VM_Offset int32_t");
      pln("#define VM_Extent uint32_t");
      pln("#define VM_Word uint32_t");
      pln("#define JavaObject_t uint32_t");
    } else {
      pln("#define VM_Address uint64_t");
      pln("#define VM_Offset int64_t");
      pln("#define VM_Extent uint64_t");
      pln("#define VM_Word uint64_t");
      pln("#define JavaObject_t uint64_t");
    }
    pln("#endif /* NEED_BOOT_RECORD_DECLARATIONS || NEED_VIRTUAL_MACHINE_DECLARATIONS */");
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

    pln("#ifdef NEED_GNU_CLASSPATH_VERSION");
    // version of the classpath library from gnu.classpath.configuration
    p("static const char*classpath_version                        = \"" +
      gnu.classpath.Configuration
          .CLASSPATH_VERSION +
                             "\";\n");
    pln("#endif /* NEED_GNU_CLASSPATH_VERSION */");
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

    pln("#ifdef NEED_MM_INTERFACE_DECLARATIONS");
    pln("#define MAXHEAPS " + org.jikesrvm.memorymanagers.mminterface.MM_Interface.getMaxHeaps());
    pln("#endif /* NEED_MM_INTERFACE_DECLARATIONS */");
    pln();

  }

  static void emitCDeclarationsForJavaType(String Cname, VM_Class cls) {

    // How many instance fields are there?
    //
    VM_Field[] allFields = cls.getDeclaredFields();
    int fieldCount = 0;
    for (VM_Field field : allFields) {
      if (!field.isStatic()) {
        fieldCount++;
      }
    }

    // Sort them in ascending offset order
    //
    SortableField[] fields = new SortableField[fieldCount];
    for (int i = 0, j = 0; i < allFields.length; i++) {
      if (!allFields[i].isStatic()) {
        fields[j++] = new SortableField(allFields[i]);
      }
    }
    Arrays.sort(fields);

    // Emit field declarations
    //
    p("struct " + Cname + " {\n");

    // Set up cursor - scalars will waste 4 bytes on 64-bit arch
    //
    boolean needsAlign = VM.BuildFor64Addr;
    int addrSize = VM.BuildFor32Addr ? 4 : 8;

    // Header Space for objects
    int startOffset = VM_ObjectModel.objectStartOffset(cls);
    Offset current = Offset.fromIntSignExtend(startOffset);
    for (int i = 0; current.sLT(fields[0].f.getOffset()); i++) {
      pln("  uint32_t    headerPadding" + i + ";\n");
      current = current.plus(4);
    }

    for (int i = 0; i < fields.length; i++) {
      VM_Field field = fields[i].f;
      VM_TypeReference t = field.getType();
      Offset offset = field.getOffset();
      String name = field.getName().toString();
      // Align by blowing 4 bytes if needed
      if (needsAlign && current.plus(4).EQ(offset)) {
        pln("  uint32_t    padding" + i + ";");
        current = current.plus(4);
      }
      if (!current.EQ(offset)) {
        System.err.printf("current (%d) and offset (%d) are neither identical nor differ by 4",
                          current.toInt(),
                          offset.toInt());
        System.exit(1);
      }
      if (t.isIntType()) {
        current = current.plus(4);
        p("   uint32_t " + name + ";\n");
      } else if (t.isLongType()) {
        current = current.plus(8);
        p("   uint64_t " + name + ";\n");
      } else if (t.isWordType()) {
        p("   VM_Address " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isWordType()) {
        p("   VM_Address * " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isIntType()) {
        p("   unsigned int * " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isReferenceType()) {
        p("   JavaObject_t " + name + ";\n");
        current = current.plus(addrSize);
      } else {
        System.err.println("Unexpected field " + name + " with type " + t);
        throw new RuntimeException("unexpected field type");
      }
    }

    p("};\n");
  }

  static void emitBootRecordDeclarations() {
    VM_Class bootRecord = VM_TypeReference.findOrCreate(org.jikesrvm.runtime.VM_BootRecord.class).resolve().asClass();
    emitCDeclarationsForJavaType("VM_BootRecord", bootRecord);
  }

  // Emit declarations for VM_BootRecord object.
  //
  static void emitBootRecordInitialization() {
    VM_Class bootRecord = VM_TypeReference.findOrCreate(org.jikesrvm.runtime.VM_BootRecord.class).resolve().asClass();
    VM_Field[] fields = bootRecord.getDeclaredFields();

    // emit function declarations
    //
    for (int i = fields.length; --i >= 0;) {
      VM_Field field = fields[i];
      if (field.isStatic()) {
        continue;
      }
      String fieldName = field.getName().toString();
      int suffixIndex = fieldName.indexOf("IP");
      if (suffixIndex > 0) {
        // java field "xxxIP" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        // e. g.,
        // extern "C" void sysFOOf();
        p("extern \"C\" int " + functionName + "();\n");
      } else if (fieldName.equals("sysJavaVM")) {
        p("extern struct JavaVM_ " + fieldName + ";\n");
      }
    }

    // emit field initializers
    //
    p("extern \"C\" void setLinkage(VM_BootRecord* br){\n");
    for (int i = fields.length; --i >= 0;) {
      VM_Field field = fields[i];
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
        p("  br->" + fieldName + " = (intptr_t)" + functionName + ";\n");
      } else if (fieldName.equals("sysJavaVM")) {
        p("  br->" + fieldName + " = (intptr_t)&" + fieldName + ";\n");
      }
    }

    p("}\n");
  }

  // Emit virtual machine class interface information.
  //
  static void emitVirtualMachineDeclarations(int bootImageDataAddress, int bootImageCodeAddress,
                                             int bootImageRMapAddress) {

    // load address for the boot image
    //
    p("static const void *bootImageDataAddress                     = (void*)0x" +
      Integer.toHexString(bootImageDataAddress) +
      ";\n");
    p("static const void *bootImageCodeAddress                     = (void *)0x" +
      Integer.toHexString(bootImageCodeAddress) +
      ";\n");
    p("static const void *bootImageRMapAddress                     = (void *)0x" +
      Integer.toHexString(bootImageRMapAddress) +
      ";\n");

    // values in VM_Constants, from VM_Configuration
    //
    p("static const int VM_Constants_STACK_SIZE_GUARD          = " +
      ArchitectureSpecific.VM_StackframeLayoutConstants
          .STACK_SIZE_GUARD +
                            ";\n");

    p("static const int VM_Constants_INVISIBLE_METHOD_ID       = " +
      ArchitectureSpecific.VM_StackframeLayoutConstants
          .INVISIBLE_METHOD_ID +
                               ";\n");
    p("static const int VM_ThinLockConstants_TL_THREAD_ID_SHIFT= " + VM_ThinLockConstants.TL_THREAD_ID_SHIFT + ";\n");
    p("static const int VM_Constants_STACKFRAME_HEADER_SIZE    = " +
      ArchitectureSpecific.VM_StackframeLayoutConstants
          .STACKFRAME_HEADER_SIZE +
                                  ";\n");
    p("static const int VM_Constants_STACKFRAME_METHOD_ID_OFFSET = " +
      ArchitectureSpecific.VM_StackframeLayoutConstants
          .STACKFRAME_METHOD_ID_OFFSET +
                                       ";\n");
    p("static const int VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET    = " +
      ArchitectureSpecific.VM_StackframeLayoutConstants
          .STACKFRAME_FRAME_POINTER_OFFSET +
                                           ";\n");
    pln("VM_Constants_STACKFRAME_SENTINEL_FP             = ",
        ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP);
    p("\n");

    // values in VM_ObjectModel
    //
    pln("VM_ObjectModel_ARRAY_LENGTH_OFFSET = ", VM_ObjectModel.getArrayLengthOffset());
    pln();

    // values in VM_Scheduler
    //
    p("static const int VM_GreenScheduler_PRIMORDIAL_PROCESSOR_ID = " + VM_GreenScheduler.PRIMORDIAL_PROCESSOR_ID + ";\n");
    p("static const int VM_Scheduler_PRIMORDIAL_THREAD_INDEX = " + VM_Scheduler.PRIMORDIAL_THREAD_INDEX + ";\n");
    p("\n");

    // values in VM_ThreadEventConstants
    //
    p("static const double VM_ThreadEventConstants_WAIT_INFINITE = " + VM_ThreadEventConstants.WAIT_INFINITE + ";\n");

    // values in VM_ThreadIOQueue
    //
    p("static const int VM_ThreadIOQueue_READ_OFFSET = " + VM_ThreadIOQueue.READ_OFFSET + ";\n");
    p("static const int VM_ThreadIOQueue_WRITE_OFFSET = " + VM_ThreadIOQueue.WRITE_OFFSET + ";\n");
    p("static const int VM_ThreadIOQueue_EXCEPT_OFFSET = " + VM_ThreadIOQueue.EXCEPT_OFFSET + ";\n");
    p("\n");

    // values in VM_ThreadIOConstants
    //
    p("static const int VM_ThreadIOConstants_FD_READY = " + VM_ThreadIOConstants.FD_READY + ";\n");
    p("static const int VM_ThreadIOConstants_FD_READY_BIT = " + VM_ThreadIOConstants.FD_READY_BIT + ";\n");
    p("static const int VM_ThreadIOConstants_FD_INVALID = " + VM_ThreadIOConstants.FD_INVALID + ";\n");
    p("static const int VM_ThreadIOConstants_FD_INVALID_BIT = " + VM_ThreadIOConstants.FD_INVALID_BIT + ";\n");
    p("static const int VM_ThreadIOConstants_FD_MASK = " + VM_ThreadIOConstants.FD_MASK + ";\n");
    p("\n");

    // values in VM_ThreadProcessWaitQueue
    //
    p("static const int VM_ThreadProcessWaitQueue_PROCESS_FINISHED = " +
      VM_ThreadProcessWaitQueue.PROCESS_FINISHED + ";\n");

    // values in VM_Runtime
    //
    p("static const int VM_Runtime_TRAP_UNKNOWN        = " + VM_Runtime.TRAP_UNKNOWN + ";\n");
    p("static const int VM_Runtime_TRAP_NULL_POINTER   = " + VM_Runtime.TRAP_NULL_POINTER + ";\n");
    p("static const int VM_Runtime_TRAP_ARRAY_BOUNDS   = " + VM_Runtime.TRAP_ARRAY_BOUNDS + ";\n");
    p("static const int VM_Runtime_TRAP_DIVIDE_BY_ZERO = " + VM_Runtime.TRAP_DIVIDE_BY_ZERO + ";\n");
    p("static const int VM_Runtime_TRAP_STACK_OVERFLOW = " + VM_Runtime.TRAP_STACK_OVERFLOW + ";\n");
    p("static const int VM_Runtime_TRAP_CHECKCAST      = " + VM_Runtime.TRAP_CHECKCAST + ";\n");
    p("static const int VM_Runtime_TRAP_REGENERATE     = " + VM_Runtime.TRAP_REGENERATE + ";\n");
    p("static const int VM_Runtime_TRAP_JNI_STACK     = " + VM_Runtime.TRAP_JNI_STACK + ";\n");
    p("static const int VM_Runtime_TRAP_MUST_IMPLEMENT = " + VM_Runtime.TRAP_MUST_IMPLEMENT + ";\n");
    p("static const int VM_Runtime_TRAP_STORE_CHECK = " + VM_Runtime.TRAP_STORE_CHECK + ";\n");
    pln();

    // values in VM_FileSystem
    //
    p("static const int VM_FileSystem_OPEN_READ                 = " + VM_FileSystem.OPEN_READ + ";\n");
    p("static const int VM_FileSystem_OPEN_WRITE                 = " + VM_FileSystem.OPEN_WRITE + ";\n");
    p("static const int VM_FileSystem_OPEN_MODIFY                 = " + VM_FileSystem.OPEN_MODIFY + ";\n");
    p("static const int VM_FileSystem_OPEN_APPEND                 = " + VM_FileSystem.OPEN_APPEND + ";\n");
    p("static const int VM_FileSystem_SEEK_SET                 = " + VM_FileSystem.SEEK_SET + ";\n");
    p("static const int VM_FileSystem_SEEK_CUR                 = " + VM_FileSystem.SEEK_CUR + ";\n");
    p("static const int VM_FileSystem_SEEK_END                 = " + VM_FileSystem.SEEK_END + ";\n");
    p("static const int VM_FileSystem_STAT_EXISTS                 = " + VM_FileSystem.STAT_EXISTS + ";\n");
    p("static const int VM_FileSystem_STAT_IS_FILE                 = " + VM_FileSystem.STAT_IS_FILE + ";\n");
    p("static const int VM_FileSystem_STAT_IS_DIRECTORY                 = " + VM_FileSystem.STAT_IS_DIRECTORY + ";\n");
    p("static const int VM_FileSystem_STAT_IS_READABLE                 = " + VM_FileSystem.STAT_IS_READABLE + ";\n");
    p("static const int VM_FileSystem_STAT_IS_WRITABLE                 = " + VM_FileSystem.STAT_IS_WRITABLE + ";\n");
    p("static const int VM_FileSystem_STAT_LAST_MODIFIED                 = " +
      VM_FileSystem
          .STAT_LAST_MODIFIED +
                              ";\n");
    p("static const int VM_FileSystem_STAT_LENGTH                 = " + VM_FileSystem.STAT_LENGTH + ";\n");

    // Value in org.mmtk.vm.Constants:
    p("static const int MMTk_Constants_BYTES_IN_PAGE            = " + org.mmtk.utility.Constants.BYTES_IN_PAGE + ";\n");

    // fields in VM_Processor
    //
    Offset offset;
    offset = VM_Entrypoints.timeSliceExpiredField.getOffset();
    pln("VM_Processor_timeSliceExpired_offset = ", offset);
    offset = VM_Entrypoints.takeYieldpointField.getOffset();
    pln("VM_Processor_takeYieldpoint_offset = ", offset);
    offset = VM_Entrypoints.activeThreadStackLimitField.getOffset();
    pln("VM_Processor_activeThreadStackLimit_offset = ", offset);
    offset = VM_Entrypoints.pthreadIDField.getOffset();
    pln("VM_Processor_pthread_id_offset = ", offset);
    offset = VM_Entrypoints.activeThreadField.getOffset();
    pln("VM_Processor_activeThread_offset = ", offset);
    offset = VM_Entrypoints.vpStatusField.getOffset();
    pln("VM_Processor_vpStatus_offset = ", offset);
    offset = VM_Entrypoints.threadIdField.getOffset();
    pln("VM_Processor_threadId_offset = ", offset);

    // fields in VM_Thread
    //
    offset = VM_Entrypoints.threadStackField.getOffset();
    pln("VM_Thread_stack_offset = ", offset);
    offset = VM_Entrypoints.stackLimitField.getOffset();
    pln("VM_Thread_stackLimit_offset = ", offset);
    offset = VM_Entrypoints.threadExceptionRegistersField.getOffset();
    pln("VM_Thread_exceptionRegisters_offset = ", offset);
    offset = VM_Entrypoints.jniEnvField.getOffset();
    pln("VM_Thread_jniEnv_offset = ", offset);

    // fields in VM_Registers
    //
    offset = VM_ArchEntrypoints.registersGPRsField.getOffset();
    pln("VM_Registers_gprs_offset = ", offset);
    offset = VM_ArchEntrypoints.registersFPRsField.getOffset();
    pln("VM_Registers_fprs_offset = ", offset);
    offset = VM_ArchEntrypoints.registersIPField.getOffset();
    pln("VM_Registers_ip_offset = ", offset);

    offset = VM_ArchEntrypoints.registersInUseField.getOffset();
    pln("VM_Registers_inuse_offset = ", offset);

    // fields in VM_JNIEnvironment
    offset = VM_Entrypoints.JNIExternalFunctionsField.getOffset();
    pln("VM_JNIEnvironment_JNIExternalFunctions_offset = ", offset);

    // fields in java.net.InetAddress
    //
    offset = VM_Entrypoints.inetAddressAddressField.getOffset();
    pln("java_net_InetAddress_address_offset = ", offset);
    offset = VM_Entrypoints.inetAddressFamilyField.getOffset();
    pln("java_net_InetAddress_family_offset = ", offset);

    // fields in java.net.SocketImpl
    //
    offset = VM_Entrypoints.socketImplAddressField.getOffset();
    pln("java_net_SocketImpl_address_offset = ", offset);
    offset = VM_Entrypoints.socketImplPortField.getOffset();
    pln("java_net_SocketImpl_port_offset = ", offset);

    arch.emitArchVirtualMachineDeclarations();
  }

  // Codes for exit(3).
  static void emitExitStatusCodes() {
    pln("/* Automatically generated from the exitStatus declarations in VM_ExitStatus.java */");
    pln("const int EXIT_STATUS_EXECUTABLE_NOT_FOUND                 = " + VM.EXIT_STATUS_EXECUTABLE_NOT_FOUND + ";");
    pln("const int EXIT_STATUS_COULD_NOT_EXECUTE                    = " + VM.EXIT_STATUS_COULD_NOT_EXECUTE + ";");
    pln("const int EXIT_STATUS_MISC_TROUBLE                         = " + VM.EXIT_STATUS_MISC_TROUBLE + ";");
    pln("const int EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR    = " +
        VM.EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR + ";");
    pln("const int EXIT_STATUS_SYSCALL_TROUBLE                      = " + VM.EXIT_STATUS_SYSCALL_TROUBLE + ";");
    pln("const int EXIT_STATUS_TIMER_TROUBLE                        = " + VM.EXIT_STATUS_TIMER_TROUBLE + ";");
    pln("const int EXIT_STATUS_UNSUPPORTED_INTERNAL_OP              = " + VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP + ";");
    pln("const int EXIT_STATUS_UNEXPECTED_CALL_TO_SYS               = " + VM.EXIT_STATUS_UNEXPECTED_CALL_TO_SYS + ";");
    pln("const int EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION        = " +
        VM.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION + ";");
    pln("const int EXIT_STATUS_BOGUS_COMMAND_LINE_ARG               = " + VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG + ";");
    pln("const int EXIT_STATUS_JNI_TROUBLE                          = " + VM.EXIT_STATUS_JNI_TROUBLE + ";");
    pln("const int EXIT_STATUS_BAD_WORKING_DIR                      = " + VM.EXIT_STATUS_BAD_WORKING_DIR + ";");
  }

  // Emit assembler constants.
  //
  static void emitAssemblerDeclarations() {
    arch.emitArchAssemblerDeclarations();
  }
}



