#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001, 2003, 2004, 2005
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001
# @modified Steven Augart
# @date June, 2003

## Auxiliary AWK program to help out /bin/findDeviantFiles.  You should
## never run this directly; to discourage people from doing so, this is not an
## executable file.
#
# Find files that don't have a copyright notice.
#
/^\.\/etc\/gnu-classpath-on-mac-osx\.patch$/ { next }

/\/bin\/classpath.stamp/ { next }

/\/doc\/userguide\// { next }
/\/etc\/testing\// { next }

/\/regression\/tests\/SPECjbb2000\/SPECjbb./ { next }
/\/regression\/tests\/pseudojbb\/pseudojbb/ {next}
/\/regression\/tests\/pseudojbb\/props/ {next}
/\/regression\/tests\/mauve\/mauve-jikesrvm/ {next}

/\/tests\/javalex\/qb1.lex.ref/ { next }

/\/tools\/bootImageRunner\/com_ibm_JikesRVM_VM_0005fProcess.h/ { next }
/\/tools\/bootImageWriter\/rvm.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }
/\/src\/tools\/eclipse\/plugin2\/src\/com\/ibm\/jikesrvm\/eclipse\/ui\/jalapeno.jpg/ { next }

/\/LICENSE/ { next }
/\/NEWS/ { next }
/\/src\/tools\/install\/macros.txt/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit.sanity/ { next }
/\/TimeLimit.performance/ { next }

/\.properties$/ { next }
/\.properties\.sample$/ { next }
/README$/ { next }
/\.txt$/ { next }

#
# print everything else
#
/.*/
