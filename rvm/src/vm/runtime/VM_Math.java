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
public class VM_Math {

  //-#if RVM_FOR_IA32
  /**
   * A well-known memory location used to manipulate the FPU control word.
   */
  static int FPUControlWord;
  //-#endif RVM_FOR_IA32

  /*
   * Constants that are used by the compilers in generated code.
   */
  static double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);
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

  // smallest double that can be rounded to an int
  static double minint   =  (double)Integer.MIN_VALUE;
  
  /*
   * implements Float.valueOf and Double.valueOf
   */

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
  public static double stringToDouble(String s) throws NumberFormatException {
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
}
