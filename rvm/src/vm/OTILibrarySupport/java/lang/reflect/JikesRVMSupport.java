/*
 * Copyright IBM Corp 2002
 */
package java.lang.reflect;

import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_Field;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
public class JikesRVMSupport {

    public static Field createField(VM_Field m) {
	return new Field(m);
    }

    public static Method createMethod(VM_Method m) {
	return new Method(m);
    }

    public static Constructor createConstructor(VM_Method m) {
	return new Constructor(m);
    }

    public static VM_Field getFieldOf(Field f) {
	return f.field;
    }

    public static VM_Method getMethodOf(Method f) {
	return f.method;
    }

    public static VM_Method getMethodOf(Constructor f) {
	return f.constructor;
    }

}
