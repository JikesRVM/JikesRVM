;;; -*- coding: iso-8859-1; -*-
;;;
;;; Copyright © IBM Corp 2003
;;;
;;; $Id$

;;; C, C++, and Java style for editing Jikes RVM source code.  This
;;; customizes Java mode, C++ mode, and C mode.  Since this file will
;;; probably improve with time, I recommend you load it in rather
;;; than cutting and pasting its contents into your ~/.emacs file.
;;;
;;; @author Steven Augart
;;; @date May 20, 2003, through November 21, 2003 (work in progress)

;;; This style is designed to work nicely with the functions
;;; `c-toggle-auto-state' and `c-toggle-hungry-state'.

;; To use this file, see the file "dot-emacs.el" in this directory.


(add-hook 'java-mode-hook #'jikes-rvm-java-buffer-setup)
(add-hook 'c-mode-hook #'jikes-rvm-c-buffer-setup)
(add-hook 'c++-mode-hook #'jikes-rvm-c++-buffer-setup)

(defun jikes-rvm-c-buffer-setup ()
  (interactive)
  (c-set-style "jikes-rvm-c")
  ;; automatically buffer-local
  (setq indent-tabs-mode nil))


(defun jikes-rvm-c++-buffer-setup ()
  (interactive)
  (jikes-rvm-c-buffer-setup) )

(defun jikes-rvm-java-buffer-setup ()
  (interactive)
  (c-set-style "jikes-rvm-java")

  ;; indent-tabs-mode is automatically buffer-local.  This set to
  ;; nil per a suggestion by Steve Blackburn, October 9, 2003
  (setq indent-tabs-mode nil)
  
  (if (null c-syntactic-indentation)
      (message "You have overridden c-syntactic-indentation by
setting it to nil.   You're responsible for meeting the
JikesRVM Java style guidelines on your own"))
  
  ;; c-cleanup-list should already be buffer-local.  But in case
  ;; it is not, make it local for this buffer.
;  (make-local-variable 'c-cleanup-list)
;  (unless (boundp 'c-cleanup-list)
;    (setq c-cleanup-list nil))
  ;; Now reset c-cleanup-list
  ;; remove a couple of items from c-cleanup-list
  (setq c-cleanup-list
	;;scope-operator is specific to C++, although it somehow
	;; crept into the stock cc-mode's Java submode.
	(delq 'space-before-funcall
	      (delq 'scope-operator c-cleanup-list)))
  (mapc #'(lambda (new-elem)
	    (add-to-list 'c-cleanup-list new-elem))
	'(
	  ;; stock items; provided in default CC mode
	  ;; anyway. 
	  'brace-else-brace
	  'brace-elseif-brace
	  'brace-catch-brace
	  'list-close-comma 
	  ;; Other items that we find useful for our style. 
	  'empty-defun-braces 'defun-close-semi 
	  ;; These will be activated all the time, not just when
	  ;; auto-hungry-mode is on. 
	  ;; we really want to compact every funcall.
	  ;; 'space-before-funcall
	  'compact-empty-funcall
	  ;; These don't exist; they're fantasy names.  Won't hurt.
;	  'no-space-before-funcall
;	  'compact-funcall
;	  'compact-every-funcall
	  ))
	
  (make-local-variable 'c-hanging-braces-alist)
  (mapc #'(lambda (new-elem) (add-to-list 'c-hanging-braces-alist new-elem))
					; '(brace-entry-open . (after))
					; '(brace-entry-close . (before))
					; '(brace-list-open . (after))
					; '(brace-list-close . (before))
	'((extern-lang-open after)
	  (extern-lang-close before)
	  (defun-open after)
	  (defun-close before)
	  (class-open after)
	  (class-close before)
	  (inline-open after)
	  (inline-close before)
	  (block-open after)
	  (block-close before)
	  ;; no substatement-close?
	  (substatement-open after))))

(defun add-jikes-rvm-cc-styles ()
  (interactive)
  ;; Most of the things we set aren't buffer-local, so we have to use the hook
  ;; to do it.  Pity. :(
  (c-add-style "jikes-rvm-java" '("java"
				  (c-basic-offset . 2)))
  (c-add-style "jikes-rvm-c++"  '("jikes-rvm-c"
				  ))
  (c-add-style "jikes-rvm-c" '("k&r"
			       (c-special-indent-hook . nil)
			       (c-basic-offset . 4))))

(eval-after-load "cc-styles" #'(add-jikes-rvm-cc-styles))

;; This sets up our Java.

;; Add these to your local INFO directory (/usr/local/share/info/dir):
; Classpath
; * Hacking GNU Classpath (hacking): Hacking GNU Classpath
; * vmintegration (vmintegration): Integrating GNU Classpath with your VM
(add-to-list 'Info-default-directory-list
	     "/usr/local/classpath/classpath/doc")

(setq java-font-lock-extra-types
      ;; correct the defn. in Emacs 21.2 so we do not include small sharp-S
      ;; This is annoyingly specific to ISO-Latin-1.
      '("[A-Z\300-\326\330-\336]\\sw*[a-z]\\sw*"))

;; Modify java mode level 3 (gaudy) highlighting
(add-hook 'java-mode-hook 
	  #'(lambda () 
	      (when (boundp 'java-font-lock-keywords-3)
		(add-to-list 'java-font-lock-keywords-3
			     '("@\\(modified\\|date\\)\\>"
			       (1 font-lock-constant-face prepend))))))

(defvar jikes-rvm-javadoc-font-lock-keywords
  '("\\(@\\(author\\|date\\|deprecated\\|link\\|modified\\|param\\|return\\|see\\|since\\|value\\|version\\)\\)\\>"
    (1 font-lock-constant-face prepend))
  "*Keywords used for highlighting Javadoc-style comments in Jikes RVM,
even if they appear in C or C++ programs.  This is kludgy; we really
need to do a better job of recognizing doc comments.")

(add-hook 'c++-mode-hook
	  #'(lambda () 
	      (when (boundp 'c++-font-lock-keywords-3)
		(add-to-list 'c++-font-lock-keywords-3
			     jikes-rvm-javadoc-font-lock-keywords))))

(add-hook 'c-mode-hook
	  #'(lambda () 
	      (when (boundp 'c-font-lock-keywords-3)
		(add-to-list 'c-font-lock-keywords-3
			     jikes-rvm-javadoc-font-lock-keywords))))

;;; As of Jikes RVM 2.3.1, we will only use spaces for indentation.
(add-hook 'c-mode-common-hook
	  #'(lambda () 
	      ;; TAB will only insert spaces, not literal TAB characters.
	      (setq indent-tabs-mode nil)))


(provide 'jikes-rvm)
