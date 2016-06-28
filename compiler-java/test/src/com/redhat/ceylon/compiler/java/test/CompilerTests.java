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
package com.redhat.ceylon.compiler.java.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.redhat.ceylon.cmr.impl.NodeUtils;
import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.ModuleSpec;
import com.redhat.ceylon.common.Versions;
import com.redhat.ceylon.compiler.java.codegen.AbstractTransformer;
import com.redhat.ceylon.compiler.java.codegen.JavaPositionsRetriever;
import com.redhat.ceylon.compiler.java.launcher.Main;
import com.redhat.ceylon.compiler.java.launcher.Main.ExitState;
import com.redhat.ceylon.compiler.java.launcher.Main.ExitState.CeylonState;
import com.redhat.ceylon.compiler.java.runtime.metamodel.Metamodel;
import com.redhat.ceylon.compiler.java.tools.CeyloncFileManager;
import com.redhat.ceylon.compiler.java.tools.CeyloncTaskImpl;
import com.redhat.ceylon.compiler.java.tools.CeyloncTool;
import com.redhat.ceylon.compiler.java.util.RepositoryLister;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.javax.tools.Diagnostic;
import com.redhat.ceylon.javax.tools.DiagnosticListener;
import com.redhat.ceylon.javax.tools.FileObject;
import com.redhat.ceylon.javax.tools.JavaFileObject;
import com.redhat.ceylon.langtools.source.util.TaskEvent;
import com.redhat.ceylon.langtools.source.util.TaskEvent.Kind;
import com.redhat.ceylon.langtools.source.util.TaskListener;
import com.redhat.ceylon.langtools.tools.javac.tree.JCTree.JCCompilationUnit;
import com.redhat.ceylon.launcher.Launcher;
import com.redhat.ceylon.model.cmr.ArtifactResult;
import com.redhat.ceylon.model.cmr.ArtifactResultType;
import com.redhat.ceylon.model.cmr.ImportType;
import com.redhat.ceylon.model.cmr.JDKUtils;
import com.redhat.ceylon.model.cmr.JDKUtils.JDK;
import com.redhat.ceylon.model.cmr.PathFilter;
import com.redhat.ceylon.model.cmr.Repository;
import com.redhat.ceylon.model.cmr.RepositoryException;
import com.redhat.ceylon.model.cmr.VisibilityType;
import com.redhat.ceylon.model.loader.AbstractModelLoader;
import com.redhat.ceylon.model.loader.JvmBackendUtil;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.tools.jigsaw.CeylonJigsawTool;

@RunWith(Parameterized.class)
public abstract class CompilerTests {

    @Parameters
    public static Iterable<Object[]> testParameters() {
        return Arrays.asList(
                new Object[]{new String[]{"-target", "7", "-source", "7"}}, 
                new Object[]{new String[]{"-target", "8", "-source", "8"}});
    }
    
    protected final static String dir = "test/src";
    protected final static String destDirGeneral = "build/test-cars";
    protected final static String cacheDirGeneral = "build/test-cache";
    public static final String LANGUAGE_MODULE_CAR = "../language/ide-dist/ceylon.language-"+TypeChecker.LANGUAGE_MODULE_VERSION+".car";
    protected final String destDir;
    protected final String cacheDir;
    protected final String moduleName;
    protected final List<String> defaultOptions;
    protected final boolean eight;

    private static final String jbmv = Versions.DEPENDENCY_JBOSS_MODULES_VERSION;
    
    public static final String[] CLASS_PATH = new String[] {
        "../spec/bin",
        "./build/classes",
        "./../model/build/classes",
        "./../cmr/build/classes",
        "./../common/build/classes",
        "./../runtime/build/classes",
        "./../runtime/dist/repo/org/jboss/modules/" + jbmv + "/org.jboss.modules-" + jbmv + ".jar",
        "./../runtime/dist/repo/org/slf4j/api/1.6.1/org.slf4j.api-1.6.1.jar",
        LANGUAGE_MODULE_CAR,
    };

    public static final String CLASS_PATH_AS_PATH;
    static{
        StringBuilder b = new StringBuilder();
        for(int i=0;i<CLASS_PATH.length;i++){
            if(i > 0)
                b.append(File.pathSeparator);
            b.append(CLASS_PATH[i]);
        }
        CLASS_PATH_AS_PATH = b.toString();
    }
    
    /**
     * See run() for why this is needed.
     */
    public static final Object RUN_LOCK = new Object();

    // We use Java assertions in our tests, so if they are not enabled
    // we're not really testing anything in those cases.
    static {
        boolean assertsEnabled = false;
        assert assertsEnabled = true; // Intentional side effect!!!
        if (!assertsEnabled) {
            throw new RuntimeException("Assertions have not been be enabled! Add the `-ea` option to the compiler arguments");
        }
    } 

