#!/bin/sh
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#

FILENAME=$1

cat $2 > $FILENAME

function emitBinaryReg() {
  acronym=$1
  opStr=$2
  rmrCode=$3
  rrmCode=$4
  sizeOrPrefix=$5
  ext=
  code=
  prefix=
  if [ x$sizeOrPrefix = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$sizeOrPrefix = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="
        setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$sizeOrPrefix != x ]; then
    prefix="
	setMachineCodes(mi++, (byte) $sizeOrPrefix);"
  fi
  cat >> $FILENAME <<EOF
    /**
     * Generate a register--register ${acronym}. That is,
     * <PRE>
     * [dstReg] ${opStr}= ${code} srcReg
     * </PRE>
     *
     * @param dstReg the destination register
     * @param srcReg the source register
     */
    public final void emit${acronym}_RegInd_Reg${ext}(byte dstReg, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitRegIndirectRegOperands(dstReg, srcReg);
	if (lister != null) lister.RNR(miStart, "${acronym}", dstReg, srcReg);
    }

    /**
     * Generate a register-offset--register ${acronym}. That is,
     * <PRE>
     * [dstReg<<dstScale + dstDisp] ${opStr}= ${code} srcReg
     * </PRE>
     *
     * @param srcReg the source register
     * @param dstIndex the destination index register
     * @param dstScale the destination shift amount
     * @param dstDisp the destination displacement
     */
    public final void emit${acronym}_RegOff_Reg${ext}(byte dstIndex, short scale, int dstDisp, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitRegOffRegOperands(dstIndex, scale, dstDisp, srcReg);
	if (lister != null) lister.RFDR(miStart, "${acronym}", dstIndex, scale, dstDisp, srcReg);
    }

    // [dstDisp] ${opStr}= $code srcReg 
    public final void emit${acronym}_Abs_Reg${ext}(int dstDisp, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitAbsRegOperands(dstDisp, srcReg);
	if (lister != null) lister.RAR(miStart, "${acronym}", dstDisp, srcReg);
    }

    // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= $code srcReg 
    public final void emit${acronym}_RegIdx_Reg${ext}(byte dstBase, byte dstIndex, short scale, int dstDisp, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, srcReg);
	if (lister != null) lister.RXDR(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, srcReg);
    }

    // [dstReg + dstDisp] ${opStr}= $code srcReg
    public final void emit${acronym}_RegDisp_Reg${ext}(byte dstReg, int dstDisp, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitRegDispRegOperands(dstReg, dstDisp, srcReg);
	if (lister != null) lister.RDR(miStart, "${acronym}", dstReg, dstDisp, srcReg);
    }

    // dstReg ${opStr}= $code srcReg
    public final void emit${acronym}_Reg_Reg${ext}(byte dstReg, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rmrCode});
	emitRegRegOperands(dstReg, srcReg);
	if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
    }

EOF
    if [ x$rrmCode != xnone ]; then
	cat >> $FILENAME <<EOF
    // dstReg ${opStr}= $code [srcReg + srcDisp]
    public final void emit${acronym}_Reg_RegDisp${ext}(byte dstReg, byte srcReg, int srcDisp) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rrmCode});
	emitRegDispRegOperands(srcReg, srcDisp, dstReg);
	if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcReg, srcDisp);
    }

    // dstReg ${opStr}= $code [srcIndex<<scale + srcDisp] 
    public final void emit${acronym}_Reg_RegOff${ext}(byte dstReg, byte srcIndex, short scale, int srcDisp) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rrmCode});
	emitRegOffRegOperands(srcIndex, scale, srcDisp, dstReg);
	if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, scale, srcDisp);
    }

    // dstReg ${opStr}= $code [srcDisp] 
    public final void emit${acronym}_Reg_Abs${ext}(byte dstReg, int srcDisp) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rrmCode});
	emitAbsRegOperands(srcDisp, dstReg);
	if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
    }

    // dstReg ${opStr}= $code [srcBase + srcIndex<<scale + srcDisp] 
    public final void emit${acronym}_Reg_RegIdx${ext}(byte dstReg, byte srcBase, byte srcIndex, short scale, int srcDisp) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rrmCode});
	emitSIBRegOperands(srcBase, srcIndex, scale, srcDisp, dstReg);
	if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, scale, srcDisp);
    }

    // dstReg ${opStr}= $code [srcReg]
    public final void emit${acronym}_Reg_RegInd${ext}(byte dstReg, byte srcReg) {
	int miStart = mi; ${prefix}
	setMachineCodes(mi++, (byte) ${rrmCode});
	emitRegIndirectRegOperands(srcReg, dstReg);
	if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcReg);
    }

EOF
    fi
}

