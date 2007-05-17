-*-text-*-
$Id: README,v 1.3 2005/03/14 23:15:49 saugart Exp $

jalapeno-pepper*.gif and fat-jalapeno-pepper*.gif are two different
Jalapeno peppers drawn by Ton Ngo for the Jalapeno project, the old
name for Jikes RVM.  As of this writing, fat-jalapeno-pepper-62x54.gif
and jalapeno-pepper-57x25.gif represent the closest things we have to
the originals that Ton Ngo drew -- his actual original seem lost;
Mike Hind pulled these out of images attached to Lotus Freelance
presentations he had archived.


jalapeno-pepper-16x16.* and jalapeno-pepper-32x32.* are rotated
versions of jalapeno-pepper-57x25.gif.  (I rotated them to get the
length of the pepper to appear diagonally from corner to corner.
Aesthetically, this was not a big success -- Mike Hind refers to the
jalapeno-pepper-16x16 icon as "the drunk pepper".)  The 16x16 and
32x32 winicons are suitable to be placed in the web server's root
directory as favicon.ico; they've been combined as
jalapeno-pepper-combined.winicon.  We briefly (for a couple of days)
used this as our icon for the web pages.


The "fat" pepper is the current web page icon.  From
fat-jalapeno-pepper-62x54.gif, I used the GIMP to crop the whitespace
-- this knocks it down to 48x40.  I then used the GIMP to center the
48x40 image in a 48x48 frame, and write it out as
"fat-jalapeno-pepper-cropped-48x48.gif".

I then scaled down fat-jalapeno-pepper-cropped-48x48.gif to make the
32x32.gif and 16x16.gif versions, and wrote them out with the GIMP.

Now, we want to turn these into a "winicon".  I used "giftopnm" to
turn fat-jalapeno-pepper-cropped-{48x48,32x32,16x16}.gif into .pnm
files.

I then read the manpage for "ppmtowinicon".   That page says:

       A Windows icon contains 1 or  more  images,  at  different
       resolutions and color depths.

       Microsoft recommends including at least the following for?
       mats in each icon.

       16 x 16 - 4 bpp

       32 x 32 - 4 bpp

       48 x 48 - 8 bpp

I then ran something like the following command:

ppmtowinicon -output jalapeno-pepper-combined.winicon		\
	fat-jalapeno-pepper-cropped-{48x48,32x32,16x16}.pnm	\
	<(ppmquant 256 fat-jalapeno-pepper-cropped-48x48.gif )	\
        <(ppmquant 16 fat-jalapeno-pepper-cropped-32x32.gif )	\
        <(ppmquant 16 fat-jalapeno-pepper-cropped-16x16.gif )

jalapeno-pepper-combined.winicon became web/pages/favicon.ico.

Back at the step where I made fat-jalapeno-opepper-cropped-16x16.gif,
I also used the GIMP to write out fat-jalapeno-pepper-16x16.png.
Mozilla is supposed to be able to make good use of a PNG image, and
ther is a line of text in the standard web page header that tells
Mozilla to do this.  See web/pgas/bitsAndPieces/header.html.
However, Mozilla seems to be using favicon.ico instead of the PNG, so
something is obviously wrong.

If you change favicon.ico again, then to get Mozilla to show the new
icon in its title bar you'll have to visit
http://jikesrvm.sf.net/favicon.ico.  Otherwise the old one is sticky.

BUGS:

There are some problems with this conversion.  The biggest one is
that favicon.ico is horribly fat (11K) -- it is not clear that any web
browsers use the 48x48 images.

The two 48x48 images in favicon.ico are identical, since the fat
pepper was already at less than 256 unique colors.



