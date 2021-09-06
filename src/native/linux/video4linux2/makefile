#
# Copyright @ 2015 Atlassian Pty Ltd
# Copyright @ 2021 Raptor Engineering, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CC=gcc

JNI_HEADERS = -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/linux"
VIDEO4LINUX2_HEADERS = -I"/usr/include/"

CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(VIDEO4LINUX2_HEADERS)

OBJS = org_jitsi_impl_neomedia_jmfext_media_protocol_video4linux2_Video4Linux2.c

LIBS = -lv4l2 -lpthread

libjnvideo4linux2.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnvideo4linux2.so $(LIBS)

install32: libjnvideo4linux2.so
	cp libjnvideo4linux2.so "../../../../lib/native/linux-x86/libjnvideo4linux2.so"
	cp libjnvideo4linux2.so "../../../../../jitsi-videobridge/lib/native/linux/libjnvideo4linux2.so"

install64: libjnvideo4linux2.so
	cp libjnvideo4linux2.so "../../../../lib/native/linux-x86-64/libjnvideo4linux2.so"
	cp libjnvideo4linux2.so "../../../../../jitsi-videobridge/lib/native/linux-64/libjnvideo4linux2.so"

installppc64: libjnvideo4linux2.so
	cp libjnvideo4linux2.so "../../../../lib/native/linux-ppc64le/libjnvideo4linux2.so"
	cp libjnvideo4linux2.so "../../../../../jitsi-videobridge/lib/native/linux-ppc64le/libjnvideo4linux2.so"

clean:
	rm -f *.so