function emitBinaryImmWordOrDouble() {
  acronym=$1
  opStr=$2
  eaxOpcode=$3
  imm8Code=$4
  imm32Code=$5
  immExtOp=$6
  size=$7
  local ext=
  local code=
  local prefix=
  if [ x$size = xword ]; then
    ext=_Word
    code=" (word) "
    emitImm=emitImm16  
    prefix="
            setMachineCodes(mi++, (byte) 0x66);"
  else
    emitImm=emitImm32
  fi
  cat >> $FILENAME <<EOF
    // dstReg ${opStr}= ${code} imm
    public final void emit${acronym}_Reg_Imm${ext}(byte dstReg, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegRegOperands(dstReg, (byte) ${immExtOp});
	    emitImm8((byte)imm);
        } else if (dstReg == EAX) {
	    setMachineCodes(mi++, (byte) $eaxOpcode);
	    ${emitImm}(imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegRegOperands(dstReg, (byte) ${immExtOp});
	    ${emitImm}(imm);
	}
        if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
    }

    // [dstReg + dstDisp] ${opStr}= ${code} imm
    public final void emit${acronym}_RegDisp_Imm${ext}(byte dstReg, int dstDisp, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegDispRegOperands(dstReg, dstDisp, (byte) ${immExtOp});
	    emitImm8((byte)imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegDispRegOperands(dstReg, dstDisp, (byte) ${immExtOp});
	    ${emitImm}(imm);
	}
	if (lister != null) lister.RDI(miStart, "${acronym}", dstReg, dstDisp, imm);
    }
		
    // [dstIndex<<scale + dstDisp] ${opStr}= ${code} imm 
    public final void emit${acronym}_RegOff_Imm${ext}(byte dstIndex, short scale, int dstDisp, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegOffRegOperands(dstIndex, scale, dstDisp, (byte) ${immExtOp});
	    emitImm8((byte)imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegOffRegOperands(dstIndex, scale, dstDisp, (byte) ${immExtOp});
	    emitImm32(imm);
	}
	if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, scale, dstDisp, imm);
    }
		
    // [dstDisp] ${opStr}= ${code} imm 
    public final void emit${acronym}_Abs_Imm${ext}(int dstDisp, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitAbsRegOperands(dstDisp, (byte) ${immExtOp});
	    emitImm8((byte)imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitAbsRegOperands(dstDisp, (byte) ${immExtOp});
	    emitImm32(imm);
	}
	if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
    }
		
    // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= ${code} imm 
    public final void emit${acronym}_RegIdx_Imm${ext}(byte dstBase, byte dstIndex, short scale, int dstDisp, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, (byte) ${immExtOp});
	    emitImm8((byte)imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, (byte) ${immExtOp});
	    emitImm32(imm);
	}
	if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, imm);
    }
		
    // [dstReg] ${opStr}= ${code} imm
    public final void emit${acronym}_RegInd_Imm${ext}(byte dstReg, int imm) {
	int miStart = mi;$prefix
	if (fits(imm,8)) {
	    setMachineCodes(mi++, (byte) ${imm8Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegIndirectRegOperands(dstReg, (byte) ${immExtOp});
	    emitImm8((byte)imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegIndirectRegOperands(dstReg, (byte) ${immExtOp});
	    ${emitImm}(imm);
	}
	if (lister != null) lister.RNI(miStart, "${acronym}", dstReg, imm);
    }
		
EOF
}

function emitBinaryImmByte() {
  acronym=$1
  opStr=$2
  eaxOpcode=$3
  imm8Code=$4
  imm32Code=$5
  immExtOp=$6
  size=$7
  ext=
  code=
  prefix=
  cat >> $FILENAME <<EOF
    // dstReg ${opStr}= (byte) imm
    public final void emit${acronym}_Reg_Imm_Byte(byte dstReg, int imm) {
	int miStart = mi;
	if (dstReg == EAX) {
	    setMachineCodes(mi++, (byte) $eaxOpcode);
	    emitImm8(imm);
	} else {
	    setMachineCodes(mi++, (byte) ${imm32Code});
	    // "register ${immExtOp}" is really part of the opcode 
	    emitRegRegOperands(dstReg, (byte) ${immExtOp});
	    emitImm8(imm);
	}
        if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
    }

    // [dstReg + dstDisp] ${opStr}= (byte) imm
    public final void emit${acronym}_RegDisp_Imm_Byte(byte dstReg, int dstDisp, int imm) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) ${imm32Code});
	// "register ${immExtOp}" is really part of the opcode 
	emitRegDispRegOperands(dstReg, dstDisp, (byte) ${immExtOp});
	emitImm8(imm);
	if (lister != null) lister.RDI(miStart, "${acronym}", dstReg, dstDisp, imm);
    }
		
    // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= (byte) imm 
    public final void emit${acronym}_RegIdx_Imm_Byte(byte dstBase, byte dstIndex, short scale, int dstDisp, int imm) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) ${imm32Code});
	// "register ${immExtOp}" is really part of the opcode 
	emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, (byte) ${immExtOp});
	emitImm8(imm);
	if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, imm);
    }
		
    // [dstIndex<<scale + dstDisp] ${opStr}= (byte) imm 
    public final void emit${acronym}_RegOff_Imm_Byte(byte dstIndex, short scale, int dstDisp, int imm) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) ${imm32Code});
	// "register ${immExtOp}" is really part of the opcode 
	emitRegOffRegOperands(dstIndex, scale, dstDisp, (byte) ${immExtOp});
	emitImm8(imm);
	if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, scale, dstDisp, imm);
    }
		
    // [dstDisp] ${opStr}= (byte) imm 
    public final void emit${acronym}_Abs_Imm_Byte(int dstDisp, int imm) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) ${imm32Code});
	// "register ${immExtOp}" is really part of the opcode 
	emitAbsRegOperands(dstDisp, (byte) ${immExtOp});
	emitImm8(imm);
	if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
    }
		
    // [dstReg] ${opStr}= (byte) imm
    public final void emit${acronym}_RegInd_Imm_Byte(byte dstReg, int imm) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) ${imm32Code});
	// "register ${immExtOp}" is really part of the opcode 
	emitRegIndirectRegOperands(dstReg, (byte) ${immExtOp});
        emitImm8(imm);
	if (lister != null) lister.RNI(miStart, "${acronym}", dstReg, imm);
    }
		
EOF
}

function emitBinaryAcc () {
  emitBinaryReg $1 $2 $7 $8 
  emitBinaryReg $1 $2 $7 $8 word
  emitBinaryReg $1 $2 ${11} ${12} byte
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6 word
  emitBinaryImmByte $1 $2 $9 none ${10} $6
}

emitBinaryAcc ADC +CF 0x15 0x83 0x81 0x2 0x11 0x13 0x14 0x80 0x10 0x12
emitBinaryAcc ADD + 0x05 0x83 0x81 0x0 0x01 0x03 0x04 0x80 0x00 0x02
emitBinaryAcc AND \& 0x25 0x83 0x81 0x4 0x21 0x23 0x24 0x80 0x20 0x22
emitBinaryAcc CMP = 0x3D 0x83 0x81 0x7 0x39 0x3B 0x3C 0x80 0x38 0x3A
emitBinaryAcc OR \| 0x0D 0x83 0x81 0x1 0x09 0x0B 0x0C 0x80 0x08 0x0A
emitBinaryAcc SBB -CF 0x1D 0x83 0x81 0x3 0x19 0x1B 0x1C 0x80 0x18 0x1A
emitBinaryAcc SUB - 0x2D 0x83 0x81 0x5 0x29 0x2B 0x2C 0x80 0x28 0x2A
emitBinaryAcc TEST \& 0xA9 0xF6 0xF7 0x0 0x85 none 0xA8 0xF6 0x84 none
emitBinaryAcc XOR \~ 0x35 0x83 0x81 0x6 0x31 0x33 0x34 0x80 0x30 0x32

