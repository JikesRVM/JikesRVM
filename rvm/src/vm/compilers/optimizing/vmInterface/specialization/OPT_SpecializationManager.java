/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;

/**
 * @author Julian Dolby
 */
class OPT_SpecializationManager {
    
    static public final boolean DEBUG = true;

    static public OPT_SpecializationGraph G;

    static class OutEdges extends OPT_FilterEnumerator
	implements OPT_SpecializationGraphEdgeEnumeration
    {
	OutEdges(Enumeration e, OPT_FilterEnumerator.Filter f) {
	    super(e, f);
	}
	
	public OPT_SpecializationGraphEdge next() {
	    return (OPT_SpecializationGraphEdge) nextElement();
	}
    }

    static public OPT_SpecializationGraphEdgeEnumeration 
	computeApplicableSpecializations(OPT_SpecializationGraphNode src,
					 final GNO_InstructionLocation callSite)
    {
	GNO_InstructionLocation p = callSite.getInlinedParent();
	if (p == null) {
	    OPT_SpecializationGraphEdgeEnumeration e = G.enumerateEdges(src, callSite);
	    if (DEBUG && ! e.hasMoreElements())
		VM.sysWrite("warning: failed to find specializations for call site " + callSite + " to " + callSite.getTargetOfCall() + " of " + src + "\n");
	    return e;
	} else {
	    OPT_SpecializationGraphEdgeEnumeration parents = 
		new OutEdges(
		  computeApplicableSpecializations(src, p),
		  new OPT_FilterEnumerator.Filter() {
		    public boolean isElement(Object o) {
			OPT_SpecializationGraphEdge z = (OPT_SpecializationGraphEdge)o;
			return z.genericTargetMethod() == callSite.getMethod();
		    }
		 }
	       );

	    OPT_SpecializationGraphEdgeEnumeration e =
		new OPT_EmptySpecializationGraphEdgeEnumeration();

	    while ( parents.hasMoreElements() ) {
		OPT_SpecializationGraphEdgeEnumeration x =
		    G.enumerateEdges(
		       ((OPT_SpecializationGraphNode)parents.next().to()),
		       callSite);

		e = new OPT_CompoundSpecializationGraphEdgeEnumeration(e, x);
	    }
	    
	    if (DEBUG && ! e.hasMoreElements())
		VM.sysWrite("warning: failed to find specializations for inlined call site " + callSite + " to " + callSite.getTargetOfCall() + " of " + src + "\n");
	    
	    return e;
	}
    }
    
    static public OPT_SpecializationGraphEdgeEnumeration 
	computeChildSpecializations(OPT_SpecializationGraphNode src,
				    GNO_InstructionLocation callSite,
				    final VM_Method target) 
    {
	return 
	    new OutEdges(
	      G.enumerateEdges(src, callSite),
	      new OPT_FilterEnumerator.Filter() {
		public boolean isElement(Object o) {
		    OPT_SpecializationGraphEdge z = 
			(OPT_SpecializationGraphEdge)o;
		    return z.genericTargetMethod() == target;
		}
	    });
    }

    static class ConcreteMethodData {
	private final Vector callSiteContextTable = new Vector();
	private final Vector specializedCodeTable = new Vector();
	private int specializedCodeMapJTOCoffset = -1;

	void setCallSiteContext(int site, OPT_SpecializationGraphNode c) {
	    if (callSiteContextTable.size() < site+1)
		callSiteContextTable.setSize(2*site);
	    callSiteContextTable.setElementAt(c, site);
	}

	void setJTOCoffset(int offset) {
	    specializedCodeMapJTOCoffset = offset;
	}

	int getJTOCoffset() {
	    return specializedCodeMapJTOCoffset;
	}

	OPT_SpecializationGraphNode getCallSiteContext(int site) {
	    return (OPT_SpecializationGraphNode) 
		callSiteContextTable.elementAt(site);
	}

	VM_CompiledMethod getSpecializedCode(int site) {
	    return (VM_CompiledMethod) 
		specializedCodeTable.elementAt(site);
	}

	void setSpecializedCode(int site, VM_CompiledMethod code) {
	    if (specializedCodeTable.size() < site+1)
		specializedCodeTable.setSize(2*site);
	    specializedCodeTable.setElementAt(code, site);
	}
    }
    
    static private java.util.HashMap concreteMethodTable = new java.util.HashMap();

    static class SpecializedCodeMapSlot {
	final ConcreteMethodData table;
	final int callSiteIndex;
	
