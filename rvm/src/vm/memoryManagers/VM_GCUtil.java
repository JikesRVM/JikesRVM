/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Common utility functions used by various garbage collectors
 * @author Stephen Smith
 */  
public class VM_GCUtil
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  private final static boolean TRACE = false;

  static int bootImageStart;
  static int bootImageEnd;
  static int heapStart;
  static int heapEnd;
  static int largeStart;
  static int largeEnd;

  static int minBootRef;
  static int maxBootRef;
  static int minHeapRef;
  static int maxHeapRef;
  static int minLargeRef;
  static int maxLargeRef;

  static int tibForArrayType;
  static int tibForClassType;
  static int tibForPrimitiveType;

  static void boot() {
    bootImageStart = VM_BootRecord.the_boot_record.startAddress;
    bootImageEnd = VM_BootRecord.the_boot_record.freeAddress;
    heapStart = bootImageEnd;
    heapEnd = VM_BootRecord.the_boot_record.endAddress;
    largeStart = VM_BootRecord.the_boot_record.largeStart;
    largeEnd = largeStart + VM_BootRecord.the_boot_record.largeSize;


    minBootRef = bootImageStart + ARRAY_HEADER_SIZE;    // start + 12
    maxBootRef = bootImageEnd - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;  // end + 4
    minHeapRef = heapStart + ARRAY_HEADER_SIZE;    // start + 12
    maxHeapRef = heapEnd - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;  // end + 4
    minLargeRef = largeStart + ARRAY_HEADER_SIZE;    // start + 12
    maxLargeRef = largeEnd - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;  // end + 4

    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType( 10 );
    tibForArrayType = VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(t) + OBJECT_TIB_OFFSET);
    tibForPrimitiveType = VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(VM_Type.IntType) + OBJECT_TIB_OFFSET);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(t) + OBJECT_TIB_OFFSET);
    if (TRACE) {
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForArrayType =", tibForArrayType);
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForPrimitiveType =", tibForPrimitiveType);
      VM_Scheduler.traceHex("VM_GCUtil.boot","tibForClassType =", tibForClassType);
    }
  }

  static boolean
  referenceInVM ( int ref ) {
    if ( (ref >= minBootRef && ref <= maxHeapRef) ||
	 (ref >= minLargeRef && ref <= maxLargeRef) )
      return true;
    else
      return false;
  }

  static boolean
  referenceInBootImage ( int ref ) {
    if ( (ref >= minBootRef) && (ref <= maxBootRef) )
      return true;
    else
      return false;
  }

  static boolean
  referenceInHeap ( int ref ) {
    if ( (ref >= minHeapRef) && (ref <= maxHeapRef) )
      return true;
    else
      return false;
  }

  static boolean
  addressInVM ( int address ) {
    if ( (address >= bootImageStart && address < heapEnd) ||
	 (address >= largeStart && address < largeEnd) )
      return true;
    else
      return false;
  }

  static boolean
  addressInBootImage ( int address ) {
    if ( (address >= bootImageStart) && (address < bootImageEnd) )
      return true;
    else
      return false;
  }

  static boolean
  addressInHeap ( int address ) {
    if ( (address >= heapStart) && (address < heapEnd) )
      return true;
    else
      return false;
  }

  // check if an address appears to point to a an instance of VM_Type
  static boolean
  validType ( int typeAddress ) {
    if ( ! (referenceInBootImage(typeAddress) || referenceInHeap(typeAddress)) )
      return false;  // type address is outside of heap

    // check if types tib is one of three possible values
    int typeTib = VM_Magic.getMemoryWord(typeAddress + OBJECT_TIB_OFFSET);
    if ( (typeTib == tibForClassType) || (typeTib == tibForArrayType) ||
	 (typeTib == tibForPrimitiveType) )
      return true;
    else
      return false;
  }

  /**
   * dump all threads & their stacks starting at the frame identified
   * by the threads saved contextRegisters (ip & fp fields).
   */
  static void 
  dumpAllThreadStacks() {
    int        i, ip, fp;
    VM_Thread  t;
    VM_Scheduler.trace("\ndumpAllThreadStacks","dumping stacks for all threads");
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      if ( t == null ) continue;
      VM.sysWrite("\n Thread "); t.dump(); VM.sysWrite("\n");
      // start stack dump using fp & ip in threads saved context registers
      ip = t.contextRegisters.getInnermostInstructionAddress();
      fp = t.contextRegisters.getInnermostFramePointer();
      VM_Scheduler.dumpStack(ip,fp);
    }
    VM.sysWrite("\ndumpAllThreadStacks: end of thread stacks\n\n");
  }  // dumpAllThreadStacks

  /**
   * check if a ref, its tib pointer & type pointer are all in the heap
   */
  static boolean
  validRef ( int ref ) {
    if (ref == VM_NULL) return true;
    if ( ! referenceInVM(ref)) {
      VM.sysWrite("validRef: REF outside heap, ref = "); VM.sysWriteHex(ref); VM.sysWrite("\n");
      return false;
    }
    int tib = VM_Magic.getMemoryWord(ref + OBJECT_TIB_OFFSET);
    if ( ! referenceInVM(tib)) {
      VM.sysWrite("validRef: TIB outside heap, ref = "); VM.sysWriteHex(ref);
      VM.sysWrite(" tib = ");VM.sysWriteHex(tib); VM.sysWrite("\n");
      return false;
    }
    int type = VM_Magic.getMemoryWord(tib);
    if ( ! validType(type)) {
      VM.sysWrite("validRef: invalid TYPE, ref = "); VM.sysWriteHex(ref);
      VM.sysWrite(" tib = ");VM.sysWriteHex(tib);
      VM.sysWrite(" type = ");VM.sysWriteHex(type); VM.sysWrite("\n");
      return false;
    }
    return true;
  }  // validRef

   public static void
   dumpRef ( int ref ) {
     VM.sysWrite("REF=");
     if (ref==VM_NULL) {
       VM.sysWrite("NULL\n");
       return;
     }
     VM.sysWriteHex(ref);
     if ( !referenceInVM(ref) ) {
       VM.sysWrite(" (REF OUTSIDE OF HEAP)\n");
       return;
     }
     VM.sysWrite(" TIB=");
     int tib = VM_Magic.getMemoryWord(ref + OBJECT_TIB_OFFSET);
     VM.sysWriteHex(tib);
     VM.sysWrite(" STATUS=");
     VM.sysWriteHex(VM_Magic.getMemoryWord(ref + OBJECT_STATUS_OFFSET));
     if ( ! (referenceInBootImage(tib) || referenceInHeap(tib)) ) {
       VM.sysWrite(" (INVALID TIB: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
     int itype = VM_Magic.objectAsAddress(type);
     VM.sysWrite(" TYPE=");
     VM.sysWriteHex(itype);
     if ( ! validType(itype) ) {
       VM.sysWrite(" (INVALID TYPE: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM.sysWrite(" CLASS=");
     VM.sysWrite(type.getDescriptor());
     VM.sysWrite("\n");
   }

  static void
  dumpMemoryWords( int start, int count ) {
    int end = start + count * WORDSIZE;
    VM.sysWrite("---- Dumping memory from ");
    VM.sysWriteHex(start);
    VM.sysWrite(" to ");
    VM.sysWriteHex(end);
    VM.sysWrite(" ----\n");
    for ( int loc = start; loc <= end; loc+=WORDSIZE ) {
      VM.sysWriteHex(loc);
      VM.sysWrite(" ");
      int value = VM_Magic.getMemoryWord(loc);
      VM.sysWriteHex(value);
      VM.sysWrite("\n");
    }
    VM.sysWrite("----\n");
  }

  public static void printclass (int ref) {
    if ( validRef(ref) ) {
      VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
      if ( validRef( VM_Magic.objectAsAddress(type) ) )
	VM.sysWrite(type.getDescriptor());
    }
  }
  
}   // VM_GCUtil
