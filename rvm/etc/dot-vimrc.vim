""" vim:filetype=vim
"""
""" Copyright © IBM Corp 2003
"""
""" $Id$

""" @author Igor Pechtchanski

"" If you're going to use the Jikes RVM Vim code, I'd recommend
"" adding something like the following to a file in your home directory named
"" ~/.vimrc:

if 1                           " in case +eval is off
  let r = $RVM_ROOT
  if r != ""
    execute 'set runtimepath+=' . escape(r, ' \') . '/rvm/etc'
    runtime jikes-rvm.vim
  endif
  unlet r
endif

"" I recommend adding the following to your ~/.vimrc; they can make
"" it more pleasant to edit Jikes RVM code
if has("syntax")
  syntax on                    " turn on syntax highlighting

  "" If your window background is dark, like mine, uncomment the following
"  set background=dark
endif

"" autowrap is good for writing new code, but for some it will make it
"" annoying to edit existing code (although vim is pretty good at figuring
"" out which is which).  If you don't like the feature, leave it commented
"" out below.

"set formatoptions-=tcl formatoptions+=tcl

"" I like the use of <Tab> for indenting according to the code rules.  If
"" you find it annoying, leave the feature setting below commented out also.

"set smarttab