	SpecializedCodeMapSlot(ConcreteMethodData table, int index) {
	    this.table = table;
	    callSiteIndex = index;
	}
    }
    
    static class SpecializationGraphNodeData {

	private VM_CompiledMethod code;
	final private java.util.HashSet codeMapSlots = new java.util.HashSet();

	public void setCodeMapSlot(ConcreteMethodData table, int index) {
	    codeMapSlots.add( new SpecializedCodeMapSlot(table, index) );
	}

	public java.util.Iterator getCodeMapSlots() {
	    return codeMapSlots.iterator();
	}

	public void setCompiledMethod(VM_CompiledMethod code) {
	    this.code = code;
	}
	
	public VM_CompiledMethod getCompiledMethod() {
	    return code;
	}

    }

    static private java.util.HashMap SpecializationGraphNodeData = 
	new java.util.HashMap();

    static private void setCodeMapSlot(OPT_SpecializationGraphNode context,
				OPT_ConcreteMethodKey table,
				int callSiteIndex)
    {
	SpecializationGraphNodeData x = (SpecializationGraphNodeData)
	    SpecializationGraphNodeData.get(context);
	if (x == null) {
	    x = new SpecializationGraphNodeData();
	    SpecializationGraphNodeData.put(context, x);
	}

	ConcreteMethodData y = 
	    (ConcreteMethodData)concreteMethodTable.get(table);

	x.setCodeMapSlot(y, callSiteIndex);
    }

    static private java.util.Iterator 
	getCodeMapSlots(OPT_SpecializationGraphNode context) 
    {
	SpecializationGraphNodeData x = (SpecializationGraphNodeData)
	    SpecializationGraphNodeData.get(context);
	if (x != null)
	    return x.getCodeMapSlots();
	else
	    return new OPT_EmptyIterator();
    }
    
    static private void setCompiledMethod(OPT_SpecializationGraphNode context,
				   VM_CompiledMethod code)
    {
	SpecializationGraphNodeData x = (SpecializationGraphNodeData)
	    SpecializationGraphNodeData.get(context);
	if (x == null) {
	    x = new SpecializationGraphNodeData();
	    SpecializationGraphNodeData.put(context, x);
	}

	x.setCompiledMethod(code);
    }

    static private VM_CompiledMethod
	getCompiledMethod(OPT_SpecializationGraphNode context)
    {
	SpecializationGraphNodeData x = (SpecializationGraphNodeData)
	    SpecializationGraphNodeData.get(context);

	if (x != null)
	    return x.getCompiledMethod();
	else
	    return null;
    }

    static private void
	setContextForSite(VM_Type dispatchClass,
			  VM_Atom name,
			  VM_Atom descriptor,
			  int callSiteIndex,
			  OPT_SpecializationGraphNode specializedTarget)
    {
	OPT_ConcreteMethodKey key = 
	    new OPT_ConcreteMethodKey(dispatchClass, name, descriptor);
	ConcreteMethodData data = 
	    (ConcreteMethodData) concreteMethodTable.get(key);
	if (data == null) {
	    data = new ConcreteMethodData();
	    concreteMethodTable.put( key, data );
	}

	data.setCallSiteContext( callSiteIndex, specializedTarget );
    }

    static public OPT_SpecializationGraphNode 
	findContextForSite(VM_Type dispatchClass,
			   VM_Atom name,
			   VM_Atom descriptor,
			   int callSiteIndex)
    {
	OPT_ConcreteMethodKey key = 
	    new OPT_ConcreteMethodKey(dispatchClass, name, descriptor);
	ConcreteMethodData data = 
	    (ConcreteMethodData) concreteMethodTable.get(key);
	return data.getCallSiteContext( callSiteIndex );
    }

    static private void 
	setJTOCoffset(OPT_ConcreteMethodKey key, int JTOCoffset)
    {
	ConcreteMethodData data = 
	    (ConcreteMethodData) concreteMethodTable.get(key);
	if (data == null) {
	    data = new ConcreteMethodData();
	    concreteMethodTable.put( key, data );
	}

	data.setJTOCoffset( JTOCoffset );
    }

    static public int getJTOCoffset(OPT_ConcreteMethodKey key) {
	ConcreteMethodData data = 
	    (ConcreteMethodData) concreteMethodTable.get(key);
	return data.getJTOCoffset( );
    }
    
