package com.redhat.ceylon.compiler.java.test.statement;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.JdkVersionDependentTests;

public class TryCatchTests extends JdkVersionDependentTests {

    public TryCatchTests(String target, String source) {
        super(target, source);
    }

    @Override
    protected String transformDestDir(String name) {
        return name + "-trycatch";
    }
    
    @Override
    protected ModuleWithArtifact getDestModuleWithArtifact(String main) {
        return new ModuleWithArtifact("com.redhat.ceylon.compiler.java.test.statement.trycatch", "1");
    }

    @Test
    public void testTryExceptionTypes(){
        compile("trycatch/JavaThrower.java");
        compareWithJavaSource("trycatch/ExceptionTypes");
    }
    
    @Test
    public void testTryExceptionStrings(){
        compileAndRun("com.redhat.ceylon.compiler.java.test.statement.trycatch.exceptionStrings",
                "trycatch/ExceptionStrings.ceylon");
    }
    
    @Test
    public void testTryExceptionAttr(){
        compareWithJavaSource("trycatch/ExceptionAttr");
    }
    
    @Test
    public void testTryExceptionAttributes(){
        compareWithJavaSource("trycatch/ExceptionAttributes");
    }
    
    @Test
    public void testTryExceptionSuppressed(){
        compileAndRun("com.redhat.ceylon.compiler.java.test.statement.trycatch.exceptionSuppressed",
                "trycatch/ExceptionSuppressed.ceylon");
    }
    
    @Test
    public void testTryBareThrow(){
        compareWithJavaSource("trycatch/Throw");
    }
    
    @Test
    public void testTryThrowNothing(){
    	compareWithJavaSource("trycatch/ThrowNothing");
    }

    @Test
    public void testTryThrowException(){
        compareWithJavaSource("trycatch/ThrowException");
    }
    
    @Test
    public void testTryThrowExceptionNamedArgs(){
        compareWithJavaSource("trycatch/ThrowExceptionNamedArgs");
    }
    
    @Test
    public void testTryThrowExceptionSubclass(){
        compareWithJavaSource("trycatch/ThrowExceptionSubclass");
    }
    
    @Test
    public void testTryThrowMethodResult(){
        compareWithJavaSource("trycatch/ThrowMethodResult");
    }
    
    @Test
    public void testTryThrowThrowable(){
        compareWithJavaSource("trycatch/ThrowThrowable");
    }
    
    @Test
    public void testTryThrowNpe(){
        compareWithJavaSource("trycatch/ThrowNpe");
    }
    
    @Test
    public void testTryTryFinally(){
        compareWithJavaSource("trycatch/TryFinally");
    }
    
    @Test
    public void testTryTryCatch(){
        compareWithJavaSource("trycatch/TryCatch");
    }
    
    @Test
    public void testTryTryCatchError(){
        compareWithJavaSource("trycatch/TryCatchError");
        run("com.redhat.ceylon.compiler.java.test.statement.trycatch.tryCatchError");
    }
    
    @Test
    public void testTryTryCatchErrorAssertionError(){
        compareWithJavaSource("trycatch/TryCatchErrorAssertionError");
        run("com.redhat.ceylon.compiler.java.test.statement.trycatch.tryCatchErrorAssertionError");
    }
    
    @Test
    public void testTryTryCatchFinally(){
        compareWithJavaSource("trycatch/TryCatchFinally");
    }
    
    @Test
    public void testTryTryCatchSubclass(){
        compareWithJavaSource("trycatch/TryCatchSubclass");
    }
    
    @Test
    public void testTryTryCatchUnion(){
        compareWithJavaSource("trycatch/TryCatchUnion");
    }
    
    @Test
    public void testTryTryCatchGenericIntersection(){
        compareWithJavaSource("trycatch/TryCatchGenericIntersection");
        //compile("trycatch/TryCatchGenericIntersection.ceylon");
        run("com.redhat.ceylon.compiler.java.test.statement.trycatch.tryCatchGenericIntersection");
    }
    
    @Test
    public void testTryTryWithResource(){
        compareWithJavaSource("trycatch/TryWithResource");
    }
    
    @Test
    public void testTryReplaceExceptionAtJavaCallSite(){
        compile("trycatch/JavaThrower.java");
        compareWithJavaSource("trycatch/WrapExceptionAtJavaCallSite");
    }
    
    @Test
    public void testTryTrySelfSuppression(){
        compileAndRun("com.redhat.ceylon.compiler.java.test.statement.trycatch.trySelfSuppression", 
                "trycatch/TrySelfSuppression.ceylon");
    }

}
