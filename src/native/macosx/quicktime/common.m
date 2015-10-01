/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "common.h"

#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSObject.h> /* NSSelectorFromString */
#import <Foundation/NSString.h>

id NSObject_performSelector(id obj, NSString *selectorName)
{
    NSAutoreleasePool *autoreleasePool;
    SEL selector;
    id ret;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    selector = NSSelectorFromString(selectorName);
    ret = [obj performSelector:selector];

    [autoreleasePool release];
    return ret;
}