    static public void setCompiledCode(OPT_SpecializationGraphNode context,
				       VM_CompiledMethod specializedCode)
    {
	java.util.Iterator e = getCodeMapSlots( context );
	while ( e.hasNext() ) {
	    SpecializedCodeMapSlot s = (SpecializedCodeMapSlot)e.next();
	    ConcreteMethodData dat = s.table;

	    // record method in specialization manager data structure
	    int callSiteNumber = s.callSiteIndex;
	    dat.setSpecializedCode(callSiteNumber, specializedCode);
	    
	    // record method in runtime Specialized Code Maps
	    int tableJTOCoffset = dat.getJTOCoffset();
	    Object slot = VM_Statics.getSlotContentsAsObject(tableJTOCoffset);
	    INSTRUCTION[][] spm = (INSTRUCTION[][])slot;
	    spm[callSiteNumber] = specializedCode.getInstructions();
	}
    }

    static public VM_CompiledMethod getCompiledCode(VM_Type typeOfThis, 
					     VM_Method invokedMethod, 
					     int callSiteNumber)
    {
	OPT_ConcreteMethodKey key = 
	    new OPT_ConcreteMethodKey(typeOfThis, invokedMethod);
	ConcreteMethodData data = 
	    (ConcreteMethodData) concreteMethodTable.get(key);

	return data.getSpecializedCode(callSiteNumber);

    }

    static public INSTRUCTION[]
	specializeAndInstall(VM_Type dispatchClass,
			     VM_Atom name,
			     VM_Atom descriptor,
			     int callSiteIndex)
    {
	if (DEBUG) 
	    VM.sysWrite("installing " + dispatchClass + " " + name + " " + descriptor + " " + callSiteIndex + "\n");
	
	OPT_SpecializationGraphNode context =
	    findContextForSite(dispatchClass, name, descriptor, callSiteIndex);

	VM_Method methodToSpecialize = context.getSpecializedMethod();

	return specializeAndInstall(methodToSpecialize, context);
    }

    static public INSTRUCTION[]
	specializeAndInstall(VM_Method genericMethod, 
			     OPT_SpecializationGraphNode context)
    {
	VM_CompiledMethod code = 
	    VM_RuntimeOptCompilerInfrastructure.
	        optCompileWithFallBack(genericMethod, context);

	VM_CompiledMethods.setCompiledMethod(code.getId(), code);

	setCompiledCode(context, code);

	return code.getInstructions();
    }

    static private void assignCallSiteNumbers() {
	OPT_SpecializationGraphEdgeEnumeration e = G.enumerateEdges();
	while ( e.hasMoreElements() ) {
	    OPT_SpecializationGraphEdge edge = e.next();
	    OPT_SpecializationGraphNode src =
		(OPT_SpecializationGraphNode)edge.from();
	    GNO_InstructionLocation callSite = edge.callSite();
	    int callSiteNumber = VM_SpecializationCallSites.
		ensureCallSiteNumber(src, callSite);
	}

	if (DEBUG) {
	    OPT_SpecializationGraphEdgeEnumeration ee = G.enumerateEdges();
	    while ( ee.hasMoreElements() ) {
		OPT_SpecializationGraphEdge edge = ee.next();
		OPT_SpecializationGraphNode src =
		    (OPT_SpecializationGraphNode)edge.from();
		OPT_SpecializationGraphNode target =
		    (OPT_SpecializationGraphNode)edge.to();
		GNO_InstructionLocation callSite = edge.callSite();
		int callSiteNumber = VM_SpecializationCallSites.
		    getCallSiteNumber(src,callSite);

		if (DEBUG) {
		    VM.sysWrite("from " +  src + " and\n");
		    VM.sysWrite("from " +  callSite + " -->\n");
		    VM.sysWrite("uses call site " + callSiteNumber + "\n");
		    VM.sysWrite("to " + target);
		    VM.sysWrite("\n");
		}
	    }
	}

    }

    static private void setCallSiteTargets() {
	OPT_SpecializationGraphEdgeEnumeration e = G.enumerateEdges();
	while ( e.hasMoreElements() ) {
	    OPT_SpecializationGraphEdge edge = e.next();
	    OPT_SpecializationGraphNode src =
		(OPT_SpecializationGraphNode)edge.from();
	    OPT_SpecializationGraphNode dest =
		(OPT_SpecializationGraphNode)edge.to();
	    GNO_InstructionLocation callSite = edge.callSite();
	    VM_Method target = callSite.getTargetOfCall();
	    int callSiteNumber =  VM_SpecializationCallSites.
		getCallSiteNumber(src, callSite);
	    VM_Type concreteType = edge.genericTargetClass();
	    if (concreteType == null) concreteType=target.getDeclaringClass();
	    OPT_ConcreteMethodKey k =
		new OPT_ConcreteMethodKey(concreteType, target);
	    setContextForSite( concreteType,
			       target.getName(),
			       target.getDescriptor(),
			       callSiteNumber,
			       dest);
	    setCodeMapSlot(dest, k, callSiteNumber);
	}
    }