    public CompilerTests(String[] compilerArgs) {
        // for comparing with java source
        Package pakage = getClass().getPackage();
        moduleName = pakage.getName();
        boolean e = false;
        for (int ii = 0; ii < compilerArgs.length-1; ii++) {
            if (compilerArgs[ii].equals("-target")
                    && compilerArgs[ii+1].equals("8")) {
                e = true;
            }
        }
        eight = e;
        
        
        int lastDot = moduleName.lastIndexOf('.');
        if(lastDot == -1){
            destDir = destDirGeneral + File.separator + transformDestDir(moduleName);
        } else {
            destDir = destDirGeneral + File.separator + transformDestDir(moduleName.substring(lastDot+1));
        }
        if(lastDot == -1){
            cacheDir = cacheDirGeneral + File.separator + transformDestDir(moduleName);
        } else {
            cacheDir = cacheDirGeneral + File.separator + transformDestDir(moduleName.substring(lastDot+1));
        }
        defaultOptions = new ArrayList<String>(Arrays.asList(
                "-out", destDir,
                "-cacherep", cacheDir,
                "-g", 
                "-cp", getClassPathAsPath(),
                //"-target", "8",
                "-suppress-warnings", "compilerAnnotation"));
        defaultOptions.addAll(Arrays.asList(compilerArgs));
    }

    public static String getClassPathAsPath() {
        return CLASS_PATH_AS_PATH;
    }

    public static String[] getClassPath() {
        return CLASS_PATH;
    }
    
    public static String getClassPathAsPathExcludingLanguageModule() {
        StringBuilder b = new StringBuilder();
        for(int i=0;i<CLASS_PATH.length;i++){
            if(i > 0)
                b.append(File.pathSeparator);
            if (!CLASS_PATH[i].equals(LANGUAGE_MODULE_CAR)) {
                b.append(CLASS_PATH[i]);
            }
        }
        return b.toString();
    }

    // for subclassers 
    protected String transformDestDir(String name) {
        return name;
    }

    protected CeyloncTool makeCompiler(){
        try {
            return new CeyloncTool();
        } catch (VerifyError e) {
            System.err.println("ERROR: Cannot run tests! Did you maybe forget to configure the -Xbootclasspath/p: parameter?");
            throw e;
        }
    }

    protected String getPackagePath() {
        Package pakage = getClass().getPackage();
        String pkg = pakage == null ? "" : moduleName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        return dir + File.separator + pkg + File.separator;
    }
    
    protected CeyloncFileManager makeFileManager(CeyloncTool compiler, DiagnosticListener<? super FileObject> diagnosticListener){
        return (CeyloncFileManager)compiler.getStandardFileManager(diagnosticListener, null, null);
	}
	
	protected void compareWithJavaSource(String name) {
	    String src = name+".src";
	    if (eight) {
	        src = name+".src8";
	    } else {
	        src = name+".src";
	    }
		compareWithJavaSource(src, name+".ceylon");
	}
    
    /*List<String> optsForJava8Interfaces() {
        ArrayList<String> opts = new ArrayList<String>(defaultOptions);
        opts.add("-target");
        opts.add("8");
        opts.add("-source");
        opts.add("8");
        return opts;
    }
    
    //@Override
    public void  compareWithJavaSource(String source) {
        compareWithJavaSource(optsForJava8Interfaces(), source+".src", source+".ceylon");
    }*/
	
    protected void compareWithJavaSourceNoOpt(String name) {
        List<String> options = new ArrayList<String>();
        options.add("-disableOptimization");
        for (String option : defaultOptions) {
            options.add(option);
        }
        compareWithJavaSource(options, name+".noopt.src", name+".ceylon");
    }

	@Before
	public void cleanCars() {
	    cleanCars(destDir);
	    // Stef: this would be very nice and all, except it makes the build take forever, so let's
	    // please have none of this correctness nonsense
//	    cleanCars(cacheDir);
	}
	
    public void cleanCars(String repo) {
        File destFile = new File(repo);
        List<String> extensionsToDelete = Arrays.asList("");
        new RepositoryLister(extensionsToDelete).list(destFile, new RepositoryLister.Actions() {
            @Override
            public void doWithFile(File path) {
                path.delete();
            }

            public void exitDirectory(File path) {
                if (path.list().length == 0) {
                    path.delete();
                }
            }
        });
    }

    protected void assertErrors(String ceylon, CompilerError... expectedErrors) {
        assertErrors(ceylon, defaultOptions, null, false, expectedErrors);
    }
    
    protected void assertErrors(String ceylon, boolean includeWarnings, CompilerError... expectedErrors) {
        assertErrors(ceylon, defaultOptions, null, includeWarnings, expectedErrors);
    }
    
    protected void assertErrors(String ceylon, Throwable expectedException, CompilerError... expectedErrors) {
        assertErrors(ceylon, defaultOptions, expectedException, false, expectedErrors);
    }
    
    protected void assertErrors(String ceylon, List<String> options, Throwable expectedException, boolean includeWarnings, CompilerError... expectedErrors) {
        assertErrors(new String[] {ceylon+".ceylon"}, options, expectedException, includeWarnings, expectedErrors);
    }
    
    protected void assertErrors(String ceylon, List<String> options, Throwable expectedException, CompilerError... expectedErrors) {
        assertErrors(new String[] {ceylon+".ceylon"}, options, expectedException, false, expectedErrors);
    }
    
    protected void assertErrors(String[] ceylonFiles, 
            List<String> options, 
            Throwable expectedException, 
            CompilerError... expectedErrors) {
        assertErrors(ceylonFiles, options, expectedException, false, expectedErrors);
    }
    
