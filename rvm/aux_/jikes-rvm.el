;; -*- coding: iso-8859-1; -*-
;;; $Id$

;;; Style used when coding Jikes RVM.  

;;; @author Steven Augart
;;; @date May 20, 2003, through Oct 9, 2003 (work in progress)

;;; This style is designed to work nicely with the functions
;;; `c-toggle-auto-state' and `c-toggle-hungry-state'.

;; Things I recommend running in your c-mode-common-hook, because they'll make
;; your life easier (this is not mandatory):

;; (turn-on-font-lock)
;; (which-function-mode 1)
;; (c-toggle-auto-hungry-state 1)
;; (if (fboundp 'c-context-line-break)	;In Emacs 21.2.50, not in Emacs 20.7
;;    (define-key c-mode-base-map "\r" 'c-context-line-break))


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
  (make-local-variable 'c-cleanup-list)
  ;; Now reset c-cleanup-list
  ;; remove a couple of items from c-cleanup-list
  (setq c-cleanup-list
	;;scope-operator is specific to C++, although it somehow
	;; crept into the stock cc-mode's Java submode.
	(delq 'space-before-funcall
	      (delq 'scope-operator c-cleanup-list)))
  (mapc #'(lambda (new-elem)
	    (add-to-list c-cleanup-list new-elem))
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
	  'no-space-before-funcall
	  'compact-funcall
	  'compact-every-funcall))
	
  (make-local-variable 'c-hanging-braces-alist)
  (add-to-alist 'c-hanging-braces-alist
					; '(brace-entry-open . (after))
					; '(brace-entry-close . (before))
					; '(brace-list-open . (after))
					; '(brace-list-close . (before))
		'(extern-lang-open . (after))
		'(extern-lang-close . (before))
		'(defun-open . (after))
		'(defun-close . (before))
		'(class-open . (after))
		'(class-close . (before))
		'(inline-open . (after))
		'(inline-close . (before))
		'(block-open . (after))
		'(block-close . (before))
		'(substatement-open . (after))
		;; no substatement-close?
		'()))

(defun add-jikes-rvm-cc-styles ()
  (interactive)
  ;; Most of the things we set aren't buffer-local :(
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

(defun add-jalapeño-style ()
  (interactive)
  (c-add-style "jalapeño" '("java"
			    (c-basic-offset . 2)
			    )))
;; must follow the def. of add-jalapeño-style
(eval-after-load "cc-styles" #'(add-jalapeño-style))

;; used to be tested against font-lock.el; now cc-fonts.el.
(eval-after-load "cc-fonts"
  #'(setq java-font-lock-extra-types
	  ;; correct the defn. in Emacs 21.2 so we do not include small sharp-S
	  ;; This is annoyingly specific to ISO-Latin-1.
	  '("[A-Z\300-\326\330-\336]\\sw*[a-z]\\sw*")))

;; Modify java mode level 3 (gaudy) highlighting
(eval-after-load "cc-fonts"
  #'(add-to-list 'java-font-lock-keywords-3
		 '("@\\(modified\\|date\\)\\>"
		   (1 font-lock-constant-face prepend))))

(defvar jikes-rvm-javadoc-font-lock-keywords
  '("@\\(author\\|date\\|deprecated\\|exception\\|link\\|modified\\|return\\|see\\|serial\\|serialData\\|serialField\\|since\\|throws\\|version\\)\\>"
    (1 font-lock-constant-face prepend))
  "*Keywords used for highlighting Javadoc-style comments in Jikes RVM,
even if they appear in C or C++ programs.")

(eval-after-load "cc-fonts"
  #'(add-to-list 'c++-font-lock-keywords-3
		 jikes-rvm-javadoc-font-lock-keywords))

(eval-after-load "cc-fonts"
  #'(add-to-list 'c-font-lock-keywords-3
		 jikes-rvm-javadoc-font-lock-keywords))
(provide 'jikes-rvm)
