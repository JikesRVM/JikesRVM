package org.jikesrvm.energy;

import java.io.UTFDataFormatException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

import org.jikesrvm.VM;

import static org.jikesrvm.compilers.opt.ir.Operators.RETURN_opcode;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.hir2lir.ExpandRuntimeServices;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StringConstantOperand;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.adaptive.controller.Controller;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.driver.OptConstants.RUNTIME_SERVICES_BCI;
import static org.jikesrvm.mm.mminterface.Barriers.NEEDS_LONG_ALOAD_BARRIER;

class Singleton {
	private static ExpandRuntimeServices ers_instance = null;

	public static ExpandRuntimeServices Singleton() {
		if (ers_instance == null) {
			ers_instance = new ExpandRuntimeServices();
		}

		return ers_instance;
	}
}

public class Instrumentation {
	IR ir;
	NormalMethod method;
	RVMClass cls;

	public Instrumentation(IR _ir) {
		ir = _ir;
		method = ir.getMethod();
		cls = method.getDeclaringClass();
	}


//	public void instrumentIO(){
//		if (!Util.isJavaIO(cls))
//			return;
//		NormalMethod ioArgSampling = Entrypoints.ioArgSampling;
//		Instruction start;
//		Enumeration<Operand> operands = ir.getParameters();
//		while (operands.hasMoreElements()){
//			Operand opr = operands.nextElement();
//			if (opr.getType().isArrayType()){
//				start = Call.create1(CALL, null, IRTools.AC(ioArgSampling.getOffset()),
//						MethodOperand.STATIC(ioArgSampling), opr);
//				start.position = ir.firstInstructionInCodeOrder().position;
//				start.bcIndex = RUNTIME_SERVICES_BCI;
//				ir.firstBasicBlockInCodeOrder()
//						.prependInstructionRespectingPrologue(start);
//			}
//		}
//
//	}
//


	public static int method_len=-1;


	private String[] getDvfsMthNames() {
		String dvfsNames = Controller.options.DVFS_CLS_MTH;	
		String[] names = dvfsNames.split(",");
		if(method_len==-1) {		
			method_len = names.length;
		}
		return names;
	}

