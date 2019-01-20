/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Same as small_struct.rs except for location of padding in struct small_struct[_2].

#include "shared.rsh"

int gDimX;
int gDimY;

rs_allocation A;
rs_allocation B;

static int gIntStart = 0x7;
static long gLongStart = 0x12345678abcdef12;

typedef struct small_struct_2 {
    long l;
    int i;
    // expect 4 bytes of padding here
} small_struct_2;

#define ARRAY_LEN 3

typedef struct struct_of_struct_2 {
    small_struct_2 arr[ARRAY_LEN];
} struct_of_struct_2;

void test() {
    bool failed = false;
    for (int x = 0; x < gDimX; x ++) {
        for (int y = 0; y < gDimY; y ++) {
            small_struct_2 *v = (small_struct_2 *) rsGetElementAt(A, x, y);
            _RS_ASSERT_EQU(v->i, gIntStart + y * gDimX + x);
            _RS_ASSERT_EQU(v->l, gLongStart + y * gDimX + x);
        }
    }

    for (int x = 0; x < gDimX; x ++) {
        for (int y = 0; y < gDimY; y ++) {
            struct_of_struct_2 *v = (struct_of_struct_2 *) rsGetElementAt(B, x, y);
            for (int idx = 0; idx < ARRAY_LEN; idx ++) {
                _RS_ASSERT_EQU((*v).arr[idx].i, gIntStart + y * gDimX + x + idx);
                _RS_ASSERT_EQU((*v).arr[idx].l, gLongStart + y * gDimX + x + idx);
            }
        }
    }

    if (failed) {
        rsDebug("small_struct_2 test FAILED", 0);
        rsSendToClientBlocking(RS_MSG_TEST_FAILED);
    }
    else {
        rsDebug("small_struct_2 test PASSED", 0);
        rsSendToClientBlocking(RS_MSG_TEST_PASSED);
    }
}

small_struct_2 RS_KERNEL setStruct(int x, int y) {
    small_struct_2 output;
    output.i = gIntStart + y * gDimX + x;
    output.l = gLongStart + y * gDimX + x;
    return output;
}

struct_of_struct_2 RS_KERNEL setArrayOfStruct(int x, int y) {
    struct_of_struct_2 output;
    for (int idx = 0; idx < ARRAY_LEN; idx ++) {
        output.arr[idx].i = gIntStart + y * gDimX + x + idx;
        output.arr[idx].l = gLongStart + y * gDimX + x + idx;
    }
    return output;
}
