/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.SysCallNative;
import org.vmmagic.unboxed.Address;

/**
 * Pass as much as we can, the work of Math functions onto the C
 * implementations in libm using system call (cheaper) native calls
 */
class VMMath {
  @Pure @SysCallNative private static native double mathMagic(Address functionAddress, double a);
  @Pure @SysCallNative private static native double mathMagic(Address functionAddress, double a, double b);

  @Pure
  public static double sin(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathSinIP, a);
  }
  @Pure
  public static double cos(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathCosIP, a);
  }
  @Pure
  public static double tan(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathTanIP, a);
  }
  @Pure
  public static double asin(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathAsinIP, a);
  }
  @Pure
  public static double acos(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathAcosIP, a);
  }
  @Pure
  public static double atan(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathAtanIP, a);
  }
  @Pure
  public static double atan2(double y, double x) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathAtan2IP, y , x);
  }
  @Pure
  public static double cosh(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathCoshIP, a);
  }
  @Pure
  public static double sinh(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathSinhIP, a);
  }
  @Pure
  public static double tanh(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathTanhIP, a);
  }
  @Pure
  public static double exp(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathExpIP, a);
  }
  @Pure
  public static double log(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathLogIP, a);
  }
  @Pure
  public static double sqrt(double a) {
    if (VM.BuildForHwFsqrt) {
      return Magic.sqrt(a);
    } else {
      return mathMagic(BootRecord.the_boot_record.sysVMMathSqrtIP, a);
    }
  }
  @Pure
  public static double pow(double a, double b) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathPowIP, a, b);
  }
  @Pure
  public static double IEEEremainder(double x, double y) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathIEEEremainderIP, x, y);
  }
  @Pure
  public static double ceil(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathCeilIP, a);
  }
  @Pure
  public static double floor(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathFloorIP, a);
  }
  @Pure
  public static double rint(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathRintIP, a);
  }
  @Pure
  public static double cbrt(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathCbrtIP, a);
  }
  @Pure
  public static double expm1(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathExpm1IP, a);
  }
  @Pure
  public static double hypot(double a, double b) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathHypotIP, a, b);
  }
  @Pure
  public static double log10(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathLog10IP, a);
  }
  @Pure
  public static double log1p(double a) {
    return mathMagic(BootRecord.the_boot_record.sysVMMathLog1pIP, a);
  }
}


