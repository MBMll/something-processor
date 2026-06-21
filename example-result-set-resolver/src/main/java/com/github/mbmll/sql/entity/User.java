package com.github.mbmll.sql.entity;

import com.github.mbmll.sql.annotation.AutoResultSetResolver;
import lombok.Data;

import java.io.Serializable;

/**
 * @Author xlc
 * @Description
 * @Date 2026/6/20 17:10
 */
@Data
@AutoResultSetResolver
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private Integer age;
    private Boolean sex;
}
