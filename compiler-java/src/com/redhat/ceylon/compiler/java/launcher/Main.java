/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 */

package com.redhat.ceylon.compiler.java.launcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.MissingResourceException;

import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.tools.SourceArgumentsResolver;
import com.redhat.ceylon.compiler.EnvironmentException;
import com.redhat.ceylon.compiler.java.tools.CeylonLocation;
import com.redhat.ceylon.compiler.java.tools.CeylonLog;
import com.redhat.ceylon.compiler.java.tools.CeyloncFileManager;
import com.redhat.ceylon.compiler.java.tools.LanguageCompiler;
import com.redhat.ceylon.compiler.java.util.Timer;
import com.redhat.ceylon.javax.annotation.processing.Processor;
import com.redhat.ceylon.javax.tools.JavaFileManager;
import com.redhat.ceylon.javax.tools.JavaFileObject;
import com.redhat.ceylon.javax.tools.StandardLocation;
import com.redhat.ceylon.langtools.tools.javac.code.Source;
import com.redhat.ceylon.langtools.tools.javac.file.JavacFileManager;
import com.redhat.ceylon.langtools.tools.javac.jvm.Target;
import com.redhat.ceylon.langtools.tools.javac.main.CommandLine;
import com.redhat.ceylon.langtools.tools.javac.main.JavaCompiler;
import com.redhat.ceylon.langtools.tools.javac.main.Option;
import com.redhat.ceylon.langtools.tools.javac.main.Main.Result;

import static com.redhat.ceylon.langtools.tools.javac.main.Main.Result.*;
import com.redhat.ceylon.langtools.tools.javac.processing.AnnotationProcessingError;
import com.redhat.ceylon.langtools.tools.javac.util.ClientCodeException;
import com.redhat.ceylon.langtools.tools.javac.util.Context;
import com.redhat.ceylon.langtools.tools.javac.util.FatalError;
import com.redhat.ceylon.langtools.tools.javac.util.JavacMessages;
import com.redhat.ceylon.langtools.tools.javac.util.List;
import com.redhat.ceylon.langtools.tools.javac.util.ListBuffer;
import com.redhat.ceylon.langtools.tools.javac.util.Log;
import com.redhat.ceylon.langtools.tools.javac.util.Options;
import com.redhat.ceylon.langtools.tools.javac.util.PropagatedException;
import com.redhat.ceylon.model.cmr.RepositoryException;

/**
 * This class provides a commandline interface to the GJC compiler.
 * 
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on
 * this, you do so at your own risk. This code and its internal interfaces are
 * subject to change or deletion without notice.</b>
 */
public class Main extends com.redhat.ceylon.langtools.tools.javac.main.Main {

    /**
     * The name of the compiler, for use in diagnostics.
     */
    String ownName;

    /**
     * The writer to use for diagnostic output.
     */
    PrintWriter out;

    /**
     * If true, any command line arg errors will cause an exception.
     */
    boolean fatalErrors;

    /**
     * Result codes.
     */
    public static final int EXIT_OK = 0, // Compilation completed with no errors.
            EXIT_ERROR = 1, // Completed but reported errors.
            EXIT_CMDERR = 2, // Bad command-line arguments
            EXIT_SYSERR = 3, // System error or resource exhaustion.
            EXIT_ABNORMAL = 4; // Compiler terminated abnormally

    private Option[] recognizedOptions =
            Option.getJavaCompilerOptions().toArray(new Option[0]);

    /**
     * Construct a compiler instance.
     */
    public Main(String name) {
        this(name, new PrintWriter(System.err, true));
    }

    /**
     * Construct a compiler instance.
     */
    public Main(String name, PrintWriter out) {
        super(name, out);
        this.ownName = name;
        this.out = out;
    }

    /** A timer used to calculate task execution times times */
    private Timer timer = null;
    
