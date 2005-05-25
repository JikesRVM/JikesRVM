/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.vm;

import org.mmtk.plan.*;

import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author Daniel Frampton 
 * @author Robin Garner
 *
 * @version $Revision$
 * @date $Date$
 */
//-#if RVM_WITH_SEMI_SPACE
public final class PlanConstants extends SemiSpaceConstants implements Uninterruptible {
//-#elif RVM_WITH_MARK_SWEEP
public final class PlanConstants extends MarkSweepConstants implements Uninterruptible {
//-#elif RVM_WITH_REF_COUNT
public final class PlanConstants extends RefCountConstants implements Uninterruptible {
//-#elif RVM_WITH_GEN_COPY
public final class PlanConstants extends GenCopyConstants implements Uninterruptible {
//-#elif RVM_WITH_GEN_MS
public final class PlanConstants extends GenMSConstants implements Uninterruptible {
//-#elif RVM_WITH_GEN_RC
public final class PlanConstants extends GenRCConstants implements Uninterruptible {
//-#elif RVM_WITH_COPY_MS
public final class PlanConstants extends CopyMSConstants implements Uninterruptible {
//-#elif RVM_WITH_NO_GC
public final class PlanConstants extends NoGCConstants implements Uninterruptible {
//-#elif RVM_WITH_GCTRACE
public final class PlanConstants extends GCTraceConstants implements Uninterruptible {
//-#elif RVM_WITH_SEMI_SPACE_GC_SPY
public final class PlanConstants extends SemiSpaceGCspyConstants implements Uninterruptible {
//-#endif

}
