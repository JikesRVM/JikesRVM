/*
 * (C) Copyright IBM Corp. 2001
 */
class OPT_ConcreteMethodKey {
    private final VM_Type dispatchClass;
    private final VM_Atom name;
    private final VM_Atom descriptor;
    
    VM_Type getType() { return dispatchClass; }
    
    VM_Atom getName() { return name; }
    
    VM_Atom getDescriptor() { return descriptor; }
        
    VM_Method getMethod () {
	VM_Class cls;
	if (dispatchClass instanceof VM_Class)
	    cls = (VM_Class)dispatchClass;
	else
	    cls = (VM_Class) VM_Type.JavaLangObjectType;

	for (VM_Method newmeth = null;
	     (newmeth == null) && (cls != null); 
	     cls = cls.getSuperClass())
	{
	    newmeth = (VM_Method)cls.findDeclaredMethod(name, descriptor);
	    if (newmeth != null) return newmeth;
	}
	
	return null;
    } 

    OPT_ConcreteMethodKey(VM_Type c, VM_Atom n, VM_Atom d) {
	dispatchClass = c;
	name = n;
	descriptor = d;
    }
    
    OPT_ConcreteMethodKey(VM_Type c, VM_Method m) {
	dispatchClass = c;
	name = m.getName();
	descriptor = m.getDescriptor();
    }
    
    public int hashCode() {
	if (dispatchClass == null)
	    return name.hashCode();
	else
	    return dispatchClass.hashCode()|name.hashCode();
    }
    
    public boolean equals(Object o) {
	return
	    (o instanceof OPT_ConcreteMethodKey)
	                &&
	    (dispatchClass==((OPT_ConcreteMethodKey)o).dispatchClass)
	                &&
	    (name==((OPT_ConcreteMethodKey)o).name)
	                &&
	    (descriptor==((OPT_ConcreteMethodKey)o).descriptor);
    }
}