    /**
     * Rich information about the failure (or success) of a compilation.
     * 
     * The factory methods on this class serve as a mapping between javacs
     * error handling, and that required for ceylon.
     * 
     * <h3>Gory details</h3>
     * 
     * <h4>Ceylon Syntax and Type Errors</h4>
     * 
     * <p>A `JavacAssertionVisitor` logs an `error()` with key "compiler.err.ceylon"
     * on the javac `Log` (which happens to be a `CeylonLog`)
     * 
     * <h4>Exceptions throw during codegen</h4>
     * 
     * <p>A `JavacAssertionVisitor` logs an `error()` with key "compiler.err.ceylon.codegen.exception"
     * on the javac `Log`
     * (which happens to be a `CeylonLog`)
     * 
     * <h4><code>makeErroneous()</code></h4>
     * 
     * <p>Logs an `error()` with key "ceylon.codegen.erroneous"
     * on the javac `Log` (which happens to be a `CeylonLog`) and returns an `Erroneous`
     * 
     * <h4><code>Tree.JCErroneous</code></h4>
     * 
     * <p>`Attr` produces an error for these.
     * 
     * <h4><code>Diagnostic</code> keys</h4>
     * 
     * </p>`Diagnostic`s in the javac `Log` which don't have a message 
     * code beginning with "compiler.err.ceylon" must therefore be due to codegen generating 
     * an AST which isn't accepted by javacs typechecker. I.e these 
     * represent a bug in codegen.
     * 
     * <h4>IDE Error reporting</h4>
     * 
     * <p>The IDE uses a javac `DiagnosticListener` to find out about errors
     *
     */
    public static class ExitState {
        
        public static enum CeylonState {
            OK,
            ERROR,
            SYS,
            BUG
        }
        
        public final Result javacExitCode;
        
        public final CeylonState ceylonState;
        
        /**
         * Number of errors found during compilation
         */
        public final int errorCount;
        
        /**
         * The exception which caused the compilation to fail with 
         * {@link #EXIT_ABNORMAL} or {@link #EXIT_SYSERR}  
         */
        public final Throwable abortingException;

        public final int ceylonCodegenExceptionCount;
        
        public final int ceylonCodegenErroneousCount;

        public final int ceylonCodegenGarbageCount;

        public final int nonCeylonErrorCount;
        
        private ExitState(Result javacExitCode, CeylonState ceylonState, int errorCount,
                Throwable abortingException, 
                JavaCompiler comp) {
            super();
            this.javacExitCode = javacExitCode;
            this.ceylonState = ceylonState;
            this.errorCount = errorCount;
            this.ceylonCodegenExceptionCount = comp != null ? getCeylonCodegenExceptionCount(comp) : 0;
            this.ceylonCodegenErroneousCount = comp != null ? getCeylonCodegenErroneousCount(comp) : 0;
            this.ceylonCodegenGarbageCount = comp != null ? getCeylonCodegenGarbageTreeCount(comp) : 0;
            this.nonCeylonErrorCount = comp != null ? getNonCeylonErrorCount(comp) : 0;
            this.abortingException = abortingException;
        }
        
        private ExitState(Result javacExitCode, CeylonState ceylonState, int errorCount,
                Throwable abortingException) {
            this(javacExitCode, ceylonState, errorCount, abortingException, null); 
        }
        
        /**
         * javac had errors logged. Causes:
         * <ul>
         * <li>Compiling .java source which had errors
         * <li>Compiling .ceylon source and codegen produced a bad tree
         * <li>Compiling .ceylon source and codegen threw an exception
         * </ul>
         */
        public static ExitState error(JavaCompiler comp) {
            if (hasCeylonCodegenErrors(comp)) {
                // ceylon codegen generates something which was rejected by javac => BUG
                return new ExitState(ERROR, CeylonState.BUG, comp.errorCount(), null, comp);
            } else {   
                return new ExitState(ERROR, CeylonState.ERROR, comp.errorCount(), null);
            }
        }

        private static int getCeylonCodegenExceptionCount(JavaCompiler comp) {
            if (comp.log instanceof CeylonLog) {
                CeylonLog log = ((CeylonLog)comp.log);
                return log.getCeylonCodegenExceptionCount();
            } 
            return 0;
        }
        
        private static int getCeylonCodegenErroneousCount(JavaCompiler comp) {
            if (comp.log instanceof CeylonLog) {
                CeylonLog log = ((CeylonLog)comp.log);
                return log.getCeylonCodegenErroneousCount();
            } 
            return 0;
        }
        
        private static int getCeylonCodegenGarbageTreeCount(JavaCompiler comp) {
            if (comp.log instanceof CeylonLog) {
                CeylonLog log = ((CeylonLog)comp.log);
                return log.getCeylonCodegenGarbageTreeCount();
            } 
            return 0;
        }
        
        private static int getCeylonCodegenErrorCount(JavaCompiler comp) {
            return getCeylonCodegenErroneousCount(comp) 
                    + getCeylonCodegenExceptionCount(comp)
                    + getCeylonCodegenGarbageTreeCount(comp);
        }
        
