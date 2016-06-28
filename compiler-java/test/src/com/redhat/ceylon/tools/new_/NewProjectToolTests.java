/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.tools.new_;

import java.io.File;
import java.io.FileInputStream;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.tool.CeylonBaseTool;
import com.redhat.ceylon.common.tool.ToolModel;
import com.redhat.ceylon.compiler.CeylonCompileTool;
import com.redhat.ceylon.tools.test.AbstractToolTests;

public class NewProjectToolTests extends AbstractToolTests {

    public NewProjectToolTests() {
        super();
    }

    private List<String> args(String... args) {
        List<String> ret = new ArrayList<String>(args.length+2);
        for(String s : args)
            ret.add(s);
        return ret;
    }
    
    private List<String> options(String... strings){
        List<String> ret = new ArrayList<String>(strings.length+2);
        ret.add("--sysrep");
        ret.add(getSysRepPath());
        for(String s : strings)
            ret.add(s);
        return ret;
    }
    
    private void delete(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        file.delete();
    }
    
    Map<String, String> setProperties(Map<String, String> props) {
        HashMap<String, String> oldMap = new HashMap<>(props.size());
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String oldValue = System.getProperty(entry.getKey());
            System.setProperty(entry.getKey(), entry.getValue());
            oldMap.put(entry.getKey(), oldValue);
        }
        return oldMap;
    }
    
    void restoreProperties(Map<String, String> oldMap) {
        Properties properties = System.getProperties();
        for (Map.Entry<String, String> entry : oldMap.entrySet()) {
            if (entry.getValue() == null) {
                properties.remove(entry.getKey());
            } else {
                properties.put(entry.getKey(), entry.getValue());
            }
        }
    }
    
    private void assertMatchInFile(File file, Pattern pattern) throws Exception {
        Charset charset = Charset.forName("UTF-8");
        
        FileInputStream stream = new FileInputStream(file);
        try  {
            FileChannel channel = stream.getChannel();
            try {
                MappedByteBuffer map = channel.map(MapMode.READ_ONLY, 0, channel.size());
                CharBuffer chars = charset.decode(map);
                Matcher matcher = pattern.matcher(chars);
                Assert.assertTrue(pattern+" not found in "+file, matcher.find());
            } finally {
                channel.close();
            }
        } finally {
            stream.close();
        }
    }
    
    private void assertFile(File file) {
        Assert.assertTrue(file + " should be a file", file.isFile());
    }
    
    private void assertDir(File dir) {
        Assert.assertTrue(dir + " should be a directory", dir.isDirectory());
    }
    
    private void assertNotExists(File file) {
        Assert.assertTrue(file + " should not exist", !file.exists());
    }

    private void runTool(CeylonBaseTool tool) throws Exception {
        // In an attempt for a little test isolation, we restore the system properties 
        // we set
        Map<String, String> oldMap = setProperties(Collections.singletonMap("ceylon.home", "foo"));
        try {
            tool.run();
        } finally {
            restoreProperties(oldMap);
        }
    }
    
    @Test
    public void testHelloWorld() throws Exception {
        ToolModel<CeylonNewTool> model = pluginLoader.loadToolModel("new");
        Assert.assertNotNull(model);
        Assert.assertTrue(model.isPorcelain());
        Path tmpPath = Files.createTempDirectory("ceylon-new-");
        File tmpDir = tmpPath.toFile();
        try {
            CeylonNewTool tool = pluginFactory.bindArguments(model, getMainTool(),
                    args("--from=../dist/templates", 
                            "hello-world",
                            "--module-name=org.example.hello",
                            "--module-version=1",
                            "--ant=true",
                            "--eclipse=true",
                            "--eclipse-project-name=hello",
                            tmpDir.getAbsolutePath()));
            runTool(tool);
            assertFile(new File(tmpDir, "source/org/example/hello/module.ceylon"));
            assertFile(new File(tmpDir, "source/org/example/hello/package.ceylon"));
            assertFile(new File(tmpDir, "source/org/example/hello/run.ceylon"));
            assertFile(new File(tmpDir, "build.xml"));
            assertFile(new File(tmpDir, ".classpath"));
            assertFile(new File(tmpDir, ".project"));
            assertDir(new File(tmpDir, ".settings"));
        } finally {
            delete(tmpDir);
        }
    }

    @Test
    public void testKeywordsInModuleName() throws Exception {
        ToolModel<CeylonNewTool> model = pluginLoader.loadToolModel("new");
        Assert.assertNotNull(model);
        Assert.assertTrue(model.isPorcelain());
        Path tmpPath = Files.createTempDirectory("ceylon-new-");
        File tmpDir = tmpPath.toFile();
        try {
            CeylonNewTool tool = pluginFactory.bindArguments(model, getMainTool(),
                    args("--from=../dist/templates", 
                            "hello-world",
                            "--module-name=long.module",
                            "--module-version=1",
                            "--ant=false",
                            "--eclipse=false",
                            tmpDir.getAbsolutePath()));
            runTool(tool);
            File moduleFile = new File(tmpDir, "source/long/module/module.ceylon");
            assertFile(moduleFile);
            File packageFile = new File(tmpDir, "source/long/module/package.ceylon");
            assertFile(packageFile);
            assertFile(new File(tmpDir, "source/long/module/run.ceylon"));
            Pattern pattern = Pattern.compile(Pattern.quote("long.\\imodule"));
            assertMatchInFile(moduleFile, pattern);
            assertMatchInFile(packageFile, pattern);
        } finally {
            delete(tmpDir);
        }
    }
    
    @Test
    public void testHelloWorldCwd() throws Exception {
        ToolModel<CeylonNewTool> model = pluginLoader.loadToolModel("new");
        Assert.assertNotNull(model);
        Assert.assertTrue(model.isPorcelain());
        Path tmpPath = Files.createTempDirectory("ceylon-new-");
        File tmpDir = tmpPath.toFile();
        
        File srcTemplates = new File("../dist/templates");
        File dstTemplates = new File(tmpDir, "templates");
        FileUtil.copyAll(srcTemplates, dstTemplates);
        
        try {
            CeylonNewTool tool = pluginFactory.bindArguments(model, getMainTool(),
                    args("--cwd", tmpDir.getAbsolutePath(),
                            "--from=templates", 
                            "hello-world",
                            "--module-name=org.example.hello",
                            "--module-version=1",
                            "--ant=true",
                            "--eclipse=true",
                            "--eclipse-project-name=hello",
                            "target"));
            runTool(tool);
            assertFile(new File(tmpDir, "target/source/org/example/hello/module.ceylon"));
            assertFile(new File(tmpDir, "target/source/org/example/hello/package.ceylon"));
            assertFile(new File(tmpDir, "target/source/org/example/hello/run.ceylon"));
            assertFile(new File(tmpDir, "target/build.xml"));
            assertFile(new File(tmpDir, "target/.classpath"));
            assertFile(new File(tmpDir, "target/.project"));
            assertDir(new File(tmpDir, "target/.settings"));
        } finally {
            delete(tmpDir);
        }
    }
    
    @Test
    public void testHelloWorldNoAntNoEclipse() throws Exception {
        ToolModel<CeylonNewTool> model = pluginLoader.loadToolModel("new");
        Assert.assertNotNull(model);
        Assert.assertTrue(model.isPorcelain());
        Path tmpPath = Files.createTempDirectory("ceylon-new-");
        File tmpDir = tmpPath.toFile();
        try {
            CeylonNewTool tool = pluginFactory.bindArguments(model, getMainTool(),
                    args("--from=../dist/templates", 
                            "hello-world",
                            "--module-name=org.example.hello",
                            "--module-version=1",
                            "--ant=false",
                            "--eclipse=false",
                            tmpDir.getAbsolutePath()));
            runTool(tool);
            assertFile(new File(tmpDir, "source/org/example/hello/module.ceylon"));
            assertFile(new File(tmpDir, "source/org/example/hello/package.ceylon"));
            assertFile(new File(tmpDir, "source/org/example/hello/run.ceylon"));
            assertNotExists(new File(tmpDir, "build.xml"));
            assertNotExists(new File(tmpDir, ".classpath"));
            assertNotExists(new File(tmpDir, ".project"));
            assertNotExists(new File(tmpDir, ".settings"));
        } finally {
            delete(tmpDir);
        }
    }
    
    @Test
    public void testHelloWorldNoAntNoEclipseCompiles() throws Exception {
        ToolModel<CeylonNewTool> newModel = pluginLoader.loadToolModel("new");
        Assert.assertNotNull(newModel);
        Assert.assertTrue(newModel.isPorcelain());
        Path tmpPath = Files.createTempDirectory("ceylon-new-");
        File tmpDir = tmpPath.toFile();
        try {
            CeylonNewTool newTool = pluginFactory.bindArguments(newModel, getMainTool(),
                    args("--from=../dist/templates", 
                            "hello-world",
                            "--module-name=org.example.hello",
                            "--module-version=1",
                            "--ant=false",
                            "--eclipse=false",
                            tmpDir.getAbsolutePath()));
            runTool(newTool);
            ToolModel<CeylonCompileTool> compileModel = pluginLoader.loadToolModel("compile");
            Assert.assertNotNull(compileModel);
            CeylonCompileTool compileTool = pluginFactory.bindArguments(compileModel, getMainTool(),
                    options("--src=" + tmpDir.getAbsolutePath() + "/source",
                            "--out=" + tmpDir.getAbsolutePath(),
                            "org.example.hello"));
            runTool(compileTool);
        } finally {
            delete(tmpDir);
        }
    }
}
