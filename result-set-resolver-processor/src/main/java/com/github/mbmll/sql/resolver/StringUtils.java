package com.github.mbmll.sql.resolver;


/**
 *
 */
public class StringUtils {
    public static boolean anyEmpty(String... strs) {
        for (String str : strs) {
            if (isEmpty(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param str
     *
     * @return
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }
}