    protected void assertErrors(String[] ceylonFiles, 
            List<String> options, 
            Throwable expectedException, 
            boolean includeWarnings, 
            CompilerError... expectedErrors) {
        boolean expectedToFail = false;
        Diagnostic.Kind lowestErrorLevel = includeWarnings ? Diagnostic.Kind.WARNING : Diagnostic.Kind.ERROR;
        for (CompilerError expectedError : expectedErrors) {
            if (expectedError.kind == Diagnostic.Kind.ERROR) {
                expectedToFail = true;
            }
            // if the current lowest (say, ERROR), is smaller than the expected kind (say, WARNING), then
            // we are interested in WARNING + ERROR kinds
            if(lowestErrorLevel.compareTo(expectedError.kind) == -1)
                lowestErrorLevel = expectedError.kind;
        }
        EnumSet<Diagnostic.Kind> kinds = EnumSet.range(Diagnostic.Kind.ERROR, lowestErrorLevel);
        ErrorCollector collector = compileErrorTest(ceylonFiles, options, expectedException, includeWarnings, expectedToFail);
        Set<CompilerError> actualErrors = collector.get(kinds);
        compareErrors(actualErrors, expectedErrors);
    }
    
    protected ErrorCollector compileErrorTest(String[] ceylonFiles, 
            List<String> options, 
            Throwable expectedException, 
            boolean includeWarnings, 
            boolean expectedToFail) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        ErrorCollector collector = new ErrorCollector();
        
        CeyloncTaskImpl task = getCompilerTask(options, collector, ceylonFiles);

        // now compile it all the way
        Throwable ex = null;
        ExitState exitState = task.call2();
        switch (exitState.ceylonState) {
        case OK:
            if (expectedToFail) {
                Assert.fail("Compilation successful (it should have failed!)");
            }
            break;
        case BUG:
            if (expectedException == null) {
                throw new AssertionError("Compiler bug", exitState.abortingException);
            }
            ex = exitState.abortingException;
            break;
        case ERROR:
            if (!expectedToFail) {
                Assert.fail("Compilation failed (it should have been successful!)");
            }
            break;
        case SYS:
            Assert.fail("System error");
            break;
        default:
            Assert.fail("Unknown exit state");
        }

        if (ex != null) {
            if (expectedException == null) {
                throw new AssertionError("Compilation terminated with unexpected error", ex);
            } else if (expectedException.getClass() != ex.getClass() || !eq(expectedException.getMessage(), ex.getMessage())) {
                throw new AssertionError("Compilation terminated with a different error than the expected " + expectedException, ex);
            }
        } else if (expectedException != null) {
            Assert.fail("Expected compiler exception " + expectedException);
        }
        
