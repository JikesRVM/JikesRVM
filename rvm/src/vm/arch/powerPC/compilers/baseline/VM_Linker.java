/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * Perform dynamic linking as call sites and load/store sites are encountered 
 * at execution time.
 *
 * @author Bowen Alpern
 * @author Tony Cocchi 
 * @author Derek Lieber
 * @date 13 Apr 1999 
 */
class VM_Linker implements VM_BaselineConstants {

  //-----------//
  // interface //
  //-----------//

  // Load/compile/link a static method and patch a call instruction that references it.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb8 ("invokestatic")
  //
  static void invokestatic() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    VM_Method method = VM_MethodDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.invokestatic: ", method);
    if (!method.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(method.getDeclaringClass());

    method = method.resolve();

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkInvokestaticOrInvokespecial(fetchDynamicLinkAddress(), method.getOffset()));
    VM.enableGC();
  }

  // Load/compile/link a virtual method and patch a call instruction that references it.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb6 ("invokevirtual")
  //
  static void invokevirtual () throws VM_ResolutionException {  
    VM_Magic.pragmaNoInline();

    VM_Method method = VM_MethodDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.invokevirtual: ", method);
    if (!method.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(method.getDeclaringClass());

    method = method.resolve();

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkInvokevirtual(fetchDynamicLinkAddress(), method.getParameterWords() << 2, method.getOffset()));
    VM.enableGC();
  }
   
  // Resolve a special method call.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  //           special method sought (VM_MethodDictionary id)
  // Returned: machine code corresponding to desired special method
  // See also: bytecode 0xb7 ("invokespecial")
  //
  static void invokespecial() throws IncompatibleClassChangeError {
    VM_Magic.pragmaNoInline();

    VM_Method sought = VM_MethodDictionary.getValue(fetchDynamicLinkData());

    if (VM.TraceDynamicLinking) traceDL("VM_Linker.invokespecial: ", sought);

    VM_Method target = VM_Class.findSpecialMethod(sought);
    if (target == null) throw new IncompatibleClassChangeError();

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    if (target.isObjectInitializer() || target.isStatic()) {
      VM_Magic.setNextInstructionAddress(fp, linkInvokestaticOrInvokespecial(fetchDynamicLinkAddress(), target.getOffset()));
    } else {
      // I don't think this case can ever happen: how could
      // we ever compile a "super.xxx()" form of invokespecial without having
      // first created an instance of the superclass (in which case we wouldn't
      // generate a dynamically linked call)? [--DL]
      VM.assert(VM.NOT_REACHED); 
    }
    VM.enableGC();
  }
      

  // Load/initialize a class and patch a load instruction that references one of its static fields.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb2 ("getstatic")
  //
  static void getstatic() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    VM_Field field = VM_FieldDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.getstatic: ", field);
    if (!field.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(field.getDeclaringClass());

    field = field.resolve();

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkGetstatic(fetchDynamicLinkAddress(), field.getOffset(), field.getSize()));
    VM.enableGC();
  }

  // Load/initialize a class and patch a store instruction that references one of its static fields.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb3 ("putstatic")
  //
  static void putstatic() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    VM_Field field = VM_FieldDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.putstatic: ", field);
    if (!field.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(field.getDeclaringClass());

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkPutstatic(fetchDynamicLinkAddress(), field));
    VM.enableGC();
  }

  // Patch a load instruction that references an instance field.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb4 ("getfield")
  //
  static void getfield() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    VM_Field field = VM_FieldDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.getfield: ", field);
    if (!field.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(field.getDeclaringClass());

    field = field.resolve();

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkGetfield(fetchDynamicLinkAddress(), field.getOffset(), field.getSize()));
    VM.enableGC();
  }

  // Patch a store instruction that references an instance field.
  // Taken:    call stack    (used to retrieve patch site and dynamic linkage info)
  // Returned: never returns (re-executes backpatched code)
  // See also: bytecode 0xb5 ("putfield")
  //
  static void putfield() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    VM_Field field = VM_FieldDictionary.getValue(fetchDynamicLinkData());
    if (VM.TraceDynamicLinking) traceDL("VM_Linker.putfield: ", field);
    if (!field.getDeclaringClass().isInitialized())
      VM_Runtime.initializeClassForDynamicLink(field.getDeclaringClass());

    // install patch and set our return address to start of patched code
    //
    VM.disableGC();
    int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    VM_Magic.setNextInstructionAddress(fp, linkPutfield(fetchDynamicLinkAddress(), field));
    VM.enableGC();
  }

  // Allocate something like "new Foo[cnt0][cnt1]...[cntN-1]",
  //                      or "new int[cnt0][cnt1]...[cntN-1]".
  // Taken:    number of array dimensions
  //           type of array (VM_TypeDictionary id)
  //           position of word *above* `cnt0' argument within caller's frame
  //           number of elements to allocate for each dimension (undeclared, passed on stack following "dictionaryId" argument)
  // See also: bytecode 0xc5 ("multianewarray")
  //
  // TODO: is this really architecture specific? --dave
  static Object newArrayArray(int numDimensions, int dictionaryId, int argOffset /*, cntN-1, ..., cnt1, cnt0 */)
    throws VM_ResolutionException, NegativeArraySizeException, OutOfMemoryError {
    VM_Magic.pragmaNoInline();

    // fetch number of elements to be allocated for each array dimension
    //
    int[] numElements = new int[numDimensions];
    VM.disableGC();
    int argp = VM_Magic.getMemoryWord(VM_Magic.getFramePointer()) + argOffset;
    for (int i = 0; i < numDimensions; ++i)
      numElements[i] = VM_Magic.getMemoryWord(argp -= 4);
    VM.enableGC();

    // validate arguments
    //
    for (int i = 0; i < numDimensions; ++i)
      if (numElements[i] < 0)
	throw new NegativeArraySizeException();

    // create array
    //
    return VM_Runtime.buildMultiDimensionalArray(numElements, 0, VM_TypeDictionary.getValue(dictionaryId).asArray());
  }

  //----------------//
  // implementation //
  //----------------//
  
  // Note: the patch sequences in the following methods are carefully constructed
  // so that they will execute correctly even if their instructions appear out of
  // order when seen by other processors. These sequences might even (partially) execute
  // several times, so the instructions comprising them must be "idempotent": they must
  // not overwrite any memory or registers used by logically preceeding instructions.
  
  // Patch an "invokestatic" or "invokespecial" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,invokeXXXOffset,jtoc     ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(methodId)                        DATA(methodId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: cau  t0,jtoc,HI(methodOffset) <-PATCH
  //      b    yyy                              l    s0,LO(methodOffset),t0   <-PATCH
  //      mtlr s0                               mtlr s0
  //      ...                                   ...
  //      blrl                                  blrl
  //
  // Taken:    address of "xxx" in above listing
  //           offset of method pointer within "java table of contents"
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb7 and 0xb8 of VM_Compiler.generateCode()
  //
  private static int linkInvokestaticOrInvokespecial(int xxx, int jtocOffset) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;
     
    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    if (0 == (jtocOffset&0x8000)) VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(T0, JTOC,  jtocOffset>>16));
    else                          VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(T0, JTOC, (jtocOffset>>16)+1));
    VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.L(S0, jtocOffset&0xFFFF, T0));

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.dcbst(qqq + 4);  // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.icbi(qqq + 4);  // assumption: 32 bytes <= cache line size

    return rrr;
  }

  // Patch an "invokevirtual" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,invokevirtualOffset,jtoc ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(methodId)                        DATA(methodId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: l    t0,objectOffset,SP       <-PATCH
  //      b    yyy                              l    t1,tibOffset,t0          <-PATCH
  //      b    yyy                              l    s0,methodOffset,t1       <-PATCH
  //      mtlr s0                               mtlr s0
  //      ...                                   ...
  //      blrl                                  blrl
  //
  // Taken:    address of "xxx" in above listing
  //           offset on stack to object pointer from SP
  //           offset of method pointer within "java table of contents"
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb6 of VM_Compiler.generateCode()
  //
  private static int linkInvokevirtual(int xxx, int objectOffset, int methodOffset) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;

    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    VM_Magic.setMemoryWord(qqq    , VM_Assembler.L(T0, objectOffset, SP));
    VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.L(T1, OBJECT_TIB_OFFSET, T0));
    VM_Magic.setMemoryWord(qqq + 8, VM_Assembler.L(S0, methodOffset, T1));

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.dcbst(qqq + 8);  // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.icbi(qqq + 8);  // assumption: 32 bytes <= cache line size

    return rrr;
  }
      
  // Patch a "getstatic" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,getstaticOffset,jtoc     ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(fieldId)                         DATA(fieldId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: cau  s0,JTOC,HI(jtocOffset)   <-PATCH
  //      b    yyy                              l    t0,LO(jtocOffset),s0     <-PATCH
  //      b    yyy                              stu  t0,-4,SP                 <-PATCH
  //
  // Taken:    address of "xxx" in above listing
  //           offset of field within "java table of contents"
  //           size of field
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb2 of VM_Compiler.generateCode()
  //
  private static int linkGetstatic(int xxx, int jtocOffset, int fieldSize) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;

    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    if (0 == (jtocOffset&0x8000)) VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(S0, JTOC,  jtocOffset>>16));
    else                          VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(S0, JTOC, (jtocOffset>>16)+1));
    if (fieldSize == 4) {
        VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.L(S0, jtocOffset&0xFFFF, S0));
        VM_Magic.setMemoryWord(qqq + 8, VM_Assembler.STU(S0, -4, SP));
    } else {
      if (VM.VerifyAssertions) VM.assert(fieldSize==8);
      VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.LFD(F0, jtocOffset&0xFFFF, S0));
      VM_Magic.setMemoryWord(qqq + 8, VM_Assembler.STFDU(F0, -8, SP));
    }

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.dcbst(qqq + 8);  // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.icbi(qqq + 8);  // assumption: 32 bytes <= cache line size

    return rrr;
  }
     
  // Patch a "putstatic" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //  The following is what happens without reference counting
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,putstaticOffset,jtoc     ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(fieldId)                         DATA(fieldId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: cau  s0,JTOC,HI(jtocOffset)   <-PATCH
  //      b    yyy                              l    t0,0,SP                  <-PATCH
  //      b    yyy                              st   t0,LO(jtocOffset),s0     <-PATCH
  //      b    yyy                              cal  SP,4,SP                  <-PATCH
  //
  // Taken:    address of "xxx" in above listing
  //           offset of field within "java table of contents"
  //           size of field
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb3 of VM_Compiler.generateCode()
  //

  //  With reference counting it's exactly the same, except that we compute the target address with
  //  a CAL instruction instead of actually performing the store, 
  //
  //      Before                                After
  //      ------                                -----
  //      b    yyy                         qqq: cau  s0,JTOC,HI(jtocOffset)   <-PATCH
  //      b    yyy                              l    t0,0,SP                  <-PATCH
  //      b    yyy                              cal  t1,s0,LO(jtocOffset)     <-PATCH
  //      b    yyy                              cal  SP,4,SP                  <-PATCH

  //
  // Taken:    address of "xxx" in above listing
  //           offset of field within "java table of contents"
  //           size of field
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb3 of VM_Compiler.generateCode()
  //

  private static int linkPutstatic(int xxx, VM_Field field) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;

    int jtocOffset = field.getOffset(); 
    int fieldSize = field.getSize();


    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    if (0 == (jtocOffset&0x8000)) VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(S0, JTOC,  jtocOffset>>16));
    else                          VM_Magic.setMemoryWord(qqq, VM_Assembler.CAU(S0, JTOC, (jtocOffset>>16)+1));
    if (fieldSize == 4) {
      VM_Magic.setMemoryWord(qqq +  4, VM_Assembler.L  (T0, 0, SP));

      if (VM.BuildForConcurrentGC && ! field.getType().isPrimitiveType()) 
	VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.CAL(T1, jtocOffset&0xFFFF, S0));
      else
	VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.ST (T0, jtocOffset&0xFFFF, S0));
      
      VM_Magic.setMemoryWord(qqq + 12, VM_Assembler.CAL(SP, 4, SP));
    } else {
      if (VM.VerifyAssertions) VM.assert(fieldSize==8);
      VM_Magic.setMemoryWord(qqq +  4, VM_Assembler.LFD (F0, 0, SP));
      VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.STFD(F0, jtocOffset&0xFFFF, S0));
      VM_Magic.setMemoryWord(qqq + 12, VM_Assembler.CAL (SP, 8, SP));
    }

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    if (VM.BuildForConcurrentGC && !field.getType().isPrimitiveType())
      // refcounting requires bigger patch area
      VM_Magic.dcbst(qqq + 16); // assumption: 32 bytes <= cache line size
    else
      VM_Magic.dcbst(qqq + 12); // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);      // assumption: 32 bytes <= cache line size
    if (VM.BuildForConcurrentGC && !field.getType().isPrimitiveType())
      // refcounting requires bigger patch area
      VM_Magic.icbi(qqq + 16); // assumption: 32 bytes <= cache line size
    else
      VM_Magic.icbi(qqq + 12); // assumption: 32 bytes <= cache line size

    return rrr;
  }
      
  // Patch a "getfield" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,getfieldOffset,jtoc      ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(fieldId)                         DATA(fieldId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: l    t0,0,SP                  <-PATCH
  //      b    yyy                              l    t1,fieldOffset,t0        <-PATCH
  //      b    yyy                              st   t1,0,SP                  <-PATCH
  //
  // Taken:    address of "xxx" in above listing
  //           offset of field within object
  //           size of field
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb4 of VM_Compiler.generateCode()
  //
  private static int linkGetfield(int xxx, int fieldOffset, int fieldSize) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;

    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    if (fieldSize == 4) {
      VM_Magic.setMemoryWord(qqq    , VM_Assembler.L (T0, 0, SP));
      VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.L (T1, fieldOffset, T0));
      VM_Magic.setMemoryWord(qqq + 8, VM_Assembler.ST(T1, 0, SP));
    } else {
      if (VM.VerifyAssertions) VM.assert(fieldSize==8);
      VM_Magic.setMemoryWord(qqq    , VM_Assembler.L  (T0, 0, SP));
      VM_Magic.setMemoryWord(qqq + 4, VM_Assembler.LFD(F0, fieldOffset, T0));
      VM_Magic.setMemoryWord(qqq + 8, VM_Assembler.STFDU(F0, -4, SP));
    }

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.dcbst(qqq + 8);  // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);      // assumption: 32 bytes <= cache line size
    VM_Magic.icbi(qqq + 8);  // assumption: 32 bytes <= cache line size

    return rrr;
  }
      
  // Patch a "putfield" instruction sequence, converting it
  // from dynamic link form to executable form.
  //
  //
  //  his is what happens without reference counting
  //
  //      Before                                After
  //      ------                                -----
  //
  //      l    s0,putfieldOffset,jtoc      ppp: b    qqq                      <-PATCH
  //      mtlr s0                               mtlr s0
  //      saveSP                                saveSP
  //      blrl                                  blrl
  // xxx: DATA(fieldId)                         DATA(fieldId)
  //      restoreSP                        rrr: restoreSP
  // yyy: isync                                 isync
  //      b    yyy                         qqq: l    t0,0,SP                  <-PATCH
  //      b    yyy                              l    t1,4,SP                  <-PATCH
  //      b    yyy                              st   t0,fieldOffset,t1        <-PATCH
  //      b    yyy                              cal  SP,8,SP                  <-PATCH
  //

  //  With reference counting, it's exactly the same, except that the patched store is replaced by a cal
  //  instruction, as follows:
  //
  //      Before                                After
  //      ------                                -----
  //      b    yyy                         qqq: l    t0,0,SP                  <-PATCH
  //      b    yyy                              l    t1,4,SP                  <-PATCH
  //	  b    yyy                              cal  t1,fieldOffset,t1        <-PATCH
  //      b    yyy                              cal  SP,8,SP                  <-PATCH

  // Taken:    address of "xxx" in above listing
  //           offset of field within object
  //           size of field
  // Returned: address of "rrr" in above listing (== place to resume execution of patched code)
  // See also: case 0xb5 of VM_Compiler.generateCode()
  //
  private static int linkPutfield(int xxx, VM_Field field) {
    int ppp = xxx - 16;
    int rrr = ppp + 20;
    int qqq = ppp + 28;

    // get field information
    int fieldOffset = field.getOffset();
    int fieldSize   = field.getSize();


    // apply patches
    //
    VM_Magic.setMemoryWord(ppp, VM_Assembler.B((qqq - ppp) >> 2));
    if (fieldSize == 4) {
      VM_Magic.setMemoryWord(qqq     , VM_Assembler.L  (T0, 0, SP));
      VM_Magic.setMemoryWord(qqq +  4, VM_Assembler.L  (T1, 4, SP));

      // For RCGC, simply compute address of store; for all others, store the updated field value
      if (VM.BuildForConcurrentGC && ! field.getType().isPrimitiveType()) 
	VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.CAL(T1, fieldOffset, T1));
      else 
	VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.ST (T0, fieldOffset, T1));

      VM_Magic.setMemoryWord(qqq + 12, VM_Assembler.CAL(SP, 8, SP));
    } else {
      if (VM.VerifyAssertions) VM.assert(fieldSize==8);
      VM_Magic.setMemoryWord(qqq     , VM_Assembler.LFD (F0,  0, SP));
      VM_Magic.setMemoryWord(qqq +  4, VM_Assembler.L   (T1,  8, SP));
      VM_Magic.setMemoryWord(qqq +  8, VM_Assembler.STFD(F0, fieldOffset, T1));
      VM_Magic.setMemoryWord(qqq + 12, VM_Assembler.CAL (SP, 12, SP));
    }

    // flush patches to main memory
    //
    VM_Magic.dcbst(ppp);
    VM_Magic.dcbst(qqq);      // assumption: 32 bytes <= cache line size
    if (VM.BuildForConcurrentGC && !field.getType().isPrimitiveType())
      // refcounting requires bigger patch area
      VM_Magic.dcbst(qqq + 16); // assumption: 32 bytes <= cache line size
    else
      VM_Magic.dcbst(qqq + 12); // assumption: 32 bytes <= cache line size
     
    // wait for main memory changes to propagate to all cpus
    //
    VM_Magic.sync();

    // invalidate copy of old memory that might be in instruction cache on this cpu
    //
    VM_Magic.icbi(ppp);
    VM_Magic.icbi(qqq);       // assumption: 32 bytes <= cache line size
    if (VM.BuildForConcurrentGC && !field.getType().isPrimitiveType())
      // refcounting requires bigger patch area
      VM_Magic.icbi(qqq + 16); // assumption: 32 bytes <= cache line size
    else
      VM_Magic.icbi(qqq + 12);  // assumption: 32 bytes <= cache line size

    return rrr;
  }

  // Fetch dynamic link data word (DATA at label "xxx" in above listings).
  // Taken:    nothing (dynamic link site is implicitly two callers up on call stack)
  // Returned: data word
  //
  private static int fetchDynamicLinkData() {
    VM_Magic.pragmaNoInline();
    VM.disableGC();  // prevent movement of stack while reading from it
    int fp   = VM_Magic.getFramePointer(); // frame for fetchDynamicLinkData
    fp   = VM_Magic.getCallerFramePointer(fp); // frame for invokeXXX
    fp   = VM_Magic.getCallerFramePointer(fp); // frame for caller
    int ip   = VM_Magic.getNextInstructionAddress(fp);
    int retval = VM_Magic.getMemoryWord(ip);
    VM.enableGC();
    return retval;
  }

  // Fetch address of dynamic link data word (address of label "xxx" in above listings).
  //
  // Taken:    nothing (dynamic link site is implicitly two callers up on call stack)
  // Returned: data address
  //
  private static int fetchDynamicLinkAddress() {
    VM_Magic.pragmaNoInline();
    int fp   = VM_Magic.getFramePointer(); // frame for fetchDynamicLinkAddress
    fp   = VM_Magic.getCallerFramePointer(fp); // frame for invokeXXX
    fp   = VM_Magic.getCallerFramePointer(fp); // frame for caller
    int ip   = VM_Magic.getNextInstructionAddress(fp);
    return ip;
  }

  private static void traceDL(String header, VM_Member m) {
    VM.sysWrite(header);
    VM.sysWrite(m);
    VM.sysWrite("\n");
  }
}

