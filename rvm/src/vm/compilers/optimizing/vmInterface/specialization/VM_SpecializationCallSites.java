/*
 * (C) Copyright IBM Corp. 2001
 */
import java.util.Vector;

class VM_SpecializationCallSites {

    static class CallSiteKey {
	private java.util.HashSet edgeKeys;

	public int hashCode() {
	    return edgeKeys.hashCode();
	}

	public boolean equals(Object o) {
	    if (o instanceof CallSiteKey) {
		CallSiteKey other = (CallSiteKey) o;
		if (edgeKeys.equals(other.edgeKeys)) 
		    return true;
	    }
	    
	    return false;
	}
	    
	class EdgeKey {
	    private VM_Type dispatchOnClass;
	    private OPT_SpecializationGraphNode target;
	    
	    EdgeKey(OPT_SpecializationGraphEdge e) {
		dispatchOnClass = e.genericTargetClass();
		target = (OPT_SpecializationGraphNode)e.to();
	    }
	    
	    public int hashCode() {
		if (dispatchOnClass != null)
		    return target.hashCode()|dispatchOnClass.hashCode();
		else
		    return target.hashCode();
	    }
	    
	    public boolean equals(Object o) {
		if (o instanceof EdgeKey
		                &&
		    ((EdgeKey)o).dispatchOnClass == dispatchOnClass
		                &&
		    ((EdgeKey)o).target == target)
		{
		    return true;
		} else
		    return false;
	    }
	}
	
	CallSiteKey(OPT_SpecializationGraphNode src, 
		    GNO_InstructionLocation call) 
	{
	    edgeKeys = new java.util.HashSet();
	    OPT_SpecializationGraphEdgeEnumeration e = 
		OPT_SpecializationManager.
		     computeApplicableSpecializations(src, call);
	    while ( e.hasMoreElements() )
		edgeKeys.add( new EdgeKey( e.next() ) );
	}
    }

    static class NominalMethodInfo {
	private final Vector nominalCallSites;
	private int nextIndex;

	NominalMethodInfo() {
	    nominalCallSites = new Vector(1);
	    nominalCallSites.add( null );
	    nextIndex = 1;
	}

	int getCallSiteNumber(CallSiteKey callSite) {
	    int index = nominalCallSites.indexOf( callSite );
	    if ( index > 0) return index;
	    else return 0;
	}

	int ensureCallSiteNumber(CallSiteKey callSite) {
	    int index = getCallSiteNumber( callSite );
	    if ( index > 0) return index;
	    else {
		nominalCallSites.add( callSite );
		return nextIndex++;
	    }
	}

	int getNumberOfSites() {
	    return nominalCallSites.size();
	}
    }

    static class NominalMethodKey {
	final private VM_Atom name;
	final private VM_Atom descriptor;

	public int hashCode() {
	    return name.hashCode() + descriptor.hashCode();
	}

	public boolean equals(Object o) {
	    return (o instanceof NominalMethodKey)
		                &&
		(name == ((NominalMethodKey)o).name)
		                &&
		(descriptor == ((NominalMethodKey)o).descriptor);
	}

	NominalMethodKey(VM_Method nominalMethod) {
	    name = nominalMethod.getName();
	    descriptor = nominalMethod.getDescriptor();
	}
    
	NominalMethodKey(VM_Atom name, VM_Atom descriptor) {
	    this.name = name;
	    this.descriptor = descriptor;
	}
    }

    static private java.util.HashMap nominalMethods = new java.util.HashMap();

    static private Object getMethodKey(VM_Method callTarget) {
	if ( callTarget.getDeclaringClass().isLoaded() 
	                     &&
	     (callTarget.isStatic() || callTarget.isObjectInitializer()))
	{
	    // known exactly which methid is involved, so use it
	    return callTarget;
	} else
	    // potential for virtual dispatch, use nominal method
	    return new NominalMethodKey( callTarget );
    }
    
    static public int getCallSiteNumber(OPT_SpecializationGraphNode src, 
					GNO_InstructionLocation call) 
    {
	Object key = getMethodKey( call.getTargetOfCall() );
	NominalMethodInfo x = (NominalMethodInfo) nominalMethods.get(key);
	if (x == null) return 0;

	return x.getCallSiteNumber( new CallSiteKey(src, call) );
    }

    static public int ensureCallSiteNumber(OPT_SpecializationGraphNode src, 
					   GNO_InstructionLocation call) 
    {
	Object key = getMethodKey( call.getTargetOfCall() );
	NominalMethodInfo x = (NominalMethodInfo) nominalMethods.get(key);
	if (x == null) {
	    x = new NominalMethodInfo();
	    nominalMethods.put(key, x);
	}

	return x.ensureCallSiteNumber( new CallSiteKey(src, call) );
    }

    static public int getCallSiteNumber(OPT_SpecializationGraphNode src,
					OPT_Instruction call) 
    {
	return getCallSiteNumber(src, new GNO_InstructionLocation(call));
    }

    static public int ensureCallSiteNumber(OPT_SpecializationGraphNode src,
					   OPT_Instruction call) 
    {
	return ensureCallSiteNumber(src, new GNO_InstructionLocation(call));
    }

    static public int getCallSiteNumber(OPT_SpecializationGraphNode src,
					VM_Method m,
					int bc)
    {
	boolean savedValid = VM_SpecializationSentry.isValid();
	VM_SpecializationSentry.setValid(false);
	int num = getCallSiteNumber(src, new GNO_InstructionLocation(m,bc));
	VM_SpecializationSentry.setValid(savedValid);
	return num;
    }

    static public int ensureCallSiteNumber(OPT_SpecializationGraphNode src,
					   VM_Method m,
					   int bc)
    {
	boolean savedValid = VM_SpecializationSentry.isValid();
	VM_SpecializationSentry.setValid(false);
	int num = ensureCallSiteNumber(src, new GNO_InstructionLocation(m,bc));
	VM_SpecializationSentry.setValid(savedValid);
	return num;
    }

    static public int getNumberOfSites(OPT_ConcreteMethodKey key) {
	Object nkey = getMethodKey( key.getMethod() );
	NominalMethodInfo ninfo = (NominalMethodInfo) nominalMethods.get(nkey);
	return (ninfo == null)? 0: ninfo.getNumberOfSites();
    }

}
