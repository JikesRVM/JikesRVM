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
class testSwitchHIR {

  static int argwords(String sig) {
    int n = 0;

    for (int i = 1; i < sig.length(); i++) {
        switch (sig.charAt(i)) {
            case ')':   return n;
            default:    n += 1;  break; // error
            case 'B':   n += 1;  break;
            case 'C':   n += 1;  break;
            case 'D':   n += 2;  break;
            case 'F':   n += 1;  break;
            case 'I':   n += 1;  break;
            case 'J':   n += 2;  break;
            case 'S':   n += 1;  break;
            case 'Z':   n += 1;  break;
            case 'L':
                n += 1;

/* Something is wrong with HIR in the following statement */
/* This breaks RegAlloc, reported as bug report #10. */
                while (sig.charAt(++i) != ';') ;

                break;
            case '[':
                n += 1;
                while (sig.charAt(++i) == '[')
                    ;
                if (sig.charAt(i) == 'L')
                    while (sig.charAt(++i) != ';')
                        ;
                break;
        }
    }
    return n;
}

}
