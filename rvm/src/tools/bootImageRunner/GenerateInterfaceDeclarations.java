/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

import  java.io.*;
import  java.util.*;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Emit a header file containing declarations required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 *
 * @author Derek Lieber
 * @date 20 Apr 1998
 */
class GenerateInterfaceDeclarations {

  private static int bootImageAddress = 0;


  /**
   * put your documentation comment here
   * @param args[]
   * @exception Exception
   */
  public static void main (String args[]) throws Exception {

    // Process command line directives.
    //
    for (int i = 0, n = args.length; i < n; ++i) {
      if (args[i].equals("-ia")) {              // image address
        bootImageAddress = Integer.decode(args[++i]).intValue();
        continue;
      }
      System.err.println("unrecognized command line argument: " + args[i]);
      System.exit(-1);
    }

    if (0 == bootImageAddress) {
      System.err.println("Error: Must specify boot image load address.");
      System.exit(-1);
    }

    VM.initForTool();

    System.out.print("/*------ MACHINE GENERATED: DO NOT EDIT ------*/\n\n");

    System.out.println("#ifdef NEED_BOOT_RECORD_DECLARATIONS");
    System.out.println("#include <inttypes.h>");
    if (VM.BuildFor32Addr) {
      System.out.println("#define VM_Address uint32_t");
      System.out.println("#define JavaObject_t uint32_t");
    }
    else {
      System.out.println("#define VM_Address uint64_t");
      System.out.println("#define JavaObject_t uint64_t");
    }
    emitBootRecordDeclarations();
    System.out.println("#endif /* NEED_BOOT_RECORD_DECLARATIONS */");
    System.out.println();

    System.out.println("#ifdef NEED_BOOT_RECORD_INITIALIZATION");
    emitBootRecordInitialization();
    System.out.println("#endif /* NEED_BOOT_RECORD_INITIALIZATION */");
    System.out.println();

    System.out.println("#ifdef NEED_VIRTUAL_MACHINE_DECLARATIONS");
    emitVirtualMachineDeclarations();
    System.out.println("#endif /* NEED_VIRTUAL_MACHINE_DECLARATIONS */");
    System.out.println();

    System.out.println("#ifdef NEED_ASSEMBLER_DECLARATIONS");
    emitAssemblerDeclarations();
    System.out.println("#endif /* NEED_ASSEMBLER_DECLARATIONS */");
  }

  private static class SortableField implements Comparable {
    final VM_Field f;
    final int offset;
    SortableField (VM_Field ff) { f = ff; offset = f.getOffset(); }
    public int compareTo (Object y) {
      if (y instanceof SortableField) {
	int offset2 = ((SortableField) y).offset;
	if (offset > offset2) return 1;
	if (offset < offset2) return -1;
	return 0;
      }
      return 1;
    }
  }

