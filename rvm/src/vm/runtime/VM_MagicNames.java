/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Method names that are treated specially by compiler.
 * See also: VM_Magic, various magic compilers (eg VM_MagicCompiler)
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_MagicNames {
  //-----------//
  // interface //
  //-----------//
   
  static VM_Atom sysCall0;                 
  static VM_Atom sysCall1;                 
  static VM_Atom sysCall2;
  static VM_Atom sysCall3;
  static VM_Atom sysCall4;
  static VM_Atom sysCall_L_0;                 
  static VM_Atom sysCall_L_I;                 
  static VM_Atom sysCallAD;
  static VM_Atom sysCallSigWait;
                 
  static VM_Atom invokeMain;
  static VM_Atom invokeClassInitializer;
  static VM_Atom invokeMethodReturningVoid;
  static VM_Atom invokeMethodReturningInt;
  static VM_Atom invokeMethodReturningLong;
  static VM_Atom invokeMethodReturningFloat;
  static VM_Atom invokeMethodReturningDouble;
  static VM_Atom invokeMethodReturningObject;

  static VM_Atom getFramePointer;          
  static VM_Atom getTocPointer;            
  static VM_Atom getJTOC;
  static VM_Atom getThreadId;
  static VM_Atom setThreadId;

  static VM_Atom getProcessorRegister;
  static VM_Atom setProcessorRegister;
 
  //-#if RVM_FOR_IA32
  static VM_Atom getESIAsProcessor;
  static VM_Atom setESIAsProcessor;
  //-#endif
    
  static VM_Atom getTime;
  static VM_Atom getTimeBase;

  static VM_Atom getCallerFramePointer;
  static VM_Atom setCallerFramePointer;
  static VM_Atom getCompiledMethodID;
  static VM_Atom setCompiledMethodID;
  static VM_Atom getNextInstructionAddress;
  static VM_Atom setNextInstructionAddress;
  static VM_Atom getReturnAddress;
  static VM_Atom setReturnAddress;

  static VM_Atom getByteAtOffset;
  static VM_Atom getIntAtOffset;
  static VM_Atom getObjectAtOffset;
  static VM_Atom getObjectArrayAtOffset;
  static VM_Atom getLongAtOffset;
  static VM_Atom setByteAtOffset;
  static VM_Atom setIntAtOffset;
  static VM_Atom setObjectAtOffset;
  static VM_Atom setLongAtOffset;

  static VM_Atom getMemoryWord;            
  static VM_Atom setMemoryWord;            
  static VM_Atom getMemoryAddress;
  static VM_Atom setMemoryAddress;

  static VM_Atom prepare;
  static VM_Atom attempt;
    
  static VM_Atom setThreadSwitchBit;
  static VM_Atom clearThreadSwitchBit;
    
  static VM_Atom saveThreadState;
  static VM_Atom threadSwitch;
  static VM_Atom restoreHardwareExceptionState;
  static VM_Atom returnToNewStack;
  static VM_Atom dynamicBridgeTo;
    
  static VM_Atom objectAsAddress;          
  static VM_Atom addressAsObject;          
  static VM_Atom addressAsObjectArray;          
  static VM_Atom addressAsType;
  static VM_Atom objectAsType;
  static VM_Atom addressAsByteArray;
  static VM_Atom addressAsIntArray;
  static VM_Atom objectAsByteArray;
  static VM_Atom objectAsShortArray;
  static VM_Atom objectAsIntArray;
  static VM_Atom addressAsThread;
  static VM_Atom objectAsThread;
  static VM_Atom objectAsProcessor;
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static VM_Atom addressAsBlockControl;
  static VM_Atom addressAsSizeControl;
  static VM_Atom addressAsSizeControlArray;
//-#if RVM_WITH_CONCURRENT_GC
  static VM_Atom threadAsRCCollectorThread;
//-#endif
//-#endif
  static VM_Atom threadAsCollectorThread;
  static VM_Atom addressAsRegisters;
  static VM_Atom addressAsStack;
  static VM_Atom floatAsIntBits;           
  static VM_Atom intBitsAsFloat;           
  static VM_Atom doubleAsLongBits;         
  static VM_Atom longBitsAsDouble;
             
  static VM_Atom getObjectType;            
  static VM_Atom getArrayLength;           
   
  static VM_Atom sync;
  static VM_Atom isync;
  //-#if RVM_FOR_POWERPC
  static VM_Atom dcbst;
  static VM_Atom icbi;
  //-#endif
  //-#if RVM_FOR_IA32
  static VM_Atom roundToZero;
  static VM_Atom clearFloatingPointState;
  //-#endif

  static VM_Atom pragmaNoInline;
  static VM_Atom pragmaInline;
  static VM_Atom pragmaNoOptCompile;

  // Names associated with class VM_Address
  //
  static VM_Atom addressFromInt;
  static VM_Atom addressToInt;
  static VM_Atom addressAdd;
  static VM_Atom addressSub;
  static VM_Atom addressDiff;
  static VM_Atom addressLT;
  static VM_Atom addressLE;
  static VM_Atom addressEQ;
  static VM_Atom addressNE;
  static VM_Atom addressGT;
  static VM_Atom addressGE;
  static VM_Atom addressZero;
  static VM_Atom addressMax;
  static VM_Atom addressIsZero;
  static VM_Atom addressIsMax;

   //----------------//
   // implementation //
   //----------------//
   
  static void init() {
    sysCall0                      = VM_Atom.findOrCreateAsciiAtom("sysCall0");
    sysCall1                      = VM_Atom.findOrCreateAsciiAtom("sysCall1");
    sysCall2                      = VM_Atom.findOrCreateAsciiAtom("sysCall2");
    sysCall3                      = VM_Atom.findOrCreateAsciiAtom("sysCall3");
    sysCall4                      = VM_Atom.findOrCreateAsciiAtom("sysCall4");
    sysCall_L_0                   = VM_Atom.findOrCreateAsciiAtom("sysCall_L_0");
    sysCall_L_I                   = VM_Atom.findOrCreateAsciiAtom("sysCall_L_I");
    sysCallAD  		          = VM_Atom.findOrCreateAsciiAtom("sysCallAD");
    sysCallSigWait		  = VM_Atom.findOrCreateAsciiAtom("sysCallSigWait");

    invokeMain                    = VM_Atom.findOrCreateAsciiAtom("invokeMain");
    invokeClassInitializer        = VM_Atom.findOrCreateAsciiAtom("invokeClassInitializer");
    invokeMethodReturningVoid     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningVoid");
    invokeMethodReturningInt      = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningInt");
    invokeMethodReturningLong     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningLong");
    invokeMethodReturningFloat    = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningFloat");
    invokeMethodReturningDouble   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningDouble");
    invokeMethodReturningObject   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningObject");

    getFramePointer               = VM_Atom.findOrCreateAsciiAtom("getFramePointer");
    getTocPointer                 = VM_Atom.findOrCreateAsciiAtom("getTocPointer");
    getJTOC                       = VM_Atom.findOrCreateAsciiAtom("getJTOC");
    getThreadId                   = VM_Atom.findOrCreateAsciiAtom("getThreadId");
    setThreadId                   = VM_Atom.findOrCreateAsciiAtom("setThreadId");
    getProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("getProcessorRegister");
    setProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("setProcessorRegister");
    
    //-#if RVM_FOR_IA32
    getESIAsProcessor = VM_Atom.findOrCreateAsciiAtom("getESIAsProcessor");
    setESIAsProcessor = VM_Atom.findOrCreateAsciiAtom("setESIAsProcessor");
    //-#endif

    getTime                       = VM_Atom.findOrCreateAsciiAtom("getTime");
    getTimeBase                   = VM_Atom.findOrCreateAsciiAtom("getTimeBase");

    getCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("getCallerFramePointer");
    setCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("setCallerFramePointer");
    getCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("getCompiledMethodID");
    setCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("setCompiledMethodID");
    getNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("getNextInstructionAddress");
    setNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("setNextInstructionAddress");
    getReturnAddress              = VM_Atom.findOrCreateAsciiAtom("getReturnAddress");
    setReturnAddress              = VM_Atom.findOrCreateAsciiAtom("setReturnAddress");

    getByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("getByteAtOffset");
    getIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("getIntAtOffset");
    getObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("getObjectAtOffset");
    getObjectArrayAtOffset        = VM_Atom.findOrCreateAsciiAtom("getObjectArrayAtOffset");
    getLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("getLongAtOffset");
    setByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("setByteAtOffset");
    setIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("setIntAtOffset");
    setObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("setObjectAtOffset");
    setLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("setLongAtOffset");

    getMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("getMemoryWord");
    setMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("setMemoryWord");
    getMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("getMemoryAddress");
    setMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("setMemoryAddress");

    prepare                       = VM_Atom.findOrCreateAsciiAtom("prepare");
    attempt                       = VM_Atom.findOrCreateAsciiAtom("attempt");

    setThreadSwitchBit            = VM_Atom.findOrCreateAsciiAtom("setThreadSwitchBit");
    clearThreadSwitchBit          = VM_Atom.findOrCreateAsciiAtom("clearThreadSwitchBit");
    
    saveThreadState               = VM_Atom.findOrCreateAsciiAtom("saveThreadState");
    threadSwitch                  = VM_Atom.findOrCreateAsciiAtom("threadSwitch");
    restoreHardwareExceptionState = VM_Atom.findOrCreateAsciiAtom("restoreHardwareExceptionState");
    returnToNewStack              = VM_Atom.findOrCreateAsciiAtom("returnToNewStack");
    dynamicBridgeTo               = VM_Atom.findOrCreateAsciiAtom("dynamicBridgeTo");
      
    objectAsAddress               = VM_Atom.findOrCreateAsciiAtom("objectAsAddress");
    addressAsObject               = VM_Atom.findOrCreateAsciiAtom("addressAsObject");
    addressAsObjectArray          = VM_Atom.findOrCreateAsciiAtom("addressAsObjectArray");
    addressAsType                 = VM_Atom.findOrCreateAsciiAtom("addressAsType");
    objectAsType                  = VM_Atom.findOrCreateAsciiAtom("objectAsType");
    addressAsByteArray            = VM_Atom.findOrCreateAsciiAtom("addressAsByteArray");
    addressAsIntArray             = VM_Atom.findOrCreateAsciiAtom("addressAsIntArray");
    objectAsByteArray             = VM_Atom.findOrCreateAsciiAtom("objectAsByteArray");
    objectAsShortArray            = VM_Atom.findOrCreateAsciiAtom("objectAsShortArray");
    objectAsIntArray              = VM_Atom.findOrCreateAsciiAtom("objectAsIntArray");

    addressAsThread               = VM_Atom.findOrCreateAsciiAtom("addressAsThread");
    objectAsThread                = VM_Atom.findOrCreateAsciiAtom("objectAsThread");
    objectAsProcessor             = VM_Atom.findOrCreateAsciiAtom("objectAsProcessor");
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    addressAsBlockControl         = VM_Atom.findOrCreateAsciiAtom("addressAsBlockControl");
    addressAsSizeControl          = VM_Atom.findOrCreateAsciiAtom("addressAsSizeControl");
    addressAsSizeControlArray     = VM_Atom.findOrCreateAsciiAtom("addressAsSizeControlArray");
  //-#if RVM_WITH_CONCURRENT_GC
    threadAsRCCollectorThread     = VM_Atom.findOrCreateAsciiAtom("threadAsRCCollectorThread");
  //-#endif
  //-#endif
    threadAsCollectorThread       = VM_Atom.findOrCreateAsciiAtom("threadAsCollectorThread");
    addressAsRegisters            = VM_Atom.findOrCreateAsciiAtom("addressAsRegisters");
    addressAsStack                = VM_Atom.findOrCreateAsciiAtom("addressAsStack");
    floatAsIntBits                = VM_Atom.findOrCreateAsciiAtom("floatAsIntBits");
    intBitsAsFloat                = VM_Atom.findOrCreateAsciiAtom("intBitsAsFloat");
    doubleAsLongBits              = VM_Atom.findOrCreateAsciiAtom("doubleAsLongBits");
    longBitsAsDouble              = VM_Atom.findOrCreateAsciiAtom("longBitsAsDouble");
      
    getObjectType                 = VM_Atom.findOrCreateAsciiAtom("getObjectType");
    getArrayLength                = VM_Atom.findOrCreateAsciiAtom("getArrayLength");

    sync                          = VM_Atom.findOrCreateAsciiAtom("sync");
    isync                         = VM_Atom.findOrCreateAsciiAtom("isync");
    //-#if RVM_FOR_POWERPC
    dcbst                         = VM_Atom.findOrCreateAsciiAtom("dcbst");
    icbi                          = VM_Atom.findOrCreateAsciiAtom("icbi");
    //-#endif
    //-#if RVM_FOR_IA32
    roundToZero                   = VM_Atom.findOrCreateAsciiAtom("roundToZero");
    clearFloatingPointState       = VM_Atom.findOrCreateAsciiAtom("clearFloatingPointState");
    //-#endif

    pragmaNoInline                = VM_Atom.findOrCreateAsciiAtom("pragmaNoInline");
    pragmaInline                  = VM_Atom.findOrCreateAsciiAtom("pragmaInline");
    pragmaNoOptCompile            = VM_Atom.findOrCreateAsciiAtom("pragmaNoOptCompile");

    addressFromInt                = VM_Atom.findOrCreateAsciiAtom("fromInt");
    addressToInt                  = VM_Atom.findOrCreateAsciiAtom("toInt");
    addressAdd                    = VM_Atom.findOrCreateAsciiAtom("add");
    addressSub                    = VM_Atom.findOrCreateAsciiAtom("sub");
    addressDiff                   = VM_Atom.findOrCreateAsciiAtom("diff");
    addressLT                     = VM_Atom.findOrCreateAsciiAtom("LT");
    addressLE                     = VM_Atom.findOrCreateAsciiAtom("LE");
    addressEQ                     = VM_Atom.findOrCreateAsciiAtom("EQ");
    addressNE                     = VM_Atom.findOrCreateAsciiAtom("NE");
    addressGT                     = VM_Atom.findOrCreateAsciiAtom("GT");
    addressGE                     = VM_Atom.findOrCreateAsciiAtom("GE");
    addressZero                   = VM_Atom.findOrCreateAsciiAtom("zero");
    addressMax                    = VM_Atom.findOrCreateAsciiAtom("max");
    addressIsZero                 = VM_Atom.findOrCreateAsciiAtom("isZero");
    addressIsMax                  = VM_Atom.findOrCreateAsciiAtom("isMax");
  }
}


