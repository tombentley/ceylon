package com.redhat.ceylon.compiler.java.codegen;

import java.util.EnumSet;
import java.util.List;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Annotation;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.compiler.typechecker.util.NativeUtil;
import com.redhat.ceylon.model.loader.JvmBackendUtil;
import com.redhat.ceylon.model.loader.model.OutputElement;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.Unit;

public class UnsupportedVisitor extends Visitor {
    
    static final String DYNAMIC_UNSUPPORTED_ERR = "dynamic is not supported on the JVM";

    @Override
    public void visit(Tree.Annotation that) {
        String msg = AnnotationInvocationVisitor.checkForBannedJavaAnnotation(that);
        if (msg != null) {
            that.addError(msg, Backend.Java);
        }
        super.visit(that);
    }

    @Override
    public void visit(Tree.FloatLiteral that) {
        try {
            ExpressionTransformer.literalValue(that);
        } catch (ErroneousException e) {
            that.addError(e.getMessage(), Backend.Java);
        }
        super.visit(that);
    }
    
    @Override
    public void visit(Tree.NaturalLiteral that) {
        try {
            ExpressionTransformer.literalValue(that);
        } catch (ErroneousException e) {
            that.addError(e.getMessage(), Backend.Java);
        }
        super.visit(that);
    }
    
    public void visit(Tree.NegativeOp that) {
        if (that.getTerm() instanceof Tree.NaturalLiteral) {
            try {
                ExpressionTransformer.literalValue(that);
            } catch (ErroneousException e) {
                that.addError(e.getMessage(), Backend.Java);
            }
        } else {
            super.visit(that);
        }
    }

    public void visit(Tree.AttributeGetterDefinition that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }

    public void visit(Tree.AttributeSetterDefinition that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.AttributeDeclaration that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.ObjectDefinition that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.AnyClass that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.Constructor that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.Enumerated that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.AnyInterface that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }
    
    public void visit(Tree.AnyMethod that) {
        if (!NativeUtil.isForBackend(that, Backend.Java))
            return;
        interopAnnotationTargeting(AnnotationUtil.outputs(that), that.getAnnotationList());
        super.visit(that);
    }

    private void interopAnnotationTargeting(EnumSet<OutputElement> outputs,
            Tree.AnnotationList annotationList) {
        List<Annotation> annotations = annotationList.getAnnotations();
        for (Tree.Annotation annotation : annotations) {
            AnnotationUtil.interopAnnotationTargeting(outputs, annotation, true);
        }
        AnnotationUtil.duplicateInteropAnnotation(outputs, annotations);
    }
    
    @Override
    public void visit(Tree.TypeConstraint that) {
        if (that.getSatisfiedTypes() != null) {
            for (Tree.StaticType t : that.getSatisfiedTypes().getTypes()) {
                if (t.getTypeModel() != null 
                        && JvmBackendUtil.isJavaArray(t.getTypeModel().getDeclaration())) {
                    t.addError("Type parameter cannot be bounded by a Java array", Backend.Java);
                }
            }
        }
        super.visit(that);
    }
    
    @Override
    public void visit(Tree.BaseType that) {
        super.visit(that);
        Unit unit = that.getUnit();
        if (unit.isJavaObjectArrayType(that.getTypeModel())) {
            Type ta = that.getTypeModel().getSupertype(unit.getJavaObjectArrayDeclaration()).getTypeArgumentList().get(0);
            if (ta.isNothing()
                    || ta.isIntersection()
                    || unit.getDefiniteType(ta).isUnion()) {
                that.addError("illegal type argument in Java array type: arrays with element type " + ta.asString(unit) + " are not permitted", Backend.Java);
            }
        }
        
    }
    

}
