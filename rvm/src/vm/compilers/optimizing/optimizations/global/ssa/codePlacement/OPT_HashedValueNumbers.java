/*
 * (C) Copyright IBM Corp. 2001
 */
import instructionFormats.*;
/**
 * This class provides global value numbers based on hashing.
 *
 * @author Martin Trapp
 * @modification Matthew Arnold
 */
class OPT_HashedValueNumbers implements OPT_Operators {

  private static boolean DEBUG = false;
  /**
   * The constructor.
   */
  OPT_HashedValueNumbers () {
    actNum = 2;
    hash = new JDK2_HashMap();
  }
  
  /**
   * Get the value number for an instruction or an operand
   * @param name
   * @return 
   */
  public int getValueNumber (Object name) {

    //VM.sysWrite("Get val num for "+name+"\n");
    if (name instanceof OPT_Operand) {
      OPT_Operand op = (OPT_Operand) name;
      return operandValue (op);
    } else {
      OPT_Instruction inst = (OPT_Instruction) name;
      return instructionResult (inst);
    }
  }
  

  // Implementation

  /**
   * next vailable value number
   */
  private static int actNum;
  
  /**
   * This HashMap keeps track of known values
   */
  private JDK2_HashMap hash;
  


  private int operandValue (OPT_Operand op)
  {
    Integer Num;
    
    if (op instanceof OPT_RegisterOperand) {
      OPT_Register reg = ((OPT_RegisterOperand)op).register;
      OPT_RegisterOperandEnumeration defs = OPT_RegisterInfo.defs(reg);
      if (reg.isPhysical()) {
	// these are nasty
	// todo: only handle volatile registers in this crude manner
	return  actNum++;
      } 

      if (!defs.hasMoreElements()) {
	  if (DEBUG || ((OPT_RegisterOperand)op).type != OPT_ClassLoaderProxy.VALIDATION_TYPE)
	      VM.sysWrite ("Use without def for " + op + " in "
			   + op.instruction + "\n");
	  return actNum++;
      } 
      //if (VM.VerifyAssertions) VM.assert (defs.hasMoreElements());
      OPT_Instruction def = defs.next().instruction;
      // make sure we are in SSA
      if (VM.VerifyAssertions) VM.assert (!defs.hasMoreElements());
      
      
      if (def.operator.opcode == IR_PROLOGUE_opcode) {
	// seen this register before?
 	Num = (Integer)hash.get(reg);
 	if (Num == null) {
 	  // no? so make up a new number.
 	  Num = new Integer(actNum++);
 	  hash.put(reg, Num);
 	}
 	return Num.intValue();
      }
      
      return  getValueNumber(def);
    }

    if (op instanceof OPT_MethodOperand) {
      VM_Method m = ((OPT_MethodOperand)op).method;
      Num = (Integer)hash.get(m);
      if (Num == null) {
	// no? so make up a new number.
	Num = new Integer(actNum++);
	hash.put(m, Num);
      }
      return Num.intValue();
    }
    if (op instanceof OPT_TypeOperand) {
      VM_Type t = ((OPT_TypeOperand)op).type;
      Num = (Integer)hash.get(t);
      if (Num == null) {
	// no? so make up a new number.
	Num = new Integer(actNum++);
	hash.put(t, Num);
      }
      return Num.intValue();
    }

    // must be some constant.
    return constantValue (op);
  }


  private int constantValue (OPT_Operand coop)
  {
    Object Val;
    Integer Num;
    
    if (coop instanceof OPT_NullConstantOperand) {return 0;}
    if (coop instanceof OPT_TrueGuardOperand) {return 1;}
    if (coop instanceof OPT_IntConstantOperand) {
      Val = new Integer(((OPT_IntConstantOperand)coop).value);
    } else if (coop instanceof OPT_LongConstantOperand) {
      Val = new Long(((OPT_LongConstantOperand)coop).value);
    } else if (coop instanceof OPT_ConditionOperand) {
      Val = new Integer(((OPT_ConditionOperand)coop).value);
    } else if (coop instanceof OPT_StringConstantOperand) {
      Val = ((OPT_StringConstantOperand)coop).value;
    } else if (coop instanceof OPT_TypeOperand) {
      Val = ((OPT_TypeOperand)coop).type;
    } else if (coop instanceof OPT_DoubleConstantOperand) {
      Val = new Double(((OPT_DoubleConstantOperand)coop).value);
    } else if (coop instanceof OPT_FloatConstantOperand) {
      Val = new Float(((OPT_FloatConstantOperand)coop).value);
    } else {
      VM.sysWrite(coop.getClass().toString() + ": " + coop + "\n");
      return actNum++;
    }
    
    Num = (Integer)hash.get(Val);
    if (Num == null) {
      Num = new Integer(actNum++);
      hash.put(Val, Num);
    }
    return Num.intValue();
  }

  
  private int instructionResult (OPT_Instruction inst)
  {
    int res;
    Integer Num;
    
    // Hmm.  Why are we getting an null here and what 
    // should we do?
    if (inst == null)
      return -1;

    // lookup the instruction. Seen this instruction before?
    Num = (Integer) hash.get (inst);
    if (Num != null) {
      // been there, done that, bye.
      return Num.intValue();
    }
    if (inst.isMove()) {
      // special case for move: copy propagate the rhs.
      hash.put (inst, new Integer(actNum++));
      res = getValueNumber (Move.getVal (inst));
      Num = new Integer (res);
      hash.put (inst, Num);
      return res;
    } else if (inst.operator.opcode == PI_opcode) {// same for PIs
      hash.put (inst, new Integer (actNum++));
      res = getValueNumber (GuardedUnary.getVal (inst));
      Num = new Integer (res);
      hash.put (inst, Num);
      return res;
    }
    else if (inst.isConditionalBranch()) {
      return handleConditionalBranch(inst);
    }

    if (inst.getNumberOfUses() > OpTuple.size
	|| inst.getNumberOfDefs() != 1
	|| inst.isBranch() 
	|| inst.isImplicitStore()
	|| inst.isImplicitLoad()
	|| inst.isAllocation()) {
      // won't handle that. Make up a new number.
      res = actNum++;
      Num = new Integer(res);
      hash.put(inst, Num);
      return  res;
    }

    if (Phi.conforms(inst) || inst.operator.opcode == GUARD_COMBINE_opcode) {
      // break possible recursion for these two
      res = actNum++;
      Num = new Integer(res);
      hash.put(inst, Num);
    }

    // make an OpTuple and fill in the operands' value numbers
    OpTuple op = new OpTuple(inst.operator);
    OPT_OperandEnumeration e = inst.getUses();
    int i = -1;
    while (e.hasMoreElements()) {
      OPT_Operand x = e.next();
      op.ops[++i] = getValueNumber(x);
      //VM.sysWrite(x.toString()+": ["+getValueNumber(x)+"]\n");
    }

    // lookup the OpTuple. Seen something equivalent before?
    Num = (Integer)hash.get(op);
    if (Num == null) {
      // No? So give it a new value.
      Num = new Integer(actNum++);
      hash.put(op, Num);
    }
    // remember this...
    hash.put(inst, Num);
    
    return Num.intValue();
  }

  

