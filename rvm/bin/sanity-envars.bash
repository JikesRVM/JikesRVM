#! /bin/bash
#
# (C) Copyright IBM Corp. 2001, 2003, 2003
#
# $Id$
#
# N.B.: This is an auxiliary function for loading.
#       We do not execute this script directly; the `#! /bin/bash'
#	is just there to give a hint to the text editor (or at least to
#	Emacs) about what mode to go into.
#
# @author Steven Augart
# @date  Sunday, June 1, 2003

# Who we are.  Just the short form of the name (no directory component).
: ${ME="${0##*/}"}
    

## Usage: checkenv <envar> [ <example> ]
## If the environment variable <envar> is unset, then tell the user it
## should be set, suggest an example value (if <example> is set), and
## exit fatally.

## jconfigure, findDeviantFiles, gdbrvm, and rvm all use checkenv()
function checkenv () {
    local may_not_exist=0;
    if (( $# > 1 )); then
	may_not_exist=1;
	shift;
    fi
    local envar="$1";
    shift;
    local -i wantdir=0;

    case $envar in 
	HOME | RVM_ROOT | RVM_BUILD ) wantdir=1 ;;
    esac
    ## Now perform the testing.
    if [[ ! ${!envar-} ]]; then
	echo -n "$ME: Please set your ${envar} environment variable"

	local example="$(checkenv_example ${envar})";
	if [ "${example}" ]; then
	    echo "";
	    echo -n "  (for example, to \"${example}\")"
	fi
	echo "."		# Supply a sentence-ending period.  
				# (English typography.)
	exit 1
    fi >&2
    
    ## Special tests for directories
    if (( wantdir )); then
	if [[ ${!envar} != /* ]]; then
	    echo "$ME: ${envar} must be set to an absolute path name."
	    exit 1;
	fi
	local -i exists=0
	[[ -e ${!envar} ]] && exists=1
	if (( ! may_not_exist )) && (( ! exists )); then
	    echo "The directory ${envar} (${!envar}) does not exist.  Something is wrong; check your ${envar} environment variable."
	    exit 1;
	fi
	if (( exists )) && [[ ! -d ${!envar}/. ]]; then
	    echo "${envar} (${!envar}) exists, but is not a directory or a symbolic link to a directory.  Something is wrong; check your ${envar} environment variable."
	    exit 1;
	fi >& 2
    fi
}

## Used internally.
function checkenv_example () {
    ## Now set up an example error.  So that we can complain.
    ## In another language I would do this in a lazy fashion (only 
    ## set up the example error if we need it), but this isn't 
    ## another language. 
    local example;
    if (( $# >= 1 )); then
	example="$*"; # handle the caller accidentally leaving out the quoting.
    else
	local home="${HOME=/home/${USER-${LOGNAME-'me'}}}";
	
	case "${!envar}" in
	    HOME ) 
		example="$home";
		;;

	    ## Place where source files reside.
	    RVM_ROOT ) 
		# A heuristic for guessing RVM_ROOT: Assume the program was
		# run from $RVM_ROOT/rvm/bin.  
		# The variable "mydir" should be set by the program 
		# loading this library.
		: ${mydir=${0%/*}}
		example="${mydir%/rvm/bin}"
		if [[ $example = $mydir ]]; then
		    example="$home/rvmRoot";
		fi
		;;

	    ## Place where RVM bootimage, booter, and runtime 
	    ## support files will be placed. 
	    RVM_BUILD ) example="$home/rvmBuild";
		;;

            ## What configuration will run the system?
	    RVM_HOST_CONFIG )
		checkenv RVM_ROOT
		example = "$RVM_ROOT/rvm/config/i686-pc-linux or $RVM_ROOT/rvm/config/powerpc-ibm-aix4.3.3.0"
		;;


 	    ## What configuration will run the system?
	    RVM_TARGET_CONFIG )
		# example="$RVM_ROOT/rvm/config/i686-pc-linux";
		checkenv RVM_HOST_CONFIG;
		example="$RVM_HOST_CONFIG" ;;
	    
	esac
    fi
}