        private static int getNonCeylonErrorCount(JavaCompiler comp) {
            if (comp.log instanceof CeylonLog) {
                CeylonLog log = ((CeylonLog)comp.log);
                return log.getNonCeylonErrorCount();
            } 
            return 0;
        }

        private static int getCeylonErrorCount(JavaCompiler comp) {
            if (comp.log instanceof CeylonLog) {
                CeylonLog log = ((CeylonLog)comp.log);
                return log.getCeylonErrorCount();
            } 
            return 0;
        }

        private static boolean hasCeylonCodegenErrors(JavaCompiler comp) {
            return getNonCeylonErrorCount(comp) == 0
                    && getCeylonErrorCount(comp) == 0
                    && getCeylonCodegenErrorCount(comp) > 0;
        }
        
        public boolean hasCeylonCodegenErrors() {
            return this.ceylonCodegenErroneousCount > 0
                    || this.ceylonCodegenExceptionCount > 0 
                    || this.nonCeylonErrorCount > 0;
        }

        // More nastiness
        private static boolean isAbnormalException(Throwable ex) {
            // For now we'll just assume that in principle any runtime exception is abnormal
            // Errors are considered normal because javac uses them
            if (ex instanceof RuntimeException
                    // We also assume our own errors are not abnormal
                    && !ex.getClass().getName().startsWith("com.redhat.ceylon.")
                    // And neither are CCEs from the javac code abnormal
                    && !(ex instanceof ClassCastException && ex.getMessage().contains("com.redhat.ceylon.langtools."))) {
                return true;
            }
            return false;
        }
        
        public static ExitState ok() {
            return new ExitState(ERROR, CeylonState.OK, 0, null, null);
        }

        /**
         * uncaught exception. Causes:
         * <ul>
         * <li>Despite some earlier javac phase logging an error, it's 
         *     resulted in an exception (typically AssertionError) being thrown 
         *     by a later phase. Javac uses a heuristic to determin whether
         *     an uncaught exception is really a bug
         * <li>Despite of the heuristic, if there are any backend errors, we 
         *     consider that a bug in the ceylon compiler
         * <li>Otherwise there were errors (which were not ceylon backend 
         *     errors), so the heuristic implies we treat it as an error.
         * </ul>
         */
        public static ExitState abnormal(JavaCompiler comp, Throwable ex,
                Options options) {
            if (comp == null || comp.errorCount() == 0 || options == null || options.get("dev") != null) {
                // This is the heuristic javac uses
                return new ExitState(ABNORMAL, CeylonState.BUG, 0, ex, null);
            } else if (hasCeylonCodegenErrors(comp) || isAbnormalException(ex)) {
                return new ExitState(ABNORMAL, CeylonState.BUG, comp.errorCount(), ex, comp);
            }
            return new ExitState(ABNORMAL, CeylonState.ERROR, comp.errorCount(), null, null);
        }
        
        public static ExitState systemError(JavaCompiler comp, Throwable ex) {
            // Note: ex can be null
            if (comp instanceof LanguageCompiler && 
                    ((LanguageCompiler)comp).getTreatLikelyBugsAsErrors()) {
                if (hasCeylonCodegenErrors(comp) ) {
                    return new ExitState(ERROR, CeylonState.BUG, comp.errorCount(), ex, comp);
                } else {
                    return new ExitState(ERROR, CeylonState.ERROR, 0, ex, null);
                }
            }
            return new ExitState(SYSERR, CeylonState.SYS, 0, ex, null);
        }

        public static ExitState cmderror() {
            // icky: We'd prefer this to be handled at the tool API level 
            return new ExitState(CMDERR, CeylonState.BUG, 0, null, null);
        }    
    }
    
    public ExitState exitState = null;

    /**
     * Report a usage error.
     */
    void error(String key, Object... args) {
        if (fatalErrors) {
            String msg = getLocalizedString(key, args);
            throw new PropagatedException(new IllegalStateException(msg));
        }
        warning(key, args);
        out.println(getLocalizedString("msg.usage", ownName));
    }

    /**
     * Report a warning.
     */
    void warning(String key, Object... args) {
        out.println(ownName + ": " + getLocalizedString(key, args));
    }

