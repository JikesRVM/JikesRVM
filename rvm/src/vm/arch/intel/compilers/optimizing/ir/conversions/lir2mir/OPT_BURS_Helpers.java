/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Contains architecture-specific helper functions for BURS.
 * 
 * @author Dave Grove
 * @author Stephen Fink
 */
abstract class OPT_BURS_Helpers extends OPT_PhysicalRegisterTools
  implements OPT_Operators, OPT_PhysicalRegisterConstants {
  
  // Generic helper functions.
  // Defined here to allow us to use them in the arch-specific
  // helper functions which are the bulk of this file.

  // returns the given operand as a register
  final OPT_RegisterOperand R(OPT_Operand op) {
    return (OPT_RegisterOperand) op;
  }

  // returns the given operand as an integer constant
  final OPT_IntConstantOperand I(OPT_Operand op) {
    return (OPT_IntConstantOperand) op;
  }
   
  // returns the given operand as a long constant
  final OPT_LongConstantOperand L(OPT_Operand op) {
    return (OPT_LongConstantOperand) op;
  }

  // returns the integer value of the given operand
  final int IV(OPT_Operand op) {
    return I(op).value;
  }

  // Cost functions better suited to grammars with multiple non-termials
  final int ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost) {
    return ADDRESS_EQUAL(store, load, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost, int falseCost) {
    if (Store.getAddress(store).similar(Load.getAddress(load)) &&
	Store.getOffset(store).similar(Load.getOffset(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  final int ARRAY_ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost) {
    return ARRAY_ADDRESS_EQUAL(store, load, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int ARRAY_ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost, int falseCost) {
    if (AStore.getArray(store).similar(ALoad.getArray(load)) &&
	AStore.getIndex(store).similar(ALoad.getIndex(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  final int FITS(OPT_Operand op, int numBits, int trueCost) {
    return FITS(op, numBits, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int FITS(OPT_Operand op, int numBits, int trueCost, int falseCost) {
    if(op.isIntConstant() && OPT_Bits.fits(IV(op),numBits)) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  // can an IV be the scale in a LEA instruction?
  final int LEA_SHIFT(OPT_Operand op, int trueCost) {
    return LEA_SHIFT(op, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int LEA_SHIFT(OPT_Operand op, int trueCost, int falseCost) {
    if (op.isIntConstant()) {
      int val = IV(op);
      if (val >=0 && val <= 3) {
	return trueCost;
      }
    }
    return falseCost;
  }
  final byte LEA_SHIFT(OPT_Operand op) {
    switch (IV(op)) {
    case 0: return B_S;
    case 1: return W_S;
    case 2: return DW_S;
    case 3: return QW_S;
    default:
      throw new OPT_OptimizingCompilerException("bad val for LEA shift "+op);
    }
  }

  final int isFPC_ONE(OPT_Instruction s, int trueCost) {
    return isFPC_ONE(s, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int isFPC_ONE(OPT_Instruction s, int trueCost, int falseCost) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      return fc.value == 1.0f ? trueCost : falseCost;
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      return dc.value == 1.0 ? trueCost : falseCost;
    }
  }
  final int isFPC_ZERO(OPT_Instruction s, int trueCost) {
    return isFPC_ZERO(s, trueCost, OPT_BURS_STATE.INFINITE);
  }
  final int isFPC_ZERO(OPT_Instruction s, int trueCost, int falseCost) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      return fc.value == 0.0f ? trueCost : falseCost;
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      return dc.value == 0.0 ? trueCost : falseCost;
    }
  }

  // 
  // Begin IA32 specific helper functions.
  // 
  final OPT_IA32ConditionOperand COND(OPT_ConditionOperand op) {
    return new OPT_IA32ConditionOperand(op);
  }

  // word size for memory operands
  static final byte B  = 0x01;  // byte (8 bits)
  static final byte W  = 0x02;  // word (16 bits)
  static final byte DW = 0x04;  // doubleword (32 bits)
  static final byte QW = 0x08;  // quadword (64 bits)

  static final byte B_S  = 0x00;  // byte (8*2^0 bits)
  static final byte W_S  = 0x01;  // word (8*2^116 bits)
  static final byte DW_S = 0x02;  // doubleword (8*2^2 bits)
  static final byte QW_S = 0x03;  // quadword (8*2^3 bits)

  // Get particular physical registers
  OPT_Register getEAX () {
    return getIR().regpool.getPhysicalRegisterSet().getEAX();
  }
  OPT_Register getECX () {
    return getIR().regpool.getPhysicalRegisterSet().getECX();
  }
  OPT_Register getEDX () {
    return getIR().regpool.getPhysicalRegisterSet().getEDX();
  }
  OPT_Register getEBX () {
    return getIR().regpool.getPhysicalRegisterSet().getEBX();
  }
  OPT_Register getESP () {
    return getIR().regpool.getPhysicalRegisterSet().getESP();
  }
  OPT_Register getEBP () {
    return getIR().regpool.getPhysicalRegisterSet().getEBP();
  }
  OPT_Register getESI () {
    return getIR().regpool.getPhysicalRegisterSet().getESI();
  }
  OPT_Register getEDI () {
    return getIR().regpool.getPhysicalRegisterSet().getEDI();
  }
  OPT_Register getFPR (int n) {
    return getIR().regpool.getPhysicalRegisterSet().getFPR(n);
  }

  OPT_Operand myFP0() {
    return new OPT_BURSManagedFPROperand(0);
  }
  OPT_Operand myFP1() {
    return new OPT_BURSManagedFPROperand(1);
  }

  // support to remember an address being computed in a subtree
  private static final class AddrStackElement {
    OPT_RegisterOperand base;
    OPT_RegisterOperand index;
    byte scale;
    int displacement;
    AddrStackElement next;
    AddrStackElement(OPT_RegisterOperand b,
		     OPT_RegisterOperand i,
		     byte s, int d,
		     AddrStackElement n) {
      base = b;
      index = i;
      scale = s;
      displacement = d;
      next = n;
    }
  }
  private AddrStackElement AddrStack;
  final void pushAddress(OPT_RegisterOperand base,
			 OPT_RegisterOperand index,
			 byte scale,
			 int disp) {
    AddrStack = new AddrStackElement(base, index, scale, disp, AddrStack);
  }
  final void augmentAddress(OPT_Operand op) {
    if (VM.VerifyAssertions) VM.assert(AddrStack != null, "No address to augment");
    if (op.isRegister()) {
      OPT_RegisterOperand rop = op.asRegister();
      if (AddrStack.base == null) {
	AddrStack.base = rop;
      } else if (AddrStack.index == null) {
	if (VM.VerifyAssertions) VM.assert(AddrStack.scale == (byte)0);
	AddrStack.index = rop;
      } else {
	throw new OPT_OptimizingCompilerException("three base registers in address");
      }
    } else {
      int disp = ((OPT_IntConstantOperand)op).value;
      AddrStack.displacement += disp;
    }
  }
  final void combineAddresses() {
    if (VM.VerifyAssertions) VM.assert(AddrStack != null, "No address to combine");
    AddrStackElement tmp = AddrStack;
    AddrStack = AddrStack.next;
    if (VM.VerifyAssertions) VM.assert(AddrStack != null, "only 1 address to combine");
    if (tmp.base != null) {
      if (AddrStack.base == null) {
	AddrStack.base = tmp.base;
      } else if (AddrStack.index == null) {
	if (VM.VerifyAssertions) VM.assert(AddrStack.scale == (byte)0);
	AddrStack.index = tmp.base;
      } else {
	throw new OPT_OptimizingCompilerException("three base registers in address");
      }
    }
    if (tmp.index != null) {
      if (AddrStack.index == null) {
	if (VM.VerifyAssertions) VM.assert(AddrStack.scale == (byte)0);
	AddrStack.index = tmp.index;
	AddrStack.scale = tmp.scale;
      } else if (AddrStack.base == null && tmp.scale == (byte)0) {
	AddrStack.base = tmp.base;
      } else {
	throw new OPT_OptimizingCompilerException("two scaled registers in address");
      }
    }
    AddrStack.displacement += tmp.displacement;
  }
  final OPT_MemoryOperand consumeAddress(byte size, 
					 OPT_LocationOperand loc,
					 OPT_Operand guard) {
    if (VM.VerifyAssertions) VM.assert(AddrStack != null, "No address to consume");
    OPT_MemoryOperand mo = 
      new OPT_MemoryOperand(AddrStack.base, AddrStack.index, AddrStack.scale,
			    AddrStack.displacement, size, loc, guard);
    AddrStack = AddrStack.next;
    return mo;
  }

  // support to remember a memory operand computed in a subtree
  private static final class MOStackElement {
    OPT_MemoryOperand mo;
    MOStackElement next;
    MOStackElement(OPT_MemoryOperand m, 
		   MOStackElement n) {
      mo = m;
      next = n;
    }
  }
  private MOStackElement MOStack;
  final void pushMO(OPT_MemoryOperand mo) {
    MOStack = new MOStackElement(mo, MOStack);
  }
  final OPT_MemoryOperand consumeMO() {
    if (VM.VerifyAssertions) VM.assert(MOStack != null, "No memory operand to consume");
    OPT_MemoryOperand mo = MOStack.mo;
    MOStack = MOStack.next;
    return mo;
  }


  // Construct a memory operand for the effective address of the 
  // load instruction
  final OPT_MemoryOperand MO_L(OPT_Instruction s, byte size) {
    return MO(Load.getAddress(s), Load.getOffset(s), size, 
	      Load.getLocation(s), Load.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // store instruction
  final OPT_MemoryOperand MO_S(OPT_Instruction s, byte size) {
    return MO(Store.getAddress(s), Store.getOffset(s), size, 
	      Store.getLocation(s), Store.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array load instruction
  final OPT_MemoryOperand MO_AL(OPT_Instruction s, byte scale, byte size) {
    return MO_ARRAY(ALoad.getArray(s), ALoad.getIndex(s), scale, size, 
		    ALoad.getLocation(s), ALoad.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array store instruction
  final OPT_MemoryOperand MO_AS(OPT_Instruction s, byte scale, byte size) {
    return MO_ARRAY(AStore.getArray(s), AStore.getIndex(s), scale, size, 
		    AStore.getLocation(s), AStore.getGuard(s));
  }

  // Construct a memory operand for the effective address of the 
  // load instruction 
  final OPT_MemoryOperand MO_L(OPT_Instruction s, byte size, int disp) {
    return MO(Load.getAddress(s), Load.getOffset(s), size, disp,
	      Load.getLocation(s), Load.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // store instruction
  final OPT_MemoryOperand MO_S(OPT_Instruction s, byte size, int disp) {
    return MO(Store.getAddress(s), Store.getOffset(s), size, disp,
	      Store.getLocation(s), Store.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array load instruction
  final OPT_MemoryOperand MO_AL(OPT_Instruction s, byte scale, byte size, int disp) {
    return MO_ARRAY(ALoad.getArray(s), ALoad.getIndex(s), scale, size, disp,
		    ALoad.getLocation(s), ALoad.getGuard(s));
  }
  // Construct a memory operand for the effective address of the array store instruction
  final OPT_MemoryOperand MO_AS(OPT_Instruction s, byte scale, byte size, int disp) {
    return MO_ARRAY(AStore.getArray(s), AStore.getIndex(s), scale, size, disp,
		    AStore.getLocation(s), AStore.getGuard(s));
  }

  final OPT_MemoryOperand MO(OPT_Operand base, OPT_Operand offset, 
			     byte size, OPT_LocationOperand loc,
			     OPT_Operand guard) {
    if (base instanceof OPT_IntConstantOperand) {
      if (offset instanceof OPT_IntConstantOperand) {
	return MO_D(IV(base)+IV(offset), size, loc, guard);
      } else {
	return MO_BD(offset, IV(base), size, loc, guard);
      }
    } else {
      if (offset instanceof OPT_IntConstantOperand) {
	return MO_BD(base, IV(offset), size, loc, guard);
      } else {
	return MO_BI(base, offset, size, loc, guard);
      }
    }
  }

  final OPT_MemoryOperand MO_ARRAY(OPT_Operand base, 
				   OPT_Operand index, 
				   byte scale, byte size, 
				   OPT_LocationOperand loc,
				   OPT_Operand guard) {
    if (index instanceof OPT_IntConstantOperand) {
      return MO_BD(base, IV(index)<<scale, size, loc, guard);
    } else {
      return MO_BIS(base, index, scale, size, loc, guard);
    }
  }


  final OPT_MemoryOperand MO(OPT_Operand base, OPT_Operand offset, 
			     byte size, int disp,
			     OPT_LocationOperand loc,
			     OPT_Operand guard) {
    if (base instanceof OPT_IntConstantOperand) {
      if (offset instanceof OPT_IntConstantOperand) {
	return MO_D(IV(base)+IV(offset)+disp, size, loc, guard);
      } else {
	return MO_BD(offset, IV(base)+disp, size, loc, guard);
      }
    } else {
      if (offset instanceof OPT_IntConstantOperand) {
	return MO_BD(base, IV(offset)+disp, size, loc, guard);
      } else {
	return MO_BID(base, offset, disp, size, loc, guard);
      }
    }
  }

  final OPT_MemoryOperand MO_ARRAY(OPT_Operand base, 
				   OPT_Operand index, 
				   byte scale, byte size, 
				   int disp,
				   OPT_LocationOperand loc,
				   OPT_Operand guard) {
    if (index instanceof OPT_IntConstantOperand) {
      return MO_BD(base, (IV(index)<<scale)+disp, size, loc, guard);
    } else {
      return new OPT_MemoryOperand(R(base), R(index), scale, 
				   disp, size, loc, guard);
    }
  }

 
  final OPT_MemoryOperand MO_B(OPT_Operand base, byte size, 
			       OPT_LocationOperand loc,
			       OPT_Operand guard) {
    return OPT_MemoryOperand.B(R(base), size, loc, guard);
  }

  final OPT_MemoryOperand MO_BI(OPT_Operand base, 
				OPT_Operand index, 
				byte size, OPT_LocationOperand loc,
				OPT_Operand guard) {
    return OPT_MemoryOperand.BI(R(base), R(index), size, loc, guard);
  }

  final OPT_MemoryOperand MO_BD(OPT_Operand base, int disp, 
				byte size, OPT_LocationOperand loc,
				OPT_Operand guard) {
    return OPT_MemoryOperand.BD(R(base), disp, size, loc, guard);
  }

  final OPT_MemoryOperand MO_BID(OPT_Operand base, 
				 OPT_Operand index, 
				 int disp, byte size, 
				 OPT_LocationOperand loc,
				 OPT_Operand guard) {
    return OPT_MemoryOperand.BID(R(base), R(index), disp, size, loc, guard);
  }

  final OPT_MemoryOperand MO_BIS(OPT_Operand base, 
				 OPT_Operand index, 
				 byte scale, byte size, 
				 OPT_LocationOperand loc,
				 OPT_Operand guard) {
    return OPT_MemoryOperand.BIS(R(base), R(index), scale, size, loc, guard);
  }

  final OPT_MemoryOperand MO_D(int disp, 
			       byte size, OPT_LocationOperand loc,
			       OPT_Operand guard) {
    return OPT_MemoryOperand.D(disp, size, loc, guard);
  }



  final OPT_MemoryOperand MO_MC(OPT_Instruction s) {
    OPT_Operand base = Binary.getVal1(s);
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      int offset = fc.index << 2;
      OPT_LocationOperand loc = new OPT_LocationOperand(offset);
      if (base instanceof OPT_IntConstantOperand) {
	return MO_D(IV(base)+offset, DW, loc, TG());
      } else {
	return MO_BD(Binary.getVal1(s), offset, DW, loc, TG());
      }
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      int offset = dc.index << 2;
      OPT_LocationOperand loc = new OPT_LocationOperand(offset);
      if (base instanceof OPT_IntConstantOperand) {
	return MO_D(IV(base)+offset, QW, loc, TG());
      } else {
	return MO_BD(Binary.getVal1(s), offset, QW, loc, TG());
      }
    }
  }

  final OPT_Operand MO_CONV(OPT_BURS burs, byte size) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    return new OPT_StackLocationOperand(true, offset, size);
  }

  final void STORE_LONG_FOR_CONV(OPT_BURS burs, OPT_Operand op) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    if (op instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand hval = R(op);
      OPT_RegisterOperand lval = R(burs.ir.regpool.getSecondReg(hval.register));
      burs.append(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset+4, DW), hval));
      burs.append(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), lval));
    } else {
      OPT_LongConstantOperand val = L(op);
      burs.append(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset+4, DW), I(val.upper32())));
      burs.append(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), I(val.lower32())));
    }
  }      

  // condition code state
  private OPT_ConditionOperand cc;
  void pushCOND(OPT_ConditionOperand c) {
    if (VM.VerifyAssertions) VM.assert(cc == null);
    cc = c ;
  }
  OPT_ConditionOperand consumeCOND() {
    OPT_ConditionOperand ans = cc;
    if (VM.VerifyAssertions) {
      VM.assert(cc != null);
      cc = null;
    }
    return ans;
  }

  // emit code to load 32 bits form a given jtoc offset
  private OPT_MemoryOperand loadFromJTOC(OPT_BURS burs, int offset) {
    OPT_LocationOperand loc = new OPT_LocationOperand(offset);
    OPT_Operand guard = TG();
    if (burs.ir.options.FIXED_JTOC) {
      return OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(offset).toInt(),
				 (byte)4, loc, guard);
    } else {
      OPT_Operand jtoc = 
	OPT_MemoryOperand.BD(R(burs.ir.regpool.getPhysicalRegisterSet().getPR()),
			     VM_Entrypoints.jtocField.getOffset(), 
			     (byte)4, null, TG());
      OPT_RegisterOperand regOp = burs.ir.regpool.makeTempInt();
      burs.append(MIR_Move.create(IA32_MOV, regOp, jtoc));
      return OPT_MemoryOperand.BD(regOp.copyD2U(), offset, (byte)4, loc, guard);
    }
  }

  /*
   * IA32-specific emit rules that are complex 
   * enough that we didn't want to write them in the LIR2MIR.rules file.
   * However, all expansions in this file are called during BURS and
   * thus are constrained to generate nonbranching code (ie they can't
   * create new basic blocks and/or do branching).
   *
   */

  /**
   * Emit code to get a caught exception object into a register
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  void GET_EXCEPTION_OBJECT(OPT_BURS burs, OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    burs.append(MIR_Move.mutate(s, IA32_MOV, Nullary.getResult(s), sl));
  }


  /**
   * Emit code to move a value in a register to the stack location
   * where a caught exception object is expected to be.
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  void SET_EXCEPTION_OBJECT(OPT_BURS burs, OPT_Instruction s) {
    int offset = - burs.ir.stackManager. allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    OPT_RegisterOperand obj = (OPT_RegisterOperand)CacheOp.getRef(s);
    burs.append(MIR_Move.mutate(s, IA32_MOV, sl, obj));
  }


  /**
   * Expansion of INT_2LONG
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   */
  final void INT_2LONG(OPT_BURS burs, OPT_Instruction s,
		       OPT_RegisterOperand result,
		       OPT_Operand value) {
    OPT_Register hr = result.register;
    OPT_Register lr = burs.ir.regpool.getSecondReg(hr);
    burs.append(MIR_Move.create(IA32_MOV, R(lr), value));
    burs.append(MIR_Move.create(IA32_MOV, R(hr), R(lr)));
    burs.append(MIR_BinaryAcc.create(IA32_SAR, R(hr), I(31)));
  }

  /**
   * Expansion of FLOAT_2INT and DOUBLE_2INT, using the FIST instruction.
   * This expansion does some boolean logic and conditional moves in order
   * to avoid changing the floating-point rounding mode or inserting
   * branches.  Other expansions are possible, and may be better?
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   */
  final void FPR_2INT(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    OPT_MemoryOperand M;

    // Step 1: Get value to be converted into myFP0
    //         and in 'strict' IEEE mode.
    if (value instanceof OPT_MemoryOperand) {
      // value is in memory, all we have to do is load it
      burs.append(MIR_Move.create(IA32_FLD, myFP0(), value));
    } else {
      // sigh.  value is an FP register. Unfortunately,
      // SPECjbb requires some 'strict' FP semantics.  Naturally, we don't
      // normally implement strict semantics, but we try to slide by in
      // order to pass the benchmark.  
      // In order to pass SPECjbb, it turns out we need to enforce 'strict'
      // semantics before doing a particular f2int conversion.  To do this
      // we must have a store/load sequence to cause IEEE rounding.
      if (value instanceof OPT_BURSManagedFPROperand) {
	if (VM.VerifyAssertions) VM.assert(value.similar(myFP0()));
	burs.append(MIR_Move.create(IA32_FSTP, MO_CONV(burs, DW), value));
	burs.append(MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(burs, DW)));
      } else {
	burs.append(MIR_Move.create(IA32_FMOV, MO_CONV(burs, DW), value));
	burs.append(MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(burs, DW)));
      }
    }

    // FP Stack: myFP0 = value 
    burs.append(MIR_Move.create(IA32_FIST, MO_CONV(burs, DW),  myFP0()));
    // MO_CONV now holds myFP0 converted to an integer (round-toward nearest)
    // FP Stack: myFP0 == value

    // isPositive == 1 iff 0.0 < value
    // isNegative == 1 iff 0.0 > value
    OPT_Register one        = burs.ir.regpool.getInteger();
    OPT_Register isPositive = burs.ir.regpool.getInteger();
    OPT_Register isNegative = burs.ir.regpool.getInteger();
    burs.append(MIR_Move.create(IA32_MOV, R(one), I(1)));
    burs.append(MIR_Move.create(IA32_MOV, R(isPositive), I(0)));
    burs.append(MIR_Move.create(IA32_MOV, R(isNegative), I(0)));
    burs.append(MIR_Nullary.create(IA32_FLDZ, myFP0()));
    // FP Stack: myFP0 = 0.0; myFP1 = value 
    burs.append(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    burs.append(MIR_CondMove.create(IA32_CMOV, R(isPositive), R(one),
                                    OPT_IA32ConditionOperand.LLT()));
    burs.append(MIR_CondMove.create(IA32_CMOV, R(isNegative), R(one),
                                    OPT_IA32ConditionOperand.LGT()));

    burs.append(MIR_Move.create(IA32_FILD, myFP0(), MO_CONV(burs, DW)));
    // FP Stack: myFP0 = round(value), myFP1 = value

    // addee      = 1 iff round(x) < x
    // subtractee = 1 iff round(x) > x
    OPT_Register addee      = burs.ir.regpool.getInteger();
    OPT_Register subtractee = burs.ir.regpool.getInteger();
    burs.append(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    burs.append(MIR_Move.create(IA32_MOV, R(addee) , I(0)));
    burs.append(MIR_Move.create(IA32_MOV, R(subtractee) , I(0)));
    burs.append(MIR_CondMove.create(IA32_CMOV, R(addee), R(one),
                                    OPT_IA32ConditionOperand.LLT()));
    burs.append(MIR_CondMove.create(IA32_CMOV, R(subtractee), R(one),
                                    OPT_IA32ConditionOperand.LGT()));
    
    // Now a little tricky part.
    // We will add 1 iff isNegative and x > round(x)
    // We will subtract 1 iff isPositive and x < round(x)
    burs.append(MIR_BinaryAcc.create(IA32_AND, R(addee), R(isNegative)));
    burs.append(MIR_BinaryAcc.create(IA32_AND, R(subtractee), R(isPositive)));
    burs.append(MIR_Move.create(IA32_MOV, result.copy(), MO_CONV(burs, DW)));
    burs.append(MIR_BinaryAcc.create(IA32_ADD, result.copy(), R(addee)));
    burs.append(MIR_BinaryAcc.create(IA32_SUB, result.copy(), R(subtractee)));

    // Acquire the JTOC in a register
    OPT_Register jtoc = null;
    if (!burs.ir.options.FIXED_JTOC) {
      jtoc = burs.ir.regpool.getInteger();
      burs.append(MIR_Move.create(IA32_MOV, 
				  R(jtoc), 
				  MO_BD(R(burs.ir.regpool.getPhysicalRegisterSet().getPR()),
					VM_Entrypoints.jtocField.getOffset(), DW, null, null)));
    }

    // Compare myFP0 with (double)Integer.MAX_VALUE
    if (burs.ir.options.FIXED_JTOC) {
      M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(VM_Entrypoints.maxintField.getOffset()).toInt(),
			      QW, null, null);
    } else {
      M = OPT_MemoryOperand.BD(R(jtoc), VM_Entrypoints.maxintField.getOffset(), QW, null, null);
    }
    burs.append(MIR_Move.create(IA32_FLD, myFP0(), M));
    // FP Stack: myFP0 = (double)Integer.MAX_VALUE; myFP1 = value
    burs.append(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    // If MAX_VALUE < value, then result := MAX_INT
    OPT_Register maxInt = burs.ir.regpool.getInteger();
    burs.append(MIR_Move.create(IA32_MOV, R(maxInt), I(Integer.MAX_VALUE)));
    burs.append(MIR_CondMove.create(IA32_CMOV, result.copy(), R(maxInt), 
                                    OPT_IA32ConditionOperand.LLT()));
    
    // Compare myFP0 with (double)Integer.MIN_VALUE
    if (burs.ir.options.FIXED_JTOC) {
      M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(VM_Entrypoints.minintField.getOffset()).toInt(),
			      QW, null, null);
    } else {
      M = OPT_MemoryOperand.BD(R(jtoc), VM_Entrypoints.minintField.getOffset(), QW, null, null);
    }
    burs.append(MIR_Move.create(IA32_FLD, myFP0(), M));
    // FP Stack: myFP0 = (double)Integer.MIN_VALUE; myFP1 = value
    burs.append(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    // If MIN_VALUE > value, then result := MIN_INT
    OPT_Register minInt = burs.ir.regpool.getInteger();
    burs.append(MIR_Move.create(IA32_MOV, R(minInt), I(Integer.MIN_VALUE)));
    burs.append(MIR_CondMove.create(IA32_CMOV, result.copy(), R(minInt), 
                                    OPT_IA32ConditionOperand.LGT()));
    
    // Set condition flags: set PE iff myFP0 is a NaN
    burs.append(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP0()));
    // FP Stack: back to original level (all BURS managed slots freed)
    // If FP0 was classified as a NaN, then result := 0
    OPT_Register zero = burs.ir.regpool.getInteger();
    burs.append(MIR_Move.create(IA32_MOV, R(zero), I(0)));
    burs.append(MIR_CondMove.create(IA32_CMOV, result.copy(), R(zero),
				    OPT_IA32ConditionOperand.PE()));
    
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   */
  final void FPR2GPR_64(OPT_BURS burs, OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset+4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    burs.append(MIR_Move.create(IA32_FMOV, sl, Unary.getVal(s)));
    OPT_RegisterOperand i1 = Unary.getResult(s);
    OPT_RegisterOperand i2 = R(burs.ir.regpool.getSecondReg(i1.register));
    burs.append(MIR_Move.create(IA32_MOV, i1, sl1));
    burs.append(MIR_Move.mutate(s, IA32_MOV, i2, sl2));
  }


  /**
   * Emit code to move 64 bits from GPRs to FPRs
   */
  final void GPR2FPR_64(OPT_BURS burs, OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset+4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    OPT_Operand i1, i2;
    OPT_Operand val = Unary.getVal(s);
    if (val instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rval = (OPT_RegisterOperand)val;
      i1 = val;
      i2 = R(burs.ir.regpool.getSecondReg(rval.register));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)val;
      i1 = I(rhs.upper32());
      i2 = I(rhs.lower32());
    }      
    burs.append(MIR_Move.create(IA32_MOV, sl1, i1));
    burs.append(MIR_Move.create(IA32_MOV, sl2, i2));
    burs.append(MIR_Move.mutate(s, IA32_FMOV, Unary.getResult(s), sl));
  }

  /**
   * Expansion of ROUND_TO_ZERO.
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  final void ROUND_TO_ZERO(OPT_BURS burs, OPT_Instruction s) {
    // load the JTOC into a register
    OPT_RegisterOperand PR = R(burs.ir.regpool.getPhysicalRegisterSet().
                               getPR());
    OPT_Operand jtoc = OPT_MemoryOperand.BD(PR, VM_Entrypoints.jtocField.getOffset(), 
                                            DW, null, null);
    OPT_RegisterOperand regOp = burs.ir.regpool.makeTempInt();
    burs.append(MIR_Move.create(IA32_MOV, regOp, jtoc));

    // Store the FPU Control Word to a JTOC slot
    OPT_MemoryOperand M = OPT_MemoryOperand.BD
      (regOp.copyRO(), VM_Entrypoints.FPUControlWordField.getOffset(), W, null, null);
    burs.append(MIR_UnaryNoRes.create(IA32_FNSTCW, M));
    // Set the bits in the status word that control round to zero.
    // Note that we use a 32-bit and, even though we only care about the
    // low-order 16 bits
    burs.append(MIR_BinaryAcc.create(IA32_OR, M.copy(), I(0x00000c00)));
    // Now store the result back into the FPU Control Word
    burs.append(MIR_Nullary.mutate(s,IA32_FLDCW, M.copy()));
    return;
  }


  /**
   * Expansion of INT_DIV and INT_REM
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   * @param isDiv true for div, false for rem
   */
  final void INT_DIVIDES(OPT_BURS burs, OPT_Instruction s,
			 OPT_RegisterOperand result,
			 OPT_Operand val1,
			 OPT_Operand val2,
			 boolean isDiv) {
    burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), val1));
    burs.append(MIR_ConvertDW2QW.create(IA32_CDQ, R(getEDX()), R(getEAX())));
    if (val2 instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand temp = burs.ir.regpool.makeTempInt();
      burs.append(MIR_Move.create(IA32_MOV, temp, val2));
      val2 = temp;
    }
    burs.append(MIR_Divide.mutate(s, IA32_IDIV, R(getEDX()), R(getEAX()), 
				  val2, GuardedBinary.getGuard(s)));
    if (isDiv) {
      burs.append(MIR_Move.create(IA32_MOV, result.copyD2D(), R(getEAX())));
    } else {
      burs.append(MIR_Move.create(IA32_MOV, result.copyD2D(), R(getEDX())));
    }      
  }


  /**
   * Expansion of LONG_ADD_ACC
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_ADD(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lowlhsReg), R(lowrhsReg)));
      burs.append(MIR_BinaryAcc.mutate(s, IA32_ADC, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) {
	burs.append(MIR_BinaryAcc.mutate(s, IA32_ADD, R(lhsReg), I(high)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lowlhsReg), I(low)));
	burs.append(MIR_BinaryAcc.mutate(s, IA32_ADC, R(lhsReg), I(high)));
      }
    }
  }


  /**
   * Expansion of LONG_SUB_ACC
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_SUB(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      burs.append(MIR_BinaryAcc.create(IA32_SUB, R(lowlhsReg), R(lowrhsReg)));
      burs.append(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) {
	burs.append(MIR_BinaryAcc.mutate(s, IA32_SUB, R(lhsReg), I(high)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_SUB, R(lowlhsReg), I(low)));
	burs.append(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), I(high)));
      }
    }
  }

  /**
   * Expansion of RDTSC (called GET_TIME_BASE for consistency with PPC)
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  final void GET_TIME_BASE(OPT_BURS burs,
                           OPT_Instruction s,
                           OPT_RegisterOperand result) {
    OPT_Register highReg = result.register;
    OPT_Register lowReg = burs.ir.regpool.getSecondReg(highReg);
    burs.append(MIR_RDTSC.create(IA32_RDTSC, R(getEAX()),R(getEDX())));
    burs.append(MIR_Move.create(IA32_MOV, R(lowReg), R(getEAX())));
    burs.append(MIR_Move.create(IA32_MOV, R(highReg), R(getEDX())));
  }

  /**
   * Expansion of LONG_MUL_ACC
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_MUL(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    // In general, (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      OPT_Register tmp = burs.ir.regpool.getInteger();
      burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), R(lowrhsReg)));
      burs.append(MIR_Move.create(IA32_MOV, R(tmp), R(rhsReg)));
      burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
      burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
      burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), R(lowlhsReg)));
      burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowrhsReg)));
      burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
      burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();

      // We only have to handle those cases that OPT_Simplifier wouldn't get.  
      // OPT_Simplifier catches 
      // high   low
      //    0     0  (0L)
      //    0     1  (1L)
      //   -1    -1 (-1L)
      // So, the possible cases we need to handle here:
      //   -1     0 
      //   -1     1
      //   -1     *
      //    0    -1
      //    0     *
      //    1    -1
      //    1     0 
      //    1     1
      //    1     *
      //    *    -1
      //    *     0
      //    *     1
      //    *     *
      // (where * is something other than -1,0,1)
      if (high == -1) {
	if (low == 0) {
	  // -1, 0
	  // CLAIM: (x,y) * (-1,0) = (-y,0)
	  burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
	} else if (low == 1) {
	  // -1, 1
	  // CLAIM: (x,y) * (-1,1) = (x-y,y)
	  burs.append(MIR_BinaryAcc.create(IA32_SUB, R(lhsReg), R(lowlhsReg)));
	} else {
	  // -1, *
	  // CLAIM: (x,y) * (-1, z) = (l(x imul z)-y+u(y mul z)+, l(y mul z))
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
	  burs.append(MIR_BinaryAcc.create(IA32_SUB, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	}
      } else if (high == 0) {
	if (low == -1) {
	  // 0, -1
	  // CLAIM: (x,y) * (0,-1) = (u(y mul -1)-x, l(y mul -1))
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(-1)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_SUB, R(getEDX()), R(lhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), R(getEDX())));
	} else {
	  // 0, *
	  // CLAIM: (x,y) * (0,z) = (l(x imul z)+u(y mul z), l(y mul z))
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	}
      } else if (high == 1) {
	if (low == -1) {
	  // 1, -1
	  // CLAIM: (x,y) * (1,-1) = (-x+y+u(y mul -1), l(y mul -1))
	  burs.append(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(-1)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	} else if (low == 0) {
	  // 1, 0 
	  // CLAIM: (x,y) * (1,0) = (y,0)
	  burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
	} else if (low == 1) {
	  // 1, 1
	  // CLAIM: (x,y) * (1,1)  = (x+y,y)
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
	} else {
	  // 1, *
	  // CLAIM: (x,y) * (1,z) = (l(x imul z)+y+u(y mul z), l(y mul z))
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	}
      } else {
	if (low == -1) {
	  // *, -1
	  // CLAIM: (x,y) * (z,-1) = (-x+l(y imul z)+u(y mul -1), l(y mul -1))
	  OPT_Register tmp = burs.ir.regpool.getInteger();
	  burs.append(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(tmp), I(high)));
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	} else if (low == 0) {
	  // *,  0
	  // CLAIM: (x,y) * (z,0) = (l(y imul z),0)
	  burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), I(high)));
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
	} else if (low == 1) {
	  // *, 1
	  // CLAIM: (x,y) * (z,1) = (l(y imul z)+x,y)	
	  OPT_Register tmp = burs.ir.regpool.getInteger();
	  burs.append(MIR_Move.create(IA32_MOV, R(tmp), R(lowlhsReg)));
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), I(high)));
	  burs.append(MIR_Move.create(IA32_ADD, R(lhsReg), R(tmp)));
	} else {
	  // *, * (sigh, can't do anything interesting...)
	  OPT_Register tmp = burs.ir.regpool.getInteger();
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
	  burs.append(MIR_Move.create(IA32_MOV, R(tmp), I(high)));
	  burs.append(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
	  burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
	  burs.append(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
	  burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
	  burs.append(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
	}
      }
    }
  }


  /**
   * Expansion of LONG_NEG_ACC
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  final void LONG_NEG(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    burs.append(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
    burs.append(MIR_UnaryAcc.create(IA32_NEG, R(lowlhsReg)));
    burs.append(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), I(0)));
  }


  /**
   * Expansion of LONG_AND
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_AND(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      burs.append(MIR_BinaryAcc.create(IA32_AND, R(lowlhsReg), R(lowrhsReg)));
      burs.append(MIR_BinaryAcc.mutate(s, IA32_AND, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x &= 0 ==> x = 0
	burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
      } else if (low == -1) { // x &= 0xffffffff ==> x = x ==> nop
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_AND, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x &= 0 ==> x = 0
	burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), I(0)));
      } else if (high == -1) { // x &= 0xffffffff ==> x = x ==> nop
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_AND, R(lhsReg), I(high)));
      }
    }	
  }


  /**
   * Expansion of LONG_OR
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_OR(OPT_BURS burs, OPT_Instruction s,
		     OPT_RegisterOperand result,
		     OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      burs.append(MIR_BinaryAcc.create(IA32_OR, R(lowlhsReg), R(lowrhsReg)));
      burs.append(MIR_BinaryAcc.mutate(s, IA32_OR, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x |= 0 ==> x = x ==> nop
      } else if (low == -1) { // x |= 0xffffffff ==> x = 0xffffffff
	burs.append(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(-1)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_OR, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x |= 0 ==> x = x ==> nop
      } else if (high == -1) { // x |= 0xffffffff ==> x = 0xffffffff
	burs.append(MIR_Move.create(IA32_MOV, R(lhsReg), I(-1)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_OR, R(lhsReg), I(high)));
      }
    }	
  }


  /**
   * Expansion of LONG_XOR
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  final void LONG_XOR(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = burs.ir.regpool.getSecondReg(rhsReg);
      burs.append(MIR_BinaryAcc.create(IA32_XOR, R(lowlhsReg), R(lowrhsReg)));
      burs.append(MIR_BinaryAcc.mutate(s, IA32_XOR, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x ^= 0 ==> x = x ==> nop
      } else if (low == -1) { // x ^= 0xffffffff ==> x = ~x
	burs.append(MIR_UnaryAcc.create(IA32_NOT, R(lowlhsReg)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_XOR, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x ^= 0 ==> x = x ==> nop
      } else if (high == -1) { // x ^= 0xffffffff ==> x = ~x
	burs.append(MIR_UnaryAcc.create(IA32_NOT, R(lhsReg)));
      } else {
	burs.append(MIR_BinaryAcc.create(IA32_XOR, R(lhsReg), I(high)));
      }
    }
  }


  /**
   * Expansion of LONG_NOT
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  final void LONG_NOT(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
    burs.append(MIR_UnaryAcc.create(IA32_NOT, R(lowlhsReg)));
    burs.append(MIR_UnaryAcc.mutate(s, IA32_NOT, R(lhsReg)));
  }


  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * Moves first value into fp0,
   * accumulates second value into fp0 using op,
   * moves fp0 into result.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  final void FP_MOV_OP_MOV(OPT_BURS burs, OPT_Instruction s,
			   OPT_Operator op,
			   OPT_Operand result,
			   OPT_Operand val1,
			   OPT_Operand val2) {
    burs.append(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    burs.append(MIR_BinaryAcc.mutate(s, op, D(getFPR(0)), val2));
    burs.append(MIR_Move.create(IA32_FMOV, result, D(getFPR(0))));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * Moves first value into fp0,
   * accumulates second value into fp0 using op.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param val1 the first operand
   * @param val2 the second operand
   */
  final void FP_MOV_OP(OPT_BURS burs, OPT_Instruction s,
			   OPT_Operator op,
			   OPT_Operand val1,
			   OPT_Operand val2) {
    burs.append(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    burs.append(MIR_BinaryAcc.mutate(s, op, D(getFPR(0)), val2));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * apply op to val1 and val2
   * move val1 to result using movop
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param movop the move op to use
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  final void FP_OP_MOV(OPT_BURS burs, OPT_Instruction s,
		       OPT_Operator op,
		       OPT_Operator movop,
		       OPT_Operand result,
		       OPT_Operand val1,
		       OPT_Operand val2) {
    burs.append(MIR_BinaryAcc.mutate(s, op, val1, val2));
    burs.append(MIR_Move.create(movop, result, val1.copy()));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * apply op to val1 and val2.
   * NOTE: either val1 or val2 must be either FPR0 or ST(0)!
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param val1 the first operand
   * @param val2 the second operand
   */
  final void FP_OP(OPT_BURS burs, OPT_Instruction s,
		   OPT_Operator op,
		   OPT_Operand val1,
		   OPT_Operand val2) {
    burs.append(MIR_BinaryAcc.mutate(s, op, val1, val2));
  }

  /**
   * Expansion of FP_REM 
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  final void FP_REM(OPT_BURS burs, OPT_Instruction s,
		    OPT_Operand val1,
		    OPT_Operand val2) {
    burs.append(MIR_Move.create(IA32_FMOV, D(getFPR(1)), val2));
    burs.append(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    burs.append(MIR_BinaryAcc.mutate(s,IA32_FPREM, D(getFPR(0)), D(getFPR(1))));
  }
  /**
   * Expansion of FP_REM
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param val the operand to divide with fp0 to get a remainder
   */
  final void FP_REM(OPT_BURS burs, OPT_Instruction s,
		    OPT_Operand val) {
    burs.append(MIR_Move.create(IA32_FMOV, D(getFPR(1)), val));
    burs.append(MIR_BinaryAcc.mutate(s,IA32_FPREM, D(getFPR(0)), D(getFPR(1))));
  }


  /**
   * Expansion of BOOLEAN_CMP
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to copy position info from
   * @param result the result operand
   * @param val1   the first value
   * @param val2   the second value
   * @param cond   the condition operand
   */
  final void BOOLEAN_CMP(OPT_BURS burs, OPT_Instruction s,
			 OPT_Operand res, 
			 OPT_Operand val1,
			 OPT_Operand val2,
			 OPT_ConditionOperand cond) {
    burs.append(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    OPT_RegisterOperand temp = burs.ir.regpool.makeTemp(VM_Type.BooleanType);
    burs.append(CPOS(s, MIR_Set.create(IA32_SET$B, temp, COND(cond))));
    burs.append(MIR_Unary.mutate(s, IA32_MOVZX$B, res, temp.copyD2U()));
  }


  /**
   * Expansion of a special case of BOOLEAN_CMP when the 
   * condition registers have already been set by the previous
   * ALU op.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to copy position info from
   * @param result the result operand
   * @param cond   the condition operand
   */
  final void BOOLEAN_CMP(OPT_BURS burs, OPT_Instruction s,
			 OPT_Operand res, 
			 OPT_ConditionOperand cond) {
    OPT_RegisterOperand temp = burs.ir.regpool.makeTemp(VM_Type.BooleanType);
    burs.append(CPOS(s, MIR_Set.create(IA32_SET$B, temp, COND(cond))));
    burs.append(MIR_Unary.mutate(s, IA32_MOVZX$B, res, temp.copyD2U()));
  }


  /**
   * Generate a compare and branch sequence.
   * Used in the expansion of trees where INT_IFCMP is a root
   * 
   * @param burs and OPT_BURS object
   * @param s the ifcmp instruction 
   * @param val1 the first value operand
   * @param val2 the second value operand
   * @param cond the condition operand
   */
  final void IFCMP(OPT_BURS burs, OPT_Instruction s,
		   OPT_Operand val1, OPT_Operand val2,
		   OPT_ConditionOperand cond) {
    burs.append(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    burs.append(MIR_CondBranch.mutate(s, IA32_JCC, COND(cond),
				      IfCmp.getTarget(s), 
				      IfCmp.getBranchProfile(s)));
  }


  /**
   * Generate the compare portion of a conditional move.
   * 
   * @param burs an OPT_BURS object
   * @param s the instruction to copy position info from
   * @param val1 the first value to compare
   * @param val2 the second value to compare
   */
  final void CMOV_CMP(OPT_BURS burs, OPT_Instruction s,
		      OPT_Operand val1, OPT_Operand val2) {
    if (val1.isRegister() && val1.asRegister().register.isFloatingPoint()) {
      if (VM.VerifyAssertions) {
        VM.assert(val2.isRegister());
        VM.assert(val2.asRegister().register.isFloatingPoint());
      }
      burs.append(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1)));
      burs.append(CPOS(s, MIR_Compare.create(IA32_FCOMI, D(getFPR(0)), val2)));
    } else {
      burs.append(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    }
  }

  /**
   * Generate the move portion of a conditional move.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to copy position info from
   * @param result the result of the conditional move
   * @param cond the condition operand
   * @param trueVal the value to move to result if cond is true
   * @param falseVal the value to move to result if cond is not true
   */
  final void CMOV_MOV(OPT_BURS burs, OPT_Instruction s,
		      OPT_RegisterOperand result,
		      OPT_ConditionOperand cond,
		      OPT_Operand trueValue,
		      OPT_Operand falseValue) {
    OPT_Operator movop, cmovop;
    if (result.type.isDoubleType() || result.type.isFloatType()) {
      movop = IA32_FMOV;
      cmovop = IA32_FCMOV;
    } else {
      movop = IA32_MOV;
      cmovop = IA32_CMOV;
    }

    if (result.similar(trueValue)) {
      // in this case, only need a conditional move for the false branch.
      burs.append(MIR_CondMove.mutate(s, cmovop, result,
				      asReg(burs, s, movop, falseValue),
				      COND(cond.flipCode())));
    } else if (result.similar(falseValue)) {
      // in this case, only need a conditional move for the true branch.
      burs.append(MIR_CondMove.mutate(s, cmovop, result, 
				      asReg(burs, s, movop, trueValue),
				      COND(cond)));
    } else {
      // need to handle both possible assignments. Unconditionally
      // assign one value then conditionally assign the other.
      if (falseValue.isRegister()) {
	burs.append(CPOS(s,MIR_Move.create(movop, result, trueValue)));
	burs.append(MIR_CondMove.mutate(s, cmovop, result.copy(), 
					falseValue,
					COND(cond.flipCode())));
      } else {
	burs.append(CPOS(s,MIR_Move.create(movop, result, falseValue)));
	burs.append(MIR_CondMove.mutate(s, cmovop, result.copy(), 
					asReg(burs, s, movop, trueValue),
					COND(cond)));
      }
    }
  }

  // move op into a register operand if it isn't one already.
  private OPT_Operand asReg(OPT_BURS burs, OPT_Instruction s, 
			    OPT_Operator movop, OPT_Operand op) {
    if (op.isRegister()) return op;
    OPT_RegisterOperand tmp = burs.ir.regpool.makeTemp(op);
    burs.append(CPOS(s, MIR_Move.create(movop, tmp, op)));
    return tmp.copy();
  }


  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  void PROLOGUE(OPT_BURS burs, OPT_Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i=0; i<numFormals; i++) {
      if (Prologue.getFormal(s, i).type == VM_Type.LongType) numLongs ++;
    }
    if (numLongs != 0) {
      OPT_Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals+numLongs);
      for (int sidx=0, s2idx=0; sidx<numFormals; sidx++) {
	OPT_RegisterOperand sForm = Prologue.getFormal(s, sidx);
	if (sForm.type == VM_Type.LongType) {
	  sForm.type = VM_Type.IntType;
	  Prologue.setFormal(s2, s2idx++, sForm);
          OPT_Register r2 = burs.ir.regpool.getSecondReg(sForm.register);
	  Prologue.setFormal(s2, s2idx++, R(r2));
          sForm.register.clearType();
          sForm.register.setInteger();
          r2.clearType();
          r2.setInteger();
	} else {
	  Prologue.setFormal(s2, s2idx++, sForm);
	}
      }									     
      burs.append(s2);
    } else {
      burs.append(s);
    }
  }

  /**
   * Expansion of CALL.
   * Expand longs registers into pairs of int registers.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  final void CALL(OPT_BURS burs, 
		  OPT_Instruction s,
		  OPT_Operand address) {
    OPT_RegisterPool regpool = burs.ir.regpool;

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType() == VM_Type.LongType) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = Call.getResult(s);
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type == VM_Type.LongType) {
      result.type = VM_Type.IntType;
      result2 = R(regpool.getSecondReg(result.register));
    }
    
    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed 
    // arguments, so some amount of copying is required. 
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_CALL, result, result2, 
		    address, Call.getMethod(s),
		    numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
	MIR_Call.setParam(s, mirCallIdx++, param);
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type == VM_Type.LongType) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
      } else if (param instanceof OPT_LongConstantOperand) {
	OPT_LongConstantOperand val = (OPT_LongConstantOperand)param;
	MIR_Call.setParam(s, mirCallIdx++, I(val.upper32()));
	MIR_Call.setParam(s, mirCallIdx++, I(val.lower32()));
      } else {
	MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    burs.append(s);
  }

  /**
   * Expansion of SYSCALL.
   * Expand longs registers into pairs of int registers.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  final void SYSCALL(OPT_BURS burs, OPT_Instruction s, OPT_Operand address) {
    OPT_RegisterPool regpool = burs.ir.regpool;
    burs.ir.setHasSysCall(true);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = CallSpecial.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (CallSpecial.getParam(s, pNum).getType() == VM_Type.LongType) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = CallSpecial.getResult(s);
    OPT_RegisterOperand result2 = null;
    // NOTE: C callee returns longs little endian!
    if (result != null && result.type == VM_Type.LongType) {
      result.type = VM_Type.IntType;
      result2 = result;
      result = R(regpool.getSecondReg(result.register));
    }
    
    // Step 3: Mutate the CallSpecial to an MIR_Call.
    // Note MIR_Call and CallSpecial have a different number of fixed 
    // arguments, so some amount of copying is required. 
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = CallSpecial.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_SYSCALL, result, result2, 
		    address, (OPT_MethodOperand)CallSpecial.getMethod(s),
		    numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
	// NOTE: longs passed little endian to C callee!
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type == VM_Type.LongType) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
	MIR_Call.setParam(s, mirCallIdx++, param);
      } else if (param instanceof OPT_LongConstantOperand) {
	long value = ((OPT_LongConstantOperand)param).value; 
	int valueHigh = (int)(value >> 32);
	int valueLow = (int)(value & 0xffffffff);
	// NOTE: longs passed little endian to C callee!
	MIR_Call.setParam(s, mirCallIdx++, I(valueLow));
	MIR_Call.setParam(s, mirCallIdx++, I(valueHigh));
      } else {
	MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    burs.append(s);
  }

  /**
   * Expansion of LOWTABLESWITCH.  
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  final void LOWTABLESWITCH(OPT_BURS burs, OPT_Instruction s) {
    // (1) We're changing index from a U to a DU.
    //     Inject a fresh copy instruction to make sure we aren't
    //     going to get into trouble (if someone else was also using index).
    OPT_RegisterOperand newIndex = burs.ir.regpool.makeTempInt(); 
    burs.append(MIR_Move.create(IA32_MOV, newIndex, LowTableSwitch.getIndex(s))); 
    int number = LowTableSwitch.getNumberOfTargets(s);
    OPT_Instruction s2 = CPOS(s,MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex, number*2));
    for (int i=0; i<number; i++) {
      MIR_LowTableSwitch.setTarget(s2,i,LowTableSwitch.getTarget(s,i));
      MIR_LowTableSwitch.setBranchProfile(s2,i,LowTableSwitch.getBranchProfile(s,i));
    }
    burs.append(s2);
  }

  /**
   * Expansion of RESOLVE.  Dynamic link point.
   * Build up MIR instructions for Resolve.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  final void RESOLVE(OPT_BURS burs, 
		     OPT_Instruction s) {
    OPT_Operand target = loadFromJTOC(burs, VM_Entrypoints.optResolveMethod.getOffset());
    burs.append(CPOS(s, MIR_Call.mutate0(s, CALL_SAVE_VOLATILE, 
					 null, null,  target, 
					 OPT_MethodOperand.STATIC(VM_Entrypoints.optResolveMethod))));
  }
  /**
   * Expansion of TRAP_IF, with an int constant as the second value.
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  void TRAP_IF_IMM(OPT_BURS burs, OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getGuardResult(s);
    OPT_RegisterOperand v1 =  (OPT_RegisterOperand)TrapIf.getVal1(s);
    OPT_IntConstantOperand v2 = (OPT_IntConstantOperand)TrapIf.getVal2(s);
    OPT_ConditionOperand cond = TrapIf.getCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getTCode(s);

    // A slightly ugly matter, but we need to deal with combining
    // the two pieces of a long register from a LONG_ZERO_CHECK.  
    // A little awkward, but probably the easiest workaround...
    if (tc.getTrapCode() == VM_Runtime.TRAP_DIVIDE_BY_ZERO &&
        v1.type == VM_Type.LongType) {
      OPT_RegisterOperand rr = burs.ir.regpool.makeTempInt();
      burs.append(MIR_Move.create(IA32_MOV, rr, v1.copy()));
      burs.append(MIR_BinaryAcc.create(IA32_OR, rr.copy(), 
				       R(burs.ir.regpool.getSecondReg
					 (v1.register))));
      v1 = rr.copyD2U();
    } 

    // emit the trap instruction
    burs.append(MIR_TrapIf.mutate(s, IA32_TRAPIF, gRes, v1, v2, COND(cond),
                                  tc));
  }


  /**
   * This routine expands an ATTEMPT instruction 
   * into an atomic compare exchange.
   *
   * @param burs     an OPT_BURS object
   * @param result   the register operand that is set to 0/1 as a result of the attempt
   * @param mo       the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   */
  void ATTEMPT(OPT_BURS burs, 
	       OPT_RegisterOperand result,
	       OPT_MemoryOperand mo,
	       OPT_Operand oldValue,
	       OPT_Operand newValue) {
    OPT_RegisterOperand temp = burs.ir.regpool.makeTempInt();
    OPT_RegisterOperand temp2 = burs.ir.regpool.makeTemp(result);
    burs.append(MIR_Move.create(IA32_MOV, temp, newValue));
    burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), oldValue));
    burs.append(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG, R(getEAX()), 
					   mo, (OPT_RegisterOperand)temp.copy())); 
    burs.append(MIR_Set.create(IA32_SET$B, temp2, OPT_IA32ConditionOperand.EQ()));
    // need to zero-extend the result of the set
    burs.append(MIR_Unary.create(IA32_MOVZX$B, result, temp2.copy()));
  }


  /**
   * This routine expands the compound pattern
   * IFCMP(ATTEMPT, ZERO) into an atomic compare/exchange 
   * followed by a branch on success/failure
   * of the attempted atomic compare/exchange.
   *
   * @param burs     an OPT_BURS object
   * @param mo       the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   * @param cond     the condition to branch on
   * @param target   the branch target
   * @param bp       the branch profile information
   */
  void ATTEMPT_IFCMP(OPT_BURS burs, 
		     OPT_MemoryOperand mo,
		     OPT_Operand oldValue,
		     OPT_Operand newValue,
		     OPT_ConditionOperand cond,
		     OPT_BranchOperand target,
		     OPT_BranchProfileOperand bp) {
    OPT_RegisterOperand temp = burs.ir.regpool.makeTempInt();
    burs.append(MIR_Move.create(IA32_MOV, temp, newValue));
    burs.append(MIR_Move.create(IA32_MOV, R(getEAX()), oldValue));
    burs.append(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG, R(getEAX()), 
					   mo, (OPT_RegisterOperand)temp.copy())); 
    burs.append(MIR_CondBranch.create(IA32_JCC, COND(cond), target, bp));
  }

}
