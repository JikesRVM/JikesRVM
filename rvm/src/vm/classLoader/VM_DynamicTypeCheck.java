/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * Data structures and code for fast dynamic type checking.
 * <p>
 * The question to be answered has the form:  LHS :?= RHS
 * (i.e. can an instance of the RHS class be stored in a
 * variable of the LHS class or interface.)  This question
 * arises for four bytecodes: instanceof, checkcast, aastore
 * and invokeinterface and entry into catch blocks. 
 * <p>
 * The idea of the optimizations presented below is to treat
 * each context in which these queries arises as a special
 * case to be optimised in isolation.  Consider the following
 * taxonomy of dynamic type checking conexts:
 * <p>
 * (1) Is the LHS unknown at compile time?  True only for aastore?
 *    If so, the following test will be fast in most instances:
 *    is the runtime type of the LHS array the same as compile-time 
 *    type of the variable that contains it?  If so, the Java-to-bytecode
 *    compiler (and the verifier) guarantees that the test passes.  
 *    Unfortunately, this test can only be used in two of three cases:
 *    when the LHS variable is a field or a parameter.  When the LHS is 
 *    in a local variable the Java-to-bytecode compiler has thrown away
 *    the necessary type information.
 * <p>
 * (2) Otherwise, is the LHS an array?
 *    If so, there are three sub-cases
 *    (2a) LHS is [^k primitive:
 *        If so, the dimensionality of the RHS must be k
 *        and the baseclass of the RHS must be the same primitive
 *    (2b) LHS is [^k class:
 *        If so, the dimensionality of the RHS must be k
 *        and the baseclass of the RHS must be assignable with class (see #3)
 *    (2c) LHS is [^k Ljava.lang.Object:
 *        If so, either the dimensionality of the RHS is greater than k
 *        or, this dimensionality is k and the baseclass is NOT primitive
 * <p>
 * (3) Otherwise, is the LHS unresolved?
 *    If so, fall back to calling isAssignableWith (or some other helper
 *    method) at runtime.
 * <p>
 * (4) Otherwise, is the LHS an interface?  
 *    If so, query the implementsTrits of the RHS's TIB at the entry 
 *    for the interface ID.  This will answer MAYBE, YES, or NO.  
 *    Check for YES first.  If this succeeds, so does the test.  If 
 *    the answer is NO, the test fails. If maybe, the RHS has never
 *    been tested as to whether it implements this interface before;
 *    perform the test now and update the RHS's implementsTrits vector.
 *    Note: most classes do not implement any interfaces (except
 *    those they inherit from java.lang.Object).  It will save space 
 *    to start implementsTrits off as a short vector and grow it the 
 *    first time a class is tested.  (Note: it may be useful to 
 *    define equivalence classes of classes that have a common ancestor 
 *    that implements the same interfaces.  Such classes can share the 
 *    same implementsTrits vector.  Growing this vector will be harder.)
 * <p>
 * (5) Otherwise, is the depth of the LHS greater than 
 * MIN_SUPERCLASS_IDS_SIZE? If so, if LHS depth is greater that 
 * RHS's superclassIds.length, the test fails.  Else, see #6.
 * <p>
 * (6) Otherwise.  If the LHS depth component of the RHS's superclassIds
 *    array is the LHS class ID, the test succeeds.  Else, it fails.
 *
 * @see OPT_DynamicTypeCheckExpansion
 * @see VM_Type
 * @see VM_Class
 * @see VM_Array
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 */
class VM_DynamicTypeCheck implements VM_Constants {

  /**
   * Minimum length of the superclassIds array in TIB.
   * Note: this array is padded to save a index out of
   * bounds test for classes with shallow class depth.
   */
  static final int MIN_SUPERCLASS_IDS_SIZE = 6; // a short, so div by 2.

  /**
   * Minimum length of the implements trits array in TIB.
   * Note: this array is padded to save a index out of
   * bounds test for some (hopefully most common) interfaces.
   *
   */
  static final int MIN_IMPLEMENTS_TRITS_SIZE = 20; // a byte, so div by 4.

  /*
   * Trit values for answering class implements interface queries.
   */
  static final byte MAYBE = 2;
  static final byte YES   = 1;
  static final byte NO    = 0;

  /**
   * Create the superclass Id vector for a VM_Type.
   *
   * @param t a VM_Type to create a superclass Id vector for
   * @return the superclass Id vector
   */
  static short[] buildSuperclassIds (VM_Type t) {
    int depth   = t.getTypeDepth();
    int size    = MIN_SUPERCLASS_IDS_SIZE <= depth ? depth+1 : MIN_SUPERCLASS_IDS_SIZE;
    short[] tsi = new short[size];
    VM_Type p;                          
    if (depth == 0) {        // t is Object (or eventually some interfaces TODO!!)
      int id = t.getDictionaryId();
      if (VM.VerifyAssertions) VM.assert(id <= 0xFFFF); // when this fails, make superclassIds int[] 
      tsi[0] = (short) id;
      return tsi;
    } else if (depth == 1) { // t is array or top level class
      if (VM.VerifyAssertions) VM.assert(t.isArrayType() || t.asClass().getSuperClass() == VM_Type.JavaLangObjectType);
      p = VM_Type.JavaLangObjectType; //  TODO!! handle interfaces better
    } else if (1 < depth) {  // t is a non Object, non top level class
      p = t.asClass().getSuperClass();
    } else {                 // t is a primitive
      VM.assert(VM.NOT_REACHED);
      p = null;
    }
    short[] psi = p.getSuperclassIds();
    for (int i=0; i<depth; i++) {
      tsi[i] = psi[i];
    }
    int id = t.getDictionaryId();
    if (VM.VerifyAssertions) VM.assert(id <= 0xFFFF); // when this fails, make superclassIds int[] 
    tsi[depth] = (short) id;
    return tsi;
  }

  /**
   * Create the implements trits vector for a VM_Type.
   * 
   * @param t a VM_Type to create a superclass Id vector for
   * @return the implements trits vector
   */
  static byte[] buildImplementsTrits (VM_Type t) {
    return initialImplementsTrits;
  }


  /**
   * Handle maybe case: 
   *    does class X implement interface I ?
   *    cache the answer in the implementsTrits vector
   *
   * @param I an interface
   * @param rhsTIB the TIB of an object that might implement the interface
   * @return <code>true</code> if the object implements the interface
   *         or <code>false</code> if it does not
   */
  static boolean initialInstanceOfInterface (VM_Class I, Object[] rhsTIB) 
    throws VM_ResolutionException {
    VM_Type rhsType = VM_Magic.objectAsType(rhsTIB[VM.TIB_TYPE_INDEX]);
    boolean answer;
    if (rhsType.isClassType()) {
      VM_Class Y = rhsType.asClass();
      while (Y != null && !explicitImplementsTest(I, Y)) {
	Y = Y.getSuperClass();
      }
      answer = !(Y == null);
      if (VM.BuildForIMTInterfaceInvocation & answer) 
        populateIMT(rhsType.asClass(), I);
      if (VM.BuildForITableInterfaceInvocation & answer) 
        populateITable(rhsType.asClass(), I);
    } else {
      // arrays implement java.io.Serializable and java.lang.Cloneable 
      // and nothing else.
      // primitives implement nothing.
      answer = rhsType.isArrayType() &&	((I == VM_Type.JavaLangCloneableType) ||
					 (I == VM_Type.JavaIoSerializableType));
      // don't have to populate IMT for arrays since Cloneable and Serializable
      // are both empty "marker" interfaces (and thus have no interface methods)
    }
    byte [] it = rhsType.getImplementsTrits();
    int id = I.getInterfaceId();
    //// if (id >= MIN_IMPLEMENTS_TRITS_SIZE) VM.sysWrite("\n!!!! IMT: Interface id "+id+" of "+I+" is large enough to require bounds checking code\n");
    if (it == initialImplementsTrits || it.length <= id) {
      it = growImplementsTrits (it);
      rhsType.setImplementsTrits(it);
    }
    it[id] = answer ? YES : NO;
    return answer;    
  }

  /**
   * Returns true if it is known at compile time that rhsType implements I
   * 
   * @param I an interface
   * @param rhsType a VM_Type that might implement the interface
   * @return <code>true</code> if the type implements the interface
   *         or <code>false</code> if it does not
   */
  static boolean compileTimeImplementsInterface(VM_Class I, VM_Type rhsType) 
    throws VM_ResolutionException {
    byte [] it = rhsType.getImplementsTrits();
    int id = I.getInterfaceId();
    if (it == initialImplementsTrits || it.length <= id) {
      it = growImplementsTrits (it);
      rhsType.setImplementsTrits(it);
    }
    if (it[id] == YES) return true;
    if (it[id] == NO) return false;
    
    //// if (id >= MIN_IMPLEMENTS_TRITS_SIZE) VM.sysWrite("\n!!!! IMT: Interface id "+id+" of "+I+" is large enough to require bounds checking code\n");
    boolean answer;
    if (rhsType.isClassType()) {
      VM_Class Y = rhsType.asClass();
      while (Y != null && !compileTimeExplicitImplementsTest(I, Y)) {
	Y = Y.getSuperClass();
      }
      answer = !(Y == null);
      if (answer && rhsType.isInstantiated() && 
          !rhsType.asClass().isInterface()) {
	// At compile time rhsType may not be instantiated and therefore 
        // the TIB entries
	// for target virtual method may still be null 
        // (haven't been compiled yet)!
	if (VM.BuildForIMTInterfaceInvocation) 
	  populateIMT(rhsType.asClass(), I);
	if (VM.BuildForITableInterfaceInvocation) 
          populateITable(rhsType.asClass(), I);
      }
    } else {
      // arrays implement java.io.Serializable and java.lang.Cloneable and 
      // nothing else
      // primitives implement nothing.
      answer = rhsType.isArrayType() &&	((I == VM_Type.JavaLangCloneableType) ||
					 (I == VM_Type.JavaIoSerializableType));
      // don't have to populate IMT for arrays since Cloneable and Serializable
      // are both empty "marker" interfaces (and thus have no interface methods)
    }
    if (answer) {
      if (!(VM.BuildForIMTInterfaceInvocation || 
	    VM.BuildForITableInterfaceInvocation) || 
	  rhsType.isInstantiated())
	it[id] = YES;
    }
    return answer;
  }

  /**
   * Handle the case when LHSclass is unresolved at compile time.
   *     If necessary load LHSclass and then answer is rhsTIB the TIB 
   *     of an instanceof LHSclass?
   * 
   * @param LHSclass a class or interface that may not be fully loaded
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfUnresolved (VM_Class LHSclass, Object[] rhsTIB)  
    throws VM_ResolutionException {
    if (!LHSclass.isInitialized()) {
      VM_Runtime.initializeClassForDynamicLink(LHSclass);
    }
    return instanceOfResolved(LHSclass, rhsTIB);
  }   

  /**
   * LHSclass is a fully loaded class or interface.  
   *   Is rhsTIB the TIB of an instanceof LHSclass?
   * 
   * @param LHSclass a fully loaded class or interface class
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfResolved (VM_Class LHSclass, Object[] rhsTIB) 
    throws VM_ResolutionException {
    if (LHSclass.isInterface()) {
      return instanceOfInterface(LHSclass, rhsTIB);
    } else {
      return instanceOfClass(LHSclass, rhsTIB);
    }
  }    


  /**
   * LHSclass is a fully loaded class.
   *  Is rhsTIB the TIB of a subclass of LHSclass?
   * 
   * @param LHSclass a (fully loaded) class
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfClass (VM_Class LHSclass, Object[] rhsTIB) {
    short[] superclassIds = VM_Magic.objectAsShortArray(rhsTIB[VM.TIB_SUPERCLASS_IDS_INDEX]);
    int LHSDepth = LHSclass.getTypeDepth();
    if (LHSDepth >= superclassIds.length) return false;
    int LHSId = LHSclass.getDictionaryId();
    return superclassIds[LHSDepth] == LHSId;
  }    


  /** 
   * LHSclass is a fully loaded interface.
   *   Is rhsTIB the TIB of a class that implements LHSclass?
   * 
   * @param LHSclass a class (that is a fully loaded interface)
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfInterface (VM_Class LHSclass, Object[] rhsTIB) throws VM_ResolutionException {
    byte[] implementsTrits = VM_Magic.objectAsByteArray(rhsTIB[VM.TIB_IMPLEMENTS_TRITS_INDEX]);
    int LHSId = LHSclass.getInterfaceId();
    byte trit;
    if (LHSId >= implementsTrits.length || ((trit = implementsTrits[LHSId]) == MAYBE)) {
      return initialInstanceOfInterface(LHSclass, rhsTIB);
    }
    return trit == YES;
  }


  /**
   * LHSclass is an interface that RHS class must implement.
   * Raises a VM_ResolutionException if RHStib does not implement LHSclass
   * 
   * @param LHSclass an class (should be an interface)
   * @param RHStib the TIB of an object that must implement LHSclass
   */
  static void mandatoryInstanceOfInterface (VM_Class LHSclass, Object[] RHStib) 
    throws VM_ResolutionException {
    if (!LHSclass.isInitialized()) {
      VM_Runtime.initializeClassForDynamicLink(LHSclass);
    }
    if (LHSclass.isInterface() && instanceOfInterface(LHSclass, RHStib)) return;
    // Raise an IncompatibleClassChangeError.
    VM_Type RHStype =  VM_Magic.objectAsType(RHStib[VM.TIB_TYPE_INDEX]);
    throw new VM_ResolutionException(RHStype.getDescriptor(), 
                                     new IncompatibleClassChangeError());
  }


  /**
   * mid is the dictionary id of an interface method we are trying to invoke
   * RHStib is the TIB of an object on which we are attempting to invoke it
   * We were unable to tell at compile time if mid is a real or ghost reference,
   * Therefore we must resolve it now and then call mandatoryInstanceOfInterface
   * with the right LHSclass.  This ensures that the IMT is populated before 
   * we return 
   * to our caller who will actually invoke the interface method 
   * through the IMT.
   * 
   * @param mid the dictionary id of the target interface method
   * @param RHStib, the TIB of the object on which we are attempting to 
   * invoke the interface method
   */
  static void unresolvedInterfaceMethod(int mid, Object[] RHStib) 
    throws VM_ResolutionException {
    VM_Method m = VM_MethodDictionary.getValue(mid);
    VM_Method nm = m.resolveInterfaceMethod(true);
    mandatoryInstanceOfInterface(nm.getDeclaringClass(), RHStib);
  }


  /**
   * LHSArray is an unresolved [^LHSDimension of LHSInnermostClass
   *   Is rhsType an instance of LHSArray?
   * 
   * @param LHSInnermostElementclass the innermost element type
   * @param LHSDimension the dimensionality of the array
   * @param RHStype the TIB of an object that might be an instanceof 
   *        [^LHSDimension of LHSInnermostClass
   * @return <code>true</code> if RHStype is an instanceof
   *        [^LHSDimension of LHSInnermostClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfUnresolvedArray (VM_Class LHSInnermostElementClass, 
                                            int LHSDimension,  VM_Type RHSType) 
    throws VM_ResolutionException {
    if (!LHSInnermostElementClass.isInitialized()) {
      VM_Runtime.initializeClassForDynamicLink(LHSInnermostElementClass);
    }
    return instanceOfArray(LHSInnermostElementClass, LHSDimension, RHSType);
  }
    

  /**
   * LHSArray is [^LHSDimension of java.lang.Object
   *   Is rhsType an instance of LHSArray?
   * 
   * @param LHSDimension the dimensionality of the array
   * @param RHSType a type that might be an instanceof 
   *        [^LHSDimension of java.lang.Object
   * @return <code>true</code> if RHStype is an instanceof
   *       [^LHSDimension of LHSInnermostClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfObjectArray (int LHSDimension, VM_Type RHSType) {
    int RHSDimension = RHSType.getDimensionality();
    if (RHSDimension < LHSDimension) return false;
    if (RHSDimension > LHSDimension) return true;
    return RHSType.asArray().getInnermostElementType().isClassType(); // !primitive 
  }


  /**
   * LHSArray is [^LHSDimension of LHSInnermostClass, LHSInnermostClass 
   * is not java.lang.Object.
   *   Is rhsType an instance of LHSArray?
   * 
   * @param LHSInnermostElementclass the innermost element type
   * @param LHSDimension the dimensionality of the array
   * @param RHStype a type that might be an instanceof 
   *        [^LHSDimension of LHSInnermostClass
   * @return <code>true</code> if RHStype is an instanceof
   *        [^LHSDimension of LHSInnermostClass
   *         or <code>false</code> if it is not
   */
  static boolean instanceOfArray (VM_Class LHSInnermostElementClass, 
                                  int LHSDimension,  VM_Type RHSType) 
    throws VM_ResolutionException {
    int RHSDimension = RHSType.getDimensionality();
    if (RHSDimension != LHSDimension) return false;
    VM_Type RHSInnermostElementType = RHSType.asArray().
                                      getInnermostElementType();
    if (RHSInnermostElementType.isPrimitiveType()) return false;
    return instanceOfResolved(LHSInnermostElementClass, 
			      RHSInnermostElementType.
                                getTypeInformationBlock());
  }
  
  
  /**
   * RHSType is resolved.
   *   Can we store an object of type RHSType in a variable of type LHSType?
   * 
   * @param LHSType the left-hand-side type
   * @param RHSType the right-hand-size type
   * @return <code>true</code> if we can store an object of 
   *         RHSType into a variable of type LSType
   *         or <code>false</code> if we cannot.
   */
  static boolean instanceOf (VM_Type LHSType, VM_Type RHSType) 
    throws VM_ResolutionException {
    if (LHSType == RHSType) return true;
    if (!LHSType.isResolved()) {
      synchronized(VM_ClassLoader.lock) {
	LHSType.load();
	LHSType.resolve();
      }
    }
    int LHSDimension = LHSType.getDimensionality();
    int RHSDimension = RHSType.getDimensionality();
    if (LHSDimension < 0 || RHSDimension < 0) return false;
    if (LHSDimension == 0) return instanceOfResolved(LHSType.asClass(), 
                                                     RHSType.getTypeInformationBlock());
    VM_Type LHSInnermostElementType = LHSType.asArray().getInnermostElementType();
    if (LHSInnermostElementType == VM_Type.JavaLangObjectType){
      return instanceOfObjectArray(LHSDimension, RHSType);
    } else if (!LHSInnermostElementType.isPrimitiveType()) {
      return instanceOfArray(LHSInnermostElementType.asClass(), 
                             LHSDimension, RHSType);
    } else {
      return false;
    }
  }
  

  /**
   * LHSType != RHSType
   * LHSType and RHSType are both resolved.  
   * LHSInnermostElementType in not a primitive.
   * LHSType isn't a pure class
   *   Can we store an object of type RHSType in a variable of type LHSType?
   * Throw an ArrayStoreException if we cannot.
   *
   * @param LHSType the left-hand-side type
   * @param RHSType the right-hand-size type
   */
  static void checkstoreNotArrayOfPrimitive (VM_Type LHSType, VM_Type RHSType) 
    throws VM_ResolutionException, ArrayStoreException  {
    int LHSDimension = LHSType.getDimensionality();
    if (LHSDimension == 0) {
      if (instanceOfInterface(LHSType.asClass(), 
                              RHSType.getTypeInformationBlock()))
	return; 
    } else {
      VM_Type LHSInnermostElementType = LHSType.asArray().getInnermostElementType();
      if (LHSInnermostElementType == VM_Type.JavaLangObjectType){
	if (instanceOfObjectArray(LHSDimension, RHSType)) 
	  return;
      } else {
	if (instanceOfArray(LHSInnermostElementType.asClass(), 
                            LHSDimension, RHSType))
	  return;
      }
    }
    
    throw new ArrayStoreException();
  }


  /**
   * LHSType != RHSType
   * LHSType and RHSType are both resolved.  
   * LHSType isn't a pure class
   *   Can we store an object of type RHSType in a variable of type LHSType?
   * Throw an ArrayStoreException if we cannot.
   *
   * @param LHSType the left-hand-side type
   * @param RHSType the right-hand-size type
   */
  static void checkstorePossibleArrayOfPrimitive (VM_Type LHSType, 
                                                  VM_Type RHSType) 
    throws VM_ResolutionException, ArrayStoreException {
    int LHSDimension = LHSType.getDimensionality();
    if (LHSDimension == 0) {
      if (instanceOfInterface(LHSType.asClass(), 
                              RHSType.getTypeInformationBlock()))
	return; 
    } else {
      VM_Type LHSInnermostElementType = LHSType.asArray().getInnermostElementType();
      if (LHSInnermostElementType == VM_Type.JavaLangObjectType){
	if (instanceOfObjectArray(LHSDimension, RHSType))
	  return;
      } else if (!LHSInnermostElementType.isPrimitiveType()) {
	if (instanceOfArray(LHSInnermostElementType.asClass(), 
                            LHSDimension, RHSType))
	  return;
      }
    }
    
    throw new ArrayStoreException();
  }

  
  ////////////////////
  // Implementation //
  ////////////////////


  // Most classes don't implement interfaces (or don't use the
  // interfaces they claim to implement.  Such classes will share
  // an initial implements trits vector filled with MAYBE values.
  //
  private static byte [] initialImplementsTrits; 
  static {
    initialImplementsTrits = new byte[MIN_IMPLEMENTS_TRITS_SIZE];
    for (int i=0; i<MIN_IMPLEMENTS_TRITS_SIZE; i++) {
      initialImplementsTrits[i] = MAYBE;
    }
  }

  /**
   * add methods of interface I to the IMT for class C
   */
  private static void populateIMT (VM_Class C, VM_Class I) {
    if (VM.VerifyAssertions) VM.assert(I.isInterface());
    if (C.isAbstract()) return; // no need to populate IMT for abstract class; 
                                // it will never be used.
    if (VM.BuildForEmbeddedIMT) {
      populateEmbeddedIMT(C,I);
    } else {
      populateIndirectIMT(C,I);
    }
  }

  /**
   * add methods of interface I to the IMT for class C
   */
  private static void populateEmbeddedIMT (VM_Class C, VM_Class I) {
    VM_Method [] interfaceMethods = I.getDeclaredMethods();
    synchronized (C) {
      if (C.IMTslotLists == null) {
	C.IMTslotLists = new VM_InterfaceMethodSignature.Link[IMT_METHOD_SLOTS];
      }
      for (int i=0; i<interfaceMethods.length; i++) {
	VM_Method m = interfaceMethods[i];
	if (m.isClassInitializer()) continue; 
	if (VM.VerifyAssertions) VM.assert(m.isPublic() && m.isAbstract()); 
	int id = VM_ClassLoader.findOrCreateInterfaceMethodSignatureId(m.getName(), m.getDescriptor());
	int index = VM_InterfaceMethodSignature.getIndex(id);
	if (VM_InterfaceMethodSignature.Link.isOn(C.IMTslotLists[index], id)) continue;
	VM_Method vm = C.findVirtualMethod(m.getName(), m.getDescriptor());
        
        // TODO!! when this fails: populate IMT with error stub 
	if (VM.VerifyAssertions) VM.assert(vm !=null && vm.isPublic() && !vm.isAbstract()); 

	Object [] tib = C.getTypeInformationBlock();
	C.addIMTslotElement(index, id, vm);
	int l = C.getIMTslotPopulation(index);
	if (l == 1) {
	  INSTRUCTION [] code = (INSTRUCTION []) tib[vm.getOffset()>>2];
	  tib[index+TIB_FIRST_INTERFACE_METHOD_INDEX] = code;
	} else {
	  INSTRUCTION[] stub = VM_InterfaceMethodConflictResolver.createStub(C.IMTslotLists[index], l);
	  tib[index+TIB_FIRST_INTERFACE_METHOD_INDEX] = stub;
	}
      }
    }
  }
  /**
   * add methods of interface I to the IMT for class C
   */
  private static void populateIndirectIMT (VM_Class C, VM_Class I) {
    VM_Method [] interfaceMethods = I.getDeclaredMethods();
    Object [] tib = C.getTypeInformationBlock();
    synchronized (C) {
      INSTRUCTION[][] IMT= (INSTRUCTION[][])tib[TIB_IMT_TIB_INDEX];
      // if needed, allocate the IMT
      if (IMT == null) {
        IMT = new INSTRUCTION[IMT_METHOD_SLOTS][];
        tib[TIB_IMT_TIB_INDEX] = IMT;
      }
      if (C.IMTslotLists == null) {
	C.IMTslotLists = new VM_InterfaceMethodSignature.Link[IMT_METHOD_SLOTS];
      }
      for (int i=0; i<interfaceMethods.length; i++) {
	VM_Method m = interfaceMethods[i];
	if (m.isClassInitializer()) continue; 
	if (VM.VerifyAssertions) VM.assert(m.isPublic() && m.isAbstract()); 
	int id = VM_ClassLoader.findOrCreateInterfaceMethodSignatureId
          (m.getName(), m.getDescriptor());
	int index = VM_InterfaceMethodSignature.getIndex(id);
	if (VM_InterfaceMethodSignature.Link.isOn(C.IMTslotLists[index], id)) continue;
	VM_Method vm = C.findVirtualMethod(m.getName(), m.getDescriptor());
        
        // TODO!! when this fails: populate IMT with error stub 
	if (VM.VerifyAssertions) VM.assert(vm != null && vm.isPublic() && !vm.isAbstract()); 

	C.addIMTslotElement(index, id, vm);
	int l = C.getIMTslotPopulation(index);
	if (l == 1) {
	  INSTRUCTION [] code = (INSTRUCTION []) tib[vm.getOffset()>>2];
	  IMT[index] = code;
	} else {
	  INSTRUCTION[] stub = VM_InterfaceMethodConflictResolver.createStub(C.IMTslotLists[index], l);
	  IMT[index] = stub;
	}
      }
    }
  }


  // create the iTable of interface I for class C
  //
  static void populateITable (VM_Class C, VM_Class I) {
    if (VM.VerifyAssertions) VM.assert(I.isInterface());
    if (C.isAbstract()) return; // no need to populate ITable for 
                                // abstract class; it will never be used.
    if (I.getDeclaredMethods().length == 0) return; // no need to 
                                     // populate when interface has no methods
    synchronized(C) {
      Object [] tib = C.getTypeInformationBlock();
      Object[] iTables = (Object[])tib[TIB_ITABLES_TIB_INDEX];
      int iTableIdx = -1;
      if (VM.DirectlyIndexedITables) {
	iTableIdx = I.getInterfaceId();
	if (iTables == null) {
	  iTables = new Object[iTableIdx+1];
	  tib[TIB_ITABLES_TIB_INDEX] = iTables;
	} else if (iTables.length <= iTableIdx) {
	  Object[] tmp = new Object[iTableIdx+1];
	  for (int i=0; i<iTables.length; i++) {
	    tmp[i] = iTables[i];
	  }
	  iTables = tmp;
	  tib[TIB_ITABLES_TIB_INDEX] = iTables;
	}
      } else {
	if (iTables == null) {
	  iTables = new Object[2];
	  iTableIdx = 1;
	  tib[TIB_ITABLES_TIB_INDEX] = iTables;
	} else {
	  for (int i=0; i<iTables.length; i++) {
	    if (((Object[])iTables[i])[0] == I) {
	      iTableIdx = i;
	      break;
	    }
	  }
	  if (iTableIdx == -1) {
	    iTableIdx = iTables.length;
	    Object[] tmp = new Object[iTables.length+1];
	    for (int i=0; i<iTables.length; i++) {
	      tmp[i] = iTables[i];
	    }
	    iTables = tmp;
	    tib[TIB_ITABLES_TIB_INDEX] = iTables;
	  }
	}
      }
      if (iTables[iTableIdx] == null) {
	VM_Method [] interfaceMethods = I.getDeclaredMethods();
	Object[] iTable = new Object[interfaceMethods.length+1];
	iTable[0] = I; // to support installing new compiled versions 
                       // of virtual methods
	for (int i=0; i<interfaceMethods.length; i++) {
	  VM_Method m = interfaceMethods[i];
	  if (m.isClassInitializer()) continue; 
	  if (VM.VerifyAssertions) VM.assert(m.isPublic() && m.isAbstract()); 
	  VM_Method vm = C.findVirtualMethod(m.getName(), m.getDescriptor());

	  // TODO!! when this fails: populate ITable with error stub
	  if (VM.VerifyAssertions) VM.assert(vm != null && vm.isPublic() && !vm.isAbstract()); 

	  iTable[I.getITableIndex(m)] = (INSTRUCTION []) tib[vm.getOffset()>>2];
	}
	iTables[iTableIdx] = iTable;
	// iTables[0] is a move to front cache; fill it here so we can
	// assume it always contains some iTable.
	iTables[0] = iTable;
      }
    }
  }

  // Grow trit vector
  //
  private static byte[] growImplementsTrits (byte[] oldIT) {
    int size = VM_Class.getInterfaceCount();
    if (size < MIN_IMPLEMENTS_TRITS_SIZE) {
      size = MIN_IMPLEMENTS_TRITS_SIZE;
    }
    byte[] newIT = new byte[size];
    for (int i=0; i<oldIT.length; i++) {
      newIT[i] = oldIT[i];
    }
    for (int i=oldIT.length; i<newIT.length; i++) {
      newIT[i] = MAYBE;
    }
    return newIT;
  }

  // TODO: restore me to private once VM_Type.isAssignableWith gets nuked.
  /*private*/ static boolean explicitImplementsTest (VM_Class I, VM_Class J) throws VM_ResolutionException {
    VM_Class [] superInterfaces = J.getDeclaredInterfaces();
    if (superInterfaces == null) return false;
    for (int i=0; i<superInterfaces.length; i++) {
      VM_Class superInterface = superInterfaces[i];
      if (!superInterface.isResolved()) {
	synchronized (VM_ClassLoader.lock) {
	  superInterface.load();
	  superInterface.resolve();
	}
      }
      if (!superInterface.isInterface()) throw new VM_ResolutionException(superInterface.getDescriptor(), new IncompatibleClassChangeError());
      if (I==superInterface || explicitImplementsTest(I, superInterface)) return true;
    }
    return false;
  }

  
  // TODO: restore me to private once VM_Type.isAssignableWith gets nuked.
  /* private */ static boolean compileTimeExplicitImplementsTest (VM_Class I, VM_Class J) {
    VM_Class [] superInterfaces = J.getDeclaredInterfaces();
    if (superInterfaces == null) return false;
    for (int i=0; i<superInterfaces.length; i++) {
      VM_Class superInterface = superInterfaces[i];
      if (superInterface.isResolved() && (I==superInterface || compileTimeExplicitImplementsTest(I, superInterface))) 
	return true;
    }
    return false;
  }
}