  static void emitCDeclarationsForJavaType (String Cname, VM_Class cls) {

    // How many instance fields are there?
    //
    VM_Field[] allFields = cls.getDeclaredFields();
    int fieldCount = 0;
    for (int i=0; i<allFields.length; i++)
      if (!allFields[i].isStatic())
	fieldCount++;

    // Sort them in ascending offset order
    //
    SortableField [] fields = new SortableField[fieldCount];
    for (int i=0, j=0; i<allFields.length; i++)
      if (!allFields[i].isStatic())
	fields[j++] = new SortableField(allFields[i]);
    Arrays.sort(fields);

    // Set up cursor - scalars will waste 4 bytes on 64-bit arch
    //
    boolean needsAlign = VM.BuildFor64Addr;
    int addrSize = VM.BuildFor32Addr ? 4 : 8;
    int current = fields[0].offset;
    if (needsAlign && ((current & 7) != 0))
	current -= 4;
    if (current >= 0) 
	System.out.println("Are scalars no longer backwards?  If so, check this code.");

    // Emit field declarations
    //
    System.out.print("struct " + Cname + " {\n");
    for (int i = 0; i<fields.length; i++) {
      VM_Field field = fields[i].f;
      VM_TypeReference t = field.getType();
      int offset = field.getOffset();
      String name = field.getName().toString();
      // Align by blowing 4 bytes if needed
      if (needsAlign && current + 4 == offset) {
	  System.out.println("  uint32_t    padding" + i + ";");
	  current += 4;
      }
      if (current != offset) 
	System.out.println("current = " + current + " and offset = " + offset + " are neither identical not differ by 4");
      if (t.isIntType()) {
	current += 4;
	System.out.print("   uint32_t " + name + ";\n");
      }
      else if (t.isLongType()) {
	current += 8;
	System.out.print("   uint64_t " + name + ";\n");
      }
      else if (t.isWordType()) {
	System.out.print("   VM_Address " + name + ";\n");
	current += addrSize;
      }
      else if (t.isArrayType() && t.getArrayElementType().isWordType()) {
	System.out.print("   VM_Address * " + name + ";\n");
	current += addrSize;
      }
      else if (t.isArrayType() && t.getArrayElementType().isIntType()) {
	System.out.print("   unsigned int * " + name + ";\n");
	current += addrSize;
      }
      else if (t.isReferenceType()) {
	System.out.print("   JavaObject_t " + name + ";\n");
	current += addrSize;
      }
      else {
	  System.err.print("Unexpected field " + name.toString() + " with type " + t + "\n");
	  throw new RuntimeException("unexpected field type");
      }
    }

    System.out.print("};\n");
  }


