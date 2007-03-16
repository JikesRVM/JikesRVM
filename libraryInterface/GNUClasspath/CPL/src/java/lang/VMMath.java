/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
package java.lang;
import org.jikesrvm.VM_BootRecord;
import org.vmmagic.pragma.SysCall;
import org.vmmagic.unboxed.Address;

/**
 * Pass as much as we can, the work of Math functions onto the C
 * implementations in libm using system call (cheaper) native calls
 *
 * @author Ian Rogers
 */
class VMMath {
  @SysCall private static native double mathMagic(Address functionAddress, double a);
  @SysCall private static native double mathMagic(Address functionAddress, double a, double b);

  public static double sin(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathSinIP, a);
  }
  public static double cos(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathCosIP, a);
  }
  public static double tan(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathTanIP, a);
  }
  public static double asin(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathAsinIP, a);
  }
  public static double acos(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathAcosIP, a);
  }
  public static double atan(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathAtanIP, a);
  }
  public static double atan2(double y, double x) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathAtan2IP, y , x);
  }
  public static double cosh(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathCoshIP, a);
  }
  public static double sinh(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathSinhIP, a);
  }
  public static double tanh(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathTanhIP, a);
  }
  public static double exp(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathExpIP, a);
  }
  public static double log(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathLogIP, a);
  }
  public static double sqrt(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathSqrtIP, a);
  }
  public static double pow(double a, double b) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathPowIP, a, b);
  }
  public static double IEEEremainder(double x, double y) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathIEEEremainderIP, x, y);
  }
  public static double ceil(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathCeilIP, a);
  }
  public static double floor(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathFloorIP, a);
  }
  public static double rint(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathRintIP, a);
  }
  public static double cbrt(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathCbrtIP, a);
  }
  public static double expm1(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathExpm1IP, a);
  }
  public static double hypot(double a, double b) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathHypotIP, a, b);
  }
  public static double log10(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathLog10IP, a);
  }
  public static double log1p(double a) {
    return mathMagic(VM_BootRecord.the_boot_record.sysVMMathLog1pIP, a);
  }  
}

 	  	 
