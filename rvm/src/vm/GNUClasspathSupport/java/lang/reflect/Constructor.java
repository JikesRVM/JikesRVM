package java.lang.reflect;

import com.ibm.JikesRVM.librarySupport.ReflectionSupport;
import com.ibm.JikesRVM.VM_Method;

public final class Constructor extends AccessibleObject implements Member
{
    VM_Method constructor;

    /**
     * Prevent this class from being instantiated.
     */
    private Constructor() {}

    // For use by java.lang.Class
    Constructor(VM_Method m) {
	constructor = m;
    }

    // For use by java.io.ObjectInputStream
    VM_Method getConstructor() {
	return constructor;
    }

    public boolean equals(Object object) {
	if (object != null && object instanceof Constructor) {
	    Constructor other = (Constructor)object;
	    
	    // The underlying methods are unique because
	    //  1) The only path to a Constructor is Class.getConstructor
	    //  2) Class has a unique map to VM_Class
	    //  3) VM_Methods are unique in VM_Classes.
	    //
	    return constructor == other.constructor;
	}
	return false;
    }
    
    public Class getDeclaringClass() {
	return ReflectionSupport.getDeclaringClass(this);
    }

    public Class[] getExceptionTypes() {
	return ReflectionSupport.getExceptionTypes(this);
    }

    public int getModifiers() {
	return ReflectionSupport.getModifiers(this);
    }

    public String getName() {
	return getDeclaringClass().getName();
    }
    
    public Class[] getParameterTypes() {
	return ReflectionSupport.getParameterTypes(this);
    }

    public String getSignature() {
	return ReflectionSupport.getSignature(this);
    } 

    public int hashCode() {
	return getName().hashCode();
    }

    public Object newInstance(Object args[])
	throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
	return ReflectionSupport.newInstance(this,args);
    }

    public String toString() {
	StringBuffer buf;
	String mods;
	Class[] types;
	Class current;
	int i, arity;
	
	buf = new StringBuffer();
	mods = Modifier.toString(getModifiers());
	if(mods.length() != 0)
	    {
		buf.append(mods);
		buf.append(" ");
	    }
	buf.append(getName());
	buf.append("(");
	types = getParameterTypes();
	for(i = 0; i < types.length; i++)
	{
	    current = types[i];
	    arity = 0;
	    while(current.isArray())
		{
		    current = current.getComponentType();
		    arity++;
		}
	    buf.append(current.getName());
	    for(;arity > 0; arity--) buf.append("[]");
	    if(i != (types.length - 1))
		buf.append(",");
	}
	buf.append(")");
	types = getExceptionTypes();
	if(types.length > 0)
	    {
		buf.append(" throws ");
		for(i = 0; i < types.length; i++)
		    {
			current = types[i];
			buf.append(current.getName());
			if(i != (types.length - 1))
			    buf.append(",");
		    }
	    }

	return buf.toString();
    }
}
