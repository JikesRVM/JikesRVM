#!/bin/sh
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Common Public License (CPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/cpl1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

FILENAME=$1

cat $2 > $FILENAME

# Function to emit _Reg assembler routines
function emitBinaryReg() {
  acronym=$1
  opStr=$2
  rmrCode=$3
  rrmCode=$4
  sizeOrPrefix=$5
  ext=
  code=
  prefix="// no group 1 to 4 prefix byte"
  twobyteop="// single byte opcode"
  rex_w=false
  if [ x$sizeOrPrefix = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$sizeOrPrefix = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$sizeOrPrefix = xquad ]; then
    ext=_Quad
    code=" (quad) "
    rex_w=true
  elif [ x$sizeOrPrefix = x0x0Fquad ]; then
    ext=_Quad
    code=" (quad) "
    rex_w=true
    twobyteop="setMachineCodes(mi++, (byte) 0x0F);"
  elif [ x$sizeOrPrefix = x0x0F ]; then
    twobyteop="setMachineCodes(mi++, (byte) 0x0F);"
  elif [ x$sizeOrPrefix != x ]; then
    prefix="setMachineCodes(mi++, (byte) $sizeOrPrefix);"
  fi
  cat >> $FILENAME <<EOF
  /**
   * Generate a register(indirect)--register ${acronym}. That is,
   * <PRE>
   * [dstBase] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstBase the destination base
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg${ext}(GPR dstBase, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, null, dstBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegIndirectRegOperands(dstBase, srcReg);
    if (lister != null) lister.RNR(miStart, "${acronym}", dstBase, srcReg);
  }

  /**
   * Generate a register-offset--register ${acronym}. That is,
   * <PRE>
   * [dstReg<<dstScale + dstDisp] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR dstIndex, short dstScale, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, dstIndex, null);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RFDR(miStart, "${acronym}", dstIndex, dstScale, dstDisp, srcReg);
  }

  /**
   * Generate a absolute--register ${acronym}. That is,
   * <PRE>
   * [dstDisp] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstDisp the destination address
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address dstDisp, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, null, null);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitAbsRegOperands(dstDisp, srcReg);
    if (lister != null) lister.RAR(miStart, "${acronym}", dstDisp, srcReg);
  }

  /**
   * Generate a register-index--register ${acronym}. That is,
   * <PRE>
   * [dstBase + dstIndex<<dstScale + dstDisp] ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstBase the base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, dstIndex, dstBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RXDR(miStart, "${acronym}", dstBase, dstIndex, dstScale, dstDisp, srcReg);
  }

  /**
   * Generate a register-displacement--register ${acronym}. That is,
   * <PRE>
   * [dstBase + dstDisp] ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstBase the base register
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dstBase, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, null, dstBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegDispRegOperands(dstBase, dstDisp, srcReg);
    if (lister != null) lister.RDR(miStart, "${acronym}", dstBase, dstDisp, srcReg);
  }

  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, srcReg, null, dstReg);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegRegOperands(dstReg, srcReg);
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

EOF
    if [ x$rrmCode != xnone ]; then
    cat >> $FILENAME <<EOF
  /**
   * Generate a register--register-displacement ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcReg + srcDisp]
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp${ext}(GPR dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegDispRegOperands(srcBase, srcDisp, dstReg);
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcBase, srcDisp);
  }

  /**
   * Generate a register--register-offset ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcIndex the source index register
   * @param srcScale the source shift amount
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}(GPR dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, dstReg, srcIndex, null);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a register--register-offset ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcDisp]
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}(GPR dstReg, Address srcDisp) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, dstReg, null, null);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitAbsRegOperands(srcDisp, dstReg);
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
  }

  /**
   * Generate a register--register-offset ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcBase + srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   * @param srcIndex the source index register
   * @param srcScale the source shift amount
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}(GPR dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, dstReg, srcIndex, srcBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a register--register(indirect) ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcBase]
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}(GPR dstReg, GPR srcBase) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    ${twobyteop}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegIndirectRegOperands(srcBase, dstReg);
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcBase);
  }

EOF
    fi
}

# Function to emit _Imm assembler routines for 16/32 bit immediates
function emitBinaryImmWordOrDouble() {
  acronym=$1
  opStr=$2
  eaxOpcode=$3
  imm8Code=$4
  imm32Code=$5
  immExtOp=$6
  sizeOrPrefix=$7
  local ext=
  local code=
  local prefix="// no group 1 to 4 prefix byte"
  local twobyteop="// single byte opcode"
  local emitImm=emitImm32
  local rex_w=false
  if [ x$sizeOrPrefix = xword ]; then
    ext=_Word
    code=" (word) "
    emitImm=emitImm16
    prefix="setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$sizeOrPrefix = xquad ]; then
    ext=_Quad
    code=" (quad) "
    rex_w=true
  elif [ x$sizeOrPrefix = x0x0F ]; then
    twobyteop="setMachineCodes(mi++, (byte) 0x0F);"
  elif [ x$sizeOrPrefix != x ]; then
    prefix="setMachineCodes(mi++, (byte) $sizeOrPrefix);"
  fi
  cat >> $FILENAME <<EOF
  /**
   * Generate a register--immediate ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstReg the destination register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm${ext}(GPR dstReg, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstReg);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$eaxOpcode != xnone ]; then
  cat >> $FILENAME <<EOF
    } else if (dstReg == EAX) {
      setMachineCodes(mi++, (byte) $eaxOpcode);
      ${emitImm}(imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
  }

  /**
   * Generate a register-displacement--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase + dstDisp] ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstBase the destination register
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm${ext}(GPR dstBase, Offset dstDisp, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RDI(miStart, "${acronym}", dstBase, dstDisp, imm);
  }

  /**
   * Generate a register-offset--immediate ${acronym}. That is,
   * <PRE>
   * [dstIndex<<dstScale + dstDisp] ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm${ext}(GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, null);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a absolute--immediate ${acronym}. That is,
   * <PRE>
   * [dstDisp] ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  public final void emit${acronym}_Abs_Imm${ext}(Address dstDisp, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, null, null);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
  }

  /**
   * Generate a register-index--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase + dstIndex<<dstScale + dstDisp] ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, dstBase);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a register(indirect)--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase] ${opStr}= ${code} imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm${ext}(GPR dstBase, int imm) {
    int miStart = mi;
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    ${twobyteop}
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegIndirectRegOperands(dstBase, GPR.getForOpcode(${immExtOp}));
      emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
      throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegIndirectRegOperands(dstBase, GPR.getForOpcode(${immExtOp}));
      ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RNI(miStart, "${acronym}", dstBase, imm);
  }

EOF
}

# Function to emit _Imm_Byte assembler routines
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
  cat >> $FILENAME <<EOF
  /**
   * Generate a register--immediate ${acronym}. That is,
   * <PRE>
   *  dstReg ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstReg the destination register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm_Byte(GPR dstReg, int imm) {
    int miStart = mi;
    if (dstReg == EAX) {
      setMachineCodes(mi++, (byte) $eaxOpcode);
      emitImm8(imm);
    } else {
      generateREXprefix(false, null, null, dstReg);
      setMachineCodes(mi++, (byte) ${imm32Code});
      // "register ${immExtOp}" is really part of the opcode
      emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
      emitImm8(imm);
    }
    if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
  }

  /**
   * Generate a register-displacement--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase + dstDisp] ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstBase the destination register
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm_Byte(GPR dstBase, Offset dstDisp, int imm) {
    int miStart = mi;
    generateREXprefix(false, null, null, dstBase);
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RDI(miStart, "${acronym}", dstBase, dstDisp, imm);
  }

  /**
   * Generate a register-index--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase + dstIndex<<scale + dstDisp] ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm_Byte(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    generateREXprefix(false, null, dstIndex, dstBase);
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a register-offset--immediate ${acronym}. That is,
   * <PRE>
   * [dstIndex<<dstScale + dstDisp] ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm_Byte(GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    generateREXprefix(false, null, dstIndex, null);
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a absolute--immediate ${acronym}. That is,
   * <PRE>
   * [dstDisp] ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  public final void emit${acronym}_Abs_Imm_Byte(Address dstDisp, int imm) {
    int miStart = mi;
    generateREXprefix(false, null, null, null);
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
  }

  /**
   * Generate a register(indirect)--immediate ${acronym}. That is,
   * <PRE>
   * [dstBase] ${opStr}= (byte) imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm_Byte(GPR dstBase, int imm) {
    int miStart = mi;
    generateREXprefix(false, null, null, dstBase);
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegIndirectRegOperands(dstBase, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RNI(miStart, "${acronym}", dstBase, imm);
  }

EOF
}

# Emit _Reg, _Reg_Word, _Reg_Byte, _Imm, _Imm_Word and _Imm_Byte suffixes
# $1 = acronym
# $2 = opStr
# $3 = eaxOpcode (_Imm, _Imm_Word)
# $4 = imm8code
# $5 = imm32code
# $6 = immExtOp
# $7 = rmrCode
# $8 = rrmCode
# $9 = eaxOpcode (_Imm_Byte)
# ${10} = imm32code (_Imm_Byte)
# ${11} = rmrCode
# ${12} = rrmCode
function emitBinaryAcc () {
  emitBinaryReg $1 $2 $7 $8
  emitBinaryReg $1 $2 $7 $8 word
  emitBinaryReg $1 $2 $7 $8 quad
  emitBinaryReg $1 $2 ${11} ${12} byte
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6 word
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6 quad
  emitBinaryImmByte $1 $2 $9 none ${10} $6
}
#             1   2   3    4    5    6   7    8    9    10   11   12
emitBinaryAcc ADC +CF 0x15 0x83 0x81 0x2 0x11 0x13 0x14 0x80 0x10 0x12
emitBinaryAcc ADD +   0x05 0x83 0x81 0x0 0x01 0x03 0x04 0x80 0x00 0x02
emitBinaryAcc AND \&  0x25 0x83 0x81 0x4 0x21 0x23 0x24 0x80 0x20 0x22
emitBinaryAcc CMP =   0x3D 0x83 0x81 0x7 0x39 0x3B 0x3C 0x80 0x38 0x3A
emitBinaryAcc OR \|   0x0D 0x83 0x81 0x1 0x09 0x0B 0x0C 0x80 0x08 0x0A
emitBinaryAcc SBB -CF 0x1D 0x83 0x81 0x3 0x19 0x1B 0x1C 0x80 0x18 0x1A
emitBinaryAcc SUB -   0x2D 0x83 0x81 0x5 0x29 0x2B 0x2C 0x80 0x28 0x2A
emitBinaryAcc TEST \& 0xA9 none 0xF7 0x0 0x85 none 0xA8 0xF6 0x84 none
emitBinaryAcc XOR \~  0x35 0x83 0x81 0x6 0x31 0x33 0x34 0x80 0x30 0x32

function emitBT() {
  acronym=$1
  opStr=$2
  rmrCode=$3
  immExtOp=$4
  prefix=0x0F
  immCode=0xBA
  emitBinaryReg $acronym $opStr $rmrCode none $prefix
  emitBinaryImmWordOrDouble $acronym $opStr none $immCode none $immExtOp 0x0F
}

emitBT BT BT 0xA3 0x4
emitBT BTC BTC 0xBB 0x7
emitBT BTR BTR 0xB3 0x6
emitBT BTS BTS 0xAB 0x5

function emitCall() {
  acronym=$1
  rel8Code=$2
  rel32Code=$3
  rmCode=$4
  rmExtCode=$5
  cat >> $FILENAME <<EOF
  /**
   * Generate a ${acronym} to a label or immediate. That is,
   * <PRE>
   *  pc = {future address from label | imm}
   * </PRE>
   *
   * @param imm offset to immediate ${acronym} to. 0 means use
   * label. Immediate is assumed to be within current instructions.
   * @param label label to branch to (used when imm == 0)
   */
  public final void emit${acronym}_ImmOrLabel(int imm, int label) {
    if (imm == 0)
      emit${acronym}_Label(label);
    else
      emit${acronym}_Imm(imm);
  }

  /**
   * Branch to the given target with a ${acronym} instruction
   * <PRE>
   * IP = (instruction @ label)
   * </PRE>
   *
   * This emit method is expecting only a forward branch (that is
   * what the Label operand means); it creates a ForwardReference
   * to the given label, and puts it into the assembler's list of
   * references to resolve.  This emitter knows the branch is
   * unconditional, so it uses
   * {@link org.jikesrvm.compilers.common.assembler.ForwardReference.UnconditionalBranch}
   * as the forward reference type to create.
   *
   * All forward branches have a label as the branch target; clients
   * can arbirarily associate labels and instructions, but must be
   * consistent in giving the chosen label as the target of branches
   * to an instruction and calling resolveForwardBranches with the
   * given label immediately before emitting the target instruction.
   * See the header comments of ForwardReference for more details.
   *
   * @param label the label associated with the branch target instrucion
   */
  public final void emit${acronym}_Label(int label) {
      int miStart = mi;
      ForwardReference r =
        new ForwardReference.UnconditionalBranch(mi, label);
      forwardRefs = ForwardReference.enqueue(forwardRefs, r);
      setMachineCodes(mi++, (byte) ${rel32Code});
      mi += 4; // leave space for displacement
      if (lister != null) lister.I(miStart, "${acronym}", label);
  }

  /**
   * Generate a ${acronym} to immediate. That is,
   * <PRE>
   * pc = imm
   * </PRE>
   *
   * @param imm offset to immediate ${acronym} to (within current instructions)
   */
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

  /**
   * Generate a ${acronym} to register. That is,
   * <PRE>
   * pc = dstReg
   * </PRE>
   *
   * @param dstReg register containing destination address
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg(GPR dstReg) {
    int miStart = mi;
    generateREXprefix(false, null, null, dstReg);
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegRegOperands(dstReg, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.R(miStart, "${acronym}", dstReg);
  }

  /**
   * Generate a ${acronym} to register and displacement. That is,
   * <PRE>
   * pc = [dstBase + dstDisp]
   * </PRE>
   *
   * @param dstBase the destination base address register
   * @param dstDisp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp(GPR dstBase, Offset dstDisp) {
    int miStart = mi;
    generateREXprefix(false, null, null, dstBase);
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RD(miStart, "${acronym}", dstBase, dstDisp);
  }

  /**
   * Generate a ${acronym} to register indirect. That is,
   * <PRE>
   * pc = [dstBase]
   * </PRE>
   *
   * @param dstBase the destination base address register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd(GPR dstBase) {
    int miStart = mi;
    generateREXprefix(false, null, null, dstBase);
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegIndirectRegOperands(dstBase, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RN(miStart, "${acronym}", dstBase);
  }

  /**
   * Generate a ${acronym} to register offset. That is,
   * <PRE>
   * pc = [dstIndex<<dstScale + dstDisp]
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff(GPR dstIndex, short dstScale, Offset dstDisp) {
    int miStart = mi;
    generateREXprefix(false, null, dstIndex, null);
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RFD(miStart, "${acronym}", dstIndex, dstScale, dstDisp);
  }

  /**
   * Generate a ${acronym} to absolute address. That is,
   * <PRE>
   * pc = [dstDisp]
   * </PRE>
   *
   * @param dstDisp the destination displacement
   */
  public final void emit${acronym}_Abs(Address dstDisp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitAbsRegOperands(dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RA(miStart, "${acronym}", dstDisp);
  }

  /**
   * Generate a ${acronym} to register offset. That is,
   * <PRE>
   * pc = [dstBase + dstIndex<<dstScale + dstDisp]
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp) {
    int miStart = mi;
    generateREXprefix(false, null, dstIndex, dstBase);
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RXD(miStart, "${acronym}", dstBase, dstIndex, dstScale, dstDisp);
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
  local ext=
  local code=
  local prefix="// no group 1 to 4 prefix byte"
  local rex_w=false
  if [ x$size = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$size = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$size = xquad ]; then
    ext=_Quad
    code=" (quad) "
    rex_w=true
  fi
  if [ $rOpCode != none ]; then
    cat >> $FILENAME <<EOF
  /**
   * Generate a ${acronym} on a register. That is,
   * <PRE>
   * $opStr ${code} reg
   * </PRE>
   *
   * @param reg register to operate upon
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public void emit${acronym}_Reg${ext}(GPR reg) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, reg);
    if (!VM.buildFor32Addr()) {
      setMachineCodes(mi++, (byte) ($rmOpCode));
      emitRegRegOperands(reg, GPR.getForOpcode($rmOpExt));
    } else {
      setMachineCodes(mi++, (byte) ($rOpCode | (reg.value() & 7)));
    }
    if (lister != null) lister.R(miStart, "$acronym", reg);
  }
EOF
    else
      cat >> $FILENAME <<EOF
  /**
   * Generate a ${acronym} on a register. That is,
   * <PRE>
   * $opStr ${code} reg
   * </PRE>
   *
   * @param reg register to operate upon
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg${ext}(GPR reg) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, reg);
    setMachineCodes(mi++, (byte) $rmOpCode);
    emitRegRegOperands(reg, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.R(miStart, "$acronym", reg);
  }
EOF
    fi
    cat >> $FILENAME <<EOF
  /**
   * Generate a ${acronym} to register-displacement offset. That is,
   * <PRE>
   * $opStr ${code} [base + disp]
   * </PRE>
   *
   * @param base the destination base register
   * @param disp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp${ext}(GPR base, Offset disp) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, base);
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegDispRegOperands(base, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RD(miStart, "$acronym", base, disp);
  }

  /**
   * Generate a ${acronym} to register indirect. That is,
   * <PRE>
   * $opStr ${code} [reg]
   * </PRE>
   *
   * @param base the destination base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd${ext}(GPR base) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, base);
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegIndirectRegOperands(base, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RN(miStart, "$acronym", base);
  }

  /**
   * Generate a ${acronym} to register offset. That is,
   * <PRE>
   * $opStr ${code} [index<<scale + disp]
   * </PRE>
   *
   * @param index the destination index register
   * @param scale the destination shift amount
   * @param disp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff${ext}(GPR index, short scale, Offset disp) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, index, null);
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RFD(miStart, "$acronym", index, scale, disp);
  }

  /**
   * Generate a ${acronym} to absolute address. That is,
   * <PRE>
   * $opStr ${code} [disp]
   * </PRE>
   *
   * @param disp the destination displacement
   */
  public final void emit${acronym}_Abs${ext}(Address disp) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, null);
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitAbsRegOperands(disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RA(miStart, "$acronym", disp);
  }

  /**
   * Generate a ${acronym} to register offset. That is,
   * <PRE>
   * $opStr ${code} [base + index<<scale + disp]
   * </PRE>
   *
   * @param base the destination base register
   * @param index the destination index register
   * @param scale the destination shift amount
   * @param disp the destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx${ext}(GPR base, GPR index, short scale, Offset disp) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, index, base);
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RXD(miStart, "$acronym", base, index, scale, disp);
  }

EOF
}

emitUnaryAcc DEC -- 0x48 0xFF 0x1
emitUnaryAcc DEC -- none 0xFE 0x1 byte
emitUnaryAcc DEC -- 0x48 0xFF 0x1 word
emitUnaryAcc DEC -- 0x48 0xFF 0x1 quad

emitUnaryAcc INC ++ 0x40 0xFF 0x0
emitUnaryAcc INC ++ none 0xFE 0x0 byte
emitUnaryAcc INC ++ 0x40 0xFF 0x0 word
emitUnaryAcc INC ++ 0x40 0xFF 0x0 quad

emitUnaryAcc NEG - none 0xF7 0x3
emitUnaryAcc NEG - none 0xF6 0x3 byte
emitUnaryAcc NEG - none 0xF7 0x3 word
emitUnaryAcc NEG - none 0xF7 0x3 quad

emitUnaryAcc NOT \~ none 0xF7 0x2
emitUnaryAcc NOT \~ none 0xF6 0x2 byte
emitUnaryAcc NOT \~ none 0xF7 0x2 word
emitUnaryAcc NOT \~ none 0xF7 0x2 quad

emitMulDiv() {
    acronym=$1
    opStr=$2
    opExt=$3
    size=$4
    rex_w=false
    ext=
    if [ x$size = xquad ]; then
      rex_w=true
      ext=_Quad
    fi
cat >> $FILENAME<<EOF
  /**
   * Generate a ${acronym} by register. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr srcReg
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, null, srcReg);
    setMachineCodes(mi++, (byte) 0xF7);
    emitRegRegOperands(srcReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  /**
   * Generate a ${acronym} by register displacement. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr [srcBase + srcDisp]
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcBase the source base register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp${ext}(GPR dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, null, srcBase);
    setMachineCodes(mi++, (byte) 0xF7);
    emitRegDispRegOperands(srcBase, srcDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcBase, srcDisp);
  }

  /**
   * Generate a ${acronym} by register indirect. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr [srcBase]
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcBase the source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}(GPR dstReg, GPR srcBase) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, null, srcBase);
    setMachineCodes(mi++, (byte) 0xF7);
    emitRegIndirectRegOperands(srcBase, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcBase);
  }

  /**
   * Generate a ${acronym} by register indexed. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr [srcBase + srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcBase the source base register
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}(GPR dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, srcIndex, srcBase);
    setMachineCodes(mi++, (byte) 0xF7);
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a ${acronym} by register offseted. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr [srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}(GPR dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, srcIndex, null);
    setMachineCodes(mi++, (byte) 0xF7);
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a ${acronym} by absolute address. That is,
   * <PRE>
   * EAX:EDX = EAX $opStr [srcDisp]
   * </PRE>
   *
   * @param dstReg must always be EAX/R0
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}(GPR dstReg, Address srcDisp) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
    generateREXprefix(${rex_w}, null, null, null);
    setMachineCodes(mi++, (byte) 0xF7);
    emitAbsRegOperands(srcDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RRA(miStart, "$acronym", dstReg, srcDisp);
  }

EOF
}

emitMulDiv MUL   \* 0x4
emitMulDiv MUL   \* 0x4 quad
emitMulDiv IMUL1 \* 0x5
emitMulDiv IMUL1 \* 0x5 quad
emitMulDiv DIV   /  0x6
emitMulDiv DIV   /  0x6 quad
emitMulDiv IDIV  u/ 0x7
emitMulDiv IDIV  u/ 0x7 quad

emitMoveImms() {
    opcode=$1
    size=$2
    local ext=
    local prefix="// no prefix byte"
    local immWrite=emitImm32
    local rex_w=false
    local imm_type=int
    if [ x$size = xbyte ]; then
      ext="_Byte"
      immWrite=emitImm8
    elif [ x$size = xword ]; then
      ext="_Word"
      prefix="setMachineCodes(mi++, (byte) 0x66);"
      immWrite=emitImm16
    elif [ x$size = xquad ]; then
      ext="_Quad"
      rex_w=true
    fi
    cat >> $FILENAME <<EOF
  /**
   * Generate a register-indirect--immediate MOV. That is,
   * <PRE>
   * [dstBase] MOV = imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegInd_Imm${ext}(GPR dstBase, ${imm_type} imm) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, dstBase);
    setMachineCodes(mi++, (byte) $opcode);
    emitRegIndirectRegOperands(dstBase, GPR.getForOpcode(0x0));
    ${immWrite}(imm);
    if (lister != null) lister.RNI(miStart, "MOV", dstBase, imm);
  }

  /**
   * Generate a register-displacement--immediate MOV. That is,
   * <PRE>
   * [dstBase + dstDisp] MOV = imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegDisp_Imm${ext}(GPR dstBase, Offset dstDisp, ${imm_type} imm) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, dstBase);
    setMachineCodes(mi++, (byte) $opcode);
    emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode(0x0));
    ${immWrite}(imm);
    if (lister != null) lister.RDI(miStart, "MOV", dstBase, dstDisp, imm);
  }

  /**
   * Generate a register-index--immediate MOV. That is,
   * <PRE>
   * [dstBase + dstIndex<<scale + dstDisp] MOV = imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emitMOV_RegIdx_Imm${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, ${imm_type} imm) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, dstIndex, dstBase);
    setMachineCodes(mi++, (byte) $opcode);
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode(0x0));
    ${immWrite}(imm);
    if (lister != null) lister.RXDI(miStart, "MOV", dstBase, dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a register-index--immediate MOV. That is,
   * <PRE>
   * [dstIndex<<scale + dstDisp] MOV = imm
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegOff_Imm${ext}(GPR dstIndex, short dstScale, Offset dstDisp, ${imm_type} imm) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, dstIndex, null);
    setMachineCodes(mi++, (byte) $opcode);
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode(0x0));
    ${immWrite}(imm);
    if (lister != null) lister.RFDI(miStart, "MOV", dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate an absolute MOV. That is,
   * <PRE>
   * [dstDisp] MOV = imm
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  public final void emitMOV_Abs_Imm${ext}(Address dstDisp, ${imm_type} imm) {
    int miStart = mi;
    $prefix
    generateREXprefix(${rex_w}, null, null, null);
    setMachineCodes(mi++, (byte) $opcode);
    emitAbsRegOperands(dstDisp, GPR.getForOpcode(0x0));
    ${immWrite}(imm);
    if (lister != null) lister.RAI(miStart, "MOV", dstDisp, imm);
  }

EOF
}

emitBinaryReg MOV \: 0x89 0x8B
emitBinaryReg MOV \: 0x88 0x8A byte
emitBinaryReg MOV \: 0x89 0x8B word
emitBinaryReg MOV \: 0x89 0x8B quad

emitMoveImms 0xC6 byte
emitMoveImms 0xC7 word
emitMoveImms 0xC7
emitMoveImms 0xC7 quad

emitMoveSubWord() {
    acronym=$1
    desc=$2
    rm8code=$3
    rm16code=$4
    size=$5
    rex_w=false
    if [ x$size = xquad ]; then
      rex_w=true
      acronym=${acronym}Q
    fi
cat >> $FILENAME <<EOF
  /**
   * Generate a move ${desc} from register. That is,
   * <PRE>
   * dstReg := (byte) srcReg ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Byte(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcReg);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegRegOperands(srcReg, dstReg);
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  /**
   * Generate a move ${desc} from register displacement. That is,
   * <PRE>
   * dstReg := (byte) [srcBase + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp_Byte(GPR dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegDispRegOperands(srcBase, srcDisp, dstReg);
    if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcBase, srcDisp);
  }

  /**
   * Generate a move ${desc} from register indirect. That is,
   * <PRE>
   * dstReg := (byte) [srcBase] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd_Byte(GPR dstReg, GPR srcBase) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegIndirectRegOperands(srcBase, dstReg);
    if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcBase);
  }

  /**
   * Generate a move ${desc} from register offset. That is,
   * <PRE>
   * dstReg := (byte) [srcIndex<<srcScale + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff_Byte(GPR dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, srcIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a move ${desc} from an absolute address. That is,
   * <PRE>
   * dstReg := (byte) [srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs_Byte(GPR dstReg, Address srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitAbsRegOperands(srcDisp, dstReg);
    if (lister != null) lister.RRA(miStart, "$acronym", dstReg, srcDisp);
  }

  /**
   * Generate a move ${desc} by register indexed. That is,
   * <PRE>
   * dstReg := (byte) [srcBase + srcIndex<<srcScale + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx_Byte(GPR dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, srcIndex, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a move ${desc} from register. That is,
   * <PRE>
   * dstReg := (word) srcReg ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Word(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcReg);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegRegOperands(srcReg, dstReg);
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  /**
   * Generate a move ${desc} from register displacement. That is,
   * <PRE>
   * dstReg := (word) [srcBase + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp_Word(GPR dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegDispRegOperands(srcBase, srcDisp, dstReg);
    if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcBase, srcDisp);
  }

  /**
   * Generate a move ${desc} from register indirect. That is,
   * <PRE>
   * dstReg := (word) [srcBase] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd_Word(GPR dstReg, GPR srcBase) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegIndirectRegOperands(srcBase, dstReg);
    if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcBase);
  }

  /**
   * Generate a move ${desc} from register offset. That is,
   * <PRE>
   * dstReg := (word) [srcIndex<<srcScale + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff_Word(GPR dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, srcIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a move ${desc} from an absolute address. That is,
   * <PRE>
   * dstReg := (word) [srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs_Word(GPR dstReg, Address srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitAbsRegOperands(srcDisp, dstReg);
    if (lister != null) lister.RRA(miStart, "$acronym", dstReg, srcDisp);
  }

  /**
   * Generate a move ${desc} by register indexed. That is,
   * <PRE>
   * dstReg := (word) [srcBase + srcIndex<<srcScale + srcDisp] ($desc)
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcBase the source base register
   * @param srcIndex the source index register
   * @param srcScale the source scale of the index
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx_Word(GPR dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    generateREXprefix(${rex_w}, dstReg, srcIndex, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, dstReg);
    if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, srcScale, srcDisp);
    }

EOF
}

emitMoveSubWord MOVSX "sign extended" 0xBE 0xBF
emitMoveSubWord MOVSX "sign extended" 0xBE 0xBF quad
emitMoveSubWord MOVZX "zero extended" 0xB6 0xB7
emitMoveSubWord MOVZX "zero extended" 0xB6 0xB7 quad

emitBinaryReg CMPXCHG \<\-\> 0xB1 none 0x0F
emitBinaryReg CMPXCHG \<\-\> 0xB1 none 0x0Fquad

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
    prefix="// no size prefix"
    rex_w=false
    if [ x$size = xbyte ]; then
      ext=_Byte
      code=" (byte) "
    elif [ x$size = xword ]; then
      ext=_Word
      code=" (word) "
      prefix="setMachineCodes(mi++, (byte) 0x66);"
    elif [ x$size = xquad ]; then
      ext=_Quad
      code=" (quad) "
      rex_w=true
    fi
cat >> $FILENAME <<EOF
  /**
   * Generate a register--immediate ${acronym}. That is,
   * <PRE>
   * $descr of dstReg by imm
   * </PRE>
   *
   * @param dstReg the destination register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm${ext}(GPR dstReg, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstReg);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitRegRegOperands(dstReg, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitRegRegOperands(dstReg, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RI(miStart, "$acronym", dstReg, imm);
  }

  /**
   * Generate a register-indirect--immediate ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase] by imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm${ext}(GPR dstBase, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitRegIndirectRegOperands(dstBase, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitRegIndirectRegOperands(dstBase, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RNI(miStart, "$acronym", dstBase, imm);
  }

  /**
   * Generate a register-displacement--immediate ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase + dstDisp] by imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm${ext}(GPR dstBase, Offset dstDisp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RDI(miStart, "$acronym", dstBase, dstDisp, imm);
  }

  /**
   * Generate a register-offset--immediate ${acronym}. That is,
   * <PRE>
   * $descr of [dstIndex<<dstScale + dstDisp] by imm
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm${ext}(GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, null);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RFDI(miStart, "$acronym", dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a absolute--immediate ${acronym}. That is,
   * <PRE>
   * $descr of [dstDisp] by imm
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  public final void emit${acronym}_Abs_Imm${ext}(Address dstDisp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, null, null);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitAbsRegOperands(dstDisp, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitAbsRegOperands(dstDisp, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RAI(miStart, "$acronym", dstDisp, imm);
  }

  /**
   * Generate a register-index--immediate ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase + dstIndex<<dstScale + dstDisp] by imm
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param imm immediate
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8));
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, dstBase);
    if (imm == 1) {
      setMachineCodes(mi++, (byte) ${onceOp});
      emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode($opExt));
    } else {
      setMachineCodes(mi++, (byte) ${immOp});
      emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode($opExt));
      emitImm8((byte)imm);
    }
    if (lister != null) lister.RXDI(miStart, "$acronym", dstBase, dstIndex, dstScale, dstDisp, imm);
  }

  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * $descr of dstReg by srcReg
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstReg);
    setMachineCodes(mi++, (byte) $regOp);
    emitRegRegOperands(dstReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  /**
   * Generate a register-indirect--register ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase] by srcReg
   * </PRE>
   *
   * @param dstBase the destination register
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg${ext}(GPR dstBase, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    setMachineCodes(mi++, (byte) $regOp);
    emitRegIndirectRegOperands(dstBase, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RNR(miStart, "$acronym", dstBase, srcReg);
  }

  /**
   * Generate a register-displacement--register ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase + dstDisp] by srcReg
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstDisp the destination displacement
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dstBase, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, null, dstBase);
    setMachineCodes(mi++, (byte) $regOp);
    emitRegDispRegOperands(dstBase, dstDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RDR(miStart, "$acronym", dstBase, dstDisp, srcReg);
  }

  /**
   * Generate a register-offset--register ${acronym}. That is,
   * <PRE>
   * $descr of [dstIndex<<dstScale + dstDisp] by srcReg
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR dstIndex, short dstScale, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, null);
    setMachineCodes(mi++, (byte) $regOp);
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RFDR(miStart, "$acronym", dstIndex, dstScale, dstDisp, srcReg);
  }

  /**
   * Generate an absolute--register ${acronym}. That is,
   * <PRE>
   * $descr of [dstDisp] by srcReg
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address dstDisp, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, null, null);
    setMachineCodes(mi++, (byte) $regOp);
    emitAbsRegOperands(dstDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RAR(miStart, "$acronym", dstDisp, srcReg);
  }

  /**
   * Generate a register-displacement--register ${acronym}. That is,
   * <PRE>
   * $descr of [dstBase + dstIndex<<dstScale + dstDisp] by srcReg
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg must always be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, GPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == ECX);
    ${prefix}
    generateREXprefix(${rex_w}, null, dstIndex, dstBase);
    setMachineCodes(mi++, (byte) $regOp);
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RXDR(miStart, "$acronym", dstBase, dstIndex, dstScale, dstDisp, srcReg);
  }

EOF
}

emitShift ROL "rotate left" 0xD0 0xD2 0xC0 0x0 byte
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0 word
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0 quad

emitShift ROR "rotate right" 0xD0 0xD2 0xC0 0x1 byte
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1 word
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1 quad

emitShift RCL "rotate left with carry" 0xD0 0xD2 0xC0 0x2 byte
emitShift RCL "rotate left with carry" 0xD1 0xD3 0xC1 0x2 word
emitShift RCL "rotate left with carry " 0xD1 0xD3 0xC1 0x2
emitShift RCL "rotate left with carry " 0xD1 0xD3 0xC1 0x2 quad

emitShift RCR "rotate right with carry" 0xD0 0xD2 0xC0 0x3 byte
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3 word
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3 quad

emitShift SAL "arithemetic shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SAL "arithemetic shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SAL "arithemetic shift left" 0xD1 0xD3 0xC1 0x4
emitShift SAL "arithemetic shift left" 0xD1 0xD3 0xC1 0x4 quad

emitShift SHL "logical shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4 quad

emitShift SHR "logical shift right" 0xD0 0xD2 0xC0 0x5 byte
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5 word
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5 quad

emitShift SAR "arithemetic shift right" 0xD0 0xD2 0xC0 0x7 byte
emitShift SAR "arithemetic shift right" 0xD1 0xD3 0xC1 0x7 word
emitShift SAR "arithemetic shift right" 0xD1 0xD3 0xC1 0x7
emitShift SAR "arithemetic shift right" 0xD1 0xD3 0xC1 0x7 quad

emitShiftDouble() {
    acronym=$1
    opStr=$2
    immOp=$3
    regOp=$4
    size=$5
    ext=
    rex_w=false
    if [ x$size = xquad ]; then
      ext=_Quad
      rex_w=true
    fi
    cat >> $FILENAME <<EOF
  /**
   * Generate a register--register--immediate ${acronym}. That is,
   * <PRE>
   * left ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination register
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Imm${ext}(GPR left, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegRegOperands(left, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RRI(miStart, "$acronym", left, right, shiftBy);
  }

  /**
   * Generate a register-indirect--register--immediate ${acronym}. That is,
   * <PRE>
   * [left] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination base register
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg_Imm${ext}(GPR left, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegIndirectRegOperands(left, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RNRI(miStart, "$acronym", left, right, shiftBy);
  }

  /**
   * Generate a register-displacement--register--immediate ${acronym}. That is,
   * <PRE>
   * [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination base register
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg_Imm${ext}(GPR left, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegDispRegOperands(left, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RDRI(miStart, "$acronym", left, disp, right, shiftBy);
  }

  /**
   * Generate a register-index--register--immediate ${acronym}. That is,
   * <PRE>
   * [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param leftBase the destination base register
   * @param leftIndex the destination index register
   * @param scale the destination scale
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg_Imm${ext}(GPR leftBase, GPR leftIndex, short scale, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, leftIndex, leftBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RXDRI(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
  }

  /**
   * Generate a register-offset--register--immediate ${acronym}. That is,
   * <PRE>
   * [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param leftIndex the destination index register
   * @param scale the destination scale
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg_Imm${ext}(GPR leftIndex, short scale, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, leftIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegOffRegOperands(leftIndex, scale, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RFDRI(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
  }

  /**
   * Generate an absolute--register--immediate ${acronym}. That is,
   * <PRE>
   * [disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy the amount to shift by
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg_Imm${ext}(Address disp, GPR right, int shiftBy) {
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitAbsRegOperands(disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RARI(miStart, "$acronym", disp, right, shiftBy);
  }

  /**
   * Generate a register--register--register ${acronym}. That is,
   * <PRE>
   * left ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination register
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_Reg_Reg${ext}(GPR left, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegRegOperands(left, right);
    if (lister != null) lister.RRR(miStart, "$acronym", left, right, shiftBy);
  }

  /**
   * Generate a register-indirect--register--register ${acronym}. That is,
   * <PRE>
   * [left] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination base register
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_RegInd_Reg_Reg${ext}(GPR left, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegIndirectRegOperands(left, right);
    if (lister != null) lister.RNRR(miStart, "$acronym", left, right, shiftBy);
  }

  /**
   * Generate a register-displacement--register--register ${acronym}. That is,
   * <PRE>
   * [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param left the destination base register
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public final void emit${acronym}_RegDisp_Reg_Reg${ext}(GPR left, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, left);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegDispRegOperands(left, disp, right);
    if (lister != null) lister.RDRR(miStart, "$acronym", left, disp, right, shiftBy);
  }

  /**
   * Generate a register-index--register--register ${acronym}. That is,
   * <PRE>
   * [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param leftBase the destination base register
   * @param leftIndex the destination index register
   * @param scale the destination scale
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5,6})
  public final void emit${acronym}_RegIdx_Reg_Reg${ext}(GPR leftBase, GPR leftIndex, short scale, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, leftIndex, leftBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
    if (lister != null) lister.RXDRR(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
  }

  /**
   * Generate a register-index--register--register ${acronym}. That is,
   * <PRE>
   * [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param leftIndex the destination index register
   * @param scale the destination scale
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4,5})
  public final void emit${acronym}_RegOff_Reg_Reg${ext}(GPR leftIndex, short scale, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, leftIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegOffRegOperands(leftIndex, scale, disp, right);
    if (lister != null) lister.RFDRR(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
  }

  /**
   * Generate a register-index--register--register ${acronym}. That is,
   * <PRE>
   * [disp] ${opStr}= shiftBy (with bits from right shifted in)
   * </PRE>
   *
   * @param disp the destination displacement
   * @param right the register containing bits that are shifted in
   * @param shiftBy must be ECX
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2,3})
  public final void emit${acronym}_Abs_Reg_Reg${ext}(Address disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    generateREXprefix(${rex_w}, right, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitAbsRegOperands(disp, right);
    if (lister != null) lister.RARR(miStart, "$acronym", disp, right, shiftBy);
  }

EOF
}

emitShiftDouble SHLD \<\< 0xA4 0xA5
emitShiftDouble SHLD \<\< 0xA4 0xA5 quad
emitShiftDouble SHRD \<\< 0xAC 0xAD
emitShiftDouble SHRD \<\< 0xAC 0xAD quad

emitStackOp() {
    acronym=$1
    op1=$2
    op2=$3
    regCode=$4
    memCode=$5
    memExt=$6
    imm8Code=$7
    imm32Code=$8
    cat >> $FILENAME <<EOF
  /**
   * Generate a register ${acronym}. That is,
   * <PRE>
   * $op1 dstReg, SP $op2 4
   * </PRE>
   *
   * @param reg the destination register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg (GPR reg) {
    int miStart = mi;
    generateREXprefix(false, null, null, reg);
    setMachineCodes(mi++, (byte) ($regCode + reg.valueForOpcode()));
    if (lister != null) lister.R(miStart, "${acronym}", reg);
  }

  /**
   * Generate a register-displacement ${acronym}. That is,
   * <PRE>
   * $op1 [base + disp], SP $op2 4
   * </PRE>
   *
   * @param base the base register
   * @param disp the displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp (GPR base, Offset disp) {
    int miStart = mi;
    generateREXprefix(false, null, null, base);
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegDispRegOperands(base, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RD(miStart, "${acronym}", base, disp);
  }

  /**
   * Generate a register-indirect ${acronym}. That is,
   * <PRE>
   * $op1 [base], SP $op2 4
   * </PRE>
   *
   * @param base the base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd (GPR base) {
    int miStart = mi;
    generateREXprefix(false, null, null, base);
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegIndirectRegOperands(base, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RN(miStart, "${acronym}", base);
  }

  /**
   * Generate a register-index ${acronym}. That is,
   * <PRE>
   * $op1 [base + index<<scale + disp], SP $op2 4
   * </PRE>
   *
   * @param base the base register
   * @param index the index register
   * @param scale the scale
   * @param disp the displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx (GPR base, GPR index, short scale, Offset disp) {
    int miStart = mi;
    generateREXprefix(false, null, index, base);
    setMachineCodes(mi++, (byte) ${memCode});
    emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RXD(miStart, "${acronym}", base, index, scale, disp);
  }

  /**
   * Generate a register-offset ${acronym}. That is,
   * <PRE>
   * $op1 [index<<scale + disp], SP $op2 4
   * </PRE>
   *
   * @param index the index register
   * @param scale the scale
   * @param disp the displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff (GPR index, short scale, Offset disp) {
    int miStart = mi;
    generateREXprefix(false, null, index, null);
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RFD(miStart, "${acronym}", index, scale, disp);
  }

  /**
   * Generate an absolute ${acronym}. That is,
   * <PRE>
   * $op1 [disp], SP $op2 4
   * </PRE>
   *
   * @param disp the displacement
   */
  public final void emit${acronym}_Abs (Address disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
    if [ $imm32Code != none ]; then
    cat >> $FILENAME <<EOF
  /**
   * Generate an immediate ${acronym}. That is,
   * <PRE>
   * $op1 imm, SP $op2 4
   * </PRE>
   *
   * @param imm the immediate value
   */
  public final void emit${acronym}_Imm(int imm) {
    int miStart = mi;
    if (fits(imm, 8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      emitImm8(imm);
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      emitImm32(imm);
    }
    if (lister != null) lister.I(miStart, "${acronym}", imm);
  }

EOF
    fi
}

emitStackOp POP pop -= 0x58 0x8F 0x0 none none
emitStackOp PUSH push += 0x50 0xFF 0x6 0x6A 0x68

# SSE/2 instructions
emitSSE2Op() {
  prefix=$1
  prefix2=$2
  acronym=$3
  opCode=$4
  opCode2=$5
  condByte=$6
  fromRegType=$7
  toRegType=$8
  size=$9
  ext=

  # Pairs of opcodes, both optional.
  # opCode is for going *into* XMMs and between XMMs
  # opCode2 is for going *out* of XMMs
  # Reg_Reg defaults to opCode, but created for opCode2 if opCode none or missing
  # an example is MOVD_Reg_Reg(EAX, XMM1)
  # TODO: Reg_Reg (see above) is potentially confusing.
  # TODO: Check for bad/missed ops.

  if [ x$opCode2 == x ]; then
    opCode2=none;
  fi

  if [ x$fromRegType == x ]; then
    fromRegType=XMM
  fi

  if [ x$toRegType == x ]; then
    toRegType=XMM
  fi

  condLine=
  if [[ x$condByte != x ]] && [[ x$condByte != xnone ]]; then
    condLine="
    setMachineCodes(mi++, (byte) ${condByte});"
  fi

  prefix1Line=
  if [[ x$prefix != xnone ]]; then
    prefix1Line="
    setMachineCodes(mi++, (byte) ${prefix});"
  fi

  prefix2Line=
  if [[ x$prefix2 != xnone ]]; then
    prefix2Line="
    setMachineCodes(mi++, (byte) ${prefix2});"
  fi

  rex_w=false
  if [ x$size = xquad ]; then
    ext=_Quad
    rex_w=true
  fi

  if [ x$opCode != xnone ]; then
    cat >> $FILENAME <<EOF

  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcReg source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}($toRegType dstReg, $fromRegType srcReg) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, null, srcReg);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegRegOperands(srcReg, dstReg);$condLine
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

  /**
   * Generate a register--register-displacement ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcBase + srcDisp]
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcBase the source base register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp${ext}($toRegType dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegDispRegOperands(srcBase, srcDisp, dstReg);$condLine
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcBase, srcDisp);
  }

  /**
   * Generate a register--register-offset ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcIndex the source index register
   * @param srcScale the source scale
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}($toRegType dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, srcIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, dstReg);$condLine
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a register--absolute ${acronym}. That is,
   * <PRE>
   *  dstReg ${opStr}= $code [srcDisp]
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcDisp the source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}($toRegType dstReg, Address srcDisp) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitAbsRegOperands(srcDisp, dstReg);$condLine
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
  }

  /**
   * Generate a register--register-index ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcBase the source base register
   * @param srcIndex the source index register
   * @param srcScale the source scale
   * @param srcDisp the source displacement
   */
  // dstReg ${opStr}= $code [srcBase + srcIndex<<scale + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}($toRegType dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, srcIndex, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, dstReg);$condLine
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, srcScale, srcDisp);
  }

  /**
   * Generate a register--register-indirect ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code [srcBase]
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcBase the source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}($toRegType dstReg, GPR srcBase) {
    int miStart = mi;$prefix1Line
    generateREXprefix(${rex_w}, dstReg, null, srcBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegIndirectRegOperands(srcBase, dstReg);$condLine
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcBase);
  }

EOF
  fi

  if [[ x$opCode2 != xnone ]] && [[ x$opCode == xnone ]]; then
    cat >> $FILENAME <<EOF

  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * dstReg ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcReg source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}($toRegType dstReg, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, null, dstReg);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegRegOperands(dstReg, srcReg);$condLine
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }
EOF
  fi

  if [ x$opCode2 != xnone ]; then
    cat >> $FILENAME <<EOF

  /**
   * Generate a register-indirect--register ${acronym}. That is,
   * <PRE>
   * [dstBase] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg${ext}(GPR dstBase, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, null, dstBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegIndirectRegOperands(dstBase, srcReg);
    if (lister != null) lister.RNR(miStart, "${acronym}", dstBase, srcReg);
  }

  /**
   * Generate a register-offset--register ${acronym}. That is,
   * <PRE>
   * [dstReg<<dstScale + dstDisp] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR dstIndex, short dstScale, Offset dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, dstIndex, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RFDR(miStart, "${acronym}", dstIndex, dstScale, dstDisp, srcReg);
  }

  /**
   * Generate a absolute--register ${acronym}. That is,
   * <PRE>
   * [dstDisp] ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, null, null);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitAbsRegOperands(dstDisp, srcReg);
    if (lister != null) lister.RAR(miStart, "${acronym}", dstDisp, srcReg);
  }

  /**
   * Generate a register-index--register ${acronym}. That is,
   * <PRE>
   * [dstBase + dstIndex<<dstScale + dstDisp] ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR dstBase, GPR dstIndex, short dstScale, Offset dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, dstIndex, dstBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitSIBRegOperands(dstBase, dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RXDR(miStart, "${acronym}", dstBase, dstIndex, dstScale, dstDisp, srcReg);
  }

  /**
   * Generate a register-displacement--register ${acronym}. That is,
   * <PRE>
   * [dstBase + dstDisp] ${opStr}= $code srcReg
   * </PRE>
   *
   * @param dstBase the destination base register
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dstBase, Offset dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    generateREXprefix(${rex_w}, srcReg, null, dstBase);
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegDispRegOperands(dstBase, dstDisp, srcReg);
    if (lister != null) lister.RDR(miStart, "${acronym}", dstBase, dstDisp, srcReg);
  }

EOF
  fi
}

# Single precision FP ops.
emitSSE2Op 0xF3 none ADDSS 0x58 none
emitSSE2Op 0xF3 none SUBSS 0x5C none
emitSSE2Op 0xF3 none MULSS 0x59 none
emitSSE2Op 0xF3 none DIVSS 0x5E none
emitSSE2Op 0xF3 0xF3 MOVSS 0x10 0x11
emitSSE2Op none none MOVLPS 0x12 0x13
emitSSE2Op 0xF3 none SQRTSS 0x51 none
emitSSE2Op 0xF3 none CVTSS2SD 0x5A none
emitSSE2Op 0xF3 none CVTSI2SS 0x2A none none GPR XMM
emitSSE2Op 0xF3 none CVTSI2SS 0x2A none none GPR XMM quad
emitSSE2Op 0xF3 none CVTSS2SI 0x2D none none XMM GPR
emitSSE2Op 0xF3 none CVTSS2SI 0x2D none none XMM GPR quad
emitSSE2Op 0xF3 none CVTTSS2SI 0x2C none none XMM GPR
emitSSE2Op 0xF3 none CVTTSS2SI 0x2C none none XMM GPR quad

# Single precision FP comparisons.
emitSSE2Op none none UCOMISS 0x2E none
emitSSE2Op 0xF3 none CMPEQSS 0xC2 none 0
emitSSE2Op 0xF3 none CMPLTSS 0xC2 none 1
emitSSE2Op 0xF3 none CMPLESS 0xC2 none 2
emitSSE2Op 0xF3 none CMPUNORDSS 0xC2 none 3
emitSSE2Op 0xF3 none CMPNESS 0xC2 none 4
emitSSE2Op 0xF3 none CMPNLTSS 0xC2 none 5
emitSSE2Op 0xF3 none CMPNLESS 0xC2 none 6
emitSSE2Op 0xF3 none CMPORDSS 0xC2 none 7

# Generic data move ops.
emitSSE2Op none none MOVD none 0x7E none MM GPR
emitSSE2Op none none MOVD 0x6E none none GPR MM
emitSSE2Op none 0x66 MOVD none 0x7E none XMM GPR
emitSSE2Op 0x66 none MOVD 0x6E none none GPR XMM
# NB there is a related MOVQ for x86 64 that handles 64bit GPRs to/from MM/XMM registers
emitSSE2Op none none MOVQ 0x6F 0x7F none MM MM
emitSSE2Op 0xF3 0x66 MOVQ 0x7E 0xD6 none XMM XMM

# Double precision FP ops.
emitSSE2Op 0xF2 none ADDSD 0x58 none
emitSSE2Op 0xF2 none SUBSD 0x5C none
emitSSE2Op 0xF2 none MULSD 0x59 none
emitSSE2Op 0xF2 none DIVSD 0x5E none
emitSSE2Op 0xF2 0xF2 MOVSD 0x10 0x11
emitSSE2Op 0x66 0x66 MOVLPD 0x12 0x13
emitSSE2Op 0xF2 none SQRTSD 0x51 none
emitSSE2Op 0xF2 none CVTSI2SD 0x2A none none GPR XMM
emitSSE2Op 0xF2 none CVTSD2SS 0x5A none
emitSSE2Op 0xF2 none CVTSD2SI 0x2D none none XMM GPR
emitSSE2Op 0xF2 none CVTTSD2SI 0x2C none none XMM GPR
# NB for SD conversion one operand is always quad so we must alter the name
emitSSE2Op 0xF2 none CVTSI2SDQ 0x2A none none GPR XMM quad
emitSSE2Op 0xF2 none CVTSD2SIQ 0x2D none none XMM GPR quad
emitSSE2Op 0xF2 none CVTTSD2SIQ 0x2C none none XMM GPR quad

# Double precision comparison ops.
emitSSE2Op 0x66 none UCOMISD 0x2E none
emitSSE2Op 0xF2 none CMPEQSD 0xC2 none 0
emitSSE2Op 0xF2 none CMPLTSD 0xC2 none 1
emitSSE2Op 0xF2 none CMPLESD 0xC2 none 2
emitSSE2Op 0xF2 none CMPUNORDSD 0xC2 none 3
emitSSE2Op 0xF2 none CMPNESD 0xC2 none 4
emitSSE2Op 0xF2 none CMPNLTSD 0xC2 none 5
emitSSE2Op 0xF2 none CMPNLESD 0xC2 none 6
emitSSE2Op 0xF2 none CMPORDSD 0xC2 none 7

# Long ops.
emitSSE2Op none none PSLLQ 0xF3 none none MM MM
emitSSE2Op none none PSRLQ 0xD3 none none MM MM
emitSSE2Op 0x66 none PSLLQ 0xF3 none
emitSSE2Op 0x66 none PSRLQ 0xD3 none
emitSSE2Op none none ANDPS 0x54 none
emitSSE2Op 0x66 none ANDPD 0x54 none
emitSSE2Op none none ANDNPS 0x55 none
emitSSE2Op 0x66 none ANDNPD 0x55 none
emitSSE2Op none none ORPS 0x56 none
emitSSE2Op 0x66 none ORPD 0x56 none
emitSSE2Op none none XORPS 0x57 none
emitSSE2Op 0x66 none XORPD 0x57 none

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
  /**
   * Perform ${op} on FP0. That is,
   * <PRE>
   * dstReg ${op}= (${size}) [srcBase + srcDisp]
   * </PRE>
   *
   * @param dstReg destination register, must be FP0
   * @param srcBase source base register
   * @param srcDisp source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp${ext}(FPR dstReg, GPR srcBase, Offset srcDisp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegDispRegOperands(srcBase, srcDisp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcBase, srcDisp);
  }

  /**
   * Perform ${op} on FP0. That is,
   * <PRE>
   * dstReg ${op}= (${size}) [srcBase]
   * </PRE>
   *
   * @param dstReg destination register, must be FP0
   * @param srcBase source base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}(FPR dstReg, GPR srcBase) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegIndirectRegOperands(srcBase, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcBase);
  }

  /**
   * Perform ${op} on dstReg. That is,
   * <PRE>
   * dstReg ${op}= (${size}) [srcBase + srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg destination register, must be FP0
   * @param srcBase source base register
   * @param srcIndex source index register
   * @param srcScale source scale
   * @param srcDisp source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}(FPR dstReg, GPR srcBase, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitSIBRegOperands(srcBase, srcIndex, srcScale, srcDisp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, srcScale, srcDisp);
  }

  /**
   * Perform ${op} on FP0. That is,
   * <PRE>
   * dstReg ${op}= (${size}) [srcIndex<<srcScale + srcDisp]
   * </PRE>
   *
   * @param dstReg destination register, must be FP0
   * @param srcIndex source index register
   * @param srcScale source scale
   * @param srcDisp source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}(FPR dstReg, GPR srcIndex, short srcScale, Offset srcDisp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegOffRegOperands(srcIndex, srcScale, srcDisp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, srcScale, srcDisp);
  }

  /**
   * Perform ${op} on FP0. That is,
   * <PRE>
   * dstReg ${op}= (${size}) [srcDisp]
   * </PRE>
   *
   * @param dstReg destination register, must be FP0
   * @param srcDisp source displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}(FPR dstReg, Address srcDisp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitAbsRegOperands(srcDisp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
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
  /**
   * Perform ${op} either to or from FP0. That is,
   * <PRE>
   * dstReg ${op}= srcReg
   * </PRE>
   *
   * @param dstReg destination register, this or srcReg must be FP0
   * @param srcReg source register, this or dstReg must be FP0
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg(FPR dstReg, FPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0 || dstReg == FP0);
    if (dstReg == FP0) {
      setMachineCodes(mi++, (byte) 0xD8);
      setMachineCodes(mi++, (byte) (${to0Op} | srcReg.value()));
    } else if (srcReg == FP0) {
      setMachineCodes(mi++, (byte) 0xDC);
      setMachineCodes(mi++, (byte) (${toIop} | dstReg.value()));
    }
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

  /**
   * Perform ${op} then pop stack. That is,
   * <PRE>
   * srcReg ${op}= ST(0); pop stack
   * </PRE>
   *
   * @param dstReg destination register
   * @param srcReg source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${popAcronym}_Reg_Reg(FPR dstReg, FPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0);
    setMachineCodes(mi++, (byte) 0xDE);
    setMachineCodes(mi++, (byte) (${toIop} | dstReg.value()));
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
      preArg="FPR dummy, "
      postArg=""
    else
      pre=""
      ext=_Reg${ext}
      preArg=""
      postArg=", FPR dummy"
    fi
    cat >> $FILENAME <<EOF
  /** top of stack ${op} (${size:-double word}) [reg + disp] */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegDisp${ext}(${preArg}GPR reg, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(reg, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RD(miStart, "${acronym}", reg, disp);
  }

  /** top of stack ${op} (${size:-double word}) [reg] */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegInd${ext}(${preArg}GPR reg${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(reg, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RN(miStart, "${acronym}", reg);
  }

  /** top of stack ${op} (${size:-double word}) [baseReg + idxReg<<scale + disp] */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}${pre}_RegIdx${ext}(${preArg}GPR baseReg, GPR idxReg, short scale, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, idxReg, scale, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, idxReg, scale, disp);
  }

  /** top of stack ${op} (${size:-double word}) [idxReg<<scale + disp] */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegOff${ext}(${preArg}GPR idxReg, short scale, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(idxReg, scale, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RFD(miStart, "${acronym}", idxReg, scale, disp);
  }

  /** top of stack ${op} (${size:-double word}) [disp] */
  public final void emit${acronym}${pre}_Abs${ext}(${preArg}Address disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${extCode}));
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
  /**
   * ${acronym} floating point comparison
   *
   * @param reg1 register for comparison
   * @param reg2 register for comparison
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg (FPR reg1, FPR reg2) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(reg1 == FP0);
    setMachineCodes(mi++, (byte) ${opcode1});
    setMachineCodes(mi++, (byte)  (${opcode2} | reg2.value()));
    if (lister != null) lister.RR(miStart, "${acronym}", reg1, reg2);
  }

EOF
}

emitFloatCmp FCOMI 0xDB 0xF0
emitFloatCmp FCOMIP 0xDF 0xF0
emitFloatCmp FUCOMI 0xDB 0xE8
emitFloatCmp FUCOMIP 0xDF 0xE8

emitFSTATE() {
  acronym=$1
  comment=$2
  opcode=$3
  opExt=$4
  pre=$5
  local prefix="// no prefix byte"
  if [ x$pre != x ]; then
     prefix="setMachineCodes(mi++, (byte) ${pre});"
  fi

cat >> $FILENAME <<EOF
  /**
   * ${comment} - register displacement
   *
   * @param baseReg destination base register
   * @param disp destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp (GPR baseReg, Offset disp) {
    int miStart = mi;
    $prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(baseReg, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RD(miStart, "${acronym}", baseReg, disp);
  }

  /**
   * ${comment} - register indirect
   *
   * @param baseReg destination base register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd (GPR baseReg) {
    int miStart = mi;
    $prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(baseReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RN(miStart, "${acronym}", baseReg);
  }

  /**
   * ${comment} - register index
   *
   * @param baseReg destination base register
   * @param indexReg destination index register
   * @param scale destination scale
   * @param disp destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx (GPR baseReg, GPR indexReg, short scale, Offset disp) {
    int miStart = mi;
    $prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, indexReg, scale, disp);
  }

  /**
   * ${comment} - register offset
   *
   * @param indexReg destination index register
   * @param scale destination scale
   * @param disp destination displacement
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff (GPR indexReg, short scale, Offset disp) {
    int miStart = mi;
    $prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RFD(miStart, "${acronym}", indexReg, scale, disp);
  }

  /**
   * ${comment} - absolute address
   *
   * @param disp address to store to
   */
  public final void emit${acronym}_Abs (Address disp) {
    int miStart = mi;
    $prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${opExt}));
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
  /**
   * load ${value} into FP0
   *
   * @param dstReg must be FP0
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${opcode}_Reg(FPR dstReg) {
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