    public Option getOption(String flag) {
        for (Option option : recognizedOptions) {
            if (option.matches(flag))
                return option;
        }
        return null;
    }

    public void setOptions(Options options) {
        if (options == null)
            throw new NullPointerException();
        this.options = options;
    }

    public void setFatalErrors(boolean fatalErrors) {
        this.fatalErrors = fatalErrors;
    }

    /**
     * Process command line arguments: store all command line options in
     * `options' table and return all source filenames.
     * @param flags The array of command line arguments.
     */
    @Override
    public Collection<File> processArgs(String[] flags, String[] classNames) {
        int ac = 0;
        while (ac < flags.length) {
            String flag = flags[ac];
            ac++;

            int j;
            // quick hack to speed up file processing:
            // if the option does not begin with '-', there is no need to check
            // most of the compiler options.
            int firstOptionToCheck = flag.charAt(0) == '-' ? 0 : recognizedOptions.length - 1;
            for (j = firstOptionToCheck; j < recognizedOptions.length; j++)
                if (recognizedOptions[j].matches(flag))
                    break;

            if (j == recognizedOptions.length) {
                error("err.invalid.flag", flag);
                return null;
            }

            Option option = recognizedOptions[j];
            if (option.hasArg()) {
                if (ac == flags.length) {
                    error("err.req.arg", flag);
                    return null;
                }
                String operand = flags[ac];
                ac++;
                if (option.process(optionHelper, flag, operand))
                    return null;
            } else {
                if (option.process(optionHelper, flag))
                    return null;
            }
        }

        if (this.classnames != null && classNames != null) {
            this.classnames.addAll(Arrays.asList(classNames));
        }
        
        if (!checkDirectoryOrURL("-d"))
            return null;
        if (!checkDirectory("-s"))
            return null;

        String sourceString = options.get("-source");
        if (sourceString == null) {
            sourceString = "7";
            options.put(Option.SOURCE, sourceString);
        }
        Source source = (sourceString != null) ? Source.lookup(sourceString) : Source.DEFAULT;
        String targetString = options.get("-target");
        if (targetString == null) {
            targetString = "7";
            options.put(Option.TARGET, targetString);
        }
        Target target = (targetString != null) ? Target.lookup(targetString) : Target.JDK1_7;
        // We don't check source/target consistency for CLDC, as J2ME
        // profiles are not aligned with J2SE targets; moreover, a
        // single CLDC target may have many profiles. In addition,
        // this is needed for the continued functioning of the JSR14
        // prototype.
        if (Character.isDigit(target.name.charAt(0))) {
            if (target.compareTo(source.requiredTarget()) < 0) {
                if (targetString != null) {
                    if (sourceString == null) {
                        warning("warn.target.default.source.conflict", targetString, source.requiredTarget().name);
                    } else {
                        warning("warn.source.target.conflict", sourceString, source.requiredTarget().name);
                    }
                    return null;
                } else {
                    options.put("-target", source.requiredTarget().name);
                }
            } else {
                if (targetString == null && !source.allowGenerics()) {
                    options.put("-target", Target.JDK1_4.name);
                }
            }
        }
        return filenames;
    }

    // where
    private boolean checkDirectory(String optName) {
        String value = options.get(optName);
        if (value == null)
            return true;
        File file = new File(value);
        if (!file.exists()) {
            error("err.dir.not.found", value);
            return false;
        }
        if (!file.isDirectory()) {
            error("err.file.not.directory", value);
            return false;
        }
        return true;
    }

    /**
     * Checker whether optName is a valid URL or directory
     * @param optName The option name
     * @return
     */
    private boolean checkDirectoryOrURL(String optName) {
        String value = options.get(optName);
        if (value == null)
            return true;
        try{
            URL url = new URL(value);
            String scheme = url.getProtocol();
            if("http".equals(scheme) || "https".equals(scheme))
                return true;
            error("ceylon.err.output.repo.not.supported", value);
            return false;
        }catch(MalformedURLException x){
            // not a URL, perhaps a file?
        }
        File file = new File(value);
        if (file.exists() && !file.isDirectory()) {
            error("err.file.not.directory", value);
            return false;
        }
        return true;
    }

    /**
     * Programmatic interface for main function.
     * @param args The command line parameters.
     */
    @Override
    public Result compile(String[] args) {
        Context context = new Context();
        CeyloncFileManager.preRegister(context); // can't create it until Log
                                                 // has been set up
        CeylonLog.preRegister(context);
        Result result = compile(args, context);
        if (fileManager instanceof JavacFileManager) {
            // A fresh context was created above, so jfm must be a
            // JavacFileManager
            ((JavacFileManager) fileManager).close();
        }
        return result;
    }

