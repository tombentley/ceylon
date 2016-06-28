package com.redhat.ceylon.compiler.java.test.reporting;

import java.util.Arrays;

import org.junit.Test;

import com.redhat.ceylon.compiler.java.test.CompilerError;
import com.redhat.ceylon.compiler.java.test.CompilerTests;
import com.redhat.ceylon.compiler.java.test.JdkVersionDependentTests;
import com.redhat.ceylon.javax.tools.Diagnostic.Kind;

public class ReportingTests extends JdkVersionDependentTests {
    
    public ReportingTests(String target, String source) {
        super(target, source);
        getDefaultOptions().clear();
        getDefaultOptions().addAll(Arrays.asList("-out", destDir, "-cacherep", cacheDir, "-g", 
                "-cp", getClassPathAsPath()));
    }
    
    @Test
    public void testWarningSuppressionCmdLine() {
        
    }
    
    @Test
    public void testAnnoSuppressionUnusedDecl() {
        compilesWithoutWarnings("UnusedDeclaration.ceylon");
    }
    
    @Test
    public void testOptionSuppressionUnusedImport() {
        compilesWithoutWarnings(Arrays.asList("-suppress-warnings", "unusedImport"), "UnusedImport.ceylon");
    }
    
    @Test
    public void testAnnoSuppressesNothing() {
        assertErrors(new String[]{"SuppressesNothing.ceylon"},
                getDefaultOptions(),
                null,
                new CompilerError(Kind.WARNING, "", 1, "suppresses no warnings"));
    }
    
    @Test
    public void testAnnoAlreadySuppressed() {
        // We warn about warnings which are suppressed by an annotation on an outer declaration
        assertErrors(new String[]{"AlreadySuppressed.ceylon"},
                getDefaultOptions(),
                null,
                new CompilerError(Kind.WARNING, "", 3, "warnings already suppressed by annotation"));
    }
    
    @Test
    public void testAnnoAlreadySuppressed2() {
        // 
        getDefaultOptions().add("-suppress-warnings");
        getDefaultOptions().add("unusedDeclaration");
        assertErrors(new String[]{"AlreadySuppressed.ceylon"},
                getDefaultOptions(),
                null,
                new CompilerError(Kind.WARNING, "", 3, "warnings already suppressed by annotation"));
    }
    
    @Test
    public void testUnknownWarningInAnno() {
        assertErrors(new String[]{"UnknownWarningInAnno.ceylon"},
                getDefaultOptions(),
                null,
                new CompilerError(Kind.WARNING, "", 1, "unknown warning: blahblah"));
    }
    

}
