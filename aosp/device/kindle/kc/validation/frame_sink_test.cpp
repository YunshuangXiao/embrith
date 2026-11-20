/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
#include "../../common/hwcomposer/frame_sink.h"
#include <stdio.h>
#include "../../../../../host/src/display_backend.h"
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); exit(1); } } while(0)
static void test(bool color) {
 char path[]="/tmp/embrith-sink-XXXXXX"; int fd=mkstemp(path); CHECK(fd>=0);
 const size_t n=8, len=4096+n*(color?5:1); CHECK(ftruncate(fd,len)==0);
 uint8_t *m=(uint8_t*)mmap(0,len,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0); CHECK(m!=MAP_FAILED);
 memset(m,0,len); kdisp_hdr *h=(kdisp_hdr*)m;
 h->magic=KDISP_MAGIC;h->version=KDISP_VERSION;h->width=4;h->height=2;h->stride=4;h->data_offset=4096;h->flags=color?KDISP_FLAG_RGBA_PLANE:0;
 { FrameSink s; CHECK(s.open(path)); uint8_t rgba[32];memset(rgba,255,sizeof rgba);
   rgba[0]=150;rgba[1]=0;rgba[2]=0; CHECK(s.publish(rgba,16,4,2));
   CHECK(h->seq==2 && h->dirty_h==2 && m[4096]==45);
   CHECK(!s.publish(rgba,16,4,2));
   if(color)CHECK(!memcmp(m+4096+n,rgba,sizeof rgba));
   rgba[0]=0;rgba[1]=77; // same luminance, different RGB
   CHECK(s.publish(rgba,16,4,2)==color);
   if(color) { CHECK(h->seq==4); CHECK(m[4096+n+1]==77); CHECK(h->dirty_h==2); }
   h->ack=h->seq; rgba[16]=0;rgba[17]=0;rgba[18]=255;
   CHECK(s.publish(rgba,16,4,2)); CHECK(h->dirty_y==1 && h->dirty_h==1);
 }
 munmap(m,len);close(fd);unlink(path);
}
static void backend_test() {
 struct fb_var_screeninfo v;struct fb_fix_screeninfo f;memset(&v,0,sizeof v);memset(&f,0,sizeof f);
 strcpy(f.id,"hwtcon_v2");v.bits_per_pixel=8;CHECK(!display_supports_color(&v,&f));
 v.bits_per_pixel=32;v.red.length=v.green.length=v.blue.length=8;v.green.offset=8;v.blue.offset=16;CHECK(display_supports_color(&v,&f));
 v.red.offset=16;CHECK(!display_supports_color(&v,&f));v.red.offset=0;strcpy(f.id,"unknown");CHECK(!display_supports_color(&v,&f));
}
int main(){backend_test();test(false);test(true);puts("gray/RGBA frame tests passed");return 0;}