emitBinaryReg MOV \: 0x89 0x8B
emitBinaryReg MOV \: 0x88 0x8A byte
emitBinaryReg MOV \: 0x89 0x8B word

emitBinaryReg CMPXCHG \<\-\> 0xB1 none 0x0F

function emitCall() {
  acronym=$1
  rel8Code=$2
  rel32Code=$3
  rmCode=$4
  rmExtCode=$5
  cat >> $FILENAME <<EOF
    // pc = {future address from label | imm}
    public final void emit${acronym}_ImmOrLabel(int imm, int label) {
	if (imm == 0)
	    emit${acronym}_Label( label );
	else
	    emit${acronym}_Imm( imm );
    }

    /**
     *  Branch to the given target with a ${acronym} instruction
     * <PRE>
     * IP = (instruction @ label)
     * </PRE>
     *
     *  This emit method is expecting only a forward branch (that is
     * what the Label operand means); it creates a VM_ForwardReference
     * to the given label, and puts it into the assembler's list of
     * references to resolve.  This emitter knows the branch is
     * unconditional, so it uses VM_ForwardReference.UnconditionalBranch 
     * as the forward reference type to create.
     *
     *  All forward branches have a label as the branch target; clients
     * can arbirarily associate labels and instructions, but must be
     * consistent in giving the chosen label as the target of branches
     * to an instruction and calling resolveForwardBranches with the
     * given label immediately before emitting the target instruction.
     * See the header comments of VM_ForwardReference for more details.
     *
     * @param label the label associated with the branch target instrucion
     *
     * @see VM_ForwardReference.UnconditionalBranch 
     */
    public final void emit${acronym}_Label(int label) {
      int miStart = mi;
      VM_ForwardReference r =  
	new VM_ForwardReference.UnconditionalBranch(mi, label);
      forwardRefs = VM_ForwardReference.enqueue(forwardRefs, r);
      setMachineCodes(mi++, (byte) ${rel32Code});
      mi += 4; // leave space for displacement 
      if (lister != null) lister.I(miStart, "${acronym}", label);
    }

    // pc = imm
    public final void emit${acronym}_Imm(int imm) {
	int miStart = mi;
EOF
  if [ $rel8Code != none ]; then
    cat >> $FILENAME <<EOF
	// can we fit the offset from the next instruction into 8
	// bits, assuming this instruction is 2 bytes (which it will
        // be if the offset fits into 8 bits)?
	int relOffset = imm - (mi + 2);
        if (fits(relOffset,8)) {
	    // yes, so use short form.
	    setMachineCodes(mi++, (byte) $rel8Code);
	    emitImm8((byte) relOffset);
        } else {
	    // no, must use 32 bit offset and ignore relOffset to
	    // account for the fact that this instruction now has to
	    // be 5 bytes long.
EOF
  fi
  cat >> $FILENAME <<EOF
	    setMachineCodes(mi++, (byte) $rel32Code);
	    // offset of next instruction (this instruction is 5 bytes,
	    // but we just accounted for one of them in the mi++ above)
	    emitImm32(imm - (mi + 4));
EOF
  if [ $rel8Code != none ]; then
    cat >> $FILENAME <<EOF
	}
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.I(miStart, "${acronym}", imm);
    }

    // pc = dstReg
    public final void emit${acronym}_Reg(byte dstReg) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitRegRegOperands(dstReg, (byte) $rmExtCode);
	if (lister != null) lister.R(miStart, "${acronym}", dstReg);
    }

    // pc = [dstReg + destDisp]
    public final void emit${acronym}_RegDisp(byte dstReg, int dstDisp) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitRegDispRegOperands(dstReg, dstDisp, (byte) $rmExtCode);
        if (lister != null) lister.RD(miStart, "${acronym}", dstReg, dstDisp);
    }

    // pc = [dstReg]
    public final void emit${acronym}_RegInd(byte dstReg) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitRegIndirectRegOperands(dstReg, (byte) $rmExtCode);
        if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
    }

    // pc = [dstIndex<<scale + dstDisp]
    public final void emit${acronym}_RegOff(byte dstIndex, short scale, int dstDisp) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitRegOffRegOperands(dstIndex, scale, dstDisp, (byte) $rmExtCode);
        if (lister != null) lister.RFD(miStart, "${acronym}", dstIndex, scale, dstDisp);
    }

    // pc = [dstDisp]
    public final void emit${acronym}_Abs(int dstDisp) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitAbsRegOperands(dstDisp, (byte) $rmExtCode);
        if (lister != null) lister.RA(miStart, "${acronym}", dstDisp);
    }

    // pc = [dstBase + dstIndex<<scale + dstDisp]
    public final void emit${acronym}_RegIdx(byte dstBase, byte dstIndex, short scale, int dstDisp) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) $rmCode);
	// "register $rmExtCode" is really part of the $acronym opcode
	emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, (byte) $rmExtCode);
        if (lister != null) lister.RXD(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp);
    }

EOF
}

emitCall CALL none 0xE8 0xFF 0x2
emitCall JMP 0xEB 0xE9 0xFF 0x4


emitUnaryAcc() {
    acronym=$1
    opStr=$2
    rOpCode=$3
    rmOpCode=$4
    rmOpExt=$5
    size=$6
  ext=
  code=
  prefix=
  if [ x$size = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$size = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="
        setMachineCodes(mi++, (byte) 0x66);"
  fi
    if [ $rOpCode != none ]; then
	cat >> $FILENAME <<EOF
    // $opStr ${code} reg
    public void emit${acronym}_Reg${ext}(byte reg) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) ($rOpCode | reg));
	if (lister != null) lister.R(miStart, "$acronym", reg);
    }
EOF
    else
	cat >> $FILENAME <<EOF
    // $opStr ${code} reg
    public final void emit${acronym}_Reg${ext}(byte reg) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	emitRegRegOperands(reg, (byte) $rmOpExt);
	if (lister != null) lister.R(miStart, "$acronym", reg);
    }
