package com.redhat.ceylon.compiler.java.test.fordebug;

import java.io.File;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.fordebug.Tracer.HandlerResult;
import com.redhat.ceylon.compiler.java.test.fordebug.Tracer.MethodEntry;
import com.redhat.ceylon.compiler.java.test.fordebug.Tracer.MethodExit;
import com.redhat.ceylon.compiler.java.test.fordebug.Tracer.Step;

public class TraceTests extends DebuggerTests {
    
    public TraceTests(String[] compilerArgs) {
        super(compilerArgs);
    }

    @Override
    protected String transformDestDir(String name) {
        return name + "-trace";
    }
    
    private void compileAndTrace(String mainClass, String ceylonSource) throws Exception {
        compareWithJavaSource(ceylonSource);
        trace(ceylonSource+".ceylon", mainClass);
    }

    private void trace(String ceylonSource, String mainClass) throws Exception {
        String sourceName = new File(ceylonSource).getName();
        String traceFile = ceylonSource.replaceAll(".ceylon$", ".trace");
        try (Tracer tracer = tracer(mainClass)) {
            tracer.start();
            // stop when we enter main()
            MethodEntry entry = tracer.methodEntry().classFilter(mainClass).methodFilter("main").result(HandlerResult.SUSPEND).enable();
            // resume until we hit that, then disable it
            tracer.resume();
            entry.disable();
            // now step into everything, logging only events which come from code in the given source file.
            Step step = tracer.step().within(sourceName).log().enable();
            // and also listen out for when we exit main()
            MethodExit exit = tracer.methodExit().classFilter(mainClass).methodFilter("main").result(HandlerResult.SUSPEND).enable();
            // once we've exited main() disable the step breakpoint
            tracer.resume();
            if (tracer.isVmAlive()) {
                step.disable();
                tracer.resume();
            }
            System.err.println(tracer.getTrace());
            assertSameTrace(tracer, traceFile);
        }
    }
    
    @Test
    public void testAssertFalse() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.assertFalse_", 
                "trace/AssertFalse"
                );
    }
    
    @Test
    public void testDefaultParameters() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.defaultedParametersMain_",
                "trace/DefaultedParameters"
                );
    }
    
    @Test
    public void testBug2043() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.bug2043_",
                "trace/Bug2043"
                );
    }
    
    @Test
    public void testBug2046() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.bug2046_",
                "trace/Bug2046"
                );
    }
    
    @Test
    public void testBug2047() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.bug2047_",
                "trace/Bug2047"
                );
    }
    
    @Test
    public void testSwitch() throws Exception {
        compileAndTrace(
                "com.redhat.ceylon.compiler.java.test.fordebug.trace.swtch_",
                "trace/Switch"
                );
    }
}