    static private void insertSpecializedCodeMaps() {
	java.util.Iterator e = concreteMethodTable.keySet().iterator();
	while ( e.hasNext() ) {
	    OPT_ConcreteMethodKey key = (OPT_ConcreteMethodKey) e.next();
	    int size = VM_SpecializationCallSites.getNumberOfSites(key);

	    if (size == 0) {
		if (DEBUG)
		    VM.sysWrite("size 0 table for " + key.getMethod() + "!");
		continue;
	    }
	    
	    int oldSlot = getJTOCoffset( key );
	    if (oldSlot != -1) {
		Object x = VM_Statics.getSlotContentsAsObject(oldSlot);
		INSTRUCTION[][] oldTable = (INSTRUCTION[][])x;
		int oldSize = oldTable.length;
		if (oldSize < size) {
		    INSTRUCTION[][] newTable = new INSTRUCTION[size][];
		    VM_Statics.setSlotContents(oldSlot, newTable);
		    for(int i  = 0; i < oldTable.length; i++)
			newTable[i] = oldTable[i];
		    for(int i  = oldTable.length; i < newTable.length; i++)
			newTable[i] = VM_LazySpecializationTrampoline.
			    getLazySpecializerInstructions();
		}
	    } else {
		int start = 0;
		INSTRUCTION[][] table = new INSTRUCTION[size][];
		int JTOCoffset =
		    VM_Statics.allocateSlot(VM_Statics.REFERENCE_FIELD);

		VM_Statics.setSlotContents(JTOCoffset, table);
		setJTOCoffset( key, JTOCoffset);

		VM_Method m = key.getMethod();
		if (m.isCompiled()) {
		    table[0] = m.getMostRecentlyGeneratedInstructions(); 
		    start = 1;
		}

		for(int i  = start; i < table.length; i++)
		    table[i] = VM_LazySpecializationTrampoline.
			getLazySpecializerInstructions();
	    }
	}
    }
    
    static private void insertDispatchTrampolines() {
	java.util.Iterator e = concreteMethodTable.keySet().iterator();
	while ( e.hasNext() ) {
	    OPT_ConcreteMethodKey key = (OPT_ConcreteMethodKey) e.next();
	    try {
		VM_Type type = key.getType();
		if (! type.isLoaded()) type.load();
		if (! type.isResolved()) type.resolve();
	    } catch (VM_ResolutionException x) {
		throw new java.lang.Error("cannot specialize " + key);
	    }
	    OPT_SpecializationDispatchTrampolines.insert( key );
	}
    }

    static private void patchExistingCallSites() {
	OPT_SpecializationGraphEdgeEnumeration e = G.enumerateEdges();
	while ( e.hasMoreElements() ) {
	    OPT_SpecializationGraphEdge edge = e.next();
	    GNO_InstructionLocation callSite = edge.callSite();
	    OPT_SpecializationGraphNode src = 
		(OPT_SpecializationGraphNode)edge.from();

	    if (src == null) {
		// patch any existing unspecialized call site
		int num =  VM_SpecializationCallSites.
		    getCallSiteNumber(null,callSite);
		VM_Method method = callSite.getMethod();
		Enumeration ce = VM_CodeFinder.getCompiledCode(method);
		while ( ce.hasMoreElements() ) {
		    VM_CompiledMethod x = (VM_CompiledMethod) ce.nextElement();
		    OPT_SpecializationCodePatching.
			insertCallSiteNumber(x, callSite, num);
		}
	    } else {
		// check for previously specialized method
		VM_CompiledMethod x = getCompiledMethod( src );
		if (x != null) {
		    int num = VM_SpecializationCallSites.
			getCallSiteNumber(src, callSite);
		    OPT_SpecializationCodePatching.
			insertCallSiteNumber(x, callSite, num);
		}
	    }
	}
    }

    static public void specialize(OPT_SpecializationGraph inputG) {
	VM_SpecializationSentry.setValid(false);
	G = inputG;

	assignCallSiteNumbers();
	setCallSiteTargets();
	insertSpecializedCodeMaps();
	insertDispatchTrampolines();
	patchExistingCallSites();

	VM_SpecializationSentry.setValid(true);
    }
}

