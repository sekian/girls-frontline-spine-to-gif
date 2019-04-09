# GirlsFrontline-getChibiFrames
Create a sequence of transparent png images for each animation from the Girls' Frontline spine files.

All the chibi animation sequences will be ready to be built as a .gif or any other media file.

**GIF creation example with ImageMagick**

`convert -delay 5 -dispose Background -loop 0 "*.png" attack.gif`
