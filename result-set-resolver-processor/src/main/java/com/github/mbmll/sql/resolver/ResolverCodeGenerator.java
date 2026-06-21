package com.github.mbmll.sql.resolver;


import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.util.*;

public class ResolverCodeGenerator {

    static final Set<String> columnTypes = new HashSet<>();

    static {
        columnTypes.add(Array.class.getName());
        columnTypes.add(InputStream.class.getName());
        columnTypes.add(BigDecimal.class.getName());
        columnTypes.add(Reader.class.getName());
        columnTypes.add(Blob.class.getName());
        columnTypes.add(Clob.class.getName());
        columnTypes.add(Date.class.getName());
        columnTypes.add(NClob.class.getName());
        columnTypes.add(String.class.getName());
        columnTypes.add(Object.class.getName());
        columnTypes.add(Ref.class.getName());
        columnTypes.add(RowId.class.getName());
        columnTypes.add(SQLXML.class.getName());
        columnTypes.add(Time.class.getName());
        columnTypes.add(Timestamp.class.getName());
        columnTypes.add(URL.class.getName());
        // primitive
        columnTypes.add(Boolean.class.getName());
        columnTypes.add(Byte.class.getName());
        columnTypes.add(Short.class.getName());
        columnTypes.add(Float.class.getName());
        columnTypes.add(Double.class.getName());
        columnTypes.add(Long.class.getName());
    }

    private final ProcessingEnvironment processingEnv;
    private final Elements elementUtil;
    private final Types typeUtil;

