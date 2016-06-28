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
package com.redhat.ceylon.compiler.java.test.cargeneration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.Assume;
import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerError;
import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.ErrorCollector;
import com.redhat.ceylon.compiler.java.test.JdkVersionDependentTests;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.javax.tools.Diagnostic;

public class CarGenerationTests extends JdkVersionDependentTests {
    
    public CarGenerationTests(String target, String source) {
        super(target, source);
    }

    @Test
    public void testCarResourceSimple() throws IOException{
        Assume.assumeTrue("7".equals(target));
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/simple/source");
        options.add("-res");
        options.add(getPackagePath() + "resmodules/simple/resource");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("test.simple"));
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("test.simple", "1.0");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("test/simple/README.txt");
        assertNotNull(moduleClass);

        moduleClass = car.getEntry("test/simple/subdir/SUBDIR.txt");
        assertNotNull(moduleClass);

        moduleClass = car.getEntry("test/simple/$module_.class");
        assertNotNull(moduleClass);
        car.close();
    }
    
    @Test
    public void testCarResourceFiles() throws IOException{
        Assume.assumeTrue("7".equals(target));
        testCarResourceFilesSub(false);
        testCarResourceFilesSub(true);
    }
    
    private void testCarResourceFilesSub(boolean alternative) throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/files/source");
        options.add("-res");
        options.add(getPackagePath() + "resmodules/files/resource");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task;
        if (alternative) {
            task = getCompilerTask(options, 
//                "resmodules/files/source/test/files/module.ceylon",
                "resmodules/files/resource/test/files/extrafile");
        } else {
            task = getCompilerTask(options,
                "resmodules/files/source/test/files/module.ceylon",
                "resmodules/files/resource/test/files/README.txt");
        }
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("test.files", "1.0");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("test/files/README.txt");
        assertNotNull(moduleClass);

        moduleClass = car.getEntry("test/files/extrafile");
        if (alternative) {
            assertNotNull(moduleClass);
        } else {
            assertNull(moduleClass);
        }

        moduleClass = car.getEntry("test/files/$module_.class");
        assertNotNull(moduleClass);
        car.close();
    }
    
    @Test
    public void testCarResourceMultiple() throws IOException{
        Assume.assumeTrue("7".equals(target));
        assertEquals(testCarResourceMultipleSub(false), 40);
        assertEquals(testCarResourceMultipleSub(true), 108);
    }
    
    private long testCarResourceMultipleSub(boolean reverse) throws IOException{
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/multiple/source");
        if (reverse) {
            options.add("-res");
            options.add(getPackagePath() + "resmodules/multiple/resource2");
            options.add("-res");
            options.add(getPackagePath() + "resmodules/multiple/resource");
        } else {
            options.add("-res");
            options.add(getPackagePath() + "resmodules/multiple/resource");
            options.add("-res");
            options.add(getPackagePath() + "resmodules/multiple/resource2");
        }
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("test.multiple"));
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("test.multiple", "1.0");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("test/multiple/README.txt");
        long result = moduleClass.getSize();
        assertNotNull(moduleClass);

        moduleClass = car.getEntry("test/multiple/README2.txt");
        assertNotNull(moduleClass);

        moduleClass = car.getEntry("test/multiple/$module_.class");
        assertNotNull(moduleClass);
        car.close();
        
        return result;
    }

    @Test
    public void testCarResourceDefault() throws IOException{
        Assume.assumeTrue("7".equals(target));
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/default/source");
        options.add("-res");
        options.add(getPackagePath() + "resmodules/default/resource");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                "resmodules/default/resource/README.txt",
                "resmodules/default/resource/subdir/SUBDIR.txt");
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("default", null);
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry moduleClass = car.getEntry("README.txt");
        assertNotNull(moduleClass);
        
        moduleClass = car.getEntry("subdir/SUBDIR.txt");
        assertNotNull(moduleClass);
        car.close();
    }

    @Test
    public void testCarResourceRoot() throws IOException{
        Assume.assumeTrue("7".equals(target));
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/rootdir/source");
        options.add("-res");
        options.add(getPackagePath() + "resmodules/rootdir/resource");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("test.rootdir"));
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("test.rootdir", "1.0");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry carEntry = car.getEntry("test/rootdir/README.txt");
        assertNotNull(carEntry);

        carEntry = car.getEntry("rootfile");
        assertNotNull(carEntry);

        carEntry = car.getEntry("rootdir/rootsubdirfile");
        assertNotNull(carEntry);

        carEntry = car.getEntry("test/rootdir/$module_.class");
        assertNotNull(carEntry);
        car.close();
    }

    @Test
    public void testCarResourceAlternativeRoot() throws IOException{
        Assume.assumeTrue("7".equals(target));
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "resmodules/altrootdir/source");
        options.add("-res");
        options.add(getPackagePath() + "resmodules/altrootdir/resource");
        options.add("-resroot");
        options.add("ALTROOT");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                null,
                Arrays.asList("test.altrootdir"));
        Boolean ret = task.call();
        assertTrue(ret);
        
        File carFile = getModuleArchive("test.altrootdir", "1.0");
        assertTrue(carFile.exists());

        JarFile car = new JarFile(carFile);

        ZipEntry carEntry = car.getEntry("test/altrootdir/README.txt");
        assertNotNull(carEntry);

        carEntry = car.getEntry("rootfile");
        assertNotNull(carEntry);

        carEntry = car.getEntry("test/altrootdir/$module_.class");
        assertNotNull(carEntry);
        car.close();
    }
    
    /*
     * Although not in the JAR specification, there are tools and APIs
     * which rely on the MANIFEST.MF being the first file entry in a Jar file.
     * 
     * Test for the case of a compiler-generated MANIFEST.MF
     */
    @Test
    public void testCarGeneratedManifestComesFirst() throws IOException{
        Assume.assumeTrue("7".equals(target));
        ErrorCollector ec = new ErrorCollector();
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "meta/generatedmanifest/source");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                ec,
                Arrays.asList("test.generatedmanifest"));
        assertTrue(task.call());
        assertTrue(ec.get(Diagnostic.Kind.ERROR, Diagnostic.Kind.WARNING).isEmpty());
        File carFile = getModuleArchive("test.generatedmanifest", "1.0");
        assertTrue(carFile.exists());
        try (JarFile car = new JarFile(carFile)) {
            Manifest manifest = car.getManifest();
            assertTrue(manifest != null);
            assertEquals("test.generatedmanifest", manifest.getMainAttributes().getValue("Bundle-SymbolicName"));
            Enumeration<JarEntry> entries = car.entries();
            assertTrue(entries.hasMoreElements());
            JarEntry entry = entries.nextElement();
            assertEquals("META-INF/", entry.getName());
            assertTrue(entry.isDirectory());
            entry = entries.nextElement();
            assertEquals("META-INF/MANIFEST.MF", entry.getName());
            assertTrue(!entry.isDirectory());
        }
        
        // Now test incremental compilation
        task = getCompilerTask(options,
                ec,
                "meta/generatedmanifest/source/test/generatedmanifest/run.ceylon");
        assertTrue(task.call());
        assertTrue(carFile.exists());
        assertTrue(ec.get(Diagnostic.Kind.ERROR, Diagnostic.Kind.WARNING).isEmpty());
        try (JarFile car = new JarFile(carFile)) {
            Manifest manifest = car.getManifest();
            assertTrue(manifest != null);
            assertEquals("test.generatedmanifest", manifest.getMainAttributes().getValue("Bundle-SymbolicName"));
            Enumeration<JarEntry> entries = car.entries();
            assertTrue(entries.hasMoreElements());
            JarEntry entry = entries.nextElement();
            assertEquals("META-INF/", entry.getName());
            assertTrue(entry.isDirectory());
            entry = entries.nextElement();
            assertEquals("META-INF/MANIFEST.MF", entry.getName());
            assertTrue(!entry.isDirectory());
        }
    }
    
    /*
     * Although not in the JAR specification, there are tools and APIs
     * which rely on the MANIFEST.MF being the first file entry in a Jar file.
     * 
     * Test for the case of an existing MANIFEST.MF in resources
     */
    @Test
    public void testCarMergedManifestComesFirst() throws IOException{
        Assume.assumeTrue("7".equals(target));
        ErrorCollector ec = new ErrorCollector();
        List<String> options = new LinkedList<String>();
        options.add("-src");
        options.add(getPackagePath() + "meta/mergedmanifest/source");
        options.add("-res");
        options.add(getPackagePath() + "meta/mergedmanifest/resource");
        options.addAll(getDefaultOptions());
        CeyloncTaskImpl task = getCompilerTask(options, 
                ec,
                Arrays.asList("test.mergedmanifest"));
        assertTrue(task.call());
        
        File carFile = getModuleArchive("test.mergedmanifest", "1.0");
        assertTrue(carFile.exists());
        assertTrue(ec.get(Diagnostic.Kind.ERROR).isEmpty());
        // When the compiler value overrides a user value, we expect a warning
        assertTrue(ec.get(Diagnostic.Kind.WARNING).size() == 1);
        assertTrue(ec.get(Diagnostic.Kind.WARNING).iterator().next().equals(new CompilerError(Diagnostic.Kind.WARNING, null, -1, 
                "manifest attribute provided by compiler: ignoring value from 'Bundle-ActivationPolicy' for module 'test.mergedmanifest'")));
        try (JarFile car = new JarFile(carFile)) {
            Manifest manifest = car.getManifest();
            manifest.write(System.err);
            assertTrue(manifest != null);
            assertEquals("test.mergedmanifest", manifest.getMainAttributes().getValue("Bundle-SymbolicName"));
            assertEquals("Baz", manifest.getMainAttributes().getValue("Foo-Bar"));
            assertEquals("whatever", manifest.getMainAttributes().getValue("LastLineWithoutNewline"));
            Enumeration<JarEntry> entries = car.entries();
            assertTrue(entries.hasMoreElements());
            JarEntry entry = entries.nextElement();
            assertEquals("META-INF/", entry.getName());
            assertTrue(entry.isDirectory());
            entry = entries.nextElement();
            assertEquals("META-INF/MANIFEST.MF", entry.getName());
            assertTrue(!entry.isDirectory());
        }
    }
    
}
