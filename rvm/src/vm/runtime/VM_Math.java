/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Math library.
 *
 * @author Bowen Alpern
 * @author Mauricio Serrano
 * @author John Whaley
 */
public class VM_Math
   implements VM_Uninterruptible /* see "NB:" note, below */
   {
   static final double posPowersOf10[] =
   { 1e1 , 1e2 , 1e3 , 1e4 , 1e5 , 1e6 , 1e7 , 1e8 , 1e9 , 1e10,
     1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20,
     1e21, 1e22, 1e23, 1e24, 1e25, 1e26, 1e27, 1e28, 1e29, 1e30,
     1e31, 1e32 };

   static final double negPowersOf10[] =
   { 1e-1 , 1e-2 , 1e-3 , 1e-4 , 1e-5 , 1e-6 , 1e-7 , 1e-8 , 1e-9 , 1e-10,
     1e-11, 1e-12, 1e-13, 1e-14, 1e-15, 1e-16, 1e-17, 1e-18, 1e-19, 1e-20,
     1e-21, 1e-22, 1e-23, 1e-24, 1e-25, 1e-26, 1e-27, 1e-28, 1e-29, 1e-30,
     1e-31, 1e-32 };

  /**
   * Convert a string to a double.
   * 
   * This gives the same results as the native routine (most of the time),
   * even to the point of throwing NumberFormatExceptions in all the same
   * circumstances. The differences may be some round-off differences. If
   * you want to minimize these, you will need to first go through the
   * string once to count the number of digits left of the decimal and
   * parse the exponent, and then multiply the digits by the correct values
   * from the 'posPowersOf10' array, above. I didn't bother.
   *
   * Another option is to use a scheme like David Gay uses. For reference,
   * his C source code to do this one function is 54k, requires
   * synchronization, and uses irreducible control flow. But, it's more
   * accurate.
   *
   * This currently chokes on the strings "NaN",
   * "Infinity", and "-Infinity". 
   *
   * This is not dependent on any real floating point number convention. So
   * if the Java floating point specification changes (like it did), this
   * should continue to work.
   *
   * @author John Whaley
   */
   public static double
   stringToDouble(String s)
      throws NumberFormatException
      {
       final int radix = 10; // assume radix = 10

       if (s == null)
         throw new NumberFormatException("null");

       int i = 0, max = s.length()-1;

       if (max < 0)
         throw new NumberFormatException("empty string");

       // Scan past any whitespace.
       //while (Character.isWhiteSpace(s.charAt(i))) ++i;

       // Figure out sign.
       boolean sign = false; // positive
       char c = s.charAt(i);
       if (c == '+') {
         if (i == max) throw new NumberFormatException(s);
         c = s.charAt(++i);
       } else if (c == '-') {
         sign = true;
         if (i == max) throw new NumberFormatException(s);
         c = s.charAt(++i);
       }

       int atLeast = i;

       // must be at least one digit (comment this out if you don't care)
       if ((c != '.') && !Character.isDigit(c)) throw new NumberFormatException(s);

       // Parse number.
       double val = 0;

     doExponent: // this label is so we can simulate 'goto'
       for (;;) {
         // c is already initialized at this point.
         if ((c == 'e') || (c == 'E')) break doExponent;
         if (c == '.') {

           // must be at least one digit (comment this out if you don't care)
           if (max == atLeast) throw new NumberFormatException(s);

           if (i == max) return (sign ? -val : val);

           // start parsing to the right of the decimal point.
           c = s.charAt(++i);

           // must be at least one digit (comment this out if you don't care)
           if ((i == atLeast+1) && !Character.isDigit(c)) throw new NumberFormatException(s);

           int powIndex = 0;
           int newMax = i + negPowersOf10.length;
           for (;;) {
             if ((c == 'e') || (c == 'E')) break doExponent;
             int digit = Character.digit(c, radix);
             if (digit < 0) {
               // we reached a non-digit before the end of the string.
               if ((i == max) &&
                   ((c == 'f') || (c == 'F') || (c == 'd') || (c == 'D')))
                 return (sign ? -val : val);
               // not acceptable!
               throw new NumberFormatException(s);
             }
             val += negPowersOf10[powIndex] * digit;
             if (i == newMax) break;
             if (i == max) return (sign ? -val : val);
             ++powIndex;
             c = s.charAt(++i);
           }

           // throw away insignificant digits.
           for (;;) {
             if ((c == 'e') || (c == 'E')) break doExponent;
             int digit = Character.digit(c, radix);
             if (digit < 0) {
               // we reached a non-digit before the end of the string.
               if ((i == max) &&
                   ((c == 'f') || (c == 'F') || (c == 'd') || (c == 'D')))
                 return (sign ? -val : val);
               // not acceptable!
               throw new NumberFormatException(s);
             }
             if (i == max) return (sign ? -val : val);
             c = s.charAt(++i);
           }
         } else {
           // still left of decimal place / exponent.
           int digit = Character.digit(c, radix);
           if (digit < 0) {
             // we reached a non-digit before the end of the string.
             if ((i == max) &&
                 ((c == 'f') || (c == 'F') || (c == 'd') || (c == 'D')))
               return (sign ? -val : val);
             // not acceptable!
             throw new NumberFormatException(s);
           }
           val *= 10;
           val += digit;
           if (i == max) return (sign ? -val : val);
           c = s.charAt(++i);
         }
       }

       if (i == max) throw new NumberFormatException(s);

       // now, handle the exponent.
       c = s.charAt(++i);

       // find sign
       boolean expsign = false;
       if (c == '+') {
         if (i == max) throw new NumberFormatException(s);
         c = s.charAt(++i);
       } else if (c == '-') {
         expsign = true;
         if (i == max) throw new NumberFormatException(s);
         c = s.charAt(++i);
       }

       // must be at least one digit.
       if (!Character.isDigit(c)) throw new NumberFormatException(s);

       int exp = 0;
       for (;;) {
         int digit = Character.digit(c, radix);
         if (digit < 0) {
           // we reached a non-digit before the end of the string.
           if ((i == max) &&
               ((c == 'f') || (c == 'F') || (c == 'd') || (c == 'D'))) {
           } else {
             // not acceptable!
             throw new NumberFormatException(s);
           }
         } else {
           exp *= 10;
           exp += digit;
         }
         if (i == max) {
           // note: can change this to do 'smart' exponentiation in lg time.
           // its probably not worth it -- when exponents are big, the number
           // quickly goes to infinity or 0, at which point it stops anyways.
           if (expsign) {
             // question: could we ever get a 'NaN' here?
             while ((exp != 0) && /*(val != NaN) &&*/ (val != 0.0)) {
               if (exp >= 32)      { val *= negPowersOf10[31]; exp -= 32; }
               else if (exp >= 16) { val *= negPowersOf10[15]; exp -= 16; }
               else if (exp >= 8)  { val *= negPowersOf10[7];  exp -= 8;  }
               else if (exp >= 4)  { val *= negPowersOf10[3];  exp -= 4;  }
               else if (exp >= 2)  { val *= negPowersOf10[1];  exp -= 2;  }
               else                { val *= negPowersOf10[0];  exp -= 1;  }
             }
           } else {
             // question: could we ever get a 'NaN' here?
             while ((exp != 0) && /*(val != NaN) &&*/ (val != Double.POSITIVE_INFINITY)) {
               if (exp >= 32)      { val *= posPowersOf10[31]; exp -= 32; }
               else if (exp >= 16) { val *= posPowersOf10[15]; exp -= 16; }
               else if (exp >= 8)  { val *= posPowersOf10[7];  exp -= 8;  }
               else if (exp >= 4)  { val *= posPowersOf10[3];  exp -= 4;  }
               else if (exp >= 2)  { val *= posPowersOf10[1];  exp -= 2;  }
               else                { val *= posPowersOf10[0];  exp -= 1;  }
             }
           }
           return (sign ? -val : val);
         }
         c = s.charAt(++i);
       }
     }

  /**
   * Combine two integers to make a long.
   *
   * @param hi the upper word for the long
   * @param lo the lower word for the long
   * @returns a long number
   */
  static public long twoIntsToLong(int hi, int lo) {
    long result = (((long) hi) << 32);
    result |= ((long) lo) & 0xFFFFFFFFL;
    return result;
  }

  /**
   * Standard Math methods
   * @author Bowen Alpern
   */
  public static double ceil (double x) {
    //-#if RVM_FOR_IA32
    // on IA32, use the baseline compiler to simulate strict IEEE
    // floating point arithmetic.	
    VM_Magic.pragmaNoOptCompile();
    //-#endif
    double r = x;
    r += IEEEmagic;
    r -= IEEEmagic;
    return (x>r) ? (r+1.0D) : r;
  }

  public static double floor (double x) {
    //-#if RVM_FOR_IA32
    // on IA32, use the baseline compiler to simulate strict IEEE
    // floating point arithmetic.	
    VM_Magic.pragmaNoOptCompile();
    //-#endif
    double r = x;
    r += IEEEmagic;
    r -= IEEEmagic;
    return (x<r) ? (r-1.0D) : r;
  }

 /**
  * added this constant for 32-bit Signed integer to floating-point code
  * conversion, taken from: "The PowerPC Compiler Writer's Guide"
  * @author Mauricio Serrano
  */
  static double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);

  /**
   * Math stuff used by the compiler 
   * @author Bowen Alpern
   */
  static double IEEEmagic = Double.longBitsToDouble(0x4338000000000000L);

  static long   longOne  =  1L;
  static float  minusOne = -1.0F;
  static float  zero     =  0.0F;
  static float  half     =  0.5F;
  static float  one      =  1.0F;
  static float  two      =  2.0F;
  static double zeroD    =  0.0;
  static double oneD     =  1.0;
  static float  half32   =  java.lang.Float.intBitsToFloat(0x2f800000);
  static float  two32    =  java.lang.Float.intBitsToFloat(0x4f800000);

  static double billionth = 1e-9;
  
  // largest double that can be rounded to an int
  static double maxint   =  0.5D + 0x7FFFFFFF;
  
  // largest double that can be rounded to a long
  static double maxlong  = Double.longBitsToDouble(0x43DFFFFFFFFFFFFFL);
   
  //--------------------------------------------------------------------------------------------//
  // NB: The following methods must compute their answer without allowing a thread switch,      //
  // because they are called by the compiler as "helpers" to implement bytecodes for which      //
  // no stack maps are generated: l2f, l2d, f2l, f2d, lmul, ldiv, etc.                          //
  //                                                                                            //
  // These methods must therefore be declared VM_Uninterruptible, must not invoke any non-magic //
  // methods, and must not use any bytecodes that are implemented by the compiler with          //
  // out-of-line code under the covers.                                                         //
  //                                                                                            //
  // In other words these methods may only use logical ops (shift, and, or) on longs and        //
  // may not perform any casts ( (int), (long), (float), (double) ) at all.                     //
  //                                                                                            //
  // Exception: stackmaps are generated for ldiv and lrem bytecodes, therefore                  //
  // longDivide() and longRemainder() can call out of line code to throw a divide               //
  // by zero exception.                                                                         //
  //                                                                                            //
  // [02 Nov 99 DL]                                                                             //      //
  //--------------------------------------------------------------------------------------------//

  static long longMultiply (long a, long b) {
    long result = 0L;
    long mask   = 0x8000000000000000L;
    if (0<=a) {
      for (int i=0; i<63; i++) {
	result <<= 1;
	a      <<= 1;
	if ((a&mask) != 0) {
	  result += b;
	}
      }
      return result;
    } else if (a != mask) { // mask < a < 0
      a = -a;
      for (int i=0; i<63; i++) {
	result <<= 1;
	a      <<= 1;
	if ((a&mask) != 0) {
	  result -= b;
	}
      }
      return result;
    } else /* a == mask == -a */ if (b == 1L) {
      return a;
    } else { // a == -a, b != 1
      return 0L;
    }
  }

   static long longDivide (long a, long b) {
    long result = 0L;
    long lim = 0x3FFFFFFFFFFFFFFFL;
    int n=0;
    if (0<b) {
      if (0<=a) {
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return result;
      } else if (a != -a) { // 0x8000000000000000L< a < 0
	a = -a;
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return -result;
      } else { // a == 0x8000000000000000L
	a += b;
	a = -a;
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return -(result+1L);
      }
    } else if (b != -b) { // 0x8000000000000000L < b < 0
      b = -b;
      if (0<=a) {
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return -result;
      } else if (a != -a) { // 0x8000000000000000L< a < 0
	a = -a;
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return result;
      } else { // a == 0x8000000000000000L
	a += b;
	a = -a;
	if (a<lim) lim = a;
	while (lim > b) {
	  b <<= 1;
	  n++;
	}
	for (int i=0; i<=n; i++) {
	  result <<= 1;
	  if (b<=a) {
	    a -= b;
	    result++;
	  }
	  b >>>= 1;
	}
	return result+1L;
      }
    } else if (b == 0) {
      throw new ArithmeticException(); // safe, because stack map is generated for "ldiv" bytecode
    } else if (a == b) { // b == -b == a == -a == 0x8000000000000000L 
      return 1L;
    } else { // b == -b
      return 0L;
    }
  }

  static long longRemainder (long a, long b) {
/// return a - ((a / b) * b);
    return a - longMultiply(longDivide(a, b), b);
  }

  // if -2**51 <= x < 2**51,  x an integer
  // and x-0.5 <  y <= x+0.5, y a double
  // then the last 51 bits of y+IEEEmagic == x
  // and x = (y+IEEEmagic)-IEEEmagic

  static double longToDouble (long l) {
    //-#if RVM_FOR_IA32
    // on IA32, use the baseline compiler to simulate strict IEEE
    // floating point arithmetic.	
    VM_Magic.pragmaNoOptCompile();
    //-#endif
    long magic = VM_Magic.doubleAsLongBits(IEEEmagic);
    long t = l >> 51;
    if (t==0) {
      l =  l | magic;
      return VM_Magic.longBitsAsDouble(l) - IEEEmagic;
    } else if (t == -1) {
      l = -l | magic;
      return IEEEmagic - VM_Magic.longBitsAsDouble(l);
    } else if (0 < l) {
      long h = l >> 32;
      l =  l & 0xFFFFFFFF;
      h =  h | magic;
      l =  l | magic;
      double H = VM_Magic.longBitsAsDouble(h) - IEEEmagic;
      double L = VM_Magic.longBitsAsDouble(l) - IEEEmagic;
      return L + H*two32;
    } else {
      l = -l;
      long h = l >> 32;
      l =  l & 0xFFFFFFFF;
      h =  h | magic;
      l =  l | magic;
      double H = IEEEmagic - VM_Magic.longBitsAsDouble(h);
      double L = IEEEmagic - VM_Magic.longBitsAsDouble(l);
      return L + H*two32;
    }
  }

   static int doubleToInt (double d) {
    //-#if RVM_FOR_IA32
    // on IA32, use the baseline compiler to simulate strict IEEE
    // floating point arithmetic.	
    VM_Magic.pragmaNoOptCompile();
    //-#endif
    if (1.0D <= d) {
      if (maxint <= d) return 0x7FFFFFFF;
      // find positive double integer a, such that 
      // a - 1.0 < d <= a
      // and 0 <= a < 2*31
      double A = IEEEmagic+d;
      double a = A-IEEEmagic;
      if (d < a) A -= 1.0;
      return (int) VM_Magic.doubleAsLongBits(A);
    } else if (-1.0D >= d) {
      if (d < -maxint) return 0x80000000;
      // find negative double integer a, such that 
      // a <= d < a + 1.0
      // and 0 >= a >= -2*31
      double A = IEEEmagic+d;
      double a = A-IEEEmagic;
      if (d > a) A += 1.0;
      return (int) VM_Magic.doubleAsLongBits(A);
    } else return 0; // d is 0.0 or NaN
  }

   static long doubleToLong (double d) {
    //-#if RVM_FOR_IA32
    // on IA32, use the baseline compiler to simulate strict IEEE
    // floating point arithmetic.	
    VM_Magic.pragmaNoOptCompile();
    //-#endif
    if (1.0D <= d) {
      if (maxlong <= d) return 0x7FFFFFFFFFFFFFFFL;
      // find double integers a, b such that 
      // a*2**32 + b - 1.0 < d <= a*2**32 + b 
      // and 0 <= a < 2*31, 0 <= b < 2**32
      double x = d  * half32;
      double A = IEEEmagic+x;
      double a = A-IEEEmagic;
      if (x < a) {
	a -= 1.0;
	A -= 1.0;
      }
      x = d - a*two32;
      double B = IEEEmagic+x;
      double b = B-IEEEmagic;
      if (x < b) B -= 1.0;
      long h = VM_Magic.doubleAsLongBits(A) << 32;
      long l = VM_Magic.doubleAsLongBits(B) &  0xFFFFFFFFL;
      return h | l;
    } else if (-1.0D >= d) {
      if (d <= -maxlong) return 0x8000000000000000L;
      // find double integers a, b such that 
      // a*2**32 + b <= d < a*2**32 + b + 1.0
      // and 0 >= a >= -2*31, 0 >= b > 2**32
      double x = d  * half32;
      double A = IEEEmagic+x;
      double a = A-IEEEmagic;
      if (x < a) {
	a -= 1.0;
	A -= 1.0;
      }
      x = d - a*two32;
      double B = IEEEmagic+x;
      double b = B-IEEEmagic;
      if (x > b) B += 1.0;
      long h = VM_Magic.doubleAsLongBits(A) << 32;
      long l = VM_Magic.doubleAsLongBits(B) &  0xFFFFFFFFL;
      return h | l;
    } else return 0L; // d is 0.0 or NaN
  }

/* end of math stuff for the compiler */ 

}
