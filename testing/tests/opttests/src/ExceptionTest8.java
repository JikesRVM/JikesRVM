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
