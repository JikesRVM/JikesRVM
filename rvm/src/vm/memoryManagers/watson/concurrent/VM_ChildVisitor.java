/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * @author David Bacon
 */
public abstract class VM_ChildVisitor
    extends VM_RCGC
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    protected boolean visit(VM_Address object) { VM.assert(false); return false; }
    protected boolean visit(VM_Address object, VM_Address objectRef) { VM.assert(false); return false; }

    protected final boolean ignorePrimitiveTypes = false;

    private static final boolean measureDepth = false;
    private static int depth;

    public final boolean visitChildren(VM_Address object) {
	if (measureDepth) {
	    depth++;
	    if ((depth % 50) == 0) 
		println("Depth ", depth);
	}

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(object));

	if (trace1) VM.sysWrite("(" + type.toString() + " ");

	if (type.isClassType()) { 
	    if (trace1) VM.sysWrite("<");
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		VM_Address objectRef = VM_Magic.getMemoryAddress(object.add(referenceOffsets[i]));
		if (!objectRef.isZero())
		    if (! visit(objectRef)) {
			if (measureDepth) depth--;
			return false;
		    }
	    }
	    if (trace1) VM.sysWrite(">");
	} 
	else if (type.isArrayType()) {
	    if (type.asArray().getElementType().isReferenceType()) { // ignore scalar arrays
		if (trace1) VM.sysWrite("[");
		int elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(object));
		for (int i = 0; i < elements; i++) {
		    VM_Address objectRef = VM_Magic.getMemoryAddress(object.add(i<<2));
		    if (!objectRef.isZero())
			if (! visit(objectRef)) {
			    if (measureDepth) depth--;
			    return false;
			}
		}
		if (trace1) VM.sysWrite("]");
	    } 
	}
	else if (type.isPrimitiveType()) {
	    if (! ignorePrimitiveTypes)
		fail();
	}
	else {
	    fail();
	}

	if (trace1) VM.sysWrite(")");
	if (measureDepth) depth--;

	return true;
    }

    // cloned from above routine -- provides edge info for debug purposes.
    // should merge routines back together at some point.
    public final boolean visitChildrenWithEdges(VM_Address object) {

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(object));

	if (trace1) VM.sysWrite("(" + type.toString() + " ");

	if (type.isClassType()) { 
	    if (trace1) VM.sysWrite("<");
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		VM_Address objectRef = VM_Magic.getMemoryAddress(object.add(referenceOffsets[i]));
		if (!objectRef.isZero())
		    if (! visit(object, objectRef))
			return false;
	    }
	    if (trace1) VM.sysWrite(">");
	} 
	else if (type.isArrayType()) {
	    if (type.asArray().getElementType().isReferenceType()) { // ignore scalar arrays
		if (trace1) VM.sysWrite("[");
		int elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(object));
		for (int i = 0; i < elements; i++) {
		    VM_Address objectRef = VM_Magic.getMemoryAddress(object.add(i<<2));
		    if (!objectRef.isZero())
			if (! visit(object, objectRef))
			    return false;
		}
		if (trace1) VM.sysWrite("]");
	    } 
	}
	else if (type.isPrimitiveType()) {
	    if (! ignorePrimitiveTypes)
		fail();
	}
	else {
	    fail();
	}

	if (trace1) VM.sysWrite(")");
	return true;
    }

    protected static final void fail() {
	VM.sysWrite("VM_ChildVisitor.visitChildren: unexpected type (not class or array)");
	VM.sysExit(1000);
    }
}
