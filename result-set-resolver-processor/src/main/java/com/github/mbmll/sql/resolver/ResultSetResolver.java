package com.github.mbmll.sql.resolver;

import java.io.Serializable;
import java.lang.annotation.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author xlc
 * @Description
 * @Date 2026/6/20 17:06
 */

public interface ResultSetResolver <R extends Serializable>{
    R resolve(ResultSet rs) throws SQLException;
}

