/*
 * Created on 2018/09/05.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.utils;

import androidx.annotation.NonNull;

import java.util.Stack;

/**
 * @author 刘振林
 */
public class AlgorithmUtils {
    private AlgorithmUtils() {
    }

    /**
     * 求解str1 和 str2 的最长公共子序列（忽略字母大小写）
     */
    @NonNull
    public static String LCS(@NonNull String str1, @NonNull String str2, boolean caseSensitive) {
        if (!caseSensitive) {
            str1 = str1.toLowerCase();
            str2 = str2.toLowerCase();
        }

        final char[] s1 = str1.toCharArray();
        final char[] s2 = str2.toCharArray();
        // 此处的棋盘长度要比字符串长度多加1，需要多存储一行0和一列0
        final int[][] array = new int[s1.length + 1][s2.length + 1];

        for (int i = 0; i < array.length; i++) { // 第i行，第0列全部为0
            array[i][0] = 0;
        }
        for (int j = 0; j < array[0].length; j++) { // 第0行第j列全部赋值为0
            array[0][j] = 0;
        }

        for (int i = 1; i < array.length; i++) { // 利用动态规划将数组赋满值
            for (int j = 1; j < array[i].length; j++) {
                if (s1[i - 1] == s2[j - 1]) {
                    array[i][j] = array[i - 1][j - 1] + 1;
                } else {
                    array[i][j] = Math.max(array[i - 1][j], array[i][j - 1]);
                }
            }
        }

        Stack<Character> stack = new Stack<>();
        int i = s1.length - 1;
        int j = s2.length - 1;

        while (i >= 0 && j >= 0) {
            if (s1[i] == s2[j]) { // 字符串从后开始遍历，如若相等，则存入栈中
                stack.push(s1[i]);
                i--;
                j--;
            } else {
                // 如果字符串的字符不同，则在数组中找相同的字符，
                // 注意：数组的行列要比字符串中字符的个数大1，因此i和j要各加1
                if (array[i + 1][j] > array[i][j + 1]) {
                    j--;
                } else {
                    i--;
                }
            }
        }

        StringBuilder result = new StringBuilder();
        while (!stack.isEmpty()) {
            result.append(stack.pop());
        }
        return result.toString();
    }
}