    @Override
    public Result compile(String[] args, Context context) {
        return compile(args, context, List.<JavaFileObject> nil(), null);
    }

    @Override
    public Result compile(String[] args,
            Context context,
            List<JavaFileObject> fileObjects,
            Iterable<? extends Processor> processors) {
        return compile(args, null, context, fileObjects, processors);
    }
    
    /**
     * Programmatic interface for main function.
     * @param args The command line parameters.
     */
    @Override
    public Result compile(String[] args,
            String[] classNames,
            Context context,
            List<JavaFileObject> fileObjects,
            Iterable<? extends Processor> processors) {
        if (options == null) {
            options = Options.instance(context); // creates a new one
        }

        filenames = new LinkedHashSet<File>();
        classnames = new ListBuffer<String>();
        exitState = ExitState.cmderror();
        JavaCompiler comp = null;
        /* TODO: Logic below about what is an acceptable command line should be
         * updated to take annotation processing semantics into account. */
        try {
            if (args.length == 0 && fileObjects.isEmpty()) {
                //super.help();
                this.exitState = ExitState.cmderror();
                return CMDERR;
            }
            Collection<File> filenames = processArgs(CommandLine.parse(args), classNames);
            if (filenames == null) {
                // null signals an error in options, abort
                this.exitState = ExitState.cmderror();
                return CMDERR;
            } else if (filenames.isEmpty() && fileObjects.isEmpty() && classnames.isEmpty()) {
                // it is allowed to compile nothing if just asking for help
                // or version info
                if (options.get("-help") != null 
                        || options.get("-jhelp") != null 
                        || options.get("-X") != null 
                        || options.get("-version") != null 
                        || options.get("-fullversion") != null)
                    return OK;
                error("err.no.source.files");
                this.exitState = ExitState.cmderror();
                return CMDERR;
            }

            // Set up the timer *after* we've processed to options
            // because it needs to know if we need logging or not
            timer = Timer.instance(context);
            timer.init();
            
            boolean forceStdOut = options.get("stdout") != null;
            if (forceStdOut) {
                out.flush();
                out = new PrintWriter(System.out, true);
            }

            context.put(Log.outKey, out);

            fileManager = context.get(JavaFileManager.class);

            comp = LanguageCompiler.instance(context);
            if (comp == null) {
                this.exitState = ExitState.systemError(null, null);
                return SYSERR;
            }

            if(!classnames.isEmpty()) {
                this.filenames.addAll(addModuleFiles(filenames));
                classnames.clear();
            }
            
            if (!this.filenames.isEmpty()) {
                // add filenames to fileObjects
                List<JavaFileObject> otherFiles = List.nil();
                JavacFileManager dfm = (JavacFileManager) fileManager;
                for (JavaFileObject fo : dfm.getJavaFileObjectsFromFiles(this.filenames)) {
                    otherFiles = otherFiles.append(fo);
                }
                fileObjects = fileObjects.prependList(otherFiles);
            }
            if(fileObjects.isEmpty()){
                error("err.no.source.files");
                this.exitState = ExitState.cmderror();
                return CMDERR;
            }
            comp.compile(fileObjects, classnames.toList(), processors);

            int errorCount = comp.errorCount();
            //ceylonBackendErrors = comp.log instanceof CeylonLog ? ((CeylonLog)comp.log).ceylonBackendErrors() : false;
            if (errorCount != 0) {
                this.exitState = ExitState.error(comp);
                return ERROR;
            }
        } catch (IOException ex) {
            ioMessage(ex);
            this.exitState = ExitState.systemError(null, ex);
            return SYSERR;
        } catch (OutOfMemoryError ex) {
            resourceMessage(ex);
            this.exitState = ExitState.systemError(null, ex);
            return SYSERR;
        } catch (StackOverflowError ex) {
            resourceMessage(ex);
            this.exitState = ExitState.systemError(null, ex);
            return SYSERR;
        } catch (FatalError ex) {
            this.exitState = ExitState.systemError(comp, ex);
            if (this.exitState.javacExitCode == SYSERR) {
                feMessage(ex);
            }
            return this.exitState.javacExitCode;
        } catch (AnnotationProcessingError ex) {
            apMessage(ex);
            this.exitState = ExitState.systemError(null, ex);
            return SYSERR;
        } catch (ClientCodeException ex) {
            // as specified by javax.tools.JavaCompiler#getTask
            // and javax.tools.JavaCompiler.CompilationTask#call
            throw new RuntimeException(ex.getCause());
        } catch (PropagatedException ex) {
            throw ex.getCause();
        } catch (RepositoryException ex) {
            throw new EnvironmentException(ex);
        } catch (Throwable ex) {
            // Nasty. If we've already reported an error, compensate
            // for buggy compiler error recovery by swallowing thrown
            // exceptions.
            if (comp == null || comp.errorCount() == 0 || options == null || options.get("dev") != null) {
                bugMessage(ex);
            }
            this.exitState = ExitState.abnormal(comp, ex, options);
            return ABNORMAL;
        } finally {
            if (comp != null)
                comp.close();
            filenames = null;
            options = null;
            if (timer != null) {
                timer.end();
            }
            timer = null;
        }
        this.exitState = ExitState.ok();
        return OK;
    }