EOF
    fi
    cat >> $FILENAME <<EOF
    // $opStr ${code} [reg + disp]
    public final void emit${acronym}_RegDisp${ext}(byte reg, int disp) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	// "register $rmOpExt" is really part of the opcode
	emitRegDispRegOperands(reg, disp, (byte) $rmOpExt);
	if (lister != null) lister.RD(miStart, "$acronym", reg, disp);
    }

    // $opStr ${code} [reg]
    public final void emit${acronym}_RegInd${ext}(byte reg) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	// "register $rmOpExt" is really part of the opcode
	emitRegIndirectRegOperands(reg, (byte) $rmOpExt);
	if (lister != null) lister.RN(miStart, "$acronym", reg);
    }

    // $opStr ${code} [index<<scale + disp]
    public final void emit${acronym}_RegOff${ext}(byte index, short scale, int disp) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	// "register $rmOpExt" is really part of the opcode
	emitRegOffRegOperands(index, scale, disp, (byte) $rmOpExt);
	if (lister != null) lister.RFD(miStart, "$acronym", index, scale, disp);
    }

    // $opStr ${code} [disp]
    public final void emit${acronym}_Abs${ext}(int disp) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	// "register $rmOpExt" is really part of the opcode
	emitAbsRegOperands(disp, (byte) $rmOpExt);
	if (lister != null) lister.RA(miStart, "$acronym", disp);
    }

    // $opStr ${code} [base + index<<scale + disp]
    public final void emit${acronym}_RegIdx${ext}(byte base, byte index, short scale, int disp) {
	int miStart = mi;$prefix
	setMachineCodes(mi++, (byte) $rmOpCode);
	// "register $rmOpExt" is really part of the opcode
	emitSIBRegOperands(base, index, scale, disp, (byte) $rmOpExt);
	if (lister != null) lister.RXD(miStart, "$acronym", base, index, scale, disp);
    }

EOF
}

emitUnaryAcc DEC -- 0x48 0xFF 0x1
emitUnaryAcc INC ++ 0x40 0xFF 0x0
emitUnaryAcc NEG - none 0xF7 0x3
emitUnaryAcc NOT \~ none 0xF7 0x2

emitUnaryAcc DEC -- 0x48 0xFF 0x1 word
emitUnaryAcc INC ++ 0x40 0xFF 0x0 word
emitUnaryAcc NEG - none 0xF7 0x3 word
emitUnaryAcc NOT \~ none 0xF7 0x2 word

emitUnaryAcc DEC -- none 0xFE 0x1 byte
emitUnaryAcc INC ++ none 0xFE 0x0 byte
emitUnaryAcc NEG - none 0xF6 0x3 byte 
emitUnaryAcc NOT \~ none 0xF6 0x2 byte


emitMD() {
    acronym=$1
    opStr=$2
    opExt=$3
cat >> $FILENAME<<EOF
  // EAX:EDX = EAX $opStr srcReg
  public final void emit${acronym}_Reg_Reg(byte dstReg, byte srcReg) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegRegOperands(srcReg, (byte) ${opExt});
      if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  // EAX:EDX = EAX $opStr [srcReg + disp]
  public final void emit${acronym}_Reg_RegDisp(byte dstReg, byte srcReg, int disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegDispRegOperands(srcReg, disp, (byte) ${opExt});
      if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
  }

  // EAX:EDX = EAX $opStr [srcReg]
  public final void emit${acronym}_Reg_RegInd(byte dstReg, byte srcReg) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegIndirectRegOperands(srcReg, (byte) ${opExt});
      if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
  }

  // EAX:EDX = EAX $opStr [baseReg + idxRef<<scale + disp]
  public final void emit${acronym}_Reg_RegIdx(byte dstReg, byte baseReg, byte idxReg, short scale, int disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitSIBRegOperands(baseReg, idxReg, scale, disp, (byte) ${opExt});
      if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, baseReg, idxReg, scale, disp);
  }

  // EAX:EDX = EAX $opStr [idxRef<<scale + disp]
  public final void emit${acronym}_Reg_RegOff(byte dstReg, byte idxReg, short scale, int disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegOffRegOperands(idxReg, scale, disp, (byte) ${opExt});
      if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, idxReg, scale, disp);
  }

  // EAX:EDX = EAX $opStr [disp]
  public final void emit${acronym}_Reg_Abs(byte dstReg, int disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitAbsRegOperands(disp, (byte) ${opExt});
      if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
  }

EOF
}

emitMD DIV / 0x6
emitMD IDIV u/ 0x7
emitMD MUL \* 0x4
emitMD IMUL1 \* 0x5


emitMoveSubWord() {
    acronym=$1
    desc=$2
    rm8code=$3
    rm16code=$4
cat >> $FILENAME <<EOF
    // dstReg := (byte) srcReg ($desc)
    public final void emit${acronym}_Reg_Reg_Byte(byte dstReg, byte srcReg) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitRegRegOperands(srcReg, dstReg);
	if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
    }

    // dstReg := (byte) [srcReg + disp] ($desc)
    public final void emit${acronym}_Reg_RegDisp_Byte(byte dstReg, byte srcReg, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitRegDispRegOperands(srcReg, disp, dstReg);
	if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
    }

    // dstReg := (byte) [srcReg] ($desc)
    public final void emit${acronym}_Reg_RegInd_Byte(byte dstReg, byte srcReg) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitRegIndirectRegOperands(srcReg, dstReg);
	if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
    }

    // dstReg := (byte) [srcIndex<<scale + disp] ($desc)
    public final void emit${acronym}_Reg_RegOff_Byte(byte dstReg, byte srcIndex, short scale, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitRegOffRegOperands(srcIndex, scale, disp, dstReg);
	if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, scale, disp);
    }

    // dstReg := (byte) [disp] ($desc)
    public final void emit${acronym}_Reg_Abs_Byte(byte dstReg, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitAbsRegOperands(disp, dstReg);
	if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
    }

    // dstReg := (byte) [srcBase + srcIndex<<scale + disp] ($desc)
    public final void emit${acronym}_Reg_RegIdx_Byte(byte dstReg, byte srcBase, byte srcIndex, short scale, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm8code);
	emitSIBRegOperands(srcBase, srcIndex, scale, disp, dstReg);
	if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, scale, disp);
    }

    // dstReg := (word) srcReg ($desc)
    public final void emit${acronym}_Reg_Reg_Word(byte dstReg, byte srcReg) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitRegRegOperands(srcReg, dstReg);
	if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
    }

    // dstReg := (word) [srcReg + disp] ($desc)
    public final void emit${acronym}_Reg_RegDisp_Word(byte dstReg, byte srcReg, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitRegDispRegOperands(srcReg, disp, dstReg);
	if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
    }

    // dstReg := (word) [srcReg] ($desc)
    public final void emit${acronym}_Reg_RegInd_Word(byte dstReg, byte srcReg) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitRegIndirectRegOperands(srcReg, dstReg);
	if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
    }

    // dstReg := (word) [srcIndex<<scale + disp] ($desc)
    public final void emit${acronym}_Reg_RegOff_Word(byte dstReg, byte srcIndex, short scale, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitRegOffRegOperands(srcIndex, scale, disp, dstReg);
	if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, scale, disp);
    }

    // dstReg := (word) [disp] ($desc)
    public final void emit${acronym}_Reg_Abs_Word(byte dstReg, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitAbsRegOperands(disp, dstReg);
	if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
    }

    // dstReg := (word) [srcBase + srcIndex<<scale + disp] ($desc)
    public final void emit${acronym}_Reg_RegIdx_Word(byte dstReg, byte srcBase, byte srcIndex, short scale, int disp) {
        int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) $rm16code);
	emitSIBRegOperands(srcBase, srcIndex, scale, disp, dstReg);
	if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, scale, disp);
    }

