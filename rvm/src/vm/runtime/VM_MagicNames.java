/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.VM_Atom;

/**
 * Method names that are treated specially by compiler.
 * See also: VM_Magic, various magic compilers.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_MagicNames {

  public static final VM_Atom invokeMain                    = VM_Atom.findOrCreateAsciiAtom("invokeMain");
  public static final VM_Atom invokeClassInitializer        = VM_Atom.findOrCreateAsciiAtom("invokeClassInitializer");
  public static final VM_Atom invokeMethodReturningVoid     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningVoid");
  public static final VM_Atom invokeMethodReturningInt      = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningInt");
  public static final VM_Atom invokeMethodReturningLong     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningLong");
  public static final VM_Atom invokeMethodReturningFloat    = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningFloat");
  public static final VM_Atom invokeMethodReturningDouble   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningDouble");
  public static final VM_Atom invokeMethodReturningObject   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningObject");

  public static final VM_Atom getFramePointer               = VM_Atom.findOrCreateAsciiAtom("getFramePointer");
  public static final VM_Atom getTocPointer                 = VM_Atom.findOrCreateAsciiAtom("getTocPointer");
  public static final VM_Atom setTocPointer                 = VM_Atom.findOrCreateAsciiAtom("setTocPointer");
  public static final VM_Atom getJTOC                       = VM_Atom.findOrCreateAsciiAtom("getJTOC");
  public static final VM_Atom getProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("getProcessorRegister");
  public static final VM_Atom setProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("setProcessorRegister");
  //-#if RVM_FOR_IA32
  public static final VM_Atom getESIAsProcessor             = VM_Atom.findOrCreateAsciiAtom("getESIAsProcessor");
  public static final VM_Atom setESIAsProcessor             = VM_Atom.findOrCreateAsciiAtom("setESIAsProcessor");
  //-#endif

  public static final VM_Atom getTimeBase                   = VM_Atom.findOrCreateAsciiAtom("getTimeBase");

  public static final VM_Atom getCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("getCallerFramePointer");
  public static final VM_Atom setCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("setCallerFramePointer");
  public static final VM_Atom getCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("getCompiledMethodID");
  public static final VM_Atom setCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("setCompiledMethodID");
  public static final VM_Atom getNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("getNextInstructionAddress");
  public static final VM_Atom setNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("setNextInstructionAddress");
  public static final VM_Atom getReturnAddressLocation      = VM_Atom.findOrCreateAsciiAtom("getReturnAddressLocation");

  public static final VM_Atom getByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("getByteAtOffset");
  public static final VM_Atom getCharAtOffset               = VM_Atom.findOrCreateAsciiAtom("getCharAtOffset");
  public static final VM_Atom getIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("getIntAtOffset");
  public static final VM_Atom getWordAtOffset                = VM_Atom.findOrCreateAsciiAtom("getWordAtOffset");
  public static final VM_Atom getObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("getObjectAtOffset");
  public static final VM_Atom getObjectArrayAtOffset        = VM_Atom.findOrCreateAsciiAtom("getObjectArrayAtOffset");
  public static final VM_Atom getLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("getLongAtOffset");
  public static final VM_Atom setByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("setByteAtOffset");
  public static final VM_Atom setCharAtOffset               = VM_Atom.findOrCreateAsciiAtom("setCharAtOffset");
  public static final VM_Atom setIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("setIntAtOffset");
  public static final VM_Atom setWordAtOffset                = VM_Atom.findOrCreateAsciiAtom("setWordAtOffset");
  public static final VM_Atom setObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("setObjectAtOffset");
  public static final VM_Atom setLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("setLongAtOffset");
  public static final VM_Atom setDoubleAtOffset             = VM_Atom.findOrCreateAsciiAtom("setDoubleAtOffset");

  public static final VM_Atom getMemoryInt                  = VM_Atom.findOrCreateAsciiAtom("getMemoryInt");
  public static final VM_Atom setMemoryInt                  = VM_Atom.findOrCreateAsciiAtom("setMemoryInt");
  public static final VM_Atom getMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("getMemoryWord");
  public static final VM_Atom setMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("setMemoryWord");
  public static final VM_Atom getMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("getMemoryAddress");
  public static final VM_Atom setMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("setMemoryAddress");

  public static final VM_Atom prepareInt                    = VM_Atom.findOrCreateAsciiAtom("prepareInt");
  public static final VM_Atom prepareObject                 = VM_Atom.findOrCreateAsciiAtom("prepareObject");
  public static final VM_Atom prepareAddress                = VM_Atom.findOrCreateAsciiAtom("prepareAddress");
  public static final VM_Atom prepareWord                   = VM_Atom.findOrCreateAsciiAtom("prepareWord");
  public static final VM_Atom attemptInt                    = VM_Atom.findOrCreateAsciiAtom("attemptInt");
  public static final VM_Atom attemptObject                 = VM_Atom.findOrCreateAsciiAtom("attemptObject");
  public static final VM_Atom attemptAddress                = VM_Atom.findOrCreateAsciiAtom("attemptAddress");
  public static final VM_Atom attemptWord                   = VM_Atom.findOrCreateAsciiAtom("attemptWord");

  public static final VM_Atom saveThreadState               = VM_Atom.findOrCreateAsciiAtom("saveThreadState");
  public static final VM_Atom threadSwitch                  = VM_Atom.findOrCreateAsciiAtom("threadSwitch");
  public static final VM_Atom restoreHardwareExceptionState = VM_Atom.findOrCreateAsciiAtom("restoreHardwareExceptionState");
  public static final VM_Atom returnToNewStack              = VM_Atom.findOrCreateAsciiAtom("returnToNewStack");
  public static final VM_Atom dynamicBridgeTo               = VM_Atom.findOrCreateAsciiAtom("dynamicBridgeTo");
      
  public static final VM_Atom objectAsAddress               = VM_Atom.findOrCreateAsciiAtom("objectAsAddress");
  public static final VM_Atom addressAsObject               = VM_Atom.findOrCreateAsciiAtom("addressAsObject");
  public static final VM_Atom addressAsObjectArray          = VM_Atom.findOrCreateAsciiAtom("addressAsObjectArray");
  public static final VM_Atom addressAsType                 = VM_Atom.findOrCreateAsciiAtom("addressAsType");
  public static final VM_Atom objectAsType                  = VM_Atom.findOrCreateAsciiAtom("objectAsType");
  public static final VM_Atom addressAsByteArray            = VM_Atom.findOrCreateAsciiAtom("addressAsByteArray");
  public static final VM_Atom addressAsIntArray             = VM_Atom.findOrCreateAsciiAtom("addressAsIntArray");
  public static final VM_Atom objectAsByteArray             = VM_Atom.findOrCreateAsciiAtom("objectAsByteArray");
  public static final VM_Atom objectAsShortArray            = VM_Atom.findOrCreateAsciiAtom("objectAsShortArray");
  public static final VM_Atom objectAsIntArray              = VM_Atom.findOrCreateAsciiAtom("objectAsIntArray");

  public static final VM_Atom addressAsThread               = VM_Atom.findOrCreateAsciiAtom("addressAsThread");
  public static final VM_Atom objectAsThread                = VM_Atom.findOrCreateAsciiAtom("objectAsThread");
  public static final VM_Atom objectAsProcessor             = VM_Atom.findOrCreateAsciiAtom("objectAsProcessor");
  public static final VM_Atom threadAsCollectorThread       = VM_Atom.findOrCreateAsciiAtom("threadAsCollectorThread");
  public static final VM_Atom addressAsRegisters            = VM_Atom.findOrCreateAsciiAtom("addressAsRegisters");
  public static final VM_Atom addressAsStack                = VM_Atom.findOrCreateAsciiAtom("addressAsStack");
  public static final VM_Atom floatAsIntBits                = VM_Atom.findOrCreateAsciiAtom("floatAsIntBits");
  public static final VM_Atom intBitsAsFloat                = VM_Atom.findOrCreateAsciiAtom("intBitsAsFloat");
  public static final VM_Atom doubleAsLongBits              = VM_Atom.findOrCreateAsciiAtom("doubleAsLongBits");
  public static final VM_Atom longBitsAsDouble              = VM_Atom.findOrCreateAsciiAtom("longBitsAsDouble");
      
  public static final VM_Atom getObjectType                 = VM_Atom.findOrCreateAsciiAtom("getObjectType");
  public static final VM_Atom getArrayLength                = VM_Atom.findOrCreateAsciiAtom("getArrayLength");

  public static final VM_Atom sync                          = VM_Atom.findOrCreateAsciiAtom("sync");
  public static final VM_Atom isync                         = VM_Atom.findOrCreateAsciiAtom("isync");
  //-#if RVM_FOR_POWERPC
  public static final VM_Atom dcbst                         = VM_Atom.findOrCreateAsciiAtom("dcbst");
  public static final VM_Atom icbi                          = VM_Atom.findOrCreateAsciiAtom("icbi");
  //-#endif
  //-#if RVM_FOR_IA32
  public static final VM_Atom roundToZero                   = VM_Atom.findOrCreateAsciiAtom("roundToZero");
  public static final VM_Atom clearFloatingPointState       = VM_Atom.findOrCreateAsciiAtom("clearFloatingPointState");
  //-#endif

  // atoms related to VM_Word, VM_Address, VM_Offset, VM_Extent
  public static final VM_Atom wordFromInt                   = VM_Atom.findOrCreateAsciiAtom("fromInt");
  public static final VM_Atom wordFromIntZeroExtend         = VM_Atom.findOrCreateAsciiAtom("fromIntZeroExtend");
  public static final VM_Atom wordFromIntSignExtend         = VM_Atom.findOrCreateAsciiAtom("fromIntSignExtend");
  public static final VM_Atom wordFromLong                  = VM_Atom.findOrCreateAsciiAtom("fromLong");
  public static final VM_Atom wordToInt                     = VM_Atom.findOrCreateAsciiAtom("toInt");
  public static final VM_Atom wordToLong                    = VM_Atom.findOrCreateAsciiAtom("toLong");
  public static final VM_Atom wordToWord                    = VM_Atom.findOrCreateAsciiAtom("toWord");
  public static final VM_Atom wordToAddress                 = VM_Atom.findOrCreateAsciiAtom("toAddress");
  public static final VM_Atom wordToOffset                  = VM_Atom.findOrCreateAsciiAtom("toOffset");
  public static final VM_Atom wordToExtent                  = VM_Atom.findOrCreateAsciiAtom("toExtent");
  public static final VM_Atom wordAdd                       = VM_Atom.findOrCreateAsciiAtom("add");
  public static final VM_Atom wordSub                       = VM_Atom.findOrCreateAsciiAtom("sub");
  public static final VM_Atom wordDiff                      = VM_Atom.findOrCreateAsciiAtom("diff");
  public static final VM_Atom wordEQ                        = VM_Atom.findOrCreateAsciiAtom("EQ");
  public static final VM_Atom wordNE                        = VM_Atom.findOrCreateAsciiAtom("NE");
  public static final VM_Atom wordLT                        = VM_Atom.findOrCreateAsciiAtom("LT");
  public static final VM_Atom wordLE                        = VM_Atom.findOrCreateAsciiAtom("LE");
  public static final VM_Atom wordGT                        = VM_Atom.findOrCreateAsciiAtom("GT");
  public static final VM_Atom wordGE                        = VM_Atom.findOrCreateAsciiAtom("GE");
  public static final VM_Atom wordsLT                       = VM_Atom.findOrCreateAsciiAtom("sLT");
  public static final VM_Atom wordsLE                       = VM_Atom.findOrCreateAsciiAtom("sLE");
  public static final VM_Atom wordsGT                       = VM_Atom.findOrCreateAsciiAtom("sGT");
  public static final VM_Atom wordsGE                       = VM_Atom.findOrCreateAsciiAtom("sGE");
  public static final VM_Atom wordZero                      = VM_Atom.findOrCreateAsciiAtom("zero");
  public static final VM_Atom wordOne                       = VM_Atom.findOrCreateAsciiAtom("one");
  public static final VM_Atom wordMax                       = VM_Atom.findOrCreateAsciiAtom("max");
  public static final VM_Atom wordIsZero                    = VM_Atom.findOrCreateAsciiAtom("isZero");
  public static final VM_Atom wordIsMax                     = VM_Atom.findOrCreateAsciiAtom("isMax");
  public static final VM_Atom wordAnd                       = VM_Atom.findOrCreateAsciiAtom("and");
  public static final VM_Atom wordOr                        = VM_Atom.findOrCreateAsciiAtom("or");
  public static final VM_Atom wordNot                       = VM_Atom.findOrCreateAsciiAtom("not");
  public static final VM_Atom wordXor                       = VM_Atom.findOrCreateAsciiAtom("xor");
  public static final VM_Atom wordLsh                       = VM_Atom.findOrCreateAsciiAtom("lsh");
  public static final VM_Atom wordRshl                      = VM_Atom.findOrCreateAsciiAtom("rshl");
  public static final VM_Atom wordRsha                      = VM_Atom.findOrCreateAsciiAtom("rsha");

  // atoms related to VM_WordArray, VM_AddressArray, VM_OffsetArray, VM_ExtentArray, VM_CodeArray
  public static final VM_Atom addressArrayCreate            = VM_Atom.findOrCreateAsciiAtom("create");
  public static final VM_Atom addressArrayLength            = VM_Atom.findOrCreateAsciiAtom("length");
  public static final VM_Atom addressArrayGet               = VM_Atom.findOrCreateAsciiAtom("get");
  public static final VM_Atom addressArraySet               = VM_Atom.findOrCreateAsciiAtom("set");
}