    // Now add the files for each of the modules that were given on the command line
    @SuppressWarnings("unchecked")
    private ListBuffer<File> addModuleFiles(Collection<File> f) throws IOException {
        Iterable<File> srcdirs = (Iterable<File>) ((JavacFileManager)fileManager).getLocation(StandardLocation.SOURCE_PATH);
        Iterable<File> resdirs = (Iterable<File>) ((JavacFileManager)fileManager).getLocation(CeylonLocation.RESOURCE_PATH);
        SourceArgumentsResolver resolver = new SourceArgumentsResolver(srcdirs, resdirs, Constants.CEYLON_SUFFIX, Constants.JAVA_SUFFIX);
        resolver.parse(classnames.toList());
        ListBuffer<File> filenames = ListBuffer.<File>lb();
        filenames.addAll(f);
        filenames.addAll(resolver.getSourceFiles());
        filenames.addAll(resolver.getResourceFiles());
        return filenames;
    }
    
    private List<File> appendAll(List<File> target, java.util.List<File> source) {
        for (File f : source) {
            target = target.append(f);
        }
        return target;
    }
    
    /**
     * Print a message reporting an internal error.
     */
    void bugMessage(Throwable ex) {
        out.println(getLocalizedString("msg.bug", JavaCompiler.version()));
        ex.printStackTrace(out);
    }

    /**
     * Print a message reporting an fatal error.
     */
    void feMessage(Throwable ex) {
        out.println(ex.getMessage());
    }

    /**
     * Print a message reporting an input/output error.
     */
    void ioMessage(Throwable ex) {
        out.println(getLocalizedString("msg.io"));
        ex.printStackTrace(out);
    }

    /**
     * Print a message reporting an out-of-resources error.
     */
    void resourceMessage(Throwable ex) {
        out.println(getLocalizedString("msg.resource"));
        // System.out.println("(name buffer len = " + Name.names.length + " " +
        // Name.nc);//DEBUG
        ex.printStackTrace(out);
    }

    /**
     * Print a message reporting an uncaught exception from an annotation
     * processor.
     */
    void apMessage(AnnotationProcessingError ex) {
        out.println(getLocalizedString("msg.proc.annotation.uncaught.exception"));
        ex.getCause().printStackTrace();
    }

    private JavaFileManager fileManager;

    /* ************************************************************************
     * Internationalization
     * *********************************************************************** */

    /**
     * Find a localized string in the resource bundle.
     * @param key The key for the localized string.
     */
    public static String getLocalizedString(String key, Object... args) { // FIXME
                                                                          // sb
                                                                          // private
        try {
            if (messages == null)
                messages = new JavacMessages(javacBundleName);
            return messages.getLocalizedString("javac." + key, args);
        } catch (MissingResourceException e) {
            throw new Error("Fatal Error: Resource for javac is missing", e);
        }
    }

    public static void useRawMessages(boolean enable) {
        if (enable) {
            messages = new JavacMessages(javacBundleName) {
                @Override
                public String getLocalizedString(String key, Object... args) {
                    return key;
                }
            };
        } else {
            messages = new JavacMessages(javacBundleName);
        }
    }

    private static final String javacBundleName = "com.redhat.ceylon.langtools.tools.javac.resources.ceylonc";

    private static JavacMessages messages;
    
    
}