    public ResolverCodeGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elementUtil = processingEnv.getElementUtils();
        this.typeUtil = processingEnv.getTypeUtils();
    }

    /**
     * 生成实体对应的 Resolver 类
     */
    public void generateResolver(TypeElement entityElement) {
        // 获取实体基础信息
        String entitySimpleName = entityElement.getSimpleName().toString();
        String resolverClassName = entitySimpleName + "ResultSetResolver";
        String packageName = elementUtil.getPackageOf(entityElement).toString();
        String targetPackageName = getTargetPackageName(packageName);

        // 4. 拼接源码字符串
        StringBuilder sourceCode = buildSourceCode(entityElement, targetPackageName, resolverClassName);

        // 5. 输出java文件到编译目录
        try {
            String targetClassFullName = targetPackageName + "." + resolverClassName;
            write(targetClassFullName, sourceCode);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "生成Resolver失败：" + e.getMessage(), entityElement);
        }
    }

    /**
     * @param targetClassFullName
     * @param sourceCode
     *
     * @throws IOException
     */
    private void write(String targetClassFullName, StringBuilder sourceCode) throws IOException {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(targetClassFullName);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(sourceCode.toString());
        }
    }

    /**
     * 获取目标包名, 如 com.github.mbmll.sql.entity -> com.github.mbmll.sql.resolver
     *
     * @param packageName
     *
     * @return
     */
    private String getTargetPackageName(String packageName) {
        String[] split = packageName.split("\\.");
        split[split.length - 1] = "resolver";
        return String.join(".", split);
    }

    /**
     * 解析实体字段、setter、字段类型
     */
    private List<FieldMeta> parseEntityFields(TypeElement entityElement) {
        List<FieldMeta> metaList = new ArrayList<>();
        List<? extends Element> enclosedElements = entityElement.getEnclosedElements();

        for (Element element : enclosedElements) {
            // 只处理成员变量
            if (element instanceof VariableElement && (element.getKind() == ElementKind.FIELD)) {
                VariableElement field = (VariableElement) element;
                String fieldName = field.getSimpleName().toString();
                TypeMirror fieldType = field.asType();
                // 拼接setter方法名 setXxx
                String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

                // 查找对应setter方法
                ExecutableElement setter = findSetter(entityElement, setterName);
                if (setter != null) {
                    metaList.add(new FieldMeta(fieldName, fieldType, setterName));
                }
            }
        }
        return metaList;
    }

    /**
     * @param entitySimpleCls
     * @param fieldMeta
     *
     * @return
     */
    private String buildSetter(String entitySimpleCls, FieldMeta fieldMeta) {
        String removed = StringUtils.isEmpty(fieldMeta.rsGetter) ? "//" : "";
        return "    protected void " + fieldMeta.setterName + "(" + entitySimpleCls + " target, ResultSet rs, int i) " +
                "throws SQLException {\n" +
                "        " + removed + " target." + fieldMeta.setterName + "(rs." + fieldMeta.rsGetter + "(i));\n" +
                "    }\n\n";
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
    private StringBuilder buildSourceCode(TypeElement entityElement, String targetPackageName,
                                          String resolverClsName) {
        String entitySimpleCls = entityElement.getSimpleName().toString();
        // 解析实体所有字段 + setter映射
        List<FieldMeta> fieldMetas = parseEntityFields(entityElement);
        StringBuilder sb = new StringBuilder();
        // 包名
        sb.append(buildPackage(targetPackageName));
        // import
        List<String> imports = new ArrayList<>();
        imports.add(entityElement.getQualifiedName().toString());
        imports.addAll(collectImports());
        sb.append(buildImport(imports));

        // 类定义
        sb.append("public class ").append(resolverClsName).append(" implements ResultSetResolver<").append(entitySimpleCls).append("> {\n\n");

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
            sb.append("                case \"").append(meta.column).append("\":\n");
            sb.append("                    ").append(meta.setterName).append("(entity,rs,i);\n");
            sb.append("                    break;\n");
        }

        // default分支
        sb.append("                default:\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return entity;\n");
        sb.append("    }\n");
        for (FieldMeta fieldMeta : fieldMetas) {
            sb.append(buildSetter(entitySimpleCls, fieldMeta));
        }
        sb.append("}");
        return sb;
    }

    private Collection<String> collectImports() {
        List<String> imports = new ArrayList<>();
        imports.add("com.github.mbmll.sql.resolver.ResultSetResolver");
        imports.add("java.sql.ResultSet");
        imports.add("java.sql.ResultSetMetaData");
        imports.add("java.sql.SQLException");
        return imports;
    }

    /**
     * 生成 import
     *
     * @param imports
     *
     * @return
     */
    private String buildImport(List<String> imports) {
        StringBuilder sb = new StringBuilder();
        for (String clazz : imports) {
            sb.append("import ").append(clazz).append(";\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String buildPackage(String targetPackageName) {
        // 转成 字符串拼接模式
        return "package " + targetPackageName + ";\n\n";
    }


    /**
     * Java类型 → ResultSet取值方法映射
     */
    private static String getResultSetGetter(TypeMirror type) {
        String typeName = type.toString();
        System.out.println(typeName);
        System.out.println(columnTypes);
        if (columnTypes.contains(typeName)) {
            try {
                return "get" + Class.forName(typeName).getSimpleName();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        switch (typeName) {
            case "boolean":
                return "getBoolean";
            case "byte":
                return "getByte";
            case "short":
                return "getShort";
//            case "char":
//            case "java.lang.Charater":
//                return "getChar";
            case "int":
            case "java.lang.Integer":
                return "getInt";
            case "float":
                return "getFloat";
            case "double":
                return "getDouble";
            case "long":
                return "getLong";
            case "byte[]":
                return "getBytes";
            default:
                return null;
        }
    }

    /**
     * 驼峰转下划线 userName → user_name
     */
    private static String camel2Underline(String str) {
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
        String column;
        // 获取ResultSet取值方法
        String rsGetter;

        public FieldMeta(String fieldName, TypeMirror fieldType, String setterName) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.setterName = setterName;
            column = camel2Underline(fieldName);
            // 获取ResultSet取值方法
            rsGetter = getResultSetGetter(fieldType);
        }
    }
}