EOF
}

emitMoveSubWord MOVSX "sign extended" 0xBE 0xBF
emitMoveSubWord MOVZX "zero extended" 0xB6 0xB7

emitShift () {
    acronym=$1
    descr=$2
    onceOp=$3
    regOp=$4
    immOp=$5
    opExt=$6
    size=$7
    ext=
    code=
    prefix=
    if [ x$size = xbyte ]; then
	ext=_Byte
	code=" (byte) "
    elif [ x$size = xword ]; then
	ext=_Word
	code=" (word) "
	prefix="
        setMachineCodes(mi++, (byte) 0x66);"
    fi
cat >> $FILENAME <<EOF
    // $descr of reg by imm
    public final void emit${acronym}_Reg_Imm${ext}(byte reg, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitRegRegOperands(reg, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitRegRegOperands(reg, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RI(miStart, "$acronym", reg, imm);
    }

    // $descr of [reg] by imm
    public final void emit${acronym}_RegInd_Imm${ext}(byte reg, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitRegIndirectRegOperands(reg, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitRegIndirectRegOperands(reg, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RNI(miStart, "$acronym", reg, imm);
    }

    // $descr of [reg + disp] by imm
    public final void emit${acronym}_RegDisp_Imm${ext}(byte reg, int disp, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitRegDispRegOperands(reg, disp, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitRegDispRegOperands(reg, disp, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RDI(miStart, "$acronym", reg, disp, imm);
    }

    // $descr of [index<<scale + disp] by imm
    public final void emit${acronym}_RegOff_Imm${ext}(byte index, short scale, int disp, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitRegOffRegOperands(index, scale, disp, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitRegOffRegOperands(index, scale, disp, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RFDI(miStart, "$acronym", index, scale, disp, imm);
    }

    // $descr of [disp] by imm
    public final void emit${acronym}_Abs_Imm${ext}(int disp, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitAbsRegOperands(disp, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitAbsRegOperands(disp, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RAI(miStart, "$acronym", disp, imm);
    }

    // $descr of [base + index<<scale + disp] by imm
    public final void emit${acronym}_RegIdx_Imm${ext}(byte base, byte index, short scale, int disp, int imm) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
	if (imm == 1) {
	    setMachineCodes(mi++, (byte) ${onceOp});
	    emitSIBRegOperands(base, index, scale, disp, (byte) $opExt);
	} else {
	    setMachineCodes(mi++, (byte) ${immOp});
	    emitSIBRegOperands(base, index, scale, disp, (byte) $opExt);
	    emitImm8((byte)imm);
	}
	if (lister != null) lister.RXDI(miStart, "$acronym", base, index, scale, disp, imm);
    }

    // $descr of dataReg by shiftBy
    public final void emit${acronym}_Reg_Reg${ext}(byte dataReg, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitRegRegOperands(dataReg, (byte) ${opExt});
	if (lister != null) lister.RR(miStart, "$acronym", dataReg, shiftBy);
    }

    // $descr of [dataReg] by shiftBy
    public final void emit${acronym}_RegInd_Reg${ext}(byte dataReg, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitRegIndirectRegOperands(dataReg, (byte) ${opExt});
	if (lister != null) lister.RNR(miStart, "$acronym", dataReg, shiftBy);
    }

    // $descr of [dataReg + disp] by shiftBy
    public final void emit${acronym}_RegDisp_Reg${ext}(byte dataReg, int disp, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitRegDispRegOperands(dataReg, disp, (byte) ${opExt});
	if (lister != null) lister.RDR(miStart, "$acronym", dataReg, disp, shiftBy);
    }

    // $descr of [indexReg<<scale + disp] by shiftBy
    public final void emit${acronym}_RegOff_Reg${ext}(byte indexReg, short scale, int disp, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitRegOffRegOperands(indexReg, scale, disp, (byte) ${opExt});
	if (lister != null) lister.RFDR(miStart, "$acronym", indexReg, scale, disp, shiftBy);
    }

    // $descr of [disp] by shiftBy
    public final void emit${acronym}_Abs_Reg${ext}(int disp, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitAbsRegOperands(disp, (byte) ${opExt});
	if (lister != null) lister.RAR(miStart, "$acronym", disp, shiftBy);
    }

    // $descr of [baseReg + indexReg<<scale + disp] by shiftBy
    public final void emit${acronym}_RegIdx_Reg${ext}(byte baseReg, byte indexReg, short scale, int disp, byte shiftBy) {
        int miStart = mi;
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
	setMachineCodes(mi++, (byte) $regOp);
	emitSIBRegOperands(baseReg, indexReg, scale, disp, (byte) ${opExt});
	if (lister != null) lister.RXDR(miStart, "$acronym", baseReg, indexReg, scale, disp, shiftBy);
    }

EOF
}

emitShift SAL "arithemetic shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SAL "arithemetic shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SAL "arithemetic shift left" 0xD1 0xD3 0xC1 0x4

emitShift SAR "arithemetic shift right" 0xD0 0xD2 0xC0 0x7 byte
emitShift SAR "arithemetic shift right" 0xD1 0xD3 0xC1 0x7 word
emitShift SAR "arithemetic shift right" 0xD1 0xD3 0xC1 0x7

emitShift SHL "logical shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4

emitShift SHR "logical shift right" 0xD0 0xD2 0xC0 0x5 byte
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5 word
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5

emitShift RCL "rotate left with carry" 0xD0 0xD2 0xC0 0x2 byte
emitShift RCL "rotate left with carry" 0xD1 0xD3 0xC1 0x2 word
emitShift RCL "rotate left with carry " 0xD1 0xD3 0xC1 0x2

emitShift RCR "rotate right with carry" 0xD0 0xD2 0xC0 0x3 byte
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3 word
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3

emitShift ROL "rotate left" 0xD0 0xD2 0xC0 0x0 byte
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0 word
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0

emitShift ROR "rotate right" 0xD0 0xD2 0xC0 0x1 byte
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1 word
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1

emitShiftDouble() {
    acronym=$1
    opStr=$2
    immOp=$3
    regOp=$4
    cat >> $FILENAME <<EOF
    // left ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_Reg_Reg_Imm(byte left, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitRegRegOperands(left, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RRI(miStart, "$acronym", left, right, shiftBy);
    }
    
    // [left] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegInd_Reg_Imm(byte left, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitRegIndirectRegOperands(left, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RNRI(miStart, "$acronym", left, right, shiftBy);
    }
    
    // [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegDisp_Reg_Imm(byte left, int disp, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitRegDispRegOperands(left, disp, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RDRI(miStart, "$acronym", left, disp, right, shiftBy);
    }
    
    // [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegIdx_Reg_Imm(byte leftBase, byte leftIndex, short scale, int disp, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RXDRI(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
    }

    // [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegOff_Reg_Imm(byte leftIndex, short scale, int disp, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitRegOffRegOperands(leftIndex, scale, disp, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RFDRI(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
    }
    
    // [disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_Abs_Reg_Imm(int disp, byte right, int shiftBy) {
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${immOp});
	emitAbsRegOperands(disp, right);
	emitImm8((byte)shiftBy);
	if (lister != null) lister.RARI(miStart, "$acronym", disp, right, shiftBy);
    }
    
    // left ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_Reg_Reg_Reg(byte left, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitRegRegOperands(left, right);
	if (lister != null) lister.RRR(miStart, "$acronym", left, right, shiftBy);
    }
    
    // [left] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegInd_Reg_Reg(byte left, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitRegIndirectRegOperands(left, right);
	if (lister != null) lister.RNRI(miStart, "$acronym", left, right, shiftBy);
    }
    
    // [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegDisp_Reg_Reg(byte left, int disp, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitRegDispRegOperands(left, disp, right);
	if (lister != null) lister.RDRI(miStart, "$acronym", left, disp, right, shiftBy);
    }
    
    // [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegIdx_Reg_Reg(byte leftBase, byte leftIndex, short scale, int disp, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
	if (lister != null) lister.RXDRR(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
    }
    
    // [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_RegOff_Reg_Reg(byte leftIndex, short scale, int disp, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitRegOffRegOperands(leftIndex, scale, disp, right);
	if (lister != null) lister.RFDRR(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
    }
    
    // [disp] ${opStr}= shiftBy (with bits from right shifted in)
    public final void emit${acronym}_Abs_Reg_Reg(int disp, byte right, byte shiftBy) {
	if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
	int miStart = mi;
	setMachineCodes(mi++, (byte) 0x0F);
	setMachineCodes(mi++, (byte) ${regOp});
	emitAbsRegOperands(disp, right);
	if (lister != null) lister.RARR(miStart, "$acronym", disp, right, shiftBy);
    }
    