        return collector;
    }
    
    private boolean eq(Object o1, Object o2) {
        return (o1 == o2) || o1.equals(o2);
    }
    
    protected void compareErrors(Set<CompilerError> actualErrors, CompilerError... expectedErrors) {
        TreeSet<CompilerError> expectedErrorSet = new TreeSet<CompilerError>(Arrays.asList(expectedErrors));
        // first dump the actual errors
        for(CompilerError actualError : actualErrors){
            System.err.println(actualError.lineNumber+": "+actualError.message);
        }
        
        // make sure we have all those we expect
        ArrayList<CompilerError> expected = new ArrayList<CompilerError>();
        for(CompilerError expectedError : expectedErrorSet){
            if (!actualErrors.contains(expectedError)) {
                expected.add(expectedError);
            }
        }
        //  make sure we don't have unexpected ones
        ArrayList<CompilerError> unexpected = new ArrayList<CompilerError>();
        for(CompilerError actualError : actualErrors){
            if (!expectedErrorSet.contains(actualError)) {
                unexpected.add(actualError);
            }
        }
        if (!expected.isEmpty() || !unexpected.isEmpty()) {
            StringBuffer txt = new StringBuffer();
            if (!expected.isEmpty()) {
                txt.append("Missing expected error(s):\n");
                for (CompilerError err : expected) {
                    txt.append(" - ");
                    txt.append(err);
                    txt.append("\n");
                }
            }
            if (!unexpected.isEmpty()) {
                txt.append("Unexpected error(s):\n");
                for (CompilerError err : unexpected) {
                    txt.append(" - ");
                    txt.append(err);
                    txt.append("\n");
                }
            }
            Assert.fail(txt.toString());
        }
    }
    
    public interface ExpectedError {
        boolean expected(CompilerError e);
    }
    
    protected void compareErrors(TreeSet<CompilerError> actualErrors, ExpectedError expectedErrors) {
        for(CompilerError actualError : actualErrors){
            System.err.println(actualError.lineNumber+": "+actualError.message);
        }
        
        //  make sure we don't have unexpected ones
        for(CompilerError actualError : actualErrors){
            if (!expectedErrors.expected(actualError)) {
                Assert.fail("Unexpected error: "+actualError);
            }
        }
    }
    
    protected void compileIgnoringErrors(ExpectedError expectedErrors, String... ceylon) {
        ErrorCollector c = new ErrorCollector();
        ExitState exitState = getCompilerTask(c, ceylon).call2();
        assert(exitState.javacExitCode.exitCode == Main.EXIT_ERROR);
        assert(exitState.ceylonState == CeylonState.ERROR);
        TreeSet<CompilerError> actualErrors = c.get(Diagnostic.Kind.ERROR);
        compareErrors(actualErrors, 
                expectedErrors
        );
    }
    
    protected void compareWithJavaSourceWithPositions(String name) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        CeyloncTaskImpl task = getCompilerTask(new String[] {name+".ceylon"});

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            private JavaPositionsRetriever javaPositionsRetriever = new JavaPositionsRetriever();

            @Override
            public void started(TaskEvent e) {
                AbstractTransformer.trackNodePositions(javaPositionsRetriever);
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        javaPositionsRetriever.retrieve(compilationUnit);
                        compilerSrc = normalizeLineEndings(javaPositionsRetriever.getJavaSourceCodeWithCeylonPositions());
                        AbstractTransformer.trackNodePositions(null);
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        ExitState exitState = task.call2();
        Assert.assertEquals("Compilation failed", CeylonState.OK, exitState.ceylonState);

        // now look at what we expected
        String expectedSrc = normalizeLineEndings(readFile(new File(getPackagePath(), name+".src"))).trim();
        String compiledSrc = listener.compilerSrc.trim();
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    protected void compareWithJavaSourceWithLines(String name) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        CeyloncTaskImpl task = getCompilerTask(new String[] {name+".ceylon"});

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            private JavaPositionsRetriever javaPositionsRetriever = new JavaPositionsRetriever();

            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        javaPositionsRetriever.retrieve(compilationUnit);
                        compilerSrc = normalizeLineEndings(javaPositionsRetriever.getJavaSourceCodeWithCeylonLines());
                        AbstractTransformer.trackNodePositions(null);
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        ExitState exitState = task.call2();
        Assert.assertEquals("Compilation failed", exitState.ceylonState, CeylonState.OK);

        // now look at what we expected
        String expectedSrc = normalizeLineEndings(readFile(new File(getPackagePath(), name+".src"))).trim();
        String compiledSrc = listener.compilerSrc.trim();
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    protected void compareWithJavaSource(String java, String... ceylon) {
        compareWithJavaSource(defaultOptions, java, ceylon);
    }
    
    protected void compareWithJavaSource(List<String> options, String java, String... ceylon) {
        // make a compiler task
        // FIXME: runFileManager.setSourcePath(dir);
        ErrorCollector collector = new ErrorCollector();
        CeyloncTaskImpl task = getCompilerTask(options, collector, ceylon);

        // grab the CU after we've completed it
        class Listener implements TaskListener{
            JCCompilationUnit compilationUnit;
            private String compilerSrc;
            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if(e.getKind() == Kind.ENTER){
                    if(compilationUnit == null) {
                        compilationUnit = (JCCompilationUnit) e.getCompilationUnit();
                        // for some reason compilationUnit is full here in the listener, but empty as soon
                        // as the compile task is done. probably to clean up for the gc?
                        compilerSrc = normalizeLineEndings(compilationUnit.toString());
                    }
                }
            }
        }
        Listener listener = new Listener();
        task.setTaskListener(listener);

        // now compile it all the way
        assertCompilesOk(collector, task.call2());
        
        // now look at what we expected
        File expectedSrcFile = new File(getPackagePath(), java);
        String expectedSrc = normalizeLineEndings(readFile(expectedSrcFile)).trim();
        String compiledSrc = listener.compilerSrc.trim();
        
        // THIS IS FOR INTERNAL USE ONLY!!!
        // Can be used to do batch updating of known correct tests
        // Uncomment only when you know what you're doing!
//        if (expectedSrc != null && compiledSrc != null && !expectedSrc.equals(compiledSrc)) {
//            writeFile(expectedSrcFile, compiledSrc);
//            expectedSrc = compiledSrc;
//        }
        
        Assert.assertEquals("Source code differs", expectedSrc, compiledSrc);
    }

    protected void assertCompilesOk(ErrorCollector collector, ExitState exitState)
            throws AssertionError {
        switch (exitState.ceylonState) {
        case OK:
            break;
        case BUG:
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            if (exitState.abortingException != null) {
                pw.println();
                pw.println("With Javac error");
                exitState.abortingException.printStackTrace(pw);
            }
            pw.flush();
            Assert.fail(collector.getAssertionFailureMessage() + sw.toString());
            break;
        case ERROR:
            Assert.fail(collector.getAssertionFailureMessage());
            break;
        case SYS:
            Assert.fail("System error");
            break;
        default:
            Assert.fail("Unknown exit state");
        }
    }
    


    protected String readFile(File file) {
        try{
            Reader reader = new FileReader(file);
            StringBuilder strbuf = new StringBuilder();
            try{
                char[] buf = new char[1024];
                int read;
                while((read = reader.read(buf)) > -1)
                    strbuf.append(buf, 0, read);
            }finally{
                reader.close();
            }
            return strbuf.toString();
        }catch(IOException x){
            throw new RuntimeException(x);
        }
    }

    // THIS IS FOR INTERNAL USE ONLY!!!
    private void writeFile(File file, String src) {
        try{
            Writer writer = new FileWriter(file);
            try{
                writer.write(src);
            }finally{
                writer.close();
            }
        }catch(IOException x){
            throw new RuntimeException(x);
        }
    }

    protected String normalizeLineEndings(String txt) {
        String result = txt.replaceAll("\r\n", "\n"); // Windows
        result = result.replaceAll("\r", "\n"); // Mac (OS<=9)
        return result;
    }

    protected void compile(List<String> options, String... ceylon) {
        ErrorCollector c = new ErrorCollector();
        assertCompilesOk(c, getCompilerTask(options, c, ceylon).call2());
    }
    
    protected void compile(String... ceylon) {
        compile(defaultOptions, ceylon);
    }

    protected void compilesWithoutWarnings(String... ceylon) {
        compilesWithoutWarnings(defaultOptions, ceylon);
    }

    protected void compilesWithoutWarnings(List<String> options, String... ceylon) {
        ErrorCollector dl = new ErrorCollector();
        ExitState exitState = getCompilerTask(options, dl, ceylon).call2();
        Assert.assertEquals(exitState.ceylonState, CeylonState.OK);
        Assert.assertEquals("The code compiled with javac warnings", 
                0, dl.get(Diagnostic.Kind.WARNING).size() + dl.get(Diagnostic.Kind.MANDATORY_WARNING).size());
    }

    protected Object compileAndRun(List<String> options, String main, String... ceylon) {
        compile(options, ceylon);
        return run(main);
    }
    
    protected Object compileAndRun(String main, String... ceylon) {
        return compileAndRun(defaultOptions, main, ceylon);
    }

    protected Object run(String main) {
        return run(main, getDestModuleWithArtifact(main));
    }
    
    public class ModuleWithArtifact {
        private String module;
        private String version;
        private File file;

        public ModuleWithArtifact(String module, String version) {
            this(module, version, destDir, "car");
        }

        public ModuleWithArtifact(String module, String version, String repo, String extension) {
            this.module = module;
            this.version = version;
            this.file = getModuleArchive(module,version, repo, extension);
        }
    }
    
    public static ArtifactResult makeArtifactResult(final File file) {
        return new ArtifactResult() {
            
            @Override
            public VisibilityType visibilityType() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String version() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public ArtifactResultType type() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String namespace() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String name() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public ImportType importType() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public List<ArtifactResult> dependencies() throws RepositoryException {
                return Collections.emptyList();
            }
            
            @Override
            public File artifact() throws RepositoryException {
                return file;
            }

            @Override
            public String repositoryDisplayString() {
                return NodeUtils.UNKNOWN_REPOSITORY;
            }

            @Override
            public PathFilter filter() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Repository repository() {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    protected Class<?> loadClass(String className, ModuleWithArtifact... modules) {
        synchronized(RUN_LOCK){
            // the module initialiser code needs to run in a protected section because the language module Util is not loaded by
            // the test classloader but by our own classloader, which may be shared with other tests running in parallel, so if
            // we set up the module system while another thread is setting it up for other modules we're toast
            try{
                // make sure we load the stuff from the Car
                URLClassLoader loader = getClassLoader(className, modules);
                
                java.lang.Class<?> klass = java.lang.Class.forName(className, true, loader);
                
                return klass;
            }catch(Exception x){
                throw new RuntimeException(x);
            }
        }
    }
    
    
    protected Object run(String main, ModuleWithArtifact... modules) {
        return run(main, new Class<?>[]{}, new Object[]{}, modules);
    }
    
    protected Object run(String main, Class<?>[] sig, Object[] args, ModuleWithArtifact... modules) {
        synchronized(RUN_LOCK){
            // the module initialiser code needs to run in a protected section because the language module Util is not loaded by
            // the test classloader but by our own classloader, which may be shared with other tests running in parallel, so if
            // we set up the module system while another thread is setting it up for other modules we're toast
            Object result = null;
            URLClassLoader loader = null;
            try{
                // make sure we load the stuff from the Car

                loader = getClassLoader(main, modules);
                String mainClass = main;
                String mainMethod = main.replaceAll("^.*\\.", "");
                if (JvmBackendUtil.isInitialLowerCase(mainMethod)) {
                    mainClass = main + "_";
                }
                java.lang.Class<?> klass = java.lang.Class.forName(mainClass, true, loader);
                if (JvmBackendUtil.isInitialLowerCase(mainMethod)) {
                    // A method, we need to quote the name though
                    if ("main".equals(mainMethod)
                            || "finalize".equals(mainMethod)) {
                        mainMethod = "$" + mainMethod;
                    }
                    Method m = klass.getDeclaredMethod(mainMethod, sig);
                    m.setAccessible(true);
                    result = m.invoke(null, args);
                } else {
                    // A main class
                    final Constructor<?> ctor = klass.getDeclaredConstructor(sig);
                    ctor.setAccessible(true);
                    result = ctor.newInstance(args);
                }
                return result;
            }catch(InvocationTargetException x){
                throw new RuntimeException(x.getTargetException());
            }catch(Exception x){
                throw new RuntimeException(x);
            } finally {
                if (loader != null) {
                    try {
                        loader.close();
                    } catch (IOException e) {
                        // ignore
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    protected URLClassLoader getClassLoader(String main,
            ModuleWithArtifact... modules) throws MalformedURLException {
        List<URL> urls = getClassPathAsUrls(modules);
        System.err.println("Running " + main +" with classpath" + urls);
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        // set up the runtime module system
        Metamodel.resetModuleManager();
        Metamodel.loadModule(AbstractModelLoader.CEYLON_LANGUAGE, TypeChecker.LANGUAGE_MODULE_VERSION, makeArtifactResult(new File(LANGUAGE_MODULE_CAR)), loader);
        for (ModuleWithArtifact module : modules) {
            Metamodel.loadModule(module.module, module.version, makeArtifactResult(module.file), loader);
        }
        return loader;
    }
    
    @SuppressWarnings("deprecation")
    protected List<URL> getClassPathAsUrls(ModuleWithArtifact... modules)
            throws MalformedURLException {
        List<File> files = getClassPathAsFiles(modules);
        List<URL> urls = new ArrayList<URL>(files.size());
        for (File file : files) {
            urls.add(file.toURL());
        }
        return urls;
    }
    
    protected List<File> getClassPathAsFiles(ModuleWithArtifact... modules) {
        List<File> files = new ArrayList<File>(modules.length);
        for (ModuleWithArtifact module : modules) {
            File car = module.file;
            Assert.assertTrue(car + " does not exist", car.exists());
            files.add(car);
        }
        return files;
    }

    protected CeyloncTaskImpl getCompilerTask(String... sourcePaths){
        return getCompilerTask(defaultOptions, null, sourcePaths);
    }
    
    protected CeyloncTaskImpl getCompilerTask(DiagnosticListener<? super FileObject> diagnosticListener, String... sourcePaths){
        return getCompilerTask(defaultOptions, diagnosticListener, sourcePaths);
    }

    protected CeyloncTaskImpl getCompilerTask(List<String> options, String... sourcePaths){
        return getCompilerTask(options, null, sourcePaths);
    }

    protected CeyloncTaskImpl getCompilerTask(List<String> options, DiagnosticListener<? super FileObject> diagnosticListener, 
            String... sourcePaths){
        return getCompilerTask(options, diagnosticListener, null, sourcePaths);
    }
    
    protected CeyloncTaskImpl getCompilerTask(List<String> initialOptions, DiagnosticListener<? super FileObject> diagnosticListener, 
            List<String> modules, String... sourcePaths){
        java.util.List<File> sourceFiles = new ArrayList<File>(sourcePaths.length);
        for(String file : sourcePaths){
            sourceFiles.add(new File(getPackagePath(), file));
        }

        CeyloncTool runCompiler = makeCompiler();
        CeyloncFileManager runFileManager = makeFileManager(runCompiler, diagnosticListener);

        // make sure the destination repo exists
        new File(destDir).mkdirs();

        List<String> options = new LinkedList<String>();
        options.addAll(initialOptions);
        
        if(!options.contains("-out"))
            options.addAll(Arrays.asList("-out", destDir));
        if(!options.contains("-src"))
            options.addAll(Arrays.asList("-src", getSourcePath()));
        if(!options.contains("-cacherep"))
            options.addAll(Arrays.asList("-cacherep", getCachePath()));
        if(!options.contains("-sysrep"))
            options.addAll(Arrays.asList("-sysrep", getSysRepPath()));
        if(!options.contains("-cp"))
            options.addAll(Arrays.asList("-cp", getClassPathAsPath()));
        boolean hasVerbose = false;
        for(String option : options){
            if(option.startsWith("-verbose")){
                hasVerbose = true;
                break;
            }
        }
        if(!hasVerbose)
            options.add("-verbose:ast,code");
        Iterable<? extends JavaFileObject> compilationUnits1 =
                runFileManager.getJavaFileObjectsFromFiles(sourceFiles);
        return runCompiler.getTask(null, runFileManager, diagnosticListener, 
                options, modules, compilationUnits1);
    }

    protected String getSourcePath() {
        return dir;
    }

    protected String getCachePath() {
        return cacheDir;
    }

    protected String getOutPath() {
        return destDir;
    }
    
    protected String getSysRepPath() {
        File sysrep = new File("../dist/dist/repo");
        return sysrep.getAbsolutePath();
    }

    protected ModuleWithArtifact getDestModuleWithArtifact(String main){
        return new ModuleWithArtifact(Module.DEFAULT_MODULE_NAME, null);
    }

    protected File getModuleArchive(String moduleName, String version) {
        return getModuleArchive(moduleName, version, destDir);
    }

    protected static File getModuleArchive(String moduleName, String version, String destDir) {
        return getArchiveName(moduleName, version, destDir, "car");
    }

    protected static File getModuleArchive(String moduleName, String version, String destDir, String extension) {
        return getArchiveName(moduleName, version, destDir, extension);
    }

    protected File getSourceArchive(String moduleName, String version) {
        return getArchiveName(moduleName, version, destDir, "src");
    }
    
    protected static File getSourceArchive(String moduleName, String version, String destDir) {
        return getArchiveName(moduleName, version, destDir, "src");
    }

    protected static File getArchiveName(String moduleName, String version, String destDir, String extension) {
        String modulePath = moduleName.replace('.', File.separatorChar);
        if(version != null)
            modulePath += File.separatorChar+version;
        modulePath += File.separator;
        String artifactName = modulePath+moduleName;
        if(version != null)
            artifactName += "-"+version;
        artifactName += "."+extension;
        File archiveFile = new File(destDir, artifactName);
        return archiveFile;
    }

    protected void runInJBossModules(String module) throws Throwable {
        runInJBossModules("run", module);
    }

    protected void runInJBossModules(String runner, String module) throws Throwable {
        runInJBossModules(runner, module, Collections.<String>emptyList());
    }
    
    protected void runInJBossModules(String runner, String module, List<String> runnerArgs) throws Throwable {
        Assert.assertEquals("Unexpected exit code", 0, runInJBossModules(runner, module, runnerArgs, Collections.<String>emptyList(), null, null));
    }
    
    protected void runInJBossModulesSameVM(String runner, String module, List<String> runnerArgs) throws Throwable{
        // JBoss modules fucks up just about everything. We force loading the Module class, which fucks up the JAXP
        // system properties
        org.jboss.modules.Module.getStartTime();
        // So we restore them immediatly
        __redirected.__JAXPRedirected.restorePlatformFactory();

        System.setProperty(Constants.PROP_CEYLON_HOME_DIR, "../dist/dist");

        int exit = -1;
        // make sure the class loader is cleaned up
        String[] args = new String[4 + runnerArgs.size()];
        args[0] = runner;
        args[1] = "--rep";
        args[2] = getOutPath();
        for(int i=0;i<runnerArgs.size();i++)
        	args[3+i] = runnerArgs.get(i);
        args[args.length-1] = module;
        exit = Launcher.run(true, args);
        Assert.assertEquals(0, exit);

        // Now the TCCL is fucked up, so restore it too
        Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());

        // And restore JAXP again because Launcher reloaded the Module class (and static init) in its own class
        // loader, which re-fucked up the JAXP System properties
        __redirected.__JAXPRedirected.restorePlatformFactory();

    }
    
    /**
     * Executes {@code ceylon run} in a subprocess and returns its status code
     * @param runner The ceylon subcommand to run. This is usually "run".
     * @param module The module to run
     * @param runnerArgs The arguments to pass to the module
     * @param errFile A file to write the subprocess' standard error to, for later inspection
     * @param outFile A file to write the subprocess' standard output to, for later inspection
     * @throws Throwable
     */
    protected int runInJBossModules(String runner, String module, List<String> runnerArgs, List<String> moduleArgs, 
            File errFile, File outFile) throws Throwable {
        /* Run this in its own process because jbmoss modules assumes it
         * owns the VM, and in particular because it likes to call 
         * System.exit() (which will break subsequent tests) while it 
         * also doesn't like to run with a SecurityManager 
         * (which we could otherwise use to prevent System.exit)  
         */
        ArrayList<String> a = new ArrayList<String>();
        a.add("../dist/dist/bin/ceylon");
        a.add(runner);
        a.add("--rep");
        a.add(getOutPath());
        a.addAll(runnerArgs);
        a.add(module);
        a.addAll(moduleArgs);
        System.err.println(a);
        ProcessBuilder pb = new ProcessBuilder(a);
        if (errFile != null) {
            pb.redirectError(errFile);
        } else {
            pb.redirectError(Redirect.INHERIT);
        }
        if (outFile != null) {
            pb.redirectOutput(outFile);
        } else {
            pb.redirectOutput(Redirect.INHERIT);
        }
        // use the same JVM as the current one
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        if(JDKUtils.jdk.providesVersion(JDK.JDK9.version)){
        	pb.environment().put("JAVA_OPTS", "-XaddExports:java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED"
        			+",java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED"
        			+",java.xml/com.sun.xml.internal.stream=ALL-UNNAMED");
        }
        Process p = pb.start();
        return p.waitFor();
    }
    
    protected int runInJava9(String[] repos, ModuleSpec module, List<String> moduleArgs) throws Throwable {
        File mlib = Files.createTempDirectory(new File(destDir).toPath(), "mlib").toFile();
        try{
        	
        	CeylonJigsawTool jigsawTool = new CeylonJigsawTool();
        	jigsawTool.setOut(mlib);
        	jigsawTool.setRepositoryAsStrings(Arrays.asList(repos));
        	jigsawTool.setModules(Arrays.asList(module.getName()));
        	jigsawTool.run();

        	ArrayList<String> a = new ArrayList<String>();
        	String java = System.getProperty("java.home")+"/bin/java";
        	a.add(java);
        	a.add("-modulepath");
        	a.add(mlib.getAbsolutePath());
        	a.add("-m");
        	a.add(Module.LANGUAGE_MODULE_NAME);
        	a.add(module.toString());
        	a.addAll(moduleArgs);
        	System.err.println(a);
        	ProcessBuilder pb = new ProcessBuilder(a);
        	pb.redirectError(Redirect.INHERIT);
        	pb.redirectOutput(Redirect.INHERIT);
        	Process p = pb.start();
        	return p.waitFor();
        }finally{
        	FileUtil.delete(mlib);
        }

    }

    protected void runInMainApi(String rep, ModuleSpec module, String mainJavaClassName, List<String> moduleArgs, boolean debug) throws Throwable {
        runInMainApi(rep, module, Collections.<ModuleSpec>emptyList(), mainJavaClassName, moduleArgs, debug);
    }
    
    protected void runInMainApi(String rep, ModuleSpec module, List<ModuleSpec> extraModules, String mainJavaClassName, List<String> moduleArgs, boolean debug) throws Throwable {
        /* Run this in its own process because jbmoss modules assumes it
         * owns the VM, and in particular because it likes to call 
         * System.exit() (which will break subsequent tests) while it 
         * also doesn't like to run with a SecurityManager 
         * (which we could otherwise use to prevent System.exit)  
         */
        ArrayList<String> a = new ArrayList<String>();
        String java = System.getProperty("java.home")+"/bin/java";
        a.add(java);
        if (debug) {
            a.add("-Xdebug");
            a.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9090");
        }
        a.add("-cp");
        a.add(mainApiClasspath(rep, module, extraModules, debug));
        a.add("com.redhat.ceylon.compiler.java.runtime.Main");
        a.add(module.toString());
        a.add(mainJavaClassName);
        a.addAll(moduleArgs);
        System.err.println(a);
        ProcessBuilder pb = new ProcessBuilder(a);
        pb.inheritIO();
        Process p = pb.start();
        Assert.assertEquals(0, p.waitFor());
    }
    
    protected int runInMainApi(String rep, ModuleSpec module, String mainJavaClassName, List<String> moduleArgs,
            File errFile, File outFile, boolean debug) throws Throwable {
        /* Run this in its own process because jbmoss modules assumes it
         * owns the VM, and in particular because it likes to call 
         * System.exit() (which will break subsequent tests) while it 
         * also doesn't like to run with a SecurityManager 
         * (which we could otherwise use to prevent System.exit)  
         */
        ArrayList<String> a = new ArrayList<String>();
        String java = System.getProperty("java.home")+"/bin/java";
        a.add(java);
        if (debug) {
            a.add("-Xdebug");
            a.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9090");
        }
        a.add("-cp");
        a.add(mainApiClasspath(rep, module, debug));
        a.add("com.redhat.ceylon.compiler.java.runtime.Main");
        a.add(module.toString());
        a.add(mainJavaClassName);
        a.addAll(moduleArgs);
        System.err.println(a);
        ProcessBuilder pb = new ProcessBuilder(a);
        if (errFile != null) {
            pb.redirectError(errFile);
        } else {
            pb.redirectError(Redirect.INHERIT);
        }
        if (outFile != null) {
            pb.redirectOutput(outFile);
        } else {
            pb.redirectOutput(Redirect.INHERIT);
        }
        Process p = pb.start();
        return p.waitFor();
    }
    protected String mainApiClasspath(String rep, ModuleSpec module, boolean debug) throws IOException, InterruptedException {
        return mainApiClasspath(rep, module, Collections.<ModuleSpec>emptyList(), false, debug);
    }
    protected String mainApiClasspath(String rep, ModuleSpec module, List<ModuleSpec> extraModules, boolean debug) throws IOException, InterruptedException {
        return mainApiClasspath(rep, module, extraModules, false, debug);
    }
    protected String mainApiClasspath(String rep, ModuleSpec module, List<ModuleSpec> extraModules, boolean distDowngrade, boolean debug) throws IOException, InterruptedException {
        return mainApiClasspath(rep, module, extraModules, distDowngrade, 0, null, debug);
    }
    protected String mainApiClasspath(String rep, ModuleSpec module, List<ModuleSpec> extraModules, int expectedSc, File err1, boolean debug) throws IOException, InterruptedException {
        return mainApiClasspath(rep, module, extraModules, false, expectedSc, err1, debug);
    }
    protected String mainApiClasspath(String rep, ModuleSpec module, List<ModuleSpec> extraModules,
            boolean distDowngrade, int expectedSc, File err1, boolean debug) throws IOException, InterruptedException {
        
        File dir = new File("build/mainapi");
        dir.mkdirs();
        File out = File.createTempFile("classpath-"+module, ".out", dir);
        File err = err1 != null ? err1 : File.createTempFile("classpath-"+module, ".err", dir);
        ArrayList<String> a = new ArrayList<String>();
        a.add("../dist/dist/bin/ceylon");
        a.add("classpath");
        a.add("--sysrep");
        a.add(getSysRepPath());
        a.add("--rep");
        a.add(rep);
        if (distDowngrade) {
            a.add("--link-with-current-distribution");
        }
        a.add(module.toString());
        for(ModuleSpec extraModule : extraModules)
            a.add(extraModule.toString());
        System.err.println(a);
        ProcessBuilder pb = new ProcessBuilder(a);
        if (debug) {
            pb.environment().put("JAVA_OPTS", "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9090");
        }
        pb.redirectOutput(out);
        pb.redirectError(err);
        Process p = pb.start();
        int sc = p.waitFor();
        
        if (err.length()!=0) {
            if (err1 == null) {
                try (BufferedReader r = new BufferedReader(new FileReader(err))) {
                    String error = r.readLine();
                    String msg = "`ceylon classpath " + module + "` produced standard error output: " + error + " (see " + err + " for full stderr output and " + out + " for stdout output)";
                    System.err.println(msg);
                    Assert.fail(msg);
                    return error;
                }
            }
        }
        try (BufferedReader r = new BufferedReader(new FileReader(out))) {
            String cp = r.readLine();
            System.err.println(cp);
            Assert.assertTrue("ceylon classpath " + module + " produced more than a single line of output", r.readLine() == null);
            Assert.assertEquals(expectedSc, sc);
            return cp;
        }
    }
    
    protected void assertFileContainsLine(File err, String expectedLine) throws IOException, FileNotFoundException {
        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(err))) {
            String line = reader.readLine();
            while(line != null) {
                System.err.println(line);
                if (line.equals(expectedLine)) {
                    found = true;
                    break;
                }
                line = reader.readLine();
            }
            if (!found) {
                Assert.fail("missing expected line");
            }
        }
    }
}
