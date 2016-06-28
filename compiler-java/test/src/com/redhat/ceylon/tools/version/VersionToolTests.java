package com.redhat.ceylon.tools.version;

import java.io.StringWriter;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.redhat.ceylon.common.tool.ToolFactory;
import com.redhat.ceylon.common.tool.ToolLoader;
import com.redhat.ceylon.common.tool.ToolModel;
import com.redhat.ceylon.common.tools.CeylonToolLoader;
import com.redhat.ceylon.tools.test.AbstractToolTests;

public class VersionToolTests extends AbstractToolTests {
    
    public VersionToolTests(String[] compilerArgs) {
        super(compilerArgs);
    }

    @Test
    public void testAll() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--src", "test/src/com/redhat/ceylon/tools/version/modules"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "bar/3.1\n"+
                "baz/1.2\n"+
                "foo/1.0\n", 
                normalizeLineEndings(out.toString()));
    }
    
    @Test
    public void testAllAndDeps() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--src", "test/src/com/redhat/ceylon/tools/version/modules",
                "--dependencies"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "bar/3.1\n"+
                "baz/1.2\n"+
                "foo/1.0\n", 
                normalizeLineEndings(out.toString()));
    }
    
    @Test
    public void testFoo() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--src", "test/src/com/redhat/ceylon/tools/version/modules", 
                "foo"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "foo/1.0\n", 
                normalizeLineEndings(out.toString()));
    }
    
    @Test
    public void testFooAndDeps() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--src", "test/src/com/redhat/ceylon/tools/version/modules",
                "--dependencies",
                "foo"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "foo/1.0\n" +
                "bar/3.1 depends on foo/1.0\n" +
                "baz/1.2 depends on foo/1.0\n", 
                normalizeLineEndings(out.toString()));
    }
    
    @Test
    public void testBarAndDeps() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--src", "test/src/com/redhat/ceylon/tools/version/modules",
                "--dependencies",
                "bar"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "bar/3.1\n" +
                "baz/1.2 depends on bar/3.1\n", 
                normalizeLineEndings(out.toString()));
    }
    
    @Test
    public void testAllCwd() throws Exception {
        ToolModel<CeylonVersionTool> model = pluginLoader.loadToolModel("version");
        CeylonVersionTool tool = pluginFactory.bindArguments(model, getMainTool(), Arrays.asList(
                "--cwd", "test",
                "--src", "src/com/redhat/ceylon/tools/version/modules"));
        StringWriter out = new StringWriter();
        tool.setOut(out);
        tool.run();
        Assert.assertEquals(
                "bar/3.1\n"+
                "baz/1.2\n"+
                "foo/1.0\n", 
                normalizeLineEndings(out.toString()));
    }
    
}