EOF
}

emitShiftDouble SHLD \<\< 0xA4 0xA5
emitShiftDouble SHRD \<\< 0xAC 0xAD

emitStackOp() {
    acronym=$1
    op1=$2
    op2=$3
    regCode=$4
    memCode=$5
    memExt=$6
    imm32Code=$7
    cat >> $FILENAME <<EOF
  // $op1 dstReg, SP $op2 4
  public final void emit${acronym}_Reg (byte dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ($regCode + dstReg));
    if (lister != null) lister.R(miStart, "${acronym}", dstReg);
  }

  // $op1 [dstReg + dstDisp], SP $op2 4
  public final void emit${acronym}_RegDisp (byte dstReg, int dstDisp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegDispRegOperands(dstReg, dstDisp, (byte) ${memExt});
    if (lister != null) lister.RD(miStart, "${acronym}", dstReg, dstDisp);
  }

  // $op1 [dstReg], SP $op2 4
  public final void emit${acronym}_RegInd (byte dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegIndirectRegOperands(dstReg, (byte) ${memExt});
    if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
  }

  // $op1 [dstBase + dstNdx<<scale + dstDisp], SP $op2 4
  public final void emit${acronym}_RegIdx (byte base, byte index, short scale, int disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitSIBRegOperands(base, index, scale, disp, (byte) ${memExt});
    if (lister != null) lister.RXD(miStart, "${acronym}", base, index, scale, disp);
  }

  // $op1 [dstNdx<<scale + dstDisp], SP $op2 4
  public final void emit${acronym}_RegOff (byte index, short scale, int disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegOffRegOperands(index, scale, disp, (byte) ${memExt});
    if (lister != null) lister.RFD(miStart, "${acronym}", index, scale, disp);
  }

  // $op1 [dstDisp], SP $op2 4
  public final void emit${acronym}_Abs (int disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitAbsRegOperands(disp, (byte) ${memExt});
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
    if [ $imm32Code != none ]; then
	cat >> $FILENAME <<EOF
  // $op1 imm, SP $op2 4
  public final void emit${acronym}_Imm(int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    emitImm32(imm);
    if (lister != null) lister.I(miStart, "${acronym}", imm);
  }

EOF
    fi
}