	public void perform() {

		ExpandRuntimeServices ers = Singleton.Singleton();	

		int freq = (int) Controller.options.FREQUENCY_TO_BE_PRINTED;
		//VM.sysWriteln("WWWWWWWWWWWW Frequency:" + freq);	
		//VM.sysWriteln("[Instrumentation#perform] ...");
		try {
			if (Util.irrelevantType(cls) || Util.isJavaClass(cls)) {
				return;
			}

//			VM.sysWriteln("class name:" + cls.toString() + " method name: " + method.getName().toString() 
//					+ " method Id:" + method.methodID);
//			Service.methodCount[0] = 100;
//			System.out.println(Service.methodCount[0]);
//			VM.sysWriteln("methodCount.length");
//			VM.sysWriteln(Service.methodCount.length);
			
			if (method.methodID != -1) {
				return;
			}
			if (method.methodID == -1){
				method.methodID = Service.addMethodEntry(cls.toString(), method.getName().toString());
			}

			//I have a question ?
			//How will you implement sampling based optimization here ?	

			Instruction startFreqOptimizationInst = null;
			Instruction endFreqOptimizationInst = null;
			Instruction startProfInst = null;
			Instruction endProfInst = null;

			NormalMethod startProfileMtd = Entrypoints.startProfile;
			NormalMethod endProfileMtd = Entrypoints.endProfile;
			NormalMethod startFreqOptimizationMtd = Entrypoints.startFreqOptimization;
			NormalMethod endFreqOptimizationMtd = Entrypoints.endFreqOptimization;
			StringConstantOperand clsName = new StringConstantOperand(
				cls.toString(), Offset.fromIntSignExtend(cls
				.getDescriptor().getStringLiteralOffset()));
			startProfInst = Call
					.create1(CALL, null,
							IRTools.AC(startProfileMtd.getOffset()),
							MethodOperand.STATIC(startProfileMtd), clsName,
							new IntConstantOperand(method.methodID));

			StringBuffer sb = new StringBuffer();
			sb.append(cls.toString()).append(".").append(method.getName().toString());
			String currentMth = sb.toString();

			String[] candidateDvfsMth = getDvfsMthNames();
			// Set userspace frequency for the specific method.
			int scaleFreq=freq;

			if (Controller.options.ENABLE_ENERGY_PROFILING) {
				VM.sysWrite("i_m_hspts 152");	
				// If no method can be matched, just insert start profile and end profile method then.
				startProfInst.position = ir.firstInstructionInCodeOrder().position;
				startProfInst.bcIndex = RUNTIME_SERVICES_BCI;
				ir.firstBasicBlockInCodeOrder()
						.prependInstructionRespectingPrologue(startProfInst);
				// Inline
				// ers.inline(startProfInst, ir);

				for (Instruction inst = startProfInst.nextInstructionInCodeOrder(); inst != null; inst = inst
						.nextInstructionInCodeOrder()) {
					
	//				VM.sysWriteln("Ir method: " + ir.getMethod().getName().toString() + " operator: "+ inst.operator.toString() + " opcode: " + (int)inst.getOpcode() + " RETURN_opcode: " + (int)RETURN_opcode);
	//				if (inst.operator.toString().equalsIgnoreCase("return")) {
					if (inst.getOpcode() == RETURN_opcode) {
						endProfInst = Call.create1(CALL, null,
								IRTools.AC(endProfileMtd.getOffset()),
								MethodOperand.STATIC(endProfileMtd),
								new IntConstantOperand(method.methodID));
						endProfInst.position = inst.position;
						endProfInst.bcIndex = RUNTIME_SERVICES_BCI;
						inst.insertBefore(endProfInst);
						// Inline
						// ers.inline(endProfInst, ir);
					}
				}
				VM.sysWrite("i_m_hspts end");
			} else {

				for (int i = 0; i < candidateDvfsMth.length; i++) {
					String candidate="";
					if(freq==19) {
						String[] kenan_fields = candidateDvfsMth[i].split(":");
						candidate = kenan_fields[0];
						scaleFreq = Integer.parseInt(kenan_fields[1]);
						//VM.sysWriteln(candidate);
					} else {
						candidate = candidateDvfsMth[i];
						scaleFreq=freq;
					}


					
					if (currentMth.equals(candidate)) {
						//VM.sysWriteln("[Instrumentation#perform] Candidate Selected for Instrumentation:  " + candidate);
						// Store multiple hot method by method ID
//						startFreqOptimizationInst = Call
//								.create2(CALL, null,
//										IRTools.AC(startFreqOptimizationMtd.getOffset()),
//										MethodOperand.STATIC(startFreqOptimizationMtd),
//										new IntConstantOperand(scaleFreq),
//										new IntConstantOperand(method.methodID)
//										);
						startFreqOptimizationInst = Call
								.create1(CALL, null,
										IRTools.AC(startFreqOptimizationMtd.getOffset()),
										MethodOperand.STATIC(startFreqOptimizationMtd),
										new IntConstantOperand(scaleFreq)
										);


						startFreqOptimizationInst.position = ir.firstInstructionInCodeOrder().position;
						startFreqOptimizationInst.bcIndex = RUNTIME_SERVICES_BCI;
						// Insert the DVFS instrument to the beginning of candidate method 
						
						ir.firstBasicBlockInCodeOrder()
								.prependInstructionRespectingPrologue(startFreqOptimizationInst);
						// Inline
						 // ers.inline(startFreqOptimizationInst, ir);

						for (Instruction inst = startFreqOptimizationInst.nextInstructionInCodeOrder(); inst != null; inst = inst
								.nextInstructionInCodeOrder()) {
							
			//				VM.sysWriteln("Ir method: " + ir.getMethod().getName().toString() + " operator: "+ inst.operator.toString() + " opcode: " + (int)inst.getOpcode() + " RETURN_opcode: " + (int)RETURN_opcode);
			//				if (inst.operator.toString().equalsIgnoreCase("return")) {
							if (inst.getOpcode() == RETURN_opcode) {
							// Store multiple hot method by method ID
//								endFreqOptimizationInst = Call.create1(CALL, null,
//											IRTools.AC(endFreqOptimizationMtd.getOffset()),
//											MethodOperand.STATIC(endFreqOptimizationMtd),
//											new IntConstantOperand(method.methodID));

								endFreqOptimizationInst = Call.create0(CALL, null,
											IRTools.AC(endFreqOptimizationMtd.getOffset()),
											MethodOperand.STATIC(endFreqOptimizationMtd)
											);


								endFreqOptimizationInst.position = inst.position;
								endFreqOptimizationInst.bcIndex = RUNTIME_SERVICES_BCI;
								inst.insertBefore(endFreqOptimizationInst);

								// Inline
								 //ers.inline(endFreqOptimizationInst, ir);
								//VM.sysWriteln("Method level DVFS insertion succeed!!!! Method is : " + candidate);
							}
						}
					}
				}
			}
		} catch (UTFDataFormatException e) {
			e.printStackTrace();
		}
	}
}


/* =====================================================
 * method/class name example:
 * =====================================================
 *
 * VM.write(method.getName().toString()); //main VM.writeln();
 * VM.write(method.getDescriptor().toString()); //L()V
 * VM.writeln();
 * VM.write(method.toString()); //<SystemAppCL,Lenergy/test/LoopTest; >.main ([Ljava/lang/String;)V
 * VM.writeln();
 *
 * VM.write(cls.toString());//energy.test.LoopTest VM.writeln();
 */
