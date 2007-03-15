/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */

class ExceptionTest8 {

    public static void main(String[] args) {
        int x = 7;
        while (--x > 0) {
            try {
                try {
                    if (args[0].equals("null"))
                        throw new NullPointerException();
                } finally {
                    if (args[0].equals("cast"))
                        throw new ClassCastException();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

}