emitStackOp POP pop -= 0x58 0x8F 0x0 none
emitStackOp PUSH push += 0x50 0xFF 0x6 0x68

emitFloatMemAcc() {
    local acronym=$1
    local op=$2
    local mOpExt=$3
    local opcode=$4
    local size=$5
    if [ x${size} = xquad ]; then
	ext="_Quad"
    elif [ x${size} = xword ]; then
	ext="_Word"
    else
        ext=""
    fi
    cat >> $FILENAME <<EOF
  // dstReg ${op}= (${size}) [srcReg + disp]
  public final void emit${acronym}_Reg_RegDisp${ext}(byte dstReg, byte srcReg, int disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == 0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegDispRegOperands(srcReg, disp, (byte) ${mOpExt});
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcReg, disp);
  }

  // dstReg ${op}= (${size}) [srcReg]
  public final void emit${acronym}_Reg_RegInd${ext}(byte dstReg, byte srcReg) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == 0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegIndirectRegOperands(srcReg, (byte) ${mOpExt});
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcReg);
  }

  // dstReg ${op}= (${size}) [srcBase + srcIndex<<scale + disp]
  public final void emit${acronym}_Reg_RegIdx${ext}(byte dstReg, byte srcBase, byte srcIndex, short scale, int disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == 0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitSIBRegOperands(srcBase, srcIndex, scale, disp, (byte) ${mOpExt});
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, scale, disp);
  }

  // dstReg ${op}= (${size}) [srcIndex<<scale + disp]
  public final void emit${acronym}_Reg_RegOff${ext}(byte dstReg, byte srcIndex, short scale, int disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == 0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegOffRegOperands(srcIndex, scale, disp, (byte) ${mOpExt});
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, scale, disp);
  }

  // dstReg ${op}= (${size}) [disp]
  public final void emit${acronym}_Reg_Abs${ext}(byte dstReg, int disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == 0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitAbsRegOperands(disp, (byte) ${mOpExt});
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, disp);
  }

EOF
}

emitFloatBinAcc() {
    acronym=$1
    intAcronym=$2
    popAcronym=$3
    op=$4
    mOpExt=$5
    to0Op=$6
    toIop=$7

    emitFloatMemAcc $acronym $op $mOpExt 0xD8 
    emitFloatMemAcc $acronym $op $mOpExt 0xDC quad
    emitFloatMemAcc $intAcronym $op $mOpExt 0xDA 
    emitFloatMemAcc $intAcronym $op $mOpExt 0xDE word

    cat >> $FILENAME <<EOF
  // dstReg ${op}= srcReg
  public final void emit${acronym}_Reg_Reg(byte dstReg, byte srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0 || dstReg == FP0);
    if (dstReg == FP0) {
	setMachineCodes(mi++, (byte) 0xD8);
	setMachineCodes(mi++, (byte) (${to0Op} | srcReg));
    } else if (srcReg == FP0) {
	setMachineCodes(mi++, (byte) 0xDC);
	setMachineCodes(mi++, (byte) (${toIop} | dstReg));
    }
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

  // srcReg ${op}= ST(0); pop stack
  public final void emit${popAcronym}_Reg_Reg(byte dstReg, byte srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0);
    setMachineCodes(mi++, (byte) 0xDE);
    setMachineCodes(mi++, (byte) (${toIop} | dstReg));
    if (lister != null) lister.R(miStart, "${popAcronym}", dstReg);
  }

EOF
}

emitFloatBinAcc FADD FIADD FADDP + 0 0xC0 0xC0
emitFloatBinAcc FDIV FIDIV FDIVP / 6 0xF0 0xF8
emitFloatBinAcc FDIVR FIDIVR FDIVRP / 7 0xF8 0xF0
emitFloatBinAcc FMUL FIMUL FMULP x 1 0xC8 0xC8
emitFloatBinAcc FSUB FISUB FSUBP - 4 0xE0 0xE8
emitFloatBinAcc FSUBR FISUBR FSUBRP - 5 0xE8 0xE0

emitFloatMem() {
    acronym=$1
    op=$2
    opcode=$3
    extCode=$4
    size=$5
    if [ x${size} = xquad ]; then
	ext="_Quad"
    elif [ x${size} = xword ]; then
	ext="_Word"
    else
        ext=""
    fi
    if [ ${acronym} = FILD -o ${acronym} = FLD ]; then
	pre="_Reg"
	preArg="byte dummy, "	
	postArg=""
    else
	pre=""
	ext=_Reg${ext}
	preArg=""
	postArg=", byte dummy"
    fi
    cat >> $FILENAME <<EOF
  // top of stack ${op} (${size:-double word}) [reg + disp] 
  public final void emit${acronym}${pre}_RegDisp${ext}(${preArg}byte reg, int disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(reg, disp, (byte) ${extCode});
    if (lister != null) lister.RD(miStart, "${acronym}", reg, disp);
  }

  // top of stack ${op} (${size:-double word}) [reg] 
  public final void emit${acronym}${pre}_RegInd${ext}(${preArg}byte reg${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(reg, (byte) ${extCode});
    if (lister != null) lister.RN(miStart, "${acronym}", reg);
  }

  // top of stack ${op} (${size:-double word}) [baseReg + idxReg<<scale + disp]
  public final void emit${acronym}${pre}_RegIdx${ext}(${preArg}byte baseReg, byte idxReg, short scale, int disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, idxReg, scale, disp, (byte) ${extCode});
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, idxReg, scale, disp);
  }

  // top of stack ${op} (${size:-double word}) [idxReg<<scale + disp]
  public final void emit${acronym}${pre}_RegOff${ext}(${preArg}byte idxReg, short scale, int disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(idxReg, scale, disp, (byte) ${extCode});
    if (lister != null) lister.RFD(miStart, "${acronym}", idxReg, scale, disp);
  }

  // top of stack ${op} (${size:-double word}) [disp]
  public final void emit${acronym}${pre}_Abs${ext}(${preArg}int disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, (byte) ${extCode});
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
}

