package com.github.mbmll.processor;

import com.github.mbmll.sql.annotation.AutoResultSetResolver;
import com.github.mbmll.sql.resolver.ResolverCodeGenerator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 *
 */
@SupportedAnnotationTypes("com.github.mbmll.sql.annotation.AutoResultSetResolver")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ResultSetResolverProcessor extends AbstractProcessor {

    private ResolverCodeGenerator codeGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.codeGenerator = new ResolverCodeGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // 遍历所有标记 @AutoResultSetResolver 的实体类
        roundEnv.getElementsAnnotatedWith(AutoResultSetResolver.class)
                .stream()
                .filter(element -> element instanceof TypeElement)
                .map(element -> (TypeElement) element)
                .forEach(entityType -> codeGenerator.generateResolver(entityType));
        return true;
    }
}
