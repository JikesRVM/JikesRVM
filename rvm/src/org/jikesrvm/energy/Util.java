package org.jikesrvm.energy;

import java.io.UnsupportedEncodingException;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.compilers.opt.ir.IREnumeration;

public abstract class Util {



	public static boolean irrlevantType(byte [] bytes){
		if (VM.VerifyAssertions) {
			VM._assert(bytes[0] == 'L');
		}
		if (bytes.length > 5 && bytes[1] == 'o' && bytes[2] == 'r'
				&& bytes[3] == 'g') {
			try {
				VM.sysWriteln(new String(bytes, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (bytes.length > 10 && bytes[5] == 'j' && bytes[6] == 'i'
					&& bytes[7] == 'k' && bytes[8] == 'e' && bytes[9] == 's') { // Lorg/jikes****
				return true;
			} else if (bytes.length > 8 && bytes[5] == 'm' && bytes[6] == 'm'
					&& bytes[7] == 't' && bytes[8] == 'k') {  // Lorg/mmtk****
				return true;
			} else if (bytes.length > 11 && bytes[5] == 'v' && bytes[6] == 'm'
					&& bytes[7] == 'm' && bytes[8] == 'a' && bytes[9] == 'g'
					&& bytes[10] == 'i' && bytes[11] == 'c') {  // Lorg/vmmagic***
				return true;
			}
			else if (bytes.length > 10 && bytes[5] == 'v' && bytes[6] == 'm'
					&& bytes[7] == 'u' && bytes[8] == 't' && bytes[9] == 'i'
					&& bytes[10] == 'l') {  // Lorg/vmutil***
				return true;
			}

		}else  if (bytes.length > 5 && bytes[1] == 's' && bytes[2] == 'u'
				&& bytes[3] == 'n') { //Lsun/
			return true;
		} else if (bytes.length > 5 && bytes[1] == 'c' && bytes[2] == 'o'
				&& bytes[3] == 'm')  { //Lcom/
			return true;
		}
		return false;
	}

	//check whether it's JikesRVM's class
	public static boolean irrelevantType(RVMClass cls) {
		byte[] bytes = cls.getDescriptor().getBytes();
		if (VM.VerifyAssertions) {
			VM._assert(bytes[0] == 'L');
		}
		if (bytes.length > 5 && bytes[1] == 'o' && bytes[2] == 'r'
				&& bytes[3] == 'g') {
			if (bytes.length > 10 && bytes[5] == 'j' && bytes[6] == 'i'
					&& bytes[7] == 'k' && bytes[8] == 'e' && bytes[9] == 's') { // Lorg/jikes****
				return true;
			} else if (bytes.length > 8 && bytes[5] == 'm' && bytes[6] == 'm'
					&& bytes[7] == 't' && bytes[8] == 'k') {  // Lorg/mmtk****
				return true;
			} else if (bytes.length > 11 && bytes[5] == 'v' && bytes[6] == 'm'
					&& bytes[7] == 'm' && bytes[8] == 'a' && bytes[9] == 'g'
					&& bytes[10] == 'i' && bytes[11] == 'c') {  // Lorg/vmmagic***
				return true;
			}
			else if (bytes.length > 10 && bytes[5] == 'v' && bytes[6] == 'm'
					&& bytes[7] == 'u' && bytes[8] == 't' && bytes[9] == 'i'
					&& bytes[10] == 'l') {  // Lorg/vmutil***
				return true;
			}

		}else  if (bytes.length > 5 && bytes[1] == 's' && bytes[2] == 'u'
				&& bytes[3] == 'n') { //Lsun/
			return true;
		} else if (bytes.length > 5 && bytes[1] == 'c' && bytes[2] == 'o'
				&& bytes[3] == 'm')  { //Lcom/
			return true;
		}
		return false;
	}

	public static boolean isJavaIO(RVMClass cls){
		byte[] bytes = cls.getDescriptor().getBytes();
		if (VM.VerifyAssertions) {
			VM._assert(bytes[0] == 'L');
		}
		if (bytes.length >8  && bytes[1] == 'j' && bytes[2] == 'a'
				&& bytes[3] == 'v' && bytes[4] == 'a' && (bytes[5] == '/' || bytes[5]=='.') &&
				bytes[6] == 'i' && bytes[7] == 'o') {  // Ljava.io
			return true;
		}
		return false;
	}

	public static boolean isJavaClass(RVMClass  cls) {
		byte[] bytes = cls.getDescriptor().getBytes();
		if (VM.VerifyAssertions) {
			VM._assert(bytes[0] == 'L');
		}

		if (bytes.length >6  && bytes[1] == 'j' && bytes[2] == 'a'
				&& bytes[3] == 'v' && bytes[4] == 'a' && (bytes[5] == '/' || bytes[5]=='.')) {  // Ljava/****
			return true;
		}
		if (bytes.length >6  && bytes[1] == 'j' && bytes[2] == 'a'
				&& bytes[3] == 'v' && bytes[4] == 'a' && bytes[5] == 'x' && (bytes[6] == '/' || bytes[7]=='.')) {  // Ljava/****
			return true;
		}
		if (bytes.length > 5 && bytes[1] == 'g' && bytes[2] == 'n'&& bytes[3] == 'u'
				&& (bytes[4] == '/' || bytes[4]=='.')) { // Lgnu****
			return true;
		}


		return false;

	}


}
