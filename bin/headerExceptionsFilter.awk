# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001, 2003, 2005, 2006
#

# @author Peter Sweeney
# @date 11/1/2001
# @modified Steven Augart
# @date June, 2003

## Auxiliary AWK program to help out /bin/findDeviantFiles.  You should
## never run this directly; to discourage people from doing so, this is not an
## executable file.

#
# The following is a list of patterns to match files
# that are expempted from project-wide standards on
# header infromation.  This list should be kept as
# short as possible, and all exceptions need to be
# approved by the steering committee.
#
/\/tools-external\// { next }

# Avoid reporting a gazillion bogus violations when
# this script is run from night-sanity-run
/results\// { next }
/dist\// { next }
/generated\// { next }
/target\// { next }
/doc\/api\// { next }

# don't need an author for all the build scripts
/build\// { next }

/doc\/userguide\// { next }
/MMTk\/doc\// { next }

/testing\/tests\/javalex\/qb1.lex.ref/ {next}
/testing\/tests\/SPECjbb2000\/SPECjbb./ {next}
/testing\/tests\/SPECjbb2005\/SPECjbb./ {next}
/testing\/tests\/pseudojbb\/pseudojbb/ {next}
/testing\/tests\/pseudojbb\/props/ {next}
/testing\/tests\/mauve\/mauve-jikesrvm/ {next}

# actually has the right headers, but utf8 characters break the checkers
/testing\/tests\/utf8\/src\/utf8test.java/ {next}

/tools\/bootImageRunner\/com_ibm_jikesrvm_VM_0005fProcess.h/ { next }
/tools\/bootImageWriter\/rvm.security/ { next }
/tools\/eclipse\/plugin2\/src\/com\/ibm\/jikesrvm\/eclipse\/ui\/jalapeno.jpg/ { next }

/LICENSE/ { next }
/NEWS/ { next }

/.properties$/ { next }
/.properties\.sample$/ { next }
/README$/ { next }

#
# print everything else
#
{ print; }

