/* Runtime capability selection; product names and screen resolution are not sufficient. */
#ifndef EMBRITH_DISPLAY_BACKEND_H
#define EMBRITH_DISPLAY_BACKEND_H
#include <linux/fb.h>
#include <string.h>
static int display_supports_color(const struct fb_var_screeninfo *v, const struct fb_fix_screeninfo *f) {
    return v->bits_per_pixel == 32 && !strncmp(f->id,"hwtcon",6) &&
        v->red.offset == 0 && v->green.offset == 8 && v->blue.offset == 16 &&
        v->red.length == 8 && v->green.length == 8 && v->blue.length == 8;
}
#endif