emitFloatMem FLD "loaded from" 0xD9 0
emitFloatMem FLD "loaded from" 0xDD 0 quad
emitFloatMem FILD "loaded from" 0xDF 0 word
emitFloatMem FILD "loaded from" 0xDB 0
emitFloatMem FILD "loaded from" 0xDF 5 quad
emitFloatMem FIST "stored to" 0xDF 2 word
emitFloatMem FIST "stored to" 0xDB 2
emitFloatMem FISTP "stored to" 0xDF 3 word
emitFloatMem FISTP "stored to" 0xDB 3
emitFloatMem FISTP "stored to" 0xDF 7 quad
emitFloatMem FST "stored to" 0xD9 2
emitFloatMem FST "stored to" 0xDD 2 quad
emitFloatMem FSTP "stored to" 0xD9 3
emitFloatMem FSTP "stored to" 0xDD 3 quad

emitFloatCmp() {
    acronym=$1
    opcode1=$2
    opcode2=$3
    cat >> $FILENAME <<EOF
  public final void emit${acronym}_Reg_Reg (byte reg1, byte reg2) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(reg1 == FP0);
    setMachineCodes(mi++, (byte) ${opcode1});
    setMachineCodes(mi++, (byte)  (${opcode2} | reg2));
    if (lister != null) lister.RR(miStart, "${acronym}", reg1, reg2);
  }


EOF
}

emitFloatCmp FCOMI 0xDB 0xF0
emitFloatCmp FCOMIP 0xDF 0xF0
emitFloatCmp FUCOMI 0xDB 0xE8
emitFloatCmp FUCOMIP 0xDF 0xE8

emitMoveImms() {
    opcode=$1
    size=$2
    if [ x$size = xbyte ]; then
	ext="_Byte"
	prefix=""
	immWrite=emitImm8
    elif [ x$size = xword ]; then
	ext="_Word"
	prefix="
      setMachineCodes(mi++, (byte) 0x66);
"
	immWrite=emitImm16
    else
	ext=""
	prefix=""
	immWrite=emitImm32
    fi
cat >> $FILENAME <<EOF
  public final void emitMOV_RegInd_Imm${ext}(byte dst, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegIndirectRegOperands(dst, (byte) 0x0);
      ${immWrite}(imm);
      if (lister != null) lister.RNI(miStart, "MOV", dst, imm);
  }

  public final void emitMOV_RegDisp_Imm${ext}(byte dst, int disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegDispRegOperands(dst, disp, (byte) 0x0);
      ${immWrite}(imm);
      if (lister != null) lister.RDI(miStart, "MOV", dst, disp, imm);
  }

  public final void emitMOV_RegIdx_Imm${ext}(byte dst, byte idx, short scale, int disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitSIBRegOperands(dst, idx, scale, disp, (byte) 0x0);
      ${immWrite}(imm);
      if (lister != null) lister.RXDI(miStart, "MOV", dst, idx, scale, disp, imm);
  }

  public final void emitMOV_RegOff_Imm${ext}(byte idx, short scale, int disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegOffRegOperands(idx, scale, disp, (byte) 0x0);
      ${immWrite}(imm);
      if (lister != null) lister.RFDI(miStart, "MOV", idx, scale, disp, imm);
  }

  public final void emitMOV_Abs_Imm${ext}(int disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitAbsRegOperands(disp, (byte) 0x0);
      ${immWrite}(imm);
      if (lister != null) lister.RAI(miStart, "MOV", disp, imm);
  }

EOF
}

emitMoveImms 0xC6 byte
emitMoveImms 0xC7 word
emitMoveImms 0xC7 

emitFSTATE() {
  acronym=$1
  comment=$2
  opcode=$3
  opExt=$4
  pre=$5
  if [ x$pre != x ]; then
     prefix="
    setMachineCodes(mi++, (byte) ${pre});"
  else
     prefix=""
  fi

cat >> $FILENAME <<EOF
  // ${comment}
  public final void emit${acronym}_RegDisp (byte dstReg, int dstDisp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(dstReg, dstDisp, (byte) ${opExt});
    if (lister != null) lister.RD(miStart, "${acronym}", dstReg, dstDisp);
  }

  // ${comment}
  public final void emit${acronym}_RegInd (byte dstReg) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(dstReg, (byte) ${opExt});
    if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
  }

  // ${comment}
  public final void emit${acronym}_RegIdx (byte baseReg, byte indexReg, short scale, int disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, indexReg, scale, disp, (byte) ${opExt});
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, indexReg, scale, disp);
  }

  // ${comment}
  public final void emit${acronym}_RegOff (byte indexReg, short scale, int disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(indexReg, scale, disp, (byte) ${opExt});
    if (lister != null) lister.RFD(miStart, "${acronym}", indexReg, scale, disp);
  }

  // ${comment}
  public final void emit${acronym}_Abs (int disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, (byte) ${opExt});
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
}

emitFSTATE FNSAVE "save FPU state ignoring pending exceptions" 0xDD 6
emitFSTATE FSAVE "save FPU state respecting pending exceptions" 0xDD 6 0x9B
emitFSTATE FRSTOR "restore FPU state" 0xDD 4
emitFSTATE FLDCW "load FPU control word" 0xD9 5
emitFSTATE FSTCW "store FPU control word, checking for exceptions" 0xD9 7 0x9B
emitFSTATE FNSTCW "store FPU control word, ignoring exceptions" 0xD9 7


emitFCONST() {
opcode=$1
value=$2
opExt=$3

cat >> $FILENAME <<EOF
  // load ${value} into FP0
  public final void emit${opcode}_Reg(byte dstReg) {
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0xD9);
    setMachineCodes(mi++, (byte) ${opExt});
    if (lister != null) lister.R(miStart, "${opcode}", dstReg);
  }

EOF

}

emitFCONST FLD1 "1.0" 0xE8 
emitFCONST FLDL2T "log_2(10)" 0xE9
emitFCONST FLDL2E "log_2(e)" 0xEA
emitFCONST FLDPI "pi" 0xEB
emitFCONST FLDLG2 "log_10(2)" 0xEC
emitFCONST FLDLN2 "log_e(2)" 0xED
emitFCONST FLDZ "0.0" 0xEE

cat >> $FILENAME <<EOF
}
EOF
