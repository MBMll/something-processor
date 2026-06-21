package com.github.mbmll.sql.resolver;


import com.github.mbmll.sql.annotation.AutoResultSetResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class ResolverCodeGenerator {

    private final ProcessingEnvironment processingEnv;
    private final javax.lang.model.util.Elements elementUtil;
    private final javax.lang.model.util.Types typeUtil;

    public ResolverCodeGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elementUtil = processingEnv.getElementUtils();
        this.typeUtil = processingEnv.getTypeUtils();
    }

    /**
     * 生成实体对应的 Resolver 类
     */
    public void generateResolver(TypeElement entityElement) {
        // 1. 获取实体基础信息
        String entityFullName = entityElement.getQualifiedName().toString();
        String entitySimpleName = entityElement.getSimpleName().toString();
        String resolverClassName = entitySimpleName + "ResultSetResolver";
        String packageName = elementUtil.getPackageOf(entityElement).toString();

        // 2. 读取注解配置：驼峰转下划线
        AutoResultSetResolver anno = entityElement.getAnnotation(AutoResultSetResolver.class);
        boolean camelToUnderline = anno.camelToUnderline();

        // 3. 解析实体所有字段 + setter映射
        List<FieldMeta> fieldMetaList = parseEntityFields(entityElement);

        // 4. 拼接源码字符串
        StringBuilder sourceCode = buildSourceCode(packageName, resolverClassName, entityFullName, entitySimpleName,
                fieldMetaList, camelToUnderline);

        // 5. 输出java文件到编译目录
        try {
            JavaFileObject sourceFile = processingEnv.getFiler()
                    .createSourceFile(packageName + "." + resolverClassName);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(sourceCode.toString());
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "生成Resolver失败：" + e.getMessage(), entityElement);
        }
    }

    /**
     * 解析实体字段、setter、字段类型
     */
    private List<FieldMeta> parseEntityFields(TypeElement entityElement) {
        List<FieldMeta> metaList = new ArrayList<>();
        List<? extends Element> enclosedElements = entityElement.getEnclosedElements();

        for (Element element : enclosedElements) {
            // 只处理成员变量
            if (!(element instanceof VariableElement) || element.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) element;
            String fieldName = field.getSimpleName().toString();
            TypeMirror fieldType = field.asType();
            // 拼接setter方法名 setXxx
            String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

            // 查找对应setter方法
            ExecutableElement setter = findSetter(entityElement, setterName);
            if (setter == null) {
                // 无setter跳过，无法赋值
                continue;
            }
            metaList.add(new FieldMeta(fieldName, fieldType, setterName));
        }
        return metaList;
    }

    /**
     * 查找setter方法
     */
    private ExecutableElement findSetter(TypeElement entity, String setterName) {
        for (Element element : entity.getEnclosedElements()) {
            if (element instanceof ExecutableElement
                    && element.getKind() == ElementKind.METHOD
                    && element.getSimpleName().toString().equals(setterName)) {
                return (ExecutableElement) element;
            }
        }
        return null;
    }

    /**
     * 拼接完整Resolver类源码
     */
    private StringBuilder buildSourceCode(String pkg,
                                          String resolverClsName,
                                          String entityFullCls,
                                          String entitySimpleCls,
                                          List<FieldMeta> fieldMetas,
                                          boolean camelToUnderline) {
        StringBuilder sb = new StringBuilder();
        // 包名
        sb.append("package ").append(pkg).append(";\n\n");
        // import
        sb.append("import com.github.mbmll.sql.resolver.ResultSetResolver;\n");
        sb.append("import java.sql.ResultSet;\n");
        sb.append("import java.sql.ResultSetMetaData;\n");
        sb.append("import java.sql.SQLException;\n\n");

        // 类定义
        sb.append("public class ").append(resolverClsName)
                .append(" implements ResultSetResolver<").append(entitySimpleCls).append("> {\n\n");

        // 重写resolve方法
        sb.append("    @Override\n");
        sb.append("    public ").append(entitySimpleCls).append(" resolve(ResultSet rs) throws SQLException {\n");
        sb.append("        ResultSetMetaData metaData = rs.getMetaData();\n");
        sb.append("        int columnCount = metaData.getColumnCount();\n");
        sb.append("        ").append(entitySimpleCls).append(" entity = new ").append(entitySimpleCls).append("();\n");
        sb.append("        for (int i = 1; i <= columnCount; i++) {\n");
        sb.append("            String columnName = metaData.getColumnName(i);\n");
        sb.append("            switch (columnName) {\n");

        // 循环生成每个字段case分支
        for (FieldMeta meta : fieldMetas) {
            String column = camelToUnderline ? camel2Underline(meta.fieldName) : meta.fieldName;
            // 获取ResultSet取值方法
            String rsGetter = getResultSetGetter(meta.fieldType);
            sb.append("                case \"").append(column).append("\":\n");
            sb.append("                    entity.").append(meta.setterName).append("(rs.").append(rsGetter).append(
                    "(i));\n");
            sb.append("                    break;\n");
        }

        // default分支
        sb.append("                default:\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return entity;\n");
        sb.append("    }\n");
        sb.append("}");
        return sb;
    }

    /**
     * Java类型 → ResultSet取值方法映射
     */
    private String getResultSetGetter(TypeMirror type) {
        String typeName = type.toString();
        switch (typeName) {
            case "java.lang.String":
                return "getString";
            case "int":
            case "java.lang.Integer":
                return "getInt";
            case "boolean":
            case "java.lang.Boolean":
                return "getBoolean";
            case "long":
            case "java.lang.Long":
                return "getLong";
            case "double":
            case "java.lang.Double":
                return "getDouble";
            case "java.util.Date":
                return "getTimestamp";
            default:
                // 通用Object兜底
                return "getObject";
        }
    }

    /**
     * 驼峰转下划线 userName → user_name
     */
    private String camel2Underline(String str) {
        if (str == null || str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_").append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 字段元数据内部类
     */
    public static class FieldMeta {
        String fieldName;
        TypeMirror fieldType;
        String setterName;

        public FieldMeta(String fieldName, TypeMirror fieldType, String setterName) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.setterName = setterName;
        }
    }
}