  /**
   * Get the result operand of an instruction
   * @param inst
   * @return 
   */
  OPT_RegisterOperand getResult (OPT_Instruction inst) {
    if (ResultCarrier.conforms(inst))
      return ResultCarrier.getResult(inst);
    if (GuardResultCarrier.conforms(inst))
      return GuardResultCarrier.getGuardResult(inst);
    if (Phi.conforms(inst)) {
      OPT_Operand op = Phi.getResult(inst);
      if (op instanceof OPT_RegisterOperand)
	return op.asRegister();
    }
    return null;
  }

  /**
   * Compute the value number for a conditional branch
   *
   * @param inst The instruction in question
   * @return The value number for that instruction
   */
  private int handleConditionalBranch(OPT_Instruction inst) {
    // make an OpTuple and fill in the operands' value numbers
    OpTuple op = new OpTuple(inst.operator);
    int i = -1;
    if (IfCmp.conforms(inst)) {
      op.ops[++i] = getValueNumber(IfCmp.getVal1(inst));
      op.ops[++i] = getValueNumber(IfCmp.getVal2(inst));
      op.ops[++i] = getValueNumber(IfCmp.getCond(inst));
    }
    else if (MethodIfCmp.conforms(inst)) {
      op.ops[++i] = getValueNumber(MethodIfCmp.getValue(inst));
      op.ops[++i] = getValueNumber(MethodIfCmp.getMethod(inst));
      op.ops[++i] = getValueNumber(MethodIfCmp.getCond(inst));
    }
    else if (TypeIfCmp.conforms(inst)) {
      op.ops[++i] = getValueNumber(TypeIfCmp.getValue(inst));
      op.ops[++i] = getValueNumber(TypeIfCmp.getType(inst));
      op.ops[++i] = getValueNumber(TypeIfCmp.getCond(inst));
    }
    else {
      // Should never reach here.
      throw new OPT_OptimizingCompilerException("Error:  Branch instruction not handled. in OPT_HashedValueNumbers.handleConditionalBranch" + 
						inst.toString() );
    }

    // lookup the OpTuple. Seen something equivalent before?
    if (DEBUG) VM.sysWrite("Op:  " + op + "\n");
    Integer Num = (Integer)hash.get(op);

    if (Num == null) {
      if (DEBUG) VM.sysWrite("Creating new num (" + (actNum+1) + 
			     ") because it wasn't in the hash\n");
      // No? So give it a new value.
      Num = new Integer(actNum++);
      hash.put(op, Num);
    }
    if (DEBUG)
      VM.sysWrite("Hashing inst " + inst + " num " + Num + "\n");
    // remember this...
    hash.put(inst, Num);
    
    return Num.intValue();
  }
    

  /**
   * This class is used to build the objects that go actually into
   * the hash table. It associates an operator with upto 'size'
   * operands. 
   */
  static class OpTuple {
    static final int size = 3;
    int ops[];
    int hashcode = 0;
    OPT_Operator op;
    
    /**
     * Create a new instance for the given operator
     * @param         OPT_Operator o
     */
    OpTuple (OPT_Operator o) {
      ops = new int[size];
      op = o;
    }
    
    /**
     * Check, whether two instances are the same
     * @param o
     * @return 
     */
    public boolean equals (Object o) {
      if (!(o instanceof OpTuple))
	return  false;
      OpTuple O = (OpTuple)o;
      if (O.op.opcode != op.opcode)
	return  false;
      for (int i = 0; i < size; ++i)
	if (ops[i] != O.ops[i])
	  return  false;
      return  true;
    }
    
    /**
     * Calculate the hash code from the hash codes of its children
     * @return 
     */
    public int hashCode () {
      if (hashcode == 0) {
	for (int i = size - 1; i >= 0; --i)
	  hashcode = (hashcode + ops[i]) << 4;
	hashcode += op.hashCode();
	if (hashcode == 0)
	  hashcode = 1;
      }
      return  hashcode;
    }
    
    /**
     * String representation that mentions the operator and all operands
     * @return 
     */
    public String toString () {
      String res = op.toString() + " (";
      for (int i = 0; i < size; ++i)
	res += (i > 0 ? ", " : "") + ops[i];
      return  res + ")";
    }
  }
  
  }