  static void emitBootRecordDeclarations () {
    VM_Atom className = VM_Atom.findOrCreateAsciiAtom("com/ibm/JikesRVM/VM_BootRecord");
    VM_Atom classDescriptor = className.descriptorFromClassName();
    VM_Class bootRecord = null;
    try {
      bootRecord = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classDescriptor).resolve().asClass();
    } catch (ClassNotFoundException e) {
      System.err.println("Failed to load VM_BootRecord!");
      System.exit(-1);
    }
    emitCDeclarationsForJavaType("VM_BootRecord", bootRecord);
  }




  // Emit declarations for VM_BootRecord object.
  //
  static void emitBootRecordInitialization() {
    VM_Atom className = VM_Atom.findOrCreateAsciiAtom("com/ibm/JikesRVM/VM_BootRecord");
    VM_Atom classDescriptor = className.descriptorFromClassName();
    VM_Class bootRecord = null;
    try {
      bootRecord = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classDescriptor).resolve().asClass();
    } catch (ClassNotFoundException e) {
      System.err.println("Failed to load VM_BootRecord!");
      System.exit(-1);
    }
    VM_Field[] fields = bootRecord.getDeclaredFields();

    // emit function declarations
    //
    for (int i = fields.length; --i >= 0;) {
      VM_Field field = fields[i];
      if (field.isStatic())
        continue;
      String fieldName = field.getName().toString();
      int suffixIndex = fieldName.indexOf("IP");
      if (suffixIndex > 0) {
        // java field "xxxIP" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        // e. g.,
        // extern "C" void sysSprintf();
        System.out.print("extern \"C\" int " + functionName + "();\n");
      }
    }

    // emit field initializers
    //
    System.out.print("extern \"C\" void setLinkage(VM_BootRecord* br){\n");
    for (int i = fields.length; --i >= 0;) {
      VM_Field field = fields[i];
      if (field.isStatic())
        continue;

      String fieldName = field.getName().toString();
      int suffixIndex = fieldName.indexOf("IP");
      if (suffixIndex > 0) {
        // java field "xxxIP" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        if (VM.BuildForAix)
          // e. g.,
          // sysSprintfIP = ((AixLinkageLayout *)&sysSprintf)->ip;
          System.out.print("  br->" + fieldName + " = ((AixLinkageLayout *)&" + functionName + ")->ip;\n"); 
        else 
          // e. g.,
          //sysSprintfIP = (int) sysSprintf; 
          System.out.print("  br->" + fieldName + " = (int) " + functionName + ";\n");
      }

      suffixIndex = fieldName.indexOf("TOC");
      if (suffixIndex > 0) {
        // java field "xxxTOC" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        if (VM.BuildForAix)
          // e. g.,
          // sysTOC = ((AixLinkageLayout *)&sys)->toc;
          System.out.print("  br->" + fieldName + " = ((AixLinkageLayout *)&" + functionName + ")->toc;\n"); 
        else 
          System.out.print("  br->" + fieldName + " = 0;\n");
      }
    }

    System.out.print("}\n");
  }


  // Emit virtual machine class interface information.
  //
  static void emitVirtualMachineDeclarations () {

    // load address for the boot image
    //
    System.out.print("static const int bootImageAddress                        = 0x"
        + Integer.toHexString(bootImageAddress) + ";\n");

    // values in VM_Constants
    //
    //-#if RVM_FOR_POWERPC
    if (VM.BuildForPowerPC) {
      System.out.print("static const int VM_Constants_JTOC_POINTER               = "
          + VM_Constants.JTOC_POINTER + ";\n");
      System.out.print("static const int VM_Constants_THREAD_ID_REGISTER         = "
          + VM_Constants.THREAD_ID_REGISTER + ";\n");
      System.out.print("static const int VM_Constants_FRAME_POINTER              = "
          + VM_Constants.FRAME_POINTER + ";\n");
      System.out.print("static const int VM_Constants_PROCESSOR_REGISTER         = "
          + VM_Constants.PROCESSOR_REGISTER + ";\n");
      System.out.print("static const int VM_Constants_FIRST_VOLATILE_GPR         = "
          + VM_Constants.FIRST_VOLATILE_GPR + ";\n");
      System.out.print("static const int VM_Constants_DIVIDE_BY_ZERO_MASK        = "
          + VM_Constants.DIVIDE_BY_ZERO_MASK + ";\n");
      System.out.print("static const int VM_Constants_DIVIDE_BY_ZERO_TRAP        = "
          + VM_Constants.DIVIDE_BY_ZERO_TRAP + ";\n");
      System.out.print("static const int VM_Constants_MUST_IMPLEMENT_MASK        = "
          + VM_Constants.MUST_IMPLEMENT_MASK + ";\n");
      System.out.print("static const int VM_Constants_MUST_IMPLEMENT_TRAP        = "
          + VM_Constants.MUST_IMPLEMENT_TRAP + ";\n");
      System.out.print("static const int VM_Constants_STORE_CHECK_MASK           = "
          + VM_Constants.STORE_CHECK_MASK + ";\n");
      System.out.print("static const int VM_Constants_STORE_CHECK_TRAP           = "
          + VM_Constants.STORE_CHECK_TRAP + ";\n");
      System.out.print("static const int VM_Constants_ARRAY_INDEX_MASK           = "
          + VM_Constants.ARRAY_INDEX_MASK + ";\n");
      System.out.print("static const int VM_Constants_ARRAY_INDEX_TRAP           = "
          + VM_Constants.ARRAY_INDEX_TRAP + ";\n");
      System.out.print("static const int VM_Constants_ARRAY_INDEX_REG_MASK       = "
          + VM_Constants.ARRAY_INDEX_REG_MASK + ";\n");
      System.out.print("static const int VM_Constants_ARRAY_INDEX_REG_SHIFT      = "
          + VM_Constants.ARRAY_INDEX_REG_SHIFT + ";\n");
      System.out.print("static const int VM_Constants_CONSTANT_ARRAY_INDEX_MASK  = "
          + VM_Constants.CONSTANT_ARRAY_INDEX_MASK + ";\n");
      System.out.print("static const int VM_Constants_CONSTANT_ARRAY_INDEX_TRAP  = "
          + VM_Constants.CONSTANT_ARRAY_INDEX_TRAP + ";\n");
      System.out.print("static const int VM_Constants_CONSTANT_ARRAY_INDEX_INFO  = "
          + VM_Constants.CONSTANT_ARRAY_INDEX_INFO + ";\n");
      System.out.print("static const int VM_Constants_WRITE_BUFFER_OVERFLOW_MASK = "
          + VM_Constants.WRITE_BUFFER_OVERFLOW_MASK + ";\n");
      System.out.print("static const int VM_Constants_WRITE_BUFFER_OVERFLOW_TRAP = "
          + VM_Constants.WRITE_BUFFER_OVERFLOW_TRAP + ";\n");
      System.out.print("static const int VM_Constants_STACK_OVERFLOW_MASK        = "
          + VM_Constants.STACK_OVERFLOW_MASK + ";\n");
      System.out.print("static const int VM_Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP = "
          + VM_Constants.STACK_OVERFLOW_HAVE_FRAME_TRAP + ";\n");
      System.out.print("static const int VM_Constants_STACK_OVERFLOW_TRAP        = "
          + VM_Constants.STACK_OVERFLOW_TRAP + ";\n");
      System.out.print("static const int VM_Constants_CHECKCAST_MASK             = "
          + VM_Constants.CHECKCAST_MASK + ";\n");
      System.out.print("static const int VM_Constants_CHECKCAST_TRAP             = "
          + VM_Constants.CHECKCAST_TRAP + ";\n");
      System.out.print("static const int VM_Constants_REGENERATE_MASK            = "
          + VM_Constants.REGENERATE_MASK + ";\n");
      System.out.print("static const int VM_Constants_REGENERATE_TRAP            = "
          + VM_Constants.REGENERATE_TRAP + ";\n");
      System.out.print("static const int VM_Constants_NULLCHECK_MASK             = "
          + VM_Constants.NULLCHECK_MASK + ";\n");
      System.out.print("static const int VM_Constants_NULLCHECK_TRAP             = "
          + VM_Constants.NULLCHECK_TRAP + ";\n");
      System.out.print("static const int VM_Constants_JNI_STACK_TRAP_MASK             = "
          + VM_Constants.JNI_STACK_TRAP_MASK + ";\n");
      System.out.print("static const int VM_Constants_JNI_STACK_TRAP             = "
          + VM_Constants.JNI_STACK_TRAP + ";\n");
      System.out.print("static const int VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET = "
          + VM_Constants.STACKFRAME_NEXT_INSTRUCTION_OFFSET + ";\n");
	  System.out.print("static const int VM_Constants_STACKFRAME_ALIGNMENT = "
		  + VM_Constants.STACKFRAME_ALIGNMENT + " ;\n");
    }
    //-#endif

    //-#if RVM_FOR_IA32
    if (VM.BuildForIA32) {
      System.out.print("static const int VM_Constants_EAX                    = "
          + VM_Constants.EAX + ";\n");
      System.out.print("static const int VM_Constants_ECX                    = "
          + VM_Constants.ECX + ";\n");
      System.out.print("static const int VM_Constants_EDX                    = "
          + VM_Constants.EDX + ";\n");
      System.out.print("static const int VM_Constants_EBX                    = "
          + VM_Constants.EBX + ";\n");
      System.out.print("static const int VM_Constants_ESP                    = "
          + VM_Constants.ESP + ";\n");
      System.out.print("static const int VM_Constants_EBP                    = "
          + VM_Constants.EBP + ";\n");
      System.out.print("static const int VM_Constants_ESI                    = "
          + VM_Constants.ESI + ";\n");
      System.out.print("static const int VM_Constants_EDI                    = "
          + VM_Constants.EDI + ";\n");
      System.out.print("static const int VM_Constants_STACKFRAME_BODY_OFFSET             = "
          + VM_Constants.STACKFRAME_BODY_OFFSET + ";\n");
      System.out.print("static const int VM_Constants_STACKFRAME_RETURN_ADDRESS_OFFSET   = "
	  + VM_Constants.STACKFRAME_RETURN_ADDRESS_OFFSET   + ";\n");    
      System.out.print("static const int VM_Constants_RVM_TRAP_BASE  = "
	  + VM_Constants.RVM_TRAP_BASE   + ";\n");    
    }
    //-#endif

    System.out.print("static const int VM_Constants_STACK_SIZE_GUARD           = "
        + VM_Constants.STACK_SIZE_GUARD + ";\n");
    System.out.print("static const int VM_Constants_INVISIBLE_METHOD_ID        = "
        + VM_Constants.INVISIBLE_METHOD_ID + ";\n");
    System.out.print("static const int VM_ThinLockConstants_TL_THREAD_ID_SHIFT = "
        + VM_ThinLockConstants.TL_THREAD_ID_SHIFT + ";\n");
    System.out.print("static const int VM_Constants_STACKFRAME_HEADER_SIZE             = "
        + VM_Constants.STACKFRAME_HEADER_SIZE + ";\n");
    System.out.print("static const int VM_Constants_STACKFRAME_METHOD_ID_OFFSET        = "
        + VM_Constants.STACKFRAME_METHOD_ID_OFFSET + ";\n");
    System.out.print("static const int VM_Constants_STACKFRAME_FRAME_POINTER_OFFSET    = "
        + VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET + ";\n");
    System.out.print("static const int VM_Constants_STACKFRAME_SENTINEL_FP             = "
        + VM_Constants.STACKFRAME_SENTINEL_FP.toInt() + ";\n");
    System.out.print("\n");

    // values in VM_ObjectModel
    //
    System.out.println("static const int VM_ObjectModel_ARRAY_LENGTH_OFFSET = " + 
		       VM_ObjectModel.getArrayLengthOffset() + "\n;");

    // values in VM_Scheduler
    //
    System.out.print("static const int VM_Scheduler_PRIMORDIAL_PROCESSOR_ID = "
        + VM_Scheduler.PRIMORDIAL_PROCESSOR_ID + ";\n");
    System.out.print("static const int VM_Scheduler_PRIMORDIAL_THREAD_INDEX = "
        + VM_Scheduler.PRIMORDIAL_THREAD_INDEX + ";\n");
    System.out.print("\n");

    // values in VM_ThreadEventConstants
    //
    System.out.print("static const double VM_ThreadEventConstants_WAIT_INFINITE = " +
	VM_ThreadEventConstants.WAIT_INFINITE + ";\n");

    // values in VM_ThreadIOQueue
    //
    System.out.print("static const int VM_ThreadIOQueue_READ_OFFSET = " + 
        VM_ThreadIOQueue.READ_OFFSET + ";\n");
    System.out.print("static const int VM_ThreadIOQueue_WRITE_OFFSET = " + 
        VM_ThreadIOQueue.WRITE_OFFSET + ";\n");
    System.out.print("static const int VM_ThreadIOQueue_EXCEPT_OFFSET = " + 
        VM_ThreadIOQueue.EXCEPT_OFFSET + ";\n");
    System.out.print("\n");

    // values in VM_ThreadIOConstants
    //
    System.out.print("static const int VM_ThreadIOConstants_FD_READY = " +
	VM_ThreadIOConstants.FD_READY + ";\n");
    System.out.print("static const int VM_ThreadIOConstants_FD_READY_BIT = " +
	VM_ThreadIOConstants.FD_READY_BIT + ";\n");
    System.out.print("static const int VM_ThreadIOConstants_FD_INVALID = " +
	VM_ThreadIOConstants.FD_INVALID + ";\n");
    System.out.print("static const int VM_ThreadIOConstants_FD_INVALID_BIT = " +
	VM_ThreadIOConstants.FD_INVALID_BIT + ";\n");
    System.out.print("static const int VM_ThreadIOConstants_FD_MASK = " +
	VM_ThreadIOConstants.FD_MASK + ";\n");
    System.out.print("\n");

    // values in VM_ThreadProcessWaitQueue
    //
    System.out.print("static const int VM_ThreadProcessWaitQueue_PROCESS_FINISHED = " +
	VM_ThreadProcessWaitQueue.PROCESS_FINISHED + ";\n");

    // values in VM_Runtime
    //
    System.out.print("static const int VM_Runtime_TRAP_UNKNOWN        = "
        + VM_Runtime.TRAP_UNKNOWN + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_NULL_POINTER   = "
        + VM_Runtime.TRAP_NULL_POINTER + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_ARRAY_BOUNDS   = "
        + VM_Runtime.TRAP_ARRAY_BOUNDS + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_DIVIDE_BY_ZERO = "
        + VM_Runtime.TRAP_DIVIDE_BY_ZERO + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_STACK_OVERFLOW = "
        + VM_Runtime.TRAP_STACK_OVERFLOW + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_CHECKCAST      = "
        + VM_Runtime.TRAP_CHECKCAST + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_REGENERATE     = "
        + VM_Runtime.TRAP_REGENERATE + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_JNI_STACK     = "
        + VM_Runtime.TRAP_JNI_STACK + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_MUST_IMPLEMENT = "
        + VM_Runtime.TRAP_MUST_IMPLEMENT + ";\n");
    System.out.print("static const int VM_Runtime_TRAP_STORE_CHECK = "
        + VM_Runtime.TRAP_STORE_CHECK + ";\n");
    System.out.println();

    // values in VM_FileSystem
    //
    System.out.print("static const int VM_FileSystem_OPEN_READ                 = "
        + VM_FileSystem.OPEN_READ + ";\n");
    System.out.print("static const int VM_FileSystem_OPEN_WRITE                 = "
        + VM_FileSystem.OPEN_WRITE + ";\n");
    System.out.print("static const int VM_FileSystem_OPEN_MODIFY                 = "
        + VM_FileSystem.OPEN_MODIFY + ";\n");
    System.out.print("static const int VM_FileSystem_OPEN_APPEND                 = "
        + VM_FileSystem.OPEN_APPEND + ";\n");
    System.out.print("static const int VM_FileSystem_SEEK_SET                 = "
        + VM_FileSystem.SEEK_SET + ";\n");
    System.out.print("static const int VM_FileSystem_SEEK_CUR                 = "
        + VM_FileSystem.SEEK_CUR + ";\n");
    System.out.print("static const int VM_FileSystem_SEEK_END                 = "
        + VM_FileSystem.SEEK_END + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_EXISTS                 = "
        + VM_FileSystem.STAT_EXISTS + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_IS_FILE                 = "
        + VM_FileSystem.STAT_IS_FILE + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_IS_DIRECTORY                 = "
        + VM_FileSystem.STAT_IS_DIRECTORY + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_IS_READABLE                 = "
        + VM_FileSystem.STAT_IS_READABLE + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_IS_WRITABLE                 = "
        + VM_FileSystem.STAT_IS_WRITABLE + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_LAST_MODIFIED                 = "
        + VM_FileSystem.STAT_LAST_MODIFIED + ";\n");
    System.out.print("static const int VM_FileSystem_STAT_LENGTH                 = "
        + VM_FileSystem.STAT_LENGTH + ";\n");

    // fields in VM_Processor
    //
    int offset;
    offset = VM_Entrypoints.threadSwitchRequestedField.getOffset();
    System.out.print("static const int VM_Processor_threadSwitchRequested_offset = "
        + offset + ";\n");
    offset = VM_Entrypoints.activeThreadStackLimitField.getOffset();
    offset = VM_Entrypoints.activeThreadStackLimitField.getOffset();
    System.out.print("static const int VM_Processor_activeThreadStackLimit_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.pthreadIDField.getOffset();
    System.out.print("static const int VM_Processor_pthread_id_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.epochField.getOffset();
    System.out.print("static const int VM_Processor_epoch_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.activeThreadField.getOffset();
    System.out.print("static const int VM_Processor_activeThread_offset = "
		     + offset + ";\n");
    //-#if RVM_FOR_IA32
    offset = VM_Entrypoints.processorThreadIdField.getOffset();
    System.out.print("static const int VM_Processor_threadId_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.processorFPField.getOffset();
    System.out.print("static const int VM_Processor_framePointer_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.processorJTOCField.getOffset();
    System.out.print("static const int VM_Processor_jtoc_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.processorTrapParamField.getOffset();
    System.out.print("static const int VM_Processor_arrayIndexTrapParam_offset = "
		     + offset + ";\n");
    //-#endif

    // fields in VM_Thread
    //
    offset = VM_Entrypoints.threadStackField.getOffset();
    System.out.print("static const int VM_Thread_stack_offset = " + offset + ";\n");
    offset = VM_Entrypoints.stackLimitField.getOffset();
    System.out.print("static const int VM_Thread_stackLimit_offset = " + offset + ";\n");
    offset = VM_Entrypoints.threadHardwareExceptionRegistersField.getOffset();
    System.out.print("static const int VM_Thread_hardwareExceptionRegisters_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.jniEnvField.getOffset();
    System.out.print("static const int VM_Thread_jniEnv_offset = "
		     + offset + ";\n");

    // fields in VM_Registers
    //
    offset = VM_Entrypoints.registersGPRsField.getOffset();
    System.out.print("static const int VM_Registers_gprs_offset = " + offset + ";\n");
    offset = VM_Entrypoints.registersFPRsField.getOffset();
    System.out.print("static const int VM_Registers_fprs_offset = " + offset + ";\n");
    offset = VM_Entrypoints.registersIPField.getOffset();
    System.out.print("static const int VM_Registers_ip_offset = " + offset + ";\n");
    //-#if RVM_FOR_IA32
    offset = VM_Entrypoints.registersFPField.getOffset();
    System.out.print("static const int VM_Registers_fp_offset = " + offset + ";\n");
    //-#endif
    //-#if RVM_FOR_POWERPC
    offset = VM_Entrypoints.registersLRField.getOffset();
    System.out.print("static const int VM_Registers_lr_offset = " + offset + ";\n");
    //-#endif

    offset = VM_Entrypoints.registersInUseField.getOffset();
    System.out.print("static const int VM_Registers_inuse_offset = " + 
		     offset + ";\n");

    // fields in VM_JNIEnvironment
    offset = VM_Entrypoints.JNIEnvAddressField.getOffset();
    System.out.print("static const int VM_JNIEnvironment_JNIEnvAddress_offset = " +
		     offset + ";\n");

    // fields in java.net.InetAddress
    //
    offset = VM_Entrypoints.inetAddressAddressField.getOffset();
    System.out.print("static const int java_net_InetAddress_address_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.inetAddressFamilyField.getOffset();
    System.out.print("static const int java_net_InetAddress_family_offset = "
		     + offset + ";\n");

    // fields in java.net.SocketImpl
    //
    offset = VM_Entrypoints.socketImplAddressField.getOffset();
    System.out.print("static const int java_net_SocketImpl_address_offset = "
		     + offset + ";\n");
    offset = VM_Entrypoints.socketImplPortField.getOffset();
    System.out.print("static const int java_net_SocketImpl_port_offset = "
		     + offset + ";\n");
  }


  // Emit assembler constants.
  //
  static void emitAssemblerDeclarations () {

    //-#if RVM_FOR_POWERPC
    if (VM.BuildForPowerPC) {
      System.out.println(".set FP,"   + VM_BaselineConstants.FP);
      System.out.println(".set JTOC," + VM_BaselineConstants.JTOC);
      System.out.println(".set TI,"   + VM_BaselineConstants.TI);
      System.out.println(".set PROCESSOR_REGISTER,"    + VM_BaselineConstants.PROCESSOR_REGISTER);
      System.out.println(".set S0,"   + VM_BaselineConstants.S0);
      System.out.println(".set T0,"   + VM_BaselineConstants.T0);
      System.out.println(".set T1,"   + VM_BaselineConstants.T1);
      System.out.println(".set T2,"   + VM_BaselineConstants.T2);
      System.out.println(".set T3,"   + VM_BaselineConstants.T3);
      System.out.println(".set STACKFRAME_NEXT_INSTRUCTION_OFFSET," + VM_Constants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);

      if (!VM.BuildForAix) 
        System.out.println(".set T4,"   + (VM_BaselineConstants.T3 + 1));
    }
    //-#endif

    //-#if RVM_FOR_IA32
    if (VM.BuildForIA32) {
      System.out.print("#define JTOC %" + VM_RegisterConstants.GPR_NAMES[VM_BaselineConstants.JTOC]
          + ";\n");
      System.out.print("#define PR %"   + VM_RegisterConstants.GPR_NAMES[VM_BaselineConstants.ESI]
          + ";\n");
    }
    //-#endif

  }
}



