/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An special type used as a place holder for an uninitialized object
 * This is used to implement the "new" bytecode using reflection
 * The "new" bytecode will push this special type onto the operand stack
 * then when invokespecial calls the initializer method, we will detect 
 * this special type and run the reflective method Constructor.newInstance(..)
 * Afterward, all instance of this special type on the current frame that 
 * match the type and bytecode index will be replaced with the new object
 * created by reflection
 * @author Ton Ngo (7/17/98)
 */

public class unInitType {
  /** 
   * Index of the "new" bytecode that creates this class instance
   */
  int bcIndex;               

  /**
   * The class to create the instance
   */
  VM_Class newClass;

  public unInitType (int bc, VM_Class klass) {
    bcIndex = bc;
    newClass = klass;
  }

  public VM_Class getVMClass () {
    return newClass;
  }

  public int getIndex() {
    return bcIndex;
  }

  public boolean equal(unInitType t) {
    if ((bcIndex==t.getIndex()) && 
	(newClass==t.getVMClass()))
      return true;
    else 
      return false;
  }

}
