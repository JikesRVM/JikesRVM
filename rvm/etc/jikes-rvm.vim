""" vim:filetype=vim
"""
""" Copyright © IBM Corp 2003
"""
""" $Id$

""" C, C++, and Java style for editing Jikes RVM source code.  This
""" customizes Java mode, C++ mode, and C mode.  Since this file will
""" probably improve with time, I recommend you load it in rather
""" than cutting and pasting its contents into your ~/.vimrc file.
"""
""" @author Igor Pechtchanski
""" @date Dec 3, 2003 (work in progress)

"" This also modifies syntax highlighting rules for make, sh and m4 modes.

"" To use this file, see the file "dot-vimrc.vim" in this directory.

if exists("did_load_jikesrvm")
  finish
endif
if 1                           " in case +eval is off
  let did_load_jikesrvm = 1
endif

if has("autocmd")

filetype indent on

"" Set up C/C++/Java common editing options
function! Jikes_rvm_buffer_common_setup()
  set expandtab                " replace tab characters by spaces
  set cinoptions=:0,l1,g0,t0,+8,(0,u0
  set cinwords&vim
  set formatoptions-=roql	" reset formatting
  set formatoptions+=roql
  set textwidth=80 wrapmargin=0
endfunction

"" Set up C editing
function! Jikes_rvm_c_buffer_setup()
  call Jikes_rvm_buffer_common_setup()
  set shiftwidth=4
endfunction

"" Set up C++ editing
function! Jikes_rvm_cpp_buffer_setup()
  call Jikes_rvm_c_buffer_setup()
endfunction

"" Set up Java editing
function! Jikes_rvm_java_buffer_setup()
  call Jikes_rvm_buffer_common_setup()
  set shiftwidth=2	           " for indenting and shifting
  set cinoptions+=j1
endfunction

"" non-standard JavaDoc tags used in the Jikes RVM source code
function! Jikes_rvm_new_javadoc_tags()
  syn match javaDocTags contained "@\(date\|modified\)\>"
endfunction

"" JavaDoc tags that should be recognized in non-Java files
function! Jikes_rvm_nonjava_javadoc_tags()
  syn region javaDocTags contained start="{@\(link\|value\)" end="}"
"" jikes-rvm.el doesn't have the two keywords below
"  syn match javaDocTags contained "@\(exception\|throws\)\>"
  syn match javaDocTags contained "@\(see\|param\|since\)\>"
  syn match javaDocTags contained "@\(version\|author\|return\|deprecated\)\>"
  call Jikes_rvm_new_javadoc_tags()
  hi def link javaDocTags    Special
endfunction

"" Set up syntax highlighting for Java
function! Jikes_rvm_java_highlight_setup()
  call Jikes_rvm_new_javadoc_tags()
endfunction

"" Set up syntax highlighting for C
function! Jikes_rvm_c_highlight_setup()
  syn region javaDocComment start="/\*\*" end="\*/" keepend contains=javaDocTags
  call Jikes_rvm_nonjava_javadoc_tags()
  hi def link javaDocComment Comment
endfunction

"" Set up syntax highlighting for C++
function! Jikes_rvm_cpp_highlight_setup()
  call Jikes_rvm_c_highlight_setup()
endfunction

"" Set up syntax highlighting for shell scripts
function! Jikes_rvm_sh_highlight_setup()
  call Jikes_rvm_nonjava_javadoc_tags()
  syn cluster shCommentGroup add=javaDocTags
endfunction

"" Set up syntax highlighting for Makefiles
function! Jikes_rvm_make_highlight_setup()
  syn region makeComment start="#" end="^$" end="[^\\]$" contains=javaDocTags
  call Jikes_rvm_nonjava_javadoc_tags()
endfunction

"" Set up syntax highlighting for m4 macro files
function! Jikes_rvm_m4_highlight_setup()
  syn match m4Comment "dnl\>.*" contains=javaDocTags
  call Jikes_rvm_nonjava_javadoc_tags()
endfunction

"" Read a skeleton file of a given type
function! Jikes_rvm_read_skeleton_file(file)
  execute '0r '. $RVM_ROOT . '/rvm/etc/skel/' . a:file
  redraw            " clear the 'File read' message
endfunction


augroup Jikes_rvm

"" Remove all commands in this group
au!

"" Commands to read in skeleton files
au BufNewFile *.java call Jikes_rvm_read_skeleton_file("Skeleton.java")
au BufNewFile *.c,*.C call Jikes_rvm_read_skeleton_file("skeleton.C")
au BufNewFile *.sh call Jikes_rvm_read_skeleton_file("skeleton.sh")
au BufNewFile [Mm]akefile,GNUmakefile call Jikes_rvm_read_skeleton_file("GNUmakefile.skel")
au BufNewFile *.m4 call Jikes_rvm_read_skeleton_file("skeleton.m4")

"" Set up language-specific editing features
au FileType c,cpp,java call Jikes_rvm_{expand("<amatch>")}_buffer_setup()

"" Set up highlighting
au FileType java,c,cpp,sh,make,m4 call Jikes_rvm_{expand("<amatch>")}_highlight_setup()

"" Redo the highlighting setup commands after syntax files are read in
au BufNewFile,BufReadPost,FileReadPost,FilterReadPost * if exists("#FileType#".&filetype) | doautocmd Jikes_rvm FileType | endif

augroup END

endif
