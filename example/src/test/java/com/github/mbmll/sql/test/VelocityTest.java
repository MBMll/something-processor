package com.github.mbmll.sql.test;


import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.Test;

import java.io.StringWriter;
import java.util.Properties;

/**
 * @Author xlc
 * @Description
 * @Date 2026/6/20 18:30
 */

public class VelocityTest {
    @Test
    public void test() {
        Properties props = new Properties();
        props.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        props.setProperty("classpath.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        VelocityEngine engine = new VelocityEngine();
        engine.init(props);

        VelocityContext context = new VelocityContext();
        context.put("cal", null);
        context.put("datatypeFactory", null);

        Template template = engine.getTemplate("CalendarToXmlGregorianCalendar.vm");
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        System.out.println(writer.toString());
    }
}
